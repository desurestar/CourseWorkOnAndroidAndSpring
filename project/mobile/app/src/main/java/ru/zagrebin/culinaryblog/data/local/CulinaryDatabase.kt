package ru.zagrebin.culinaryblog.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ru.zagrebin.culinaryblog.data.local.dao.DraftDao
import ru.zagrebin.culinaryblog.data.local.dao.PostDao
import ru.zagrebin.culinaryblog.data.local.dao.UserProfileDao
import ru.zagrebin.culinaryblog.data.local.entity.DraftEntity
import ru.zagrebin.culinaryblog.data.local.entity.PostEntity
import ru.zagrebin.culinaryblog.data.local.entity.UserProfileEntity
import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity

@Database(
    entities = [PostEntity::class, DraftEntity::class, UserProfileEntity::class, ChecklistEntity::class, ru.zagrebin.culinaryblog.data.local.entity.StepEntity::class, ru.zagrebin.culinaryblog.data.local.entity.IngredientEntity::class],
    version = 6
)
abstract class CulinaryDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun draftDao(): DraftDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun checklistDao(): ru.zagrebin.culinaryblog.data.local.dao.ChecklistDao
    abstract fun stepDao(): ru.zagrebin.culinaryblog.data.local.dao.StepDao
    abstract fun ingredientDao(): ru.zagrebin.culinaryblog.data.local.dao.IngredientDao
}
