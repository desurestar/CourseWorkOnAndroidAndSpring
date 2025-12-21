package ru.zagrebin.culinaryblog.model

data class SubscriptionStatus(
    val userId: Long,
    val subscribed: Boolean,
    val followersCount: Int,
    val followingCount: Int
)
