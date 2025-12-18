package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Long = 0,
    val username: String?,
    val email: String?,
    val displayName: String?,
    val role: String?,
    val avatarUrl: String?
)
