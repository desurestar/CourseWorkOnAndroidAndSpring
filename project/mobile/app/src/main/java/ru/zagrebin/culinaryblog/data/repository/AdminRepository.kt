package ru.zagrebin.culinaryblog.data.repository

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.data.remote.api.AdminApi
import ru.zagrebin.culinaryblog.data.remote.dto.CreateIngredientRequest
import ru.zagrebin.culinaryblog.data.remote.dto.CreateTagRequest
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateStatusRequest
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateUserRoleRequest
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.TagItem

class AdminRepository @Inject constructor(
    private val api: AdminApi
) {
    suspend fun getPosts(search: String? = null): Result<List<AdminPost>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getPosts(search = search)
            if (resp.isSuccessful) {
                val body = resp.body()
                Result.success(body?.results?.map { it.toModel() } ?: emptyList())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePostStatus(id: Long, status: String): Result<AdminPost> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.updatePostStatus(id, UpdateStatusRequest(status))
            if (resp.isSuccessful) {
                resp.body()?.let { Result.success(it.toModel()) } ?: Result.failure(RuntimeException("Empty body"))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deletePost(id)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Server error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getIngredients(search: String? = null): Result<List<IngredientItem>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getIngredients(search = search)
            if (resp.isSuccessful) {
                val body = resp.body()
                Result.success(body?.results?.map { it.toModel() } ?: emptyList())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createIngredient(name: String): Result<IngredientItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.createIngredient(CreateIngredientRequest(name))
            if (resp.isSuccessful) {
                resp.body()?.let { Result.success(it.toModel()) } ?: Result.failure(RuntimeException("Empty body"))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteIngredient(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteIngredient(id)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Server error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTags(search: String? = null): Result<List<TagItem>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getTags(search = search)
            if (resp.isSuccessful) {
                val body = resp.body()
                Result.success(body?.results?.map { it.toModel() } ?: emptyList())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createTag(name: String, color: String?): Result<TagItem> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.createTag(CreateTagRequest(name = name, color = color))
            if (resp.isSuccessful) {
                resp.body()?.let { Result.success(it.toModel()) } ?: Result.failure(RuntimeException("Empty body"))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTag(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteTag(id)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Server error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUsers(search: String? = null): Result<List<AdminUser>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getUsers(search = search)
            if (resp.isSuccessful) {
                val body = resp.body()
                Result.success(body?.results?.map { it.toModel() } ?: emptyList())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateUserRole(userId: Long, role: String): Result<AdminUser> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.updateUserRole(userId, UpdateUserRoleRequest(role))
            if (resp.isSuccessful) {
                resp.body()?.let { Result.success(it.toModel()) } ?: Result.failure(RuntimeException("Empty body"))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUser(userId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteUser(userId)
            if (resp.isSuccessful) Result.success(Unit) else Result.failure(RuntimeException("Server error: ${resp.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
