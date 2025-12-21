package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.SubscriptionStatus

data class SubscriptionDto(
    val userId: Long,
    val subscribed: Boolean,
    val followersCount: Int,
    val followingCount: Int
)

fun SubscriptionDto.toModel(): SubscriptionStatus = SubscriptionStatus(
    userId = userId,
    subscribed = subscribed,
    followersCount = followersCount,
    followingCount = followingCount
)
