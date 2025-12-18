package ru.zagrebin.culinaryblog.di

import android.content.Context
import androidx.room.Room
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
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CulinaryDatabase =
        Room.databaseBuilder(context, CulinaryDatabase::class.java, "culinary_offline.db").build()

    @Provides
    fun providePostDao(db: CulinaryDatabase): PostDao = db.postDao()

    @Provides
    fun provideDraftDao(db: CulinaryDatabase): DraftDao = db.draftDao()

    @Provides
    fun provideUserProfileDao(db: CulinaryDatabase): UserProfileDao = db.userProfileDao()
}
