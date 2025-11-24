package ru.zagrebin.dto;

import lombok.Data;

import java.util.List;

@Data
public class PostUpdateDto {
    private String postType;
    private String status;

    private String title;
    private String excerpt;
    private String content;

    private String coverUrl;

    private Integer cookingTimeMinutes;
    private Integer calories;

    private List<Long> tagIds;

    private List<PostIngredientCreateDto> ingredients;
    private List<RecipeStepCreateDto> steps;
}
