package ru.zagrebin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdatePostStatusRequest {
    @NotBlank
    private String status;
}
