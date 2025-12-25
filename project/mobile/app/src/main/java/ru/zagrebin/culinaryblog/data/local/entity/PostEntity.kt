package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val excerpt: String,
    val coverUrl: String?,
    val authorId: Long?,
    val postType: String?,
    val likesCount: Int,
    val cookingTimeMinutes: Int?,
    val calories: Int?,
    val authorName: String?,
    val publishedAt: String?,
    val tags: String?,
    val ingredientsJson: String? = null,
    val stepsJson: String? = null,
    val viewsCount: Long?,
    val liked: Boolean = false
)
