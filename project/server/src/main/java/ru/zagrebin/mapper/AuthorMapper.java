package ru.zagrebin.mapper;

import ru.zagrebin.dto.AuthorShortDto;
import ru.zagrebin.model.User;

public final class AuthorMapper {
    private AuthorMapper() {}

    public static AuthorShortDto toDto(User u) {
        if (u == null) {
            return null;
        }
        AuthorShortDto dto = new AuthorShortDto();
        dto.setId(u.getId());
        dto.setDisplayName(u.getDisplayName());
        dto.setAvatarUrl(u.getAvatarUrl());
        dto.setSubscribed(false);
        return dto;
    }
}
