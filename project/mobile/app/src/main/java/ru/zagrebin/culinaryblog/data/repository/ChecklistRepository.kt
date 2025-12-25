package ru.zagrebin.culinaryblog.data.repository

import kotlinx.coroutines.flow.Flow
import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity

interface ChecklistRepository {
    suspend fun insert(checklist: ChecklistEntity): Long
    suspend fun update(checklist: ChecklistEntity)
    suspend fun delete(checklist: ChecklistEntity)
    suspend fun getById(id: Long): ChecklistEntity?
    fun getByOwner(ownerId: Long): Flow<List<ChecklistEntity>>
    suspend fun getByPostId(postId: Long): List<ChecklistEntity>
    fun getAll(): kotlinx.coroutines.flow.Flow<List<ChecklistEntity>>
}
//package ru.zagrebin.culinaryblog.data.repository
//
//import kotlinx.coroutines.flow.Flow
//import ru.zagrebin.culinaryblog.data.local.entity.ChecklistEntity
//
//interface ChecklistRepository {
//    suspend fun insert(checklist: ChecklistEntity): Long
//    suspend fun update(checklist: ChecklistEntity)
//    suspend fun delete(checklist: ChecklistEntity)
//    suspend fun getById(id: Long): ChecklistEntity?
//    fun getByOwner(ownerId: Long): Flow<List<ChecklistEntity>>
//    suspend fun getByPostId(postId: Long): List<ChecklistEntity>
//}
