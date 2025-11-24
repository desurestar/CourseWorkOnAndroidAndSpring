package ru.zagrebin.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class PostSummaryDto {
    private Long id;
    private String title;
    private String excerpt;
    private String coverUrl;
    private Long authorId;
    private Integer likesCount;
    private Integer commentsCount;
    private Long viewsCount;
    private Integer cookingTimeMinutes;
    private Integer calories;
    private OffsetDateTime createdAt;
}
