package ru.zagrebin.culinaryblog.data.remote.dto

data class PostFullDto(
    val id: Long,
    val postType: String?,
    val status: String?,
    val title: String?,
    val excerpt: String?,
    val content: String?,
    val coverUrl: String?,
    val createdAt: String?,
    val updatedAt: String?,
    val author: PostAuthorDto?,
    val tags: List<PostTagDto>?,
    val ingredients: List<PostIngredientLineDto>?,
    val steps: List<PostStepDto>?,
    val likesCount: Int?,
    val liked: Boolean?,
    val viewsCount: Long?,
    val calories: Int?,
    val cookingTimeMinutes: Int?
)
