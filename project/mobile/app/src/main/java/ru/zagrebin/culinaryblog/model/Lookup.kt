package ru.zagrebin.culinaryblog.model

const val STATUS_DRAFT = "draft"

data class TagItem(
    val id: Long,
    val name: String,
    val color: String?
)

data class IngredientItem(
    val id: Long,
    val name: String
)

data class PostIngredientRequest(
    val ingredientId: Long,
    val quantityValue: Double?,
    val unit: String?
)

data class RecipeStepRequest(
    val order: Int,
    val description: String,
    val imageUrl: String?
)

data class PostCreateRequest(
    val postType: String = "recipe",
    val status: String = "draft",
    val title: String,
    val excerpt: String,
    val content: String,
    val coverUrl: String? = null,
    val cookingTimeMinutes: Int? = null,
    val calories: Int? = null,
    val authorId: Long,
    val clientId: String? = null, // UUID for idempotent sync
    val tagIds: List<Long> = emptyList(),
    val ingredients: List<PostIngredientRequest> = emptyList(),
    val steps: List<RecipeStepRequest> = emptyList()
)

data class PostUpdateRequest(
    val postType: String = "recipe",
    val status: String = "draft",
    val title: String,
    val excerpt: String,
    val content: String,
    val coverUrl: String? = null,
    val cookingTimeMinutes: Int? = null,
    val calories: Int? = null,
    val tagIds: List<Long> = emptyList(),
    val ingredients: List<PostIngredientRequest> = emptyList(),
    val steps: List<RecipeStepRequest> = emptyList()
)
