package ru.zagrebin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String displayName;
    private String role;
    private String avatarUrl;
    private int followersCount;
    private int followingCount;
}
