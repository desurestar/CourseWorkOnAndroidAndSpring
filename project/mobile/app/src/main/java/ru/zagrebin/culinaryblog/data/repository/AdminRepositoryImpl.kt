package ru.zagrebin.culinaryblog.data.repository

import android.net.Uri
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.data.remote.api.AdminApi
import ru.zagrebin.culinaryblog.data.remote.dto.*
import ru.zagrebin.culinaryblog.model.AdminPost
import ru.zagrebin.culinaryblog.model.AdminUser
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.TagItem

class AdminRepositoryImpl @Inject constructor(
    private val api: AdminApi
) : AdminRepository {
    override suspend fun getPosts(page: Int, search: String?): Result<PaginatedResult<AdminPost>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.getPosts(page = page, search = search)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                    val items = body.results?.map { it.toModel() } ?: emptyList()
                    val nextPage =
                        body.next?.let { Uri.parse(it).getQueryParameter("page")?.toIntOrNull() }
                    Result.success(PaginatedResult(items, nextPage))
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updatePostStatus(postId: Long, status: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.updatePostStatus(postId, UpdatePostStatusRequest(status))
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deletePost(postId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deletePost(postId)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTags(search: String?): Result<List<TagItem>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getTags(search = search)
            if (resp.isSuccessful) {
                Result.success(resp.body()?.results?.map { it.toModel() } ?: emptyList())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createTag(name: String, slug: String?, color: String?): Result<TagItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.createTag(CreateTagRequest(name, slug, color))
                if (resp.isSuccessful) {
                    Result.success(resp.body()!!.toModel())
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteTag(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteTag(id)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getIngredients(search: String?): Result<List<IngredientItem>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.getIngredients(search = search)
                if (resp.isSuccessful) {
                    Result.success(resp.body()?.results?.map { it.toModel() } ?: emptyList())
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun createIngredient(name: String): Result<IngredientItem> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.createIngredient(CreateIngredientRequest(name))
                if (resp.isSuccessful) {
                    Result.success(resp.body()!!.toModel())
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteIngredient(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteIngredient(id)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUsers(page: Int, search: String?): Result<PaginatedResult<AdminUser>> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.getUsers(page = page, search = search)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                    val items = body.results?.map { it.toModel() } ?: emptyList()
                    val nextPage =
                        body.next?.let { Uri.parse(it).getQueryParameter("page")?.toIntOrNull() }
                    Result.success(PaginatedResult(items, nextPage))
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun updateUserRole(userId: Long, role: String): Result<AdminUser> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val resp = api.updateUserRole(userId, UpdateUserRoleRequest(role))
                if (resp.isSuccessful) {
                    Result.success(resp.body()!!.toModel())
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun deleteUser(userId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.deleteUser(userId)
            if (resp.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
