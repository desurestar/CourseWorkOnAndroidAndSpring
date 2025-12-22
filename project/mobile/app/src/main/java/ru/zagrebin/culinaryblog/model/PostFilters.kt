package ru.zagrebin.culinaryblog.model

data class PostFilters(
    val postType: String? = null,
    val cookingTimeMin: Int? = null,
    val cookingTimeMax: Int? = null,
    val caloriesMin: Int? = null,
    val caloriesMax: Int? = null,
    val tags: Set<String> = emptySet()
) {
    fun normalizedForType(type: String?): PostFilters {
        val normalizedType = normalizeType(type, postType)
        if (normalizedType == ARTICLE_POST_TYPE) {
            return copy(
                postType = normalizedType,
                cookingTimeMin = null,
                cookingTimeMax = null,
                caloriesMin = null,
                caloriesMax = null
            )
        }
        return copy(postType = normalizedType)
    }

    fun appliedCount(type: String? = postType): Int {
        val normalizedType = normalizeType(type, postType)
        var count = 0
        if (tags.isNotEmpty()) count++
        if (normalizedType != ARTICLE_POST_TYPE) {
            if (cookingTimeMin != null) count++
            if (cookingTimeMax != null) count++
            if (caloriesMin != null) count++
            if (caloriesMax != null) count++
        }
        return count
    }

    fun isEmpty(type: String? = postType): Boolean = appliedCount(type) == 0

    fun tagList(): List<String> = tags.filter { it.isNotBlank() }

    fun matches(post: PostCard, defaultType: String = RECIPE_POST_TYPE): Boolean {
        val normalized = normalizedForType(postType ?: defaultType)
        val targetType = normalized.postType ?: defaultType
        if (normalizeType(post.postType, defaultType) != normalizeType(targetType, defaultType)) return false
        val isArticle = normalizeType(post.postType, defaultType) == ARTICLE_POST_TYPE
        val tagsOk = normalized.tags.isEmpty() || (post.tags ?: emptySet()).any { normalized.tags.contains(it) }
        val cookingOk = if (isArticle) {
            true
        } else {
            matchesRange(post.cookingTimeMinutes, normalized.cookingTimeMin, normalized.cookingTimeMax)
        }
        val caloriesOk = if (isArticle) {
            true
        } else {
            matchesRange(post.calories, normalized.caloriesMin, normalized.caloriesMax)
        }
        return tagsOk && cookingOk && caloriesOk
    }

    fun filter(posts: List<PostCard>, defaultType: String = RECIPE_POST_TYPE): List<PostCard> =
        posts.filter { matches(it, defaultType) }

    companion object {
        const val ARTICLE_POST_TYPE = "article"
        const val RECIPE_POST_TYPE = "recipe"

        fun filter(posts: List<PostCard>, filters: PostFilters?, defaultType: String = RECIPE_POST_TYPE): List<PostCard> {
            val effective = (filters ?: PostFilters(postType = defaultType)).normalizedForType(filters?.postType ?: defaultType)
            return effective.filter(posts, defaultType)
        }
    }
}

private fun normalizeType(value: String?, fallback: String? = null): String =
    value?.lowercase()?.takeIf { it.isNotBlank() }
        ?: fallback?.lowercase()?.takeIf { it.isNotBlank() }
        ?: PostFilters.RECIPE_POST_TYPE

private fun matchesRange(value: Int?, min: Int?, max: Int?): Boolean {
    if (min != null && (value == null || value < min)) return false
    if (max != null && (value == null || value > max)) return false
    return true
}
