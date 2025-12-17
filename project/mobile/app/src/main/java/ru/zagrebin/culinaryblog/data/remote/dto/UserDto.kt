package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.UserProfile

data class UserDto(
    val id: Long?,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
)

data class UpdateProfileRequest(
    val username: String? = null,
    val email: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

fun UserDto.toModel(): UserProfile = UserProfile(
    id = id,
    username = username,
    email = email,
    displayName = displayName,
    role = role,
    avatarUrl = avatarUrl
)
