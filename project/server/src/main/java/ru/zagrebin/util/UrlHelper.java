package ru.zagrebin.util;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class UrlHelper {
    private UrlHelper() {}

    /**
     * Возвращает абсолютный URL для ресурса, если в текущем запросе доступен контекст.
     * Если строка уже содержит схему (http/https) или пустая, возвращается как есть.
     */
    public static String toAbsolute(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }

        String path = trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            try {
                return ServletUriComponentsBuilder.fromCurrentContextPath()
                        .path(path)
                        .build()
                        .toUriString();
            } catch (Exception ignored) {
            }
        }
        return trimmed;
    }
}
