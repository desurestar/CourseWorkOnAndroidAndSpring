package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "steps")
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val stepOrder: Int,
    val description: String,
    val imageUrl: String?
)
