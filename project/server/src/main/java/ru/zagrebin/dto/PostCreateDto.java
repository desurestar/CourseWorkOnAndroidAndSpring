package ru.zagrebin.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostCreateDto {
    private String postType;
    private String status;

    private String title;
    private String excerpt;
    private String content;

    private String coverUrl;

    private Integer cookingTimeMinutes;
    private Integer calories;

    private Long authorId; // TODO переделать, когда будет аутентификация

    private List<Long> tagIds;

    private List<PostIngredientCreateDto> ingredients;
    private List<RecipeStepCreateDto> steps;
}
