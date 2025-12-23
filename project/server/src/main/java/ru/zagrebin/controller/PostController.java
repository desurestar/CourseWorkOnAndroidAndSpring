package ru.zagrebin.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.zagrebin.dto.*;
import ru.zagrebin.security.UserPrincipal;
import ru.zagrebin.model.PostStatus;
import ru.zagrebin.service.LikeService;
import ru.zagrebin.service.PostService;

import java.net.URI;
import java.util.List;
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final LikeService likeService;

    public PostController(PostService postService, LikeService likeService) {
        this.postService = postService;
        this.likeService = likeService;
    }

    /**
     * GET /api/posts
     * Возвращает карточки опубликованных постов
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<PostCardDto>> listPublished(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "6") int pageSize,
            @RequestParam(name = "post_type", required = false) String postType,
            @RequestParam(name = "cooking_time_min", required = false) Integer cookingTimeMin,
            @RequestParam(name = "cooking_time_max", required = false) Integer cookingTimeMax,
            @RequestParam(name = "calories_min", required = false) Integer caloriesMin,
            @RequestParam(name = "calories_max", required = false) Integer caloriesMax,
            @RequestParam(name = "tags", required = false) List<String> tags,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        PostFilterRequest filters = PostFilterRequest.builder()
                .postType(postType)
                .cookingTimeMin(cookingTimeMin)
                .cookingTimeMax(cookingTimeMax)
                .caloriesMin(caloriesMin)
                .caloriesMax(caloriesMax)
                .tags(tags)
                .build()
                .normalize();
        Page<PostCardDto> posts = postService.getPostsPageByStatus(PostStatus.PUBLISHED, pageable, filters);
        if (principal != null) {
            posts.forEach(dto -> dto.setLiked(likeService.isLiked(dto.getId(), principal.getId())));
        }

        String next = null;
        if (posts.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/posts")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            filters.appendQueryParams(builder);
            next = builder.build().toString();
        }

        return ResponseEntity.ok(new PaginatedResponse<>(posts.getContent(), next));
    }

    /**
     * GET /api/posts/{id}
     * Возвращает полный пост. currentUserId можно брать из JWT; здесь параметр опционален.
     */
    @GetMapping("/{id}")
    public ResponseEntity<PostFullDto> getFull(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "currentUserId", required = false) Long currentUserId
    ) {
        Long userId = currentUserId != null ? currentUserId : (principal != null ? principal.getId() : null);
        PostFullDto dto = postService.getFullPost(id, userId);
        return ResponseEntity.ok(dto);
    }

    /**
     * POST /api/posts
     * Создание поста. В идеале currentUser берётся по JWT — сейчас authorId в dto.
     */
    @PostMapping
    public ResponseEntity<PostCardDto> create(@Valid @RequestBody PostCreateDto dto,
                                              @AuthenticationPrincipal UserPrincipal principal) {
        PostCardDto created = postService.create(dto, principal != null ? principal.getId() : null);
        // возвращаем 201 + location
        URI location = URI.create("/api/posts/" + created.getId());
        return ResponseEntity.created(location).body(created);
    }

    /**
     * PUT /api/posts/{id}
     * Обновление поста
     * currentUserId передаётся опционально для логики авторства
     */
    @PutMapping("/{id}")
    public ResponseEntity<PostFullDto> update(
            @PathVariable Long id,
            @Valid @RequestBody PostUpdateDto dto,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        PostFullDto updated = postService.update(id, dto, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(updated);
    }

    /**
     * DELETE /api/posts/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        postService.delete(id, principal != null ? principal.getId() : null);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/posts/{id}/like
     * Поставить лайк. В реальном приложении userId берём из токена.
     */
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(@PathVariable Long id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean ok = likeService.like(id, principal.getId());
        return ok ? ResponseEntity.ok().build() : ResponseEntity.status(409).build();
    }

    /**
     * DELETE /api/posts/{id}/like
     * Убрать лайк
     */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        boolean ok = likeService.unlike(id, principal.getId());
        return ok ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * GET /api/posts/mine/drafts
     * Получить список черновиков текущего пользователя
     */
    @GetMapping("/mine/drafts")
    public ResponseEntity<PaginatedResponse<PostCardDto>> getMyDrafts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int pageIndex = Math.max(page - 1, 0);
        Pageable pageable = PageRequest.of(pageIndex, pageSize);
        Page<PostCardDto> drafts = postService.getMyDrafts(principal.getId(), pageable);
        
        String next = null;
        if (drafts.hasNext()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/posts/mine/drafts")
                    .queryParam("page", page + 1)
                    .queryParam("page_size", pageSize);
            next = builder.build().toString();
        }
        
        return ResponseEntity.ok(new PaginatedResponse<>(drafts.getContent(), next));
    }
}
