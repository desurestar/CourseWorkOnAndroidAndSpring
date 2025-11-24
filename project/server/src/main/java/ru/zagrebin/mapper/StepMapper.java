package ru.zagrebin.mapper;

import ru.zagrebin.dto.RecipeStepDto;
import ru.zagrebin.model.RecipeStep;

public final class StepMapper {
    private StepMapper() {}

    public static RecipeStepDto toDto(RecipeStep s) {
        if (s == null) return null;
        RecipeStepDto dto = new RecipeStepDto();
        dto.setOrder(s.getOrder());
        dto.setDescription(s.getDescription());
        dto.setImageUrl(s.getImageUrl());
        return dto;
    }
}
