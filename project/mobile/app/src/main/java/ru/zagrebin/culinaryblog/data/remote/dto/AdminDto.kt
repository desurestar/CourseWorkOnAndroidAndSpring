package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.util.resolveUrl

data class AdminPostDto(
    val id: Long,
    val title: String?,
    val status: String?,
    val postType: String?,
    val authorId: Long?,
    val authorUsername: String?,
    val createdAt: String?,
    val updatedAt: String?
)

data class AdminUserDto(
    val id: Long,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
)

data class UpdateStatusRequest(val status: String)

data class CreateIngredientRequest(val name: String)

data class CreateTagRequest(
    val name: String,
    val slug: String? = null,
    val color: String? = null
)

data class UpdateUserRoleRequest(val role: String)

fun AdminPostDto.toModel(): AdminPost = AdminPost(
    id = id,
    title = title,
    status = status,
    postType = postType,
    authorUsername = authorUsername,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun AdminUserDto.toModel(): AdminUser = AdminUser(
    id = id,
    username = username,
    email = email,
    displayName = displayName,
    role = role,
    avatarUrl = resolveUrl(avatarUrl)
)
