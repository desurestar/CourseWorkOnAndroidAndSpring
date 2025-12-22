package ru.zagrebin.culinaryblog.model

data class AdminUser(
    val id: Long,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
) {
    val isAdmin: Boolean
        get() = role?.equals("admin", ignoreCase = true) == true
}
