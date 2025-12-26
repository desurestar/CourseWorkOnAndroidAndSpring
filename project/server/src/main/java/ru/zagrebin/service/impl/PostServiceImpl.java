package ru.zagrebin.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import ru.zagrebin.dto.PostCardDto;
import ru.zagrebin.dto.PostCreateDto;
import ru.zagrebin.dto.PostFilterRequest;
import ru.zagrebin.dto.PostFullDto;
import ru.zagrebin.dto.PostUpdateDto;
import ru.zagrebin.mapper.PostMapper;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.PostStatus;
import ru.zagrebin.model.RecipeStep;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.PostRepository;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.security.Roles;
import ru.zagrebin.service.EmailNotificationService;
import ru.zagrebin.service.FileStorageService;
import ru.zagrebin.service.LikeService;
import ru.zagrebin.service.PostService;
import ru.zagrebin.service.assembler.PostAssembler;

@Service
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostAssembler postAssembler;
    private final LikeService likeService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final EmailNotificationService emailNotificationService;

    public PostServiceImpl(PostRepository postRepository,
                           PostAssembler postAssembler,
                           LikeService likeService,
                           UserRepository userRepository,
                           FileStorageService fileStorageService,
                           EmailNotificationService emailNotificationService) {
        this.postRepository = postRepository;
        this.postAssembler = postAssembler;
        this.likeService = likeService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Старый простой метод — возвращает все опубликованные посты (карточки) без тяжёлых связей.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PostCardDto> getPostsPageByStatus(PostStatus status, Pageable pageable, PostFilterRequest filters) {
        PostFilterRequest normalized = filters == null ? new PostFilterRequest().normalize() : filters.normalize();
        boolean tagsEmpty = !normalized.hasTags();
        List<String> tagNames = normalized.effectiveTags();

        List<Long> ids = postRepository.findIdsByFilters(
                status,
                normalized.getPostType(),
                normalized.getCookingTimeMin(),
                normalized.getCookingTimeMax(),
                normalized.getCaloriesMin(),
                normalized.getCaloriesMax(),
                tagsEmpty,
                tagNames,
                pageable
        );

        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        List<Post> posts = postRepository.findAllByIdWithEntityGraph(ids);

        Map<Long, Post> map = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));

        List<PostCardDto> orderedDtos = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Post p = map.get(id);
            if (p != null) {
                orderedDtos.add(PostMapper.toCard(p));
            }
        }

        long total = postRepository.countByFilters(
                status,
                normalized.getPostType(),
                normalized.getCookingTimeMin(),
                normalized.getCookingTimeMax(),
                normalized.getCaloriesMin(),
                normalized.getCaloriesMax(),
                tagsEmpty,
                tagNames
        );

        return new PageImpl<>(orderedDtos, pageable, total);
    }

    /**
     * Возвращает полный пост с отношениями (EntityGraph single fetch).
     */
    @Override
    @Transactional(readOnly = true)
    public PostFullDto getFullPost(Long postId, Long currentUserId) {
        Post post = postRepository.findByIdWithAllRelations(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));

        boolean isLiked = currentUserId != null && likeService.isLiked(postId, currentUserId);
        boolean isSubscribed = false;
        if (currentUserId != null && post.getAuthor() != null) {
            isSubscribed = userRepository.findById(currentUserId)
                    .map(u -> u.getSubscriptions().contains(post.getAuthor()))
                    .orElse(false);
        }

        return PostMapper.toFull(post, isLiked, isSubscribed);
    }

    @Override
    @Transactional
    public PostCardDto create(PostCreateDto dto, Long currentUserId) {
        if (currentUserId == null) {
            throw new AccessDeniedException("Требуется авторизация");
        }
        var author = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUserId));
        
        // Idempotent upsert: if clientId provided, check for existing post
        if (dto.getClientId() != null && !dto.getClientId().isBlank()) {
            Optional<Post> existing = postRepository.findByAuthorIdAndClientId(currentUserId, dto.getClientId());
            if (existing.isPresent()) {
                // Update existing post instead of creating new one
                Post existingPost = existing.get();
                existingPost.setPostType(dto.getPostType());
                existingPost.setStatus(PostStatus.from(dto.getStatus()));
                existingPost.setTitle(dto.getTitle());
                existingPost.setExcerpt(dto.getExcerpt());
                existingPost.setContent(dto.getContent());
                existingPost.setCoverUrl(dto.getCoverUrl());
                existingPost.setCookingTimeMinutes(dto.getCookingTimeMinutes());
                existingPost.setCalories(dto.getCalories());
                // Update tags, ingredients, steps via assembler helper
                postAssembler.updateCollections(existingPost, dto);
                Post saved = postRepository.save(existingPost);
                log.info("Updated existing post via clientId: {}", dto.getClientId());
                return PostMapper.toCard(saved);
            }
        }
        
        // Create new post
        Post created = postAssembler.createFromDto(dto, author);
        Post saved = postRepository.save(created);

        // Уведомление подписчиков
        if (saved.getAuthor() != null && saved.getAuthor().getSubscribers() != null) {
            String subject = "Новый пост от автора, на которого вы подписаны";
            String postTitle = saved.getTitle() != null ? saved.getTitle() : "Пост";
            String text = String.format("Автор %s опубликовал новый пост: %s", saved.getAuthor().getDisplayName() != null ? saved.getAuthor().getDisplayName() : saved.getAuthor().getUsername(), postTitle);
            for (User subscriber : saved.getAuthor().getSubscribers()) {
                if (subscriber.getEmail() != null && !subscriber.getEmail().isBlank()) {
                    emailNotificationService.sendNotification(subscriber.getEmail(), subject, text);
                }
            }
        }
        return PostMapper.toCard(saved);
    }

    @Override
    @Transactional
    public PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId) {
        Post existing = postRepository.findByIdWithAllRelations(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));

        // Проверка авторства (пример)
        if (currentUserId == null) {
            throw new AccessDeniedException("Требуется авторизация");
        }
        var currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUserId));
        boolean isAdmin = isAdmin(currentUser);
        if (existing.getAuthor() != null && !existing.getAuthor().getId().equals(currentUserId) && !isAdmin) {
            throw new AccessDeniedException("Недостаточно прав для изменения поста");
        }

        Post updated = postAssembler.updateFromDto(existing, dto);
        Post saved = postRepository.save(updated);

        boolean isLiked = currentUserId != null && likeService.isLiked(postId, currentUserId);
        boolean isSubscribed = false;
        if (currentUserId != null && saved.getAuthor() != null) {
            isSubscribed = userRepository.findById(currentUserId)
                    .map(u -> u.getSubscriptions().contains(saved.getAuthor()))
                    .orElse(false);
        }

        return PostMapper.toFull(saved, isLiked, isSubscribed);
    }

    @Override
    @Transactional
    public void delete(Long postId, Long currentUserId) {
        if (!postRepository.existsById(postId)) {
            throw new EntityNotFoundException("Post not found: " + postId);
        }

        if (currentUserId == null) {
            throw new AccessDeniedException("Требуется авторизация");
        }

        Post post = postRepository.findByIdWithAllRelations(postId).orElse(null);
        if (post != null) {
            var currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUserId));
            boolean isAdmin = isAdmin(currentUser);
            if (post.getAuthor() != null && !post.getAuthor().getId().equals(currentUserId) && !isAdmin) {
                throw new AccessDeniedException("Недостаточно прав для удаления поста");
            }
            if (post.getCoverUrl() != null) {
                try { fileStorageService.delete(post.getCoverUrl()); } catch (Exception ex) { log.warn("Failed to delete cover: {}", ex.getMessage()); }
            }
            for (RecipeStep s : post.getSteps()) {
                if (s.getImageUrl() != null) {
                    try { fileStorageService.delete(s.getImageUrl()); } catch (Exception ex) { log.warn("Failed to delete step image: {}", ex.getMessage()); }
                }
            }
        }

        postRepository.deleteById(postId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PostCardDto> getMyDrafts(Long currentUserId, Pageable pageable) {
        if (currentUserId == null) {
            throw new AccessDeniedException("Требуется авторизация");
        }
        Page<Post> drafts = postRepository.findByAuthorIdAndStatus(currentUserId, PostStatus.DRAFT, pageable);
        return drafts.map(PostMapper::toCard);
    }

    private boolean isAdmin(User user) {
        return user != null && Roles.ADMIN.equalsIgnoreCase(user.getRole());
    }

    // Дополнительные утилитарные методы (например, для лайков) можно добавить здесь.
}
