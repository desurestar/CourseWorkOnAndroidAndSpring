package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIngredientRequest(
        @NotBlank @Size(max = 255) String name
) {
}
