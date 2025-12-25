package ru.zagrebin.culinaryblog.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import ru.zagrebin.culinaryblog.data.local.CulinaryDatabase
import ru.zagrebin.culinaryblog.data.local.dao.DraftDao
import ru.zagrebin.culinaryblog.data.local.dao.PostDao
import ru.zagrebin.culinaryblog.data.local.dao.UserProfileDao

@Module
@InstallIn(SingletonComponent::class)
object StorageModule {
    
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns to drafts table
            database.execSQL("ALTER TABLE drafts ADD COLUMN clientId TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE drafts ADD COLUMN serverId INTEGER")
            database.execSQL("ALTER TABLE drafts ADD COLUMN syncState TEXT NOT NULL DEFAULT 'PENDING'")
            database.execSQL("ALTER TABLE drafts ADD COLUMN lastSyncAttempt INTEGER")
            
            // Generate UUID for existing drafts
            database.execSQL("""
                UPDATE drafts 
                SET clientId = lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-' || 
                                    '4' || substr(hex(randomblob(2)), 2) || '-' || 
                                    substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)), 2) || '-' || 
                                    hex(randomblob(6)))
                WHERE clientId = ''
            """)
        }
    }
    
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add columns to posts table to store ingredients and steps as JSON
            database.execSQL("ALTER TABLE posts ADD COLUMN ingredientsJson TEXT")
            database.execSQL("ALTER TABLE posts ADD COLUMN stepsJson TEXT")
        }
    }
    
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `checklists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `post_id` INTEGER, `title` TEXT NOT NULL, `items_json` TEXT NOT NULL, `owner_id` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL)")
        }
    }
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CulinaryDatabase =
        Room.databaseBuilder(context, CulinaryDatabase::class.java, "culinary_offline.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()


    @Provides
    fun providePostDao(db: CulinaryDatabase): PostDao = db.postDao()

    @Provides
    fun provideDraftDao(db: CulinaryDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideUserProfileDao(db: CulinaryDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideChecklistDao(db: CulinaryDatabase): ru.zagrebin.culinaryblog.data.local.dao.ChecklistDao = db.checklistDao()
}
