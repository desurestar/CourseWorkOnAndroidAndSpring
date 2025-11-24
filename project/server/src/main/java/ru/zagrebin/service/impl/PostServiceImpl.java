package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.*;
import ru.zagrebin.mapper.PostMapper;
import ru.zagrebin.model.Post;
import ru.zagrebin.model.RecipeStep;
import ru.zagrebin.repository.PostRepository;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.service.FileStorageService;
import ru.zagrebin.service.LikeService;
import ru.zagrebin.service.PostService;
import ru.zagrebin.service.assembler.PostAssembler;

import java.util.List;
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

    @Override
    @Transactional(readOnly = true)
    public List<PostCardDto> getAllPublishedPosts() {
        // Возвращаем простые PostCardDto, не грузя тяжёлые связи
        return postRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED")
                .stream()
                .map(PostMapper::toCard)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public PostFullDto getFullPost(Long postId, Long currentUserId) {
        Post post = postRepository.findByIdWithAllRelations(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));

        boolean isLiked = currentUserId != null && likeService.isLiked(postId, currentUserId);
        boolean isSubscribed = false;
        if (currentUserId != null && post.getAuthor() != null) {
            // можно вычислять через subscribers relation или отдельный репозиторий/запрос
            isSubscribed = userRepository.findById(currentUserId)
                    .map(u -> u.getSubscriptions().contains(post.getAuthor()))
                    .orElse(false);
        }

        // маппим entity->dto, передаём флаги
        return PostMapper.toFull(post, isLiked, isSubscribed);
    }

    @Override
    @Transactional
    public PostCardDto create(PostCreateDto dto) {
        // При создании: если coverUrl содержит data-uri или multipart — сначала загрузить файл через FileStorageService
        // В этой заготовке предполагаем, что client уже загрузил файлы и передал coverUrl (или null)
        Post created = postAssembler.createFromDto(dto);
        return PostMapper.toCard(created);
    }

    @Override
    @Transactional
    public PostFullDto update(Long postId, PostUpdateDto dto, Long currentUserId) {
        // Можно добавить проверку авторства: only author or admin can update
        Post post = postRepository.findByIdWithAllRelations(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found: " + postId));

        // Авторство (если currentUserId != null)
        if (currentUserId != null && post.getAuthor() != null && !post.getAuthor().getId().equals(currentUserId)) {
            // Проверяем роль; если нужно — бросаем AccessDeniedException; здесь просто логируем
            // throw new AccessDeniedException("Only author can update");
            log.debug("User {} is not author of post {}", currentUserId, postId);
            // можно выбросить, но пока позволим (потом интегрировать JWT)
        }

        Post updated = postAssembler.updateFromDto(postId, dto);
        boolean isLiked = currentUserId != null && likeService.isLiked(postId, currentUserId);
        boolean isSubscribed = false; // вычисляем аналогично
        return PostMapper.toFull(updated, isLiked, isSubscribed);
    }

    @Override
    @Transactional
    public void delete(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new EntityNotFoundException("Post not found: " + postId);
        }
        // Можно сначала удалить связанные медиа-файлы через fileStorageService (cover + step images)
        // Для безопасности — загружаем post, берём URLы и удаляем
        Post post = postRepository.findByIdWithAllRelations(postId).orElse(null);
        if (post != null) {
            // удаление cover
            if (post.getCoverUrl() != null) {
                try { fileStorageService.delete(post.getCoverUrl()); } catch (Exception ex) { log.warn("Failed to delete cover: {}", ex.getMessage()); }
            }
            // удаление шагов' изображений
            for (RecipeStep s : post.getSteps()) {
                if (s.getImageUrl() != null) {
                    try { fileStorageService.delete(s.getImageUrl()); } catch (Exception ex) { log.warn("Failed to delete step image: {}", ex.getMessage()); }
                }
            }
        }
        postRepository.deleteById(postId);
    }
}
