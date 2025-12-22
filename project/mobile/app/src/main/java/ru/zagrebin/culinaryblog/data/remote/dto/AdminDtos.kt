package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser

data class AdminPostDto(
    val id: Long,
    val title: String?,
    val postType: String?,
    val status: String?,
    val createdAt: String?,
    val coverUrl: String?,
    val authorId: Long?,
    val authorUsername: String?,
    val authorDisplayName: String?
)

data class AdminUserDto(
    val id: Long?,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
)

data class UpdatePostStatusRequest(val status: String)
data class CreateTagRequest(val name: String, val slug: String?, val color: String?)
data class CreateIngredientRequest(val name: String)
data class UpdateUserRoleRequest(val role: String)

fun AdminPostDto.toModel() = AdminPost(
    id = id,
    title = title.orEmpty(),
    postType = postType.orEmpty(),
    status = status.orEmpty(),
    createdAt = createdAt.orEmpty(),
    coverUrl = coverUrl,
    authorId = authorId,
    authorUsername = authorUsername.orEmpty(),
    authorDisplayName = authorDisplayName.orEmpty()
)

fun AdminUserDto.toModel() = AdminUser(
    id = id ?: -1,
    username = username.orEmpty(),
    email = email.orEmpty(),
    displayName = displayName.orEmpty(),
    role = role.orEmpty(),
    avatarUrl = avatarUrl
)
