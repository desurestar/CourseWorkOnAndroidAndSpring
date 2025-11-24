package ru.zagrebin.service;

import ru.zagrebin.dto.*;

import java.util.List;

public interface PostService {
    // список карточек опубликованных постов (без пагинации, как договорились)
    List<PostCardDto> getAllPublishedPosts();

    // получить полную информацию о посте; currentUserId может быть null
    PostFullDto getFullPost(Long postId, Long currentUserId);

    // создание поста (возвращаем карточку/или full, тут карточка)
    PostCardDto create(PostCreateDto dto);

    // обновление поста, возвращает полный пост (или карточку) — возвращаем PostFullDto
    PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId);

    // удалить пост
    void delete(Long postId);
}
