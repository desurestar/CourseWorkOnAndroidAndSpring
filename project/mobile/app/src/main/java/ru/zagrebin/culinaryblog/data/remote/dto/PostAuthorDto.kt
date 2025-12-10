package ru.zagrebin.culinaryblog.data.remote.dto

data class PostAuthorDto(
    val id: Long?,
    val displayName: String?,
    val avatarUrl: String?,
    val subscribed: Boolean?
)
