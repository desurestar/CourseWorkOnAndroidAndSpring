package ru.zagrebin.service;

public interface LikeService {
    // поставить лайк (возвращает true если поставлен успешно, false если уже стоял)
    boolean like(Long postId, Long userId);

    // убрать лайк (возвращает true если лайк удалён, false если не было лайка)
    boolean unlike(Long postId, Long userId);

    // проверить, залайкнул ли пользователь пост
    boolean isLiked(Long postId, Long userId);

    // получить количество лайков (обычно можно брать из post.likesCount, но иногда полезно считать)
    long countLikes(Long postId);
}