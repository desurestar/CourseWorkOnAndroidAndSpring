package ru.zagrebin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.zagrebin.model.Post;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByStatusOrderByCreatedAtDesc(String status);
    List<Post> findAllByOrderByCreatedAtDesc();

    Optional<Post> findWithAuthorAndTagsById(Long id);

    @Query("""
        select distinct p from Post p
        left join fetch p.tags t
        left join fetch p.steps s
        left join fetch p.ingredients pi
        left join fetch pi.ingredient ingr
        where p.id = :id
    """)
    Optional<Post> findByIdWithAllRelations(@Param("id") Long id);

    @Query("""
        select distinct p
        from Post p
        left join fetch p.tags
        left join fetch p.steps
        left join fetch p.ingredients pi
        left join fetch pi.ingredient
        where p.id in :ids
        """)
    Optional<Post> findAllByIdWithAllRelations(@Param("ids") Iterable<Long> ids);

    @Modifying
    @Query("update Post p set p.likesCount = p.likesCount + 1 where p.id = :postId")
    int incrementLikesCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.likesCount = p.likesCount - 1 where p.id = :postId and p.likesCount > 0")
    int decrementLikesCount(@Param("postId") Long postId);
}
