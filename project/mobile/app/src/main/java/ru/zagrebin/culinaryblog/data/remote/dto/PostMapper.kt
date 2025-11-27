package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.Post
import ru.zagrebin.culinaryblog.model.PostCard
import java.time.LocalDateTime

fun PostCardDto.toModel(): PostCard = PostCard(
    id = id,
    title = title,
    excerpt = excerpt,
    coverUrl = coverUrl,
    authorId = authorId,
    likesCount = likesCount,
    cookingTimeMinutes = cookingTimeMinutes,
    calories = calories,
    authorName = authorName,
    publishedAt = publishedAt,
    tags = tags ?: emptySet(),
    viewsCount = viewsCount
)