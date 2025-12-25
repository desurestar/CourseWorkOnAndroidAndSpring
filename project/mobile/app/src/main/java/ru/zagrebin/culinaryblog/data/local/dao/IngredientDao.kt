package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.zagrebin.culinaryblog.data.local.entity.IngredientEntity

@Dao
interface IngredientDao {
    @Query("SELECT * FROM ingredients WHERE postId = :postId")
    suspend fun getByPostId(postId: Long): List<IngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<IngredientEntity>)

    @Query("DELETE FROM ingredients WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)
}
