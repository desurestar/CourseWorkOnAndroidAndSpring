package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postType: String,
    val status: String,
    val title: String,
    val excerpt: String,
    val content: String,
    val coverUrl: String?,
    val cookingTimeMinutes: Int?,
    val calories: Int?,
    val authorId: Long,
    val tagIds: String,
    val ingredientsJson: String,
    val stepsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)
