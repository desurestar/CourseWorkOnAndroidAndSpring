package ru.zagrebin.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.PostStatus;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("""
        select p.id from Post p
        where p.status = :status
          and (:postType is null or lower(p.postType) = lower(:postType))
          and (:cookingTimeMin is null or p.cookingTimeMinutes >= :cookingTimeMin)
          and (:cookingTimeMax is null or p.cookingTimeMinutes <= :cookingTimeMax)
          and (:caloriesMin is null or p.calories >= :caloriesMin)
          and (:caloriesMax is null or p.calories <= :caloriesMax)
          and (
              :tagsEmpty = true or exists (
                  select 1 from p.tags t where t.name in :tags
              )
          )
        order by p.createdAt desc
    """)
    List<Long> findIdsByFilters(@Param("status") PostStatus status,
                                 @Param("postType") String postType,
                                 @Param("cookingTimeMin") Integer cookingTimeMin,
                                 @Param("cookingTimeMax") Integer cookingTimeMax,
                                 @Param("caloriesMin") Integer caloriesMin,
                                 @Param("caloriesMax") Integer caloriesMax,
                                 @Param("tagsEmpty") boolean tagsEmpty,
                                 @Param("tags") List<String> tags,
                                 Pageable pageable);

    @EntityGraph(attributePaths = {"author", "tags", "ingredients", "ingredients.ingredient", "steps"})
    @Query("select distinct p from Post p where p.id in :ids")
    List<Post> findAllByIdWithEntityGraph(@Param("ids") List<Long> ids);

    long countByStatus(PostStatus status);

    @Query("""
        select count(distinct p.id) from Post p
        where p.status = :status
          and (:postType is null or lower(p.postType) = lower(:postType))
          and (:cookingTimeMin is null or p.cookingTimeMinutes >= :cookingTimeMin)
          and (:cookingTimeMax is null or p.cookingTimeMinutes <= :cookingTimeMax)
          and (:caloriesMin is null or p.calories >= :caloriesMin)
          and (:caloriesMax is null or p.calories <= :caloriesMax)
          and (
              :tagsEmpty = true or exists (
                  select 1 from p.tags t where t.name in :tags
              )
          )
    """)
    long countByFilters(@Param("status") PostStatus status,
                         @Param("postType") String postType,
                         @Param("cookingTimeMin") Integer cookingTimeMin,
                         @Param("cookingTimeMax") Integer cookingTimeMax,
                         @Param("caloriesMin") Integer caloriesMin,
                        @Param("caloriesMax") Integer caloriesMax,
                        @Param("tagsEmpty") boolean tagsEmpty,
                        @Param("tags") List<String> tags);

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

    Page<Post> findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(String title, Pageable pageable);

    Optional<Post> findByAuthorIdAndClientId(Long authorId, String clientId);

    Page<Post> findByAuthorIdAndStatus(Long authorId, PostStatus status, Pageable pageable);
}
