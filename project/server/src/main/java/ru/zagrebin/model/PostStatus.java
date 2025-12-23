package ru.zagrebin.model;

import java.util.Locale;

public enum PostStatus {
    DRAFT("draft"),
    PUBLISHED("published"),
    ARCHIVED("archived");

    private final String value;

    PostStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static PostStatus from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DRAFT;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (PostStatus status : values()) {
            if (status.value.equals(normalized)) {
                return status;
            }
        }
        return DRAFT;
    }
}
