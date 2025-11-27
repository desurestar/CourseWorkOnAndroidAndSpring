package ru.zagrebin.culinaryblog.data.remote.dto;

data class PostCardDto(
        val id: Long,
        val title: String,
        val excerpt: String,
        val coverUrl: String?,
        val authorId: Long?,
        val likesCount: Int,
        val cookingTimeMinutes: Int?,
        val calories: Int?,

        val authorName: String?,
        val publishedAt: String?,
        val tags: Set<String>?,
        val viewsCount: Long?
)
