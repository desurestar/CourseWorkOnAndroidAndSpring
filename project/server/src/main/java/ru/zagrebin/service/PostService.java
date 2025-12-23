package ru.zagrebin.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.zagrebin.dto.PostCardDto;
import ru.zagrebin.dto.PostCreateDto;
import ru.zagrebin.dto.PostFilterRequest;
import ru.zagrebin.dto.PostFullDto;
import ru.zagrebin.dto.PostUpdateDto;
import ru.zagrebin.model.PostStatus;

public interface PostService {
    Page<PostCardDto> getPostsPageByStatus(PostStatus status, Pageable pageable, PostFilterRequest filters);

    PostFullDto getFullPost(Long postId, Long currentUserId);

    PostCardDto create(PostCreateDto dto, Long currentUserId);

    default PostCardDto create(PostCreateDto dto) {
        return create(dto, null);
    }

    PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId);

    void delete(Long postId, Long currentUserId);
}
