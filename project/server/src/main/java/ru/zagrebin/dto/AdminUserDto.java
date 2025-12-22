package ru.zagrebin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminUserDto {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private String avatarUrl;
}
