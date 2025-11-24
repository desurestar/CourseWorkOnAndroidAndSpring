package ru.zagrebin.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.zagrebin.dto.*;
import ru.zagrebin.model.Post;
import ru.zagrebin.service.LikeService;
import ru.zagrebin.service.PostService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService postService;
    private final LikeService likeService;

    public PostController(PostService postService, LikeService likeService) {
        this.postService = postService;
        this.likeService = likeService;
    }

    /**
     * GET /api/v1/posts
     * Возвращает карточки опубликованных постов
     */
    @GetMapping
    public ResponseEntity<List<PostCardDto>> listPublished() {
        List<PostCardDto> cards = postService.getAllPublishedPosts();
        return ResponseEntity.ok(cards);
    }

    /**
     * GET /api/v1/posts/{id}
     * Возвращает полный пост. currentUserId можно брать из JWT; здесь параметр опционален.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostFullDto> getFull(
            @PathVariable Long id,
            @RequestParam(name = "currentUserId", required = false) Long currentUserId
    ) {
        PostFullDto dto = postService.getFullPost(id, currentUserId);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/v1/posts
     * Создание поста. В идеале currentUser берётся по JWT — сейчас authorId в dto.
     */
    @PostMapping
    public ResponseEntity<PostCardDto> create(@Valid @RequestBody PostCreateDto dto) {
        PostCardDto created = postService.create(dto);
        // возвращаем 201 + location
        URI location = URI.create("/api/v1/posts/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * PUT /api/v1/posts/{id}
     * Обновление поста
     * currentUserId передаётся опционально для логики авторства
     */
    @PutMapping("/{id}")
    public ResponseEntity<PostFullDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateDto dto,
            @RequestParam(name = "currentUserId", required = false) Long currentUserId
    ) {
        PostFullDto updated = postService.update(id, dto, currentUserId);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/v1/posts/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        postService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/posts/{id}/like?userId=...
     * Поставить лайк. В реальном приложении userId берём из токена.
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id, @RequestParam Long userId) {
        boolean ok = likeService.like(id, userId);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    /**
     * DELETE /api/v1/posts/{id}/like?userId=...
     * Убрать лайк
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id, @RequestParam Long userId) {
        boolean ok = likeService.unlike(id, userId);
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
