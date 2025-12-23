package ru.zagrebin.culinaryblog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.zagrebin.culinaryblog.data.local.dao.DraftDao
import ru.zagrebin.culinaryblog.data.local.dao.PostDao
import ru.zagrebin.culinaryblog.data.local.dao.UserProfileDao
import ru.zagrebin.culinaryblog.data.local.entity.DraftEntity
import ru.zagrebin.culinaryblog.data.local.entity.PostEntity
import ru.zagrebin.culinaryblog.data.local.entity.UserProfileEntity

@Database(
    entities = [PostEntity::class, DraftEntity::class, UserProfileEntity::class],
    version = 3
)
abstract class CulinaryDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun draftDao(): DraftDao
    abstract fun userProfileDao(): UserProfileDao
}
