package ru.zagrebin.dto;

import lombok.Data;

@Data
public class RecipeStepCreateDto {
    private int order;
    private String description;
    private String imageUrl;
}
