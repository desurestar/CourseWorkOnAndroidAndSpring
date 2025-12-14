package ru.zagrebin.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

public final class UrlHelper {
    private static final Logger log = LoggerFactory.getLogger(UrlHelper.class);

    private UrlHelper() {}

    /**
     * Returns an absolute URL for the resource if a request context is available.
     * If the value already contains a scheme (http/https) or is blank, it is returned unchanged.
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
            } catch (IllegalStateException ex) {
                log.debug("Failed to build absolute URL, returning original value: {}", ex.getMessage());
            }
        }
        return trimmed;
    }
}
