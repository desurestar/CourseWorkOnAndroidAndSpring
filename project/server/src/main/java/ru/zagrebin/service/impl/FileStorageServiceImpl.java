package ru.zagrebin.service.impl;

import jakarta.annotation.PostConstruct;
import ru.zagrebin.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;


import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageServiceImpl implements FileStorageService {

    // Абсолютный путь к папке MEDIA на диске, задаётся в application.yml
    @Value("${media.path}")
    private String mediaRoot;

    // Публичный префикс URL, например "/media" или полный http://cdn.example.com/media
    @Value("${media.public-url-prefix:/media}")
    private String publicUrlPrefix;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Map<String, String> CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".webp");

    @PostConstruct
    public void init() throws IOException {
        if (mediaRoot == null || mediaRoot.isBlank()) {
            throw new IllegalStateException("media.path is not configured");
        }
        Path root = Path.of(mediaRoot);
        if (!Files.exists(root)) {
            Files.createDirectories(root);
            log.info("Created media directory: {}", root.toAbsolutePath());
        }
    }

    @Override
    public String store(MultipartFile file, String targetSubdir) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("file is empty");
        String original = StringUtils.cleanPath(file.getOriginalFilename());
        // prevent path traversal
        if (original.contains("..")) {
            throw new IllegalArgumentException("Invalid file name: " + original);
        }

        String contentType = file.getContentType();
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file type");
        }

        String ext = "";
        int idx = original.lastIndexOf('.');
        if (idx > 0) ext = original.substring(idx).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            ext = CONTENT_TYPE_EXTENSIONS.getOrDefault(
                    contentType != null ? contentType.toLowerCase() : "",
                    null
            );
        }
        if (ext == null || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported file extension");
        }

        String filename = UUID.randomUUID().toString() + ext;
        Path subdir = (targetSubdir == null || targetSubdir.isBlank())
                ? Path.of("")
                : Path.of(targetSubdir);
        Path targetDir = Path.of(mediaRoot).resolve(subdir).normalize();

        try {
            Files.createDirectories(targetDir);
            Path target = targetDir.resolve(filename).normalize();

            // additional check: target must be inside mediaRoot
            Path mediaRootPath = Path.of(mediaRoot).toAbsolutePath().normalize();
            if (!target.toAbsolutePath().normalize().startsWith(mediaRootPath)) {
                throw new SecurityException("Attempt to write outside media root");
            }

            // write file (overwrite not expected due to UUID)
            try (var is = file.getInputStream()) {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            }

            // Return public URL (prefix + relative path)
            String relative = mediaRootPath.relativize(target.toAbsolutePath()).toString().replace('\\', '/');
            String publicUrl = joinUrl(publicUrlPrefix, relative);
            return publicUrl;
        } catch (IOException e) {
            log.error("Failed to store file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public boolean delete(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return false;
        // Convert publicUrlPrefix + relative -> real path
        try {
            String relative = fileUrl;
            // if fileUrl contains publicUrlPrefix, strip it
            if (fileUrl.startsWith(publicUrlPrefix)) {
                relative = fileUrl.substring(publicUrlPrefix.length());
            } else {
                // if full URL provided (http...), try to extract path part
                try {
                    URI uri = URI.create(fileUrl);
                    relative = uri.getPath();
                    if (relative.startsWith(publicUrlPrefix)) relative = relative.substring(publicUrlPrefix.length());
                } catch (Exception ignored) {}
            }
            // remove leading slash
            if (relative.startsWith("/")) relative = relative.substring(1);
            Path target = Path.of(mediaRoot).resolve(relative).normalize();

            Path mediaRootPath = Path.of(mediaRoot).toAbsolutePath().normalize();
            if (!target.toAbsolutePath().normalize().startsWith(mediaRootPath)) {
                log.warn("Attempt to delete file outside media root: {}", target);
                return false;
            }
            return Files.deleteIfExists(target);
        } catch (Exception ex) {
            log.warn("Failed to delete file {}: {}", fileUrl, ex.getMessage());
            return false;
        }
    }

    private String joinUrl(String a, String b) {
        if (a.endsWith("/")) a = a.substring(0, a.length()-1);
        if (!b.startsWith("/")) b = "/" + b;
        return a + b;
    }
}
