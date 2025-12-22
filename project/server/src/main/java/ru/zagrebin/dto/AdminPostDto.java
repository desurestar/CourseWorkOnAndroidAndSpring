package ru.zagrebin.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AdminPostDto {
    private Long id;
    private String title;
    private String status;
    private String postType;
    private Long authorId;
    private String authorUsername;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
