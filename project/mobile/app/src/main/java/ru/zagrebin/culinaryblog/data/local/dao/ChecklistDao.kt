package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity

@Dao
interface ChecklistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checklist: ChecklistEntity): Long

    @Update
    suspend fun update(checklist: ChecklistEntity)

    @Delete
    suspend fun delete(checklist: ChecklistEntity)

    @Query("SELECT * FROM checklists WHERE id = :id")
    suspend fun getById(id: Long): ChecklistEntity?

    @Query("SELECT * FROM checklists WHERE owner_id = :ownerId ORDER BY updated_at DESC")
    fun getByOwner(ownerId: Long): Flow<List<ChecklistEntity>>

    @Query("SELECT * FROM checklists WHERE post_id = :postId ORDER BY updated_at DESC")
    suspend fun getByPostId(postId: Long): List<ChecklistEntity>

    @Query("SELECT * FROM checklists ORDER BY updated_at DESC")
    fun getAll(): Flow<List<ChecklistEntity>>
}
