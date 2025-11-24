package ru.zagrebin.culinaryblog.data.remote.dto

data class PostDto (
    val id: String,
    val title: String,
    val author: String,
    val summary: String,
    val content: String,
    val imageUrl: String?,
    val tags: List<String>,
    val likes: Int,
    val createdAt: String
)