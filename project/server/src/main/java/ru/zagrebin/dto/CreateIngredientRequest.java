package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateIngredientRequest {
    @NotBlank
    private String name;
}
