package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.TagItem

data class PaginatedResponseDto<T>(
    val results: List<T>?,
    val next: String?
)

data class TagDto(
    val id: Long,
    val name: String,
    val color: String?
)

data class IngredientDto(
    val id: Long,
    val name: String
)

fun TagDto.toModel(): TagItem = TagItem(id = id, name = name, color = color)
fun IngredientDto.toModel(): IngredientItem = IngredientItem(id = id, name = name)
