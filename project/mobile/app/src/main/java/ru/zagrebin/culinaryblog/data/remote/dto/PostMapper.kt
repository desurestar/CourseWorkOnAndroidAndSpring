package ru.zagrebin.culinaryblog.data.remote.dto

import ru.zagrebin.culinaryblog.model.Author
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostIngredientLine
import ru.zagrebin.culinaryblog.model.PostStep
import ru.zagrebin.culinaryblog.model.PostTag
import ru.zagrebin.culinaryblog.util.resolveUrl

fun PostCardDto.toModel(): PostCard = PostCard(
    id = id,
    title = title,
    excerpt = excerpt,
    coverUrl = resolveUrl(coverUrl),
    authorId = authorId,
    postType = postType,
    likesCount = likesCount,
    cookingTimeMinutes = cookingTimeMinutes,
    calories = calories,
    authorName = authorName,
    authorAvatarUrl = resolveUrl(authorAvatarUrl),
    publishedAt = publishedAt,
    tags = tags ?: emptySet(),
    viewsCount = viewsCount,
    liked = liked ?: false
)

fun PostFullDto.toModel(): PostFull = PostFull(
    id = id,
    postType = postType,
    status = status,
    title = title,
    excerpt = excerpt,
    content = content,
    coverUrl = resolveUrl(coverUrl),
    createdAt = createdAt,
    updatedAt = updatedAt,
    author = author?.toModel(),
    tags = tags?.map { it.toModel() } ?: emptyList(),
    ingredients = ingredients?.map { it.toModel() } ?: emptyList(),
    steps = steps?.sortedBy { it.order }?.map { it.toModel() } ?: emptyList(),
    likesCount = likesCount ?: 0,
    liked = liked ?: false,
    viewsCount = viewsCount ?: 0L,
    calories = calories,
    cookingTimeMinutes = cookingTimeMinutes
)

private fun PostAuthorDto.toModel(): Author = Author(
    id = id,
    displayName = displayName,
    avatarUrl = resolveUrl(avatarUrl),
    subscribed = subscribed
)

private fun PostTagDto.toModel(): PostTag = PostTag(
    id = id,
    name = name,
    color = color
)

private fun PostIngredientLineDto.toModel(): PostIngredientLine = PostIngredientLine(
    ingredientId = ingredientId,
    ingredientName = ingredientName,
    quantityValue = quantityValue,
    unit = unit
)

private fun PostStepDto.toModel(): PostStep = PostStep(
    order = order,
    description = description,
    imageUrl = resolveUrl(imageUrl)
)
