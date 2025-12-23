package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String, // UUID for idempotent sync
    val serverId: Long? = null, // Post ID from server after successful sync
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
    val syncState: String = "PENDING", // PENDING, SYNCED, FAILED
    val updatedAt: Long = System.currentTimeMillis(),
    val lastSyncAttempt: Long? = null
)
