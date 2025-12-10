package ru.zagrebin.culinaryblog.data.remote.dto

data class PostIngredientLineDto(
    val ingredientId: Long,
    val ingredientName: String,
    val quantityValue: Double?,
    val unit: String?
)
