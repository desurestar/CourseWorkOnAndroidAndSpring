package ru.zagrebin.model.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import ru.zagrebin.model.PostStatus;

@Converter(autoApply = false)
public class PostStatusConverter implements AttributeConverter<PostStatus, String> {

    @Override
    public String convertToDatabaseColumn(PostStatus attribute) {
        if (attribute == null) return null;
        return attribute.getValue(); // "published"
    }

    @Override
    public PostStatus convertToEntityAttribute(String dbData) {
        return PostStatus.from(dbData); // умеет "published" -> PUBLISHED
    }
}