package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.PostCardDto;
import ru.zagrebin.dto.PostCreateDto;
import ru.zagrebin.dto.PostFullDto;
import ru.zagrebin.dto.PostUpdateDto;
import ru.zagrebin.mapper.PostMapper;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.RecipeStep;
import ru.zagrebin.repository.PostRepository;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.service.FileStorageService;
import ru.zagrebin.service.LikeService;
import ru.zagrebin.service.PostService;
import ru.zagrebin.service.assembler.PostAssembler;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostAssembler postAssembler;
    private final LikeService likeService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public PostServiceImpl(PostRepository postRepository,
                           PostAssembler postAssembler,
                           LikeService likeService,
                           UserRepository userRepository,
                           FileStorageService fileStorageService) {
        this.postRepository = postRepository;
        this.postAssembler = postAssembler;
        this.likeService = likeService;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    /**
     * Старый простой метод — возвращает все опубликованные посты (карточки) без тяжёлых связей.
     */
    @Override
    @Transactional(readOnly = true)
    public List<PostCardDto> getAllPublishedPosts() {
        return postRepository.findByStatusOrderByCreatedAtDesc("published")
                .stream()
                .map(PostMapper::toCard)
                .collect(Collectors.toList());
    }

    /**
     * Новая реализация: пагинация + двухэтапная загрузка (IDs -> fetch by entity graph).
     * Возвращает страницу PostCardDto, сохраняя порядок по createdAt desc (через ids).
     */
    @Transactional(readOnly = true)
    public Page<PostCardDto> getPostsPageByStatus(String status, Pageable pageable) {
        // 1) Получаем id'шники с учётом пагинации/сортировки
        List<Long> ids = postRepository.findIdsByStatusOrderByCreatedAtDesc(status, pageable);

        if (ids.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), pageable, 0);
        }

        // 2) Загружаем сущности с графом (tags, steps, ingredients + their ingredient)
        List<Post> posts = postRepository.findAllByIdWithEntityGraph(ids);

        // 3) Сопоставляем и восстанавливаем порядок по ids
        Map<Long, Post> map = posts.stream()
                .collect(Collectors.toMap(Post::getId, p -> p));

        List<PostCardDto> orderedDtos = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Post p = map.get(id);
            if (p != null) {
                orderedDtos.add(PostMapper.toCard(p));
            }
        }

        long total = postRepository.countByStatus(status);

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
    public PostCardDto create(PostCreateDto dto) {
        // При создании: можно загружать файлы через fileStorageService если нужно
        Post created = postAssembler.createFromDto(dto);
        // Сохраняем сущность (assembler должен заполнить необходимые поля)
        Post saved = postRepository.save(created);
        return PostMapper.toCard(saved);
    }

    @Override
    @Transactional
    public PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId) {
        Post existing = postRepository.findByIdWithAllRelations(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));

        // Проверка авторства (пример)
        if (currentUserId != null && existing.getAuthor() != null && !existing.getAuthor().getId().equals(currentUserId)) {
            log.debug("User {} is not author of post {}", currentUserId, postId);
            // при необходимости бросаем исключение контроля доступа
        }

        Post updated = postAssembler.updateFromDto(postId, dto);
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
    public void delete(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new EntityNotFoundException("Post not found: " + postId);
        }

        Post post = postRepository.findByIdWithAllRelations(postId).orElse(null);
        if (post != null) {
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

    // Дополнительные утилитарные методы (например, для лайков) можно добавить здесь.
}
