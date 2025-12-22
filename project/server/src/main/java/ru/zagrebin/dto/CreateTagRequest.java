package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateTagRequest {
    @NotBlank
    private String name;
    private String slug;
    private String color;
}
