package ru.zagrebin.dto;

import lombok.Data;

@Data
public class PostCardDto {
    private Long id;
    private String title;
    private String excerpt;
    private String coverUrl;
    private Long authorId;
    private int likesCount;
    private Integer cookingTimeMinutes;
    private Integer calories;
}