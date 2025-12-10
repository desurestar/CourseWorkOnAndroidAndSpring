package ru.zagrebin.culinaryblog.model

data class PostFull(
    val id: Long,
    val postType: String?,
    val status: String?,
    val title: String?,
    val excerpt: String?,
    val content: String?,
    val coverUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val author: PostAuthor?,
    val tags: List<PostTag> = emptyList(),
    val ingredients: List<PostIngredientLine> = emptyList(),
    val steps: List<PostStep> = emptyList(),
    val likesCount: Int = 0,
    val liked: Boolean = false,
    val viewsCount: Long = 0L,
    val calories: Int? = null,
    val cookingTimeMinutes: Int? = null
)

data class PostAuthor(
    val id: Long?,
    val displayName: String?,
    val avatarUrl: String?,
    val subscribed: Boolean?
)

data class PostTag(
    val id: Long,
    val name: String,
    val color: String?
)

data class PostIngredientLine(
    val ingredientId: Long,
    val ingredientName: String,
    val quantityValue: Double?,
    val unit: String?
)

data class PostStep(
    val order: Int,
    val description: String,
    val imageUrl: String?
)
