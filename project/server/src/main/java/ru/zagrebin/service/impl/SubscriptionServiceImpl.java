package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.SubscriptionDto;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.service.SubscriptionService;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    private final UserRepository userRepository;

    public SubscriptionServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public SubscriptionDto subscribe(Long currentUserId, Long targetUserId) {
        UserPair pair = loadUsers(currentUserId, targetUserId);
        pair.current.getSubscriptions().add(pair.target);
        pair.target.getSubscribers().add(pair.current);
        userRepository.save(pair.current);
        return toDto(pair);
    }

    @Override
    @Transactional
    public SubscriptionDto unsubscribe(Long currentUserId, Long targetUserId) {
        UserPair pair = loadUsers(currentUserId, targetUserId);
        pair.current.getSubscriptions().remove(pair.target);
        pair.target.getSubscribers().remove(pair.current);
        userRepository.save(pair.current);
        return toDto(pair);
    }

    @Override
    @Transactional(readOnly = true)
    public SubscriptionDto getStatus(Long currentUserId, Long targetUserId) {
        UserPair pair = loadUsers(currentUserId, targetUserId);
        return toDto(pair);
    }

    private UserPair loadUsers(Long currentUserId, Long targetUserId) {
        if (currentUserId == null) {
            throw new AccessDeniedException("Требуется авторизация");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Не указан пользователь");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Нельзя подписаться на себя");
        }
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUserId));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + targetUserId));
        return new UserPair(current, target);
    }

    private SubscriptionDto toDto(UserPair pair) {
        boolean subscribed = pair.current.getSubscriptions().stream()
                .anyMatch(u -> u.getId().equals(pair.target.getId()));
        int followersCount = pair.target.getSubscribers() != null ? pair.target.getSubscribers().size() : 0;
        int followingCount = pair.target.getSubscriptions() != null ? pair.target.getSubscriptions().size() : 0;
        return new SubscriptionDto(pair.target.getId(), subscribed, followersCount, followingCount);
    }

    private record UserPair(User current, User target) {}
}
