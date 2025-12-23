package ru.zagrebin.culinaryblog.model

import ru.zagrebin.culinaryblog.formatDisplayDate
import ru.zagrebin.culinaryblog.util.resolveUrl

data class PostDraft(
    val id: Long,
    val updatedAt: Long,
    val request: PostCreateRequest,
    val syncState: String = "PENDING",
    val serverId: Long? = null,
    val coverLocalUri: String? = null,
    val stepsImagesJson: String? = null
) {
    fun toCard(): PostCard = PostCard(
        id = id,
        title = request.title,
        excerpt = request.excerpt,
        coverUrl = resolveUrl(request.coverUrl?.takeIf { it.isNotBlank() }),
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
