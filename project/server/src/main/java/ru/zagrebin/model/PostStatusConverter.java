package ru.zagrebin.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PostStatusConverter implements AttributeConverter<PostStatus, String> {
    @Override
    public String convertToDatabaseColumn(PostStatus attribute) {
        return attribute != null ? attribute.getValue() : null;
    }

    @Override
    public PostStatus convertToEntityAttribute(String dbData) {
        return PostStatus.from(dbData);
    }
}
