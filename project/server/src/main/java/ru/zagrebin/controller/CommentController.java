package ru.zagrebin.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ru.zagrebin.dto.CommentCreateDto;
import ru.zagrebin.dto.CommentDto;
import ru.zagrebin.dto.CommentUpdateDto;
import ru.zagrebin.security.UserPrincipal;
import ru.zagrebin.service.CommentService;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<List<CommentDto>> list(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.listByPost(postId));
    }

    @PostMapping
    public ResponseEntity<CommentDto> create(@PathVariable Long postId,
                                             @Valid @RequestBody CommentCreateDto dto,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        CommentDto created = commentService.create(postId, dto, principal != null ? principal.getId() : null);
        return ResponseEntity.created(URI.create(String.format("/api/posts/%d/comments/%d", postId, created.id()))).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentDto> update(@PathVariable Long postId,
                                             @PathVariable Long id,
                                             @Valid @RequestBody CommentUpdateDto dto,
                                             @AuthenticationPrincipal UserPrincipal principal) {
        CommentDto updated = commentService.update(id, dto, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long postId,
                                       @PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        commentService.delete(id, principal != null ? principal.getId() : null);
        return ResponseEntity.noContent().build();
    }
}
