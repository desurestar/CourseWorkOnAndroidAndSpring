package ru.zagrebin.mapper;

import ru.zagrebin.dto.RecipeStepDto;
import ru.zagrebin.model.RecipeStep;
import ru.zagrebin.util.UrlHelper;

public final class StepMapper {
    private StepMapper() {}

    public static RecipeStepDto toDto(RecipeStep s) {
        if (s == null) return null;
        RecipeStepDto dto = new RecipeStepDto();
        dto.setOrder(s.getOrder());
        dto.setDescription(s.getDescription());
        dto.setImageUrl(UrlHelper.toAbsolute(s.getImageUrl()));
        return dto;
    }
}
