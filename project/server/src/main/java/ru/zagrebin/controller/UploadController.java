package ru.zagrebin.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.zagrebin.service.FileStorageService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final FileStorageService fileStorageService;

    public UploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * POST /api/v1/uploads/{type}
     * type: cover | step | avatar
     * returns JSON { "url": "/media/..." }
     */
    @PostMapping(value = "/{type}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(
            @PathVariable String type,
            @RequestPart("file") MultipartFile file
    ) {
        String subdir;
        switch (type) {
            case "cover" -> subdir = "posts/covers";
            case "step" -> subdir = "posts/steps";
            case "avatar" -> subdir = "users/avatars";
            default -> subdir = "misc";
        }
        String url = fileStorageService.store(file, subdir);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
