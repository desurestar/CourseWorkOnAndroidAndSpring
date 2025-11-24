package ru.zagrebin.dto;

import lombok.Data;

@Data
public class RecipeStepDto {
    private int order;
    private String description;
    private String imageUrl;
}
