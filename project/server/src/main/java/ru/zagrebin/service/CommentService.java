package ru.zagrebin.service;

import java.util.List;

import ru.zagrebin.dto.CommentCreateDto;
import ru.zagrebin.dto.CommentDto;
import ru.zagrebin.dto.CommentUpdateDto;

public interface CommentService {
    CommentDto create(Long postId, CommentCreateDto dto, Long currentUserId);
    CommentDto update(Long commentId, CommentUpdateDto dto, Long currentUserId);
    void delete(Long commentId, Long currentUserId);
    List<CommentDto> listByPost(Long postId);
}
