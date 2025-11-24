package ru.zagrebin.dto;

import lombok.Data;

@Data
public class PostIngredientDto {
    private Long ingredientId;
    private String ingredientName;
    private Double quantityValue;
    private String unit;
}
