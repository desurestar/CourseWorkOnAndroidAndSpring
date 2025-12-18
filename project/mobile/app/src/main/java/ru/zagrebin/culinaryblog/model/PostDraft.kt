package ru.zagrebin.culinaryblog.model

import ru.zagrebin.culinaryblog.formatDisplayDate

data class PostDraft(
    val id: Long,
    val updatedAt: Long,
    val request: PostCreateRequest
) {
    fun toCard(): PostCard = PostCard(
        id = id,
        title = request.title,
        excerpt = request.excerpt,
        coverUrl = request.coverUrl,
        authorId = request.authorId,
        postType = request.postType,
        likesCount = 0,
        cookingTimeMinutes = request.cookingTimeMinutes,
        calories = request.calories,
        authorName = null,
        publishedAt = formatDisplayDate(java.time.Instant.ofEpochMilli(updatedAt).toString()),
        tags = emptySet(),
        viewsCount = null
    )
}
