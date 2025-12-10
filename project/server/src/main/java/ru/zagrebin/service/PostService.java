package ru.zagrebin.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.zagrebin.dto.PostCardDto;
import ru.zagrebin.dto.PostCreateDto;
import ru.zagrebin.dto.PostFullDto;
import ru.zagrebin.dto.PostUpdateDto;

public interface PostService {
    List<PostCardDto> getAllPublishedPosts();

    Page<PostCardDto> getPublishedPostsPage(String status, Pageable pageable);

    PostFullDto getFullPost(Long postId, Long currentUserId);

    PostCardDto create(PostCreateDto dto);

    PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId);

    void delete(Long postId);
}
