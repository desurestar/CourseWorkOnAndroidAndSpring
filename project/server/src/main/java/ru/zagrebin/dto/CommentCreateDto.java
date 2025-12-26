package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;

public record CommentCreateDto(
        @NotBlank String content,
        String clientId
) {}
