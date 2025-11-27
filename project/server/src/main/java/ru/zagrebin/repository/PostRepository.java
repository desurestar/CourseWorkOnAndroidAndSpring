package ru.zagrebin.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    // Для простого получения карточек (без тяжёлых связей)
    List<Post> findByStatusOrderByCreatedAtDesc(String status);
    List<Post> findAllByOrderByCreatedAtDesc();

    Optional<Post> findWithAuthorAndTagsById(Long id);

    // Одно-сущностный fetch по id (EntityGraph лучше читается и управляется Spring Data)
    @EntityGraph(attributePaths = {"tags", "steps", "ingredients", "ingredients.ingredient"})
    @Query("select p from Post p where p.id = :id")
    Optional<Post> findByIdWithAllRelations(@Param("id") Long id);

    // Для двухэтапной загрузки: получаем id'шники (подходит для пагинации/фильтров)
    @Query("select p.id from Post p where p.status = :status order by p.createdAt desc")
    List<Long> findIdsByStatusOrderByCreatedAtDesc(@Param("status") String status, Pageable pageable);

    // Получаем сущности с заранее заданным графом связей — один запрос, Hibernate сведёт корни
    @EntityGraph(attributePaths = {"tags", "steps", "ingredients", "ingredients.ingredient"})
    @Query("select p from Post p where p.id in :ids")
    List<Post> findAllByIdWithEntityGraph(@Param("ids") List<Long> ids);

    // Count для корректной пагинации
    long countByStatus(String status);

    // Методы для лайков
    @Modifying
    @Query("update Post p set p.likesCount = p.likesCount + 1 where p.id = :postId")
    void incrementLikesCount(@Param("postId") Long postId);

    @Modifying
    @Query("update Post p set p.likesCount = p.likesCount - 1 where p.id = :postId and p.likesCount > 0")
    void decrementLikesCount(@Param("postId") Long postId);
}
