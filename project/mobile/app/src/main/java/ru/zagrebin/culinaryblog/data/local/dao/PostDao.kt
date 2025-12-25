package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.zagrebin.culinaryblog.data.local.entity.PostEntity

@Dao
interface PostDao {
    @Query("SELECT * FROM posts ORDER BY publishedAt DESC")
    suspend fun getAll(): List<PostEntity>

    @Query("SELECT * FROM posts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PostEntity>)

    @Query("DELETE FROM posts")
    suspend fun clear()

    @Query("DELETE FROM posts WHERE postType = :postType")
    suspend fun clearByPostType(postType: String)

    @Query("UPDATE posts SET liked = 1, likesCount = likesCount + 1 WHERE id = :postId")
    suspend fun markLiked(postId: Long)

    @Query(
        "UPDATE posts SET liked = 0, likesCount = CASE WHEN likesCount > 0 THEN likesCount - 1 ELSE 0 END WHERE id = :postId"
    )
    suspend fun markUnliked(postId: Long)

    @Query("SELECT id FROM posts WHERE liked = 1")
    suspend fun getLikedIds(): List<Long>
}
