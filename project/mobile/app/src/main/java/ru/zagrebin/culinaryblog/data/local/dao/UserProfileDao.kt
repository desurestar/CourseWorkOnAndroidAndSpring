package ru.zagrebin.culinaryblog.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.zagrebin.culinaryblog.data.local.entity.UserProfileEntity

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: UserProfileEntity)
}
