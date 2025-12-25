package ru.zagrebin.culinaryblog.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ingredients")
data class IngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val postId: Long,
    val ingredientId: Long?,
    val ingredientName: String,
    val quantityValue: Double?,
    val unit: String?
)
