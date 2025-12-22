package ru.zagrebin.culinaryblog.model

data class AdminPost(
    val id: Long,
    val title: String,
    val postType: String,
    val status: String,
    val createdAt: String,
    val coverUrl: String?,
    val authorId: Long?,
    val authorUsername: String,
    val authorDisplayName: String
)

data class AdminUser(
    val id: Long,
    val username: String,
    val email: String,
    val displayName: String,
    val role: String,
    val avatarUrl: String?
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)
}
