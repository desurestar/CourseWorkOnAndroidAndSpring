package ru.zagrebin.culinaryblog.data.repository

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.data.local.dao.ChecklistDao
import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity
import javax.inject.Inject

class ChecklistRepositoryImpl @Inject constructor(
    private val dao: ChecklistDao,
    private val gson: Gson
): ChecklistRepository {

    override suspend fun insert(checklist: ChecklistEntity): Long = withContext(Dispatchers.IO) {
        dao.insert(checklist)
    }

    override suspend fun update(checklist: ChecklistEntity) = withContext(Dispatchers.IO) {
        dao.update(checklist)
    }

    override suspend fun delete(checklist: ChecklistEntity) = withContext(Dispatchers.IO) {
        dao.delete(checklist)
    }

    override suspend fun getById(id: Long): ChecklistEntity? = withContext(Dispatchers.IO) {
        dao.getById(id)
    }

    override fun getByOwner(ownerId: Long) = dao.getByOwner(ownerId)

    override suspend fun getByPostId(postId: Long): List<ChecklistEntity> = withContext(Dispatchers.IO) {
        dao.getByPostId(postId)
    }
    override fun getAll() = dao.getAll()
}
