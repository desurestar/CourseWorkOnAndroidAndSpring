package ru.zagrebin.service;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

public interface FileStorageService {
    /**
     * Сохранить файл (аватар/cover/step). Возвращает публичный URL (или путь), который сохраняется в БД.
     */
    String store(MultipartFile file, String targetSubdir);

    /**
     * Удалить файл (по URL/пути). Возвращает true если файл был удалён.
     */
    boolean delete(String fileUrl);

    /**
     * Полезный метод: вернуть абсолютный путь на диске для заданного relativePath
     */
    Path resolvePath(String relativePath);
}
