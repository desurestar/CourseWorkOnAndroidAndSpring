package ru.zagrebin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.zagrebin.model.PostLike;
import ru.zagrebin.model.PostLikeId;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, Long> {
    boolean existsById(PostLikeId postLike);

    default boolean existsByPostIdAndUserId(Long postId, Long userId) {
        return existsById(new PostLikeId(postId, userId));
    }

    long countByIdPostId(Long postId);


    void deleteById(PostLikeId id);

}
