package ru.zagrebin.culinaryblog.data.local

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import ru.zagrebin.culinaryblog.data.local.entity.DraftEntity
import ru.zagrebin.culinaryblog.data.local.entity.PostEntity
import ru.zagrebin.culinaryblog.data.local.entity.UserProfileEntity
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostIngredientRequest
import ru.zagrebin.culinaryblog.model.RecipeStepRequest
import ru.zagrebin.culinaryblog.model.UserProfile
import java.util.UUID
import kotlin.math.absoluteValue

private const val tagSeparator = ";"
private val tagListType = object : TypeToken<List<String>>() {}.type
private val tagsGson = Gson()

fun PostCard.toEntity(): PostEntity = PostEntity(
    id = id,
    title = title,
    excerpt = excerpt,
    coverUrl = coverUrl,
    authorId = authorId,
    postType = postType,
    likesCount = likesCount,
    cookingTimeMinutes = cookingTimeMinutes,
    calories = calories,
    authorName = authorName,
    publishedAt = publishedAt,
    tags = tags?.let { tagsGson.toJson(it.toList()) },
    viewsCount = viewsCount,
    liked = liked
)

fun PostEntity.toModel(): PostCard = PostCard(
    id = id,
    title = title,
    excerpt = excerpt,
    coverUrl = coverUrl,
    authorId = authorId,
    postType = postType,
    likesCount = likesCount,
    cookingTimeMinutes = cookingTimeMinutes,
    calories = calories,
    authorName = authorName,
    publishedAt = publishedAt,
    tags = tags?.let { tagsGson.fromJson<List<String>>(it, tagListType) }?.filter { it.isNotBlank() }?.toSet(),
    viewsCount = viewsCount,
    liked = liked
)

fun DraftEntity.toDraft(gson: Gson): PostDraft {
    val request = PostCreateRequest(
        postType = postType,
        status = status,
        title = title,
        excerpt = excerpt,
        content = content,
        coverUrl = coverUrl,
        cookingTimeMinutes = cookingTimeMinutes,
        calories = calories,
        authorId = authorId,
        clientId = clientId,
        tagIds = tagIds.split(tagSeparator).filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull() },
        ingredients = gson.fromJson(ingredientsJson, object : TypeToken<List<PostIngredientRequest>>() {}.type)
            ?: emptyList(),
        steps = gson.fromJson(stepsJson, object : TypeToken<List<RecipeStepRequest>>() {}.type)
            ?: emptyList()
    )
    return PostDraft(
        id = id,
        updatedAt = updatedAt,
        request = request,
        syncState = syncState,
        serverId = serverId,
        coverLocalUri = coverLocalUri,
        stepsImagesJson = stepsImagesJson
    )
}

fun PostDraft.toEntity(gson: Gson): DraftEntity = DraftEntity(
    id = id,
    clientId = request.clientId ?: UUID.randomUUID().toString(),
    serverId = serverId,
    postType = request.postType,
    status = request.status,
    title = request.title,
    excerpt = request.excerpt,
    content = request.content,
    coverUrl = request.coverUrl,
    coverLocalUri = coverLocalUri,
    cookingTimeMinutes = request.cookingTimeMinutes,
    calories = request.calories,
    authorId = request.authorId,
    tagIds = request.tagIds.joinToString(tagSeparator),
    ingredientsJson = gson.toJson(request.ingredients),
    stepsJson = gson.toJson(request.steps),
    stepsImagesJson = stepsImagesJson,
    syncState = syncState,
    updatedAt = updatedAt
)

fun PostCreateRequest.toDraftEntity(gson: Gson, draftId: Long = 0, coverLocalUri: String? = null, stepsImagesJson: String? = null): DraftEntity = DraftEntity(
    id = draftId,
    clientId = clientId ?: UUID.randomUUID().toString(),
    serverId = null,
    postType = postType,
    status = status,
    title = title,
    excerpt = excerpt,
    content = content,
    coverUrl = coverUrl,
    coverLocalUri = coverLocalUri,
    cookingTimeMinutes = cookingTimeMinutes,
    calories = calories,
    authorId = authorId,
    tagIds = tagIds.joinToString(tagSeparator),
    ingredientsJson = gson.toJson(ingredients),
    stepsJson = gson.toJson(steps),
    stepsImagesJson = stepsImagesJson,
    syncState = "PENDING",
    updatedAt = System.currentTimeMillis()
)

fun UserProfileEntity.toModel(): UserProfile = UserProfile(
    id = id,
    username = username,
    email = email,
    displayName = displayName,
    role = role,
    avatarUrl = avatarUrl
)

fun UserProfile.toEntity(): UserProfileEntity = UserProfileEntity(
    id = id ?: run {
        val source = username ?: email ?: UUID.randomUUID().toString()
        UUID.nameUUIDFromBytes(source.toByteArray()).mostSignificantBits.absoluteValue
    },
    username = username,
    email = email,
    displayName = displayName,
    role = role,
    avatarUrl = avatarUrl
)
