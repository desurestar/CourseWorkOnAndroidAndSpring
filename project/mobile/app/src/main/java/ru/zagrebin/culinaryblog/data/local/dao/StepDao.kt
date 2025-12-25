package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.zagrebin.culinaryblog.data.local.entity.StepEntity

@Dao
interface StepDao {
    @Query("SELECT * FROM steps WHERE postId = :postId ORDER BY stepOrder ASC")
    suspend fun getByPostId(postId: Long): List<StepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<StepEntity>)

    @Query("DELETE FROM steps WHERE postId = :postId")
    suspend fun deleteByPostId(postId: Long)
}
