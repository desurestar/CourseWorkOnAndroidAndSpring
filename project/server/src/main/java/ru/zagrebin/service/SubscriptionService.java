package ru.zagrebin.service;

import ru.zagrebin.dto.SubscriptionDto;

public interface SubscriptionService {
    SubscriptionDto subscribe(Long currentUserId, Long targetUserId);
    SubscriptionDto unsubscribe(Long currentUserId, Long targetUserId);
    SubscriptionDto getStatus(Long currentUserId, Long targetUserId);
}
