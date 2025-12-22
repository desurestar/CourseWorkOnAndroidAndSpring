package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTagRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String slug,
        @Size(max = 7) String color
) {
}
