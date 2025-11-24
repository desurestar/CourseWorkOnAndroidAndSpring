package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.model.PostLike;
import ru.zagrebin.model.PostLikeId;
import ru.zagrebin.repository.PostLikeRepository;
import ru.zagrebin.repository.PostRepository;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.service.LikeService;

@Service
@Slf4j
public class LikeServiceImpl implements LikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    public LikeServiceImpl(PostLikeRepository postLikeRepository,
                           PostRepository postRepository,
                           UserRepository userRepository) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public boolean like(Long postId, Long userId) {
        if (!postRepository.existsById(postId)) throw new EntityNotFoundException("Post not found: " + postId);
        if (!userRepository.existsById(userId)) throw new EntityNotFoundException("User not found: " + userId);

        PostLikeId id = new PostLikeId(postId, userId);
        if (postLikeRepository.existsById(id)) {
            return false; // уже лайкнут
        }

        PostLike like = PostLike.builder()
                .id(id)
                .post(postRepository.getReferenceById(postId))
                .user(userRepository.getReferenceById(userId))
                .build();
        postLikeRepository.save(like);

        // atomic increment at DB-level
        postRepository.incrementLikesCount(postId);
        return true;
    }

    @Override
    @Transactional
    public boolean unlike(Long postId, Long userId) {
        PostLikeId id = new PostLikeId(postId, userId);
        if (!postLikeRepository.existsById(id)) {
            return false;
        }
        postLikeRepository.deleteById(id);
        // decrement (guarded to not go below zero)
        postRepository.decrementLikesCount(postId);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isLiked(Long postId, Long userId) {
        if (postId == null || userId == null) return false;
        return postLikeRepository.existsById(new PostLikeId(postId, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countLikes(Long postId) {
        return postLikeRepository.countByIdPostId(postId);
    }
}