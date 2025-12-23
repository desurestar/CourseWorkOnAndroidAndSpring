package ru.zagrebin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class PostFullDto {
    private Long id;
    private String postType;
    private String status;

    private String title;
    private String excerpt;
    private String content;

    private String coverUrl;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private AuthorShortDto author;

    private List<TagDto> tags;
    private List<PostIngredientDto> ingredients;
    private List<RecipeStepDto> steps;

    private int likesCount;
    @JsonProperty("liked")
    private boolean liked;
    private long viewsCount;

    private Integer calories;
    private Integer cookingTimeMinutes;
}
