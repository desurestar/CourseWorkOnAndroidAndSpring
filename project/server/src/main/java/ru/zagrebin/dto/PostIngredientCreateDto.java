package ru.zagrebin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostIngredientCreateDto {
    private Long ingredientId;
    private Double quantityValue;
    private String unit;
}
