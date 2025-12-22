package ru.zagrebin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPostDto {
    private Long id;
    private String title;
    private String postType;
    private String status;
    private String createdAt;
    private String coverUrl;
    private Long authorId;
    private String authorUsername;
    private String authorDisplayName;
}
