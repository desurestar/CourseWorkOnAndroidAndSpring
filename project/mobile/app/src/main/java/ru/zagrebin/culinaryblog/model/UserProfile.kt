package ru.zagrebin.culinaryblog.model

data class UserProfile(
    val id: Long?,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
)
