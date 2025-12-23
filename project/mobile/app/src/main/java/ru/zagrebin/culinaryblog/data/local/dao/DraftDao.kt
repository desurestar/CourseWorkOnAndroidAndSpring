package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.zagrebin.culinaryblog.data.local.entity.DraftEntity

@Dao
interface DraftDao {
    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<DraftEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: DraftEntity): Long

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM drafts")
    suspend fun deleteAll()

    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): DraftEntity?

    @Query("SELECT * FROM drafts WHERE authorId = :authorId ORDER BY updatedAt DESC")
    suspend fun getByAuthor(authorId: Long): List<DraftEntity>

    @Query("SELECT * FROM drafts WHERE authorId = :authorId AND syncState = :syncState ORDER BY updatedAt DESC")
    suspend fun getByAuthorAndSyncState(authorId: Long, syncState: String): List<DraftEntity>

    @Query("UPDATE drafts SET syncState = :syncState, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun updateSyncState(id: Long, syncState: String, timestamp: Long)

    @Query("UPDATE drafts SET serverId = :serverId, syncState = :syncState, lastSyncAttempt = :timestamp WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: Long, syncState: String, timestamp: Long)
}
