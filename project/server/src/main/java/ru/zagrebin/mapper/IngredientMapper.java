package ru.zagrebin.mapper;

import ru.zagrebin.dto.PostIngredientDto;
import ru.zagrebin.model.PostIngredient;

public final class IngredientMapper {
    private IngredientMapper() {}

    public static PostIngredientDto toDto(PostIngredient pi) {
        if (pi == null) return null;
        PostIngredientDto dto = new PostIngredientDto();
        dto.setIngredientId(pi.getIngredient().getId());
        dto.setIngredientName(pi.getIngredient().getName());
        dto.setQuantityValue(pi.getQuantityValue());
        dto.setUnit(pi.getUnit());
        return dto;
    }
}
