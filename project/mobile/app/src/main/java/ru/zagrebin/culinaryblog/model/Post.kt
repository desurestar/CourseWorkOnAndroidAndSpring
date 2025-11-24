package ru.zagrebin.culinaryblog.model

import java.time.LocalDateTime
import java.util.UUID

data class Post(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val author: String,
    val summary: String,
    val content: String,
    val imageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val likes: Int = 0,
    val createdAt: LocalDateTime = LocalDateTime.now()
)