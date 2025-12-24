package ru.zagrebin.culinaryblog.data.local

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class LocalCacheManager @Inject constructor(
    private val database: CulinaryDatabase
) {
    suspend fun clearAll() = withContext(Dispatchers.IO) {
        database.clearAllTables()
    }
}
