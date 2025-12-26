package ru.zagrebin.dto;

import java.time.OffsetDateTime;

public record CommentDto(
        Long id,
        Long postId,
        Long authorId,
        String authorDisplayName,
        String content,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
