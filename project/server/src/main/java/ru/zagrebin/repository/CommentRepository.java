package ru.zagrebin.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ru.zagrebin.model.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    Optional<Comment> findByAuthorIdAndClientId(Long authorId, String clientId);
}
