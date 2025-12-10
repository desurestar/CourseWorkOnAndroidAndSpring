package ru.zagrebin.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.zagrebin.model.Post;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @EntityGraph(attributePaths = {"author", "tags"})
    List<Post> findByStatusOrderByCreatedAtDesc(String status);

    @Query("select p.id from Post p where p.status = :status order by p.createdAt desc")
    List<Long> findIdsByStatusOrderByCreatedAtDesc(@Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags", "ingredients", "ingredients.ingredient", "steps"})
    @Query("select distinct p from Post p where p.id in :ids")
    List<Post> findAllByIdWithEntityGraph(@Param("ids") List<Long> ids);

    long countByStatus(String status);

    @Query("""
        select distinct p from Post p
        left join fetch p.author
        left join fetch p.tags
        left join fetch p.ingredients pi
        left join fetch pi.ingredient
        left join fetch p.steps s
        where p.id = :id
    """)
    Optional<Post> findByIdWithAllRelations(@Param("id") Long id);

    @Modifying
    @Query("update Post p set p.likesCount = case when p.likesCount is null then 1 else p.likesCount + 1 end where p.id = :postId")
    void incrementLikesCount(@Param("postId") Long postId);

    @Modifying
    @Query("""
        update Post p
        set p.likesCount = case
            when p.likesCount is null or p.likesCount <= 0 then 0
            else p.likesCount - 1
        end
        where p.id = :postId
    """)
    void decrementLikesCount(@Param("postId") Long postId);
}
