package ru.zagrebin.mapper;

import ru.zagrebin.dto.TagDto;
import ru.zagrebin.model.Tag;

public final class TagMapper {
    private TagMapper() {}

    public static TagDto toDto(Tag t) {
        if (t == null) {
            return null;
        }
        TagDto dto = new TagDto();
        dto.setId(t.getId());
        dto.setName(t.getName());
        dto.setColor(t.getColor());
        return dto;
    }
}
