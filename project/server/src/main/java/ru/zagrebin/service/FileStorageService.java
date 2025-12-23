package ru.zagrebin.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    /**
     * Сохранить файл (аватар/cover/step). Возвращает публичный URL (или путь), который сохраняется в БД.
     */
    String store(MultipartFile file, String targetSubdir);

    /**
     * Удалить файл (по URL/пути). Возвращает true если файл был удалён.
     */
    boolean delete(String fileUrl);
}
