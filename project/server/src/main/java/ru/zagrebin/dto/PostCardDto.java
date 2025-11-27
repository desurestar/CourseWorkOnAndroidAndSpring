package ru.zagrebin.dto;

import lombok.Data;
import ru.zagrebin.model.Tag;

import java.util.Set;

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
    private String authorName;
    private String publishedAt;
    private Set<String> tags;
    private Long viewsCount;
}