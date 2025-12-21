package ru.zagrebin.service.impl;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.zagrebin.dto.SubscriptionDto;
import ru.zagrebin.model.User;
import ru.zagrebin.repository.UserRepository;
import ru.zagrebin.service.SubscriptionService;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
        if (!isSubscribed(pair)) {
            pair.current.getSubscriptions().add(pair.target);
            pair.target.getSubscribers().add(pair.current);
            userRepository.saveAll(List.of(pair.current, pair.target));
        }
        return toDto(pair);
    }

    @Override
    @Transactional
    public SubscriptionDto unsubscribe(Long currentUserId, Long targetUserId) {
        UserPair pair = loadUsers(currentUserId, targetUserId);
        if (isSubscribed(pair)) {
            pair.current.getSubscriptions().remove(pair.target);
            pair.target.getSubscribers().remove(pair.current);
            userRepository.saveAll(List.of(pair.current, pair.target));
        }
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
            throw new AccessDeniedException("Authentication required");
        }
        if (targetUserId == null) {
            throw new IllegalArgumentException("Target user is not specified");
        }
        if (currentUserId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot subscribe to yourself");
        }
        User current = userRepository.findById(currentUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + currentUserId));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + targetUserId));
        return new UserPair(current, target);
    }

    private SubscriptionDto toDto(UserPair pair) {
        Set<Long> subscriptionIds = pair.current.getSubscriptions().stream()
                .map(User::getId)
                .collect(Collectors.toSet());
        boolean subscribed = subscriptionIds.contains(pair.target.getId());
        int followersCount = pair.target.getSubscribers().size();
        int followingCount = pair.target.getSubscriptions().size();
        return new SubscriptionDto(pair.target.getId(), subscribed, followersCount, followingCount);
    }

    private boolean isSubscribed(UserPair pair) {
        return pair.current.getSubscriptions().stream()
                .anyMatch(u -> u.getId().equals(pair.target.getId()));
    }

    private record UserPair(User current, User target) {}
}
