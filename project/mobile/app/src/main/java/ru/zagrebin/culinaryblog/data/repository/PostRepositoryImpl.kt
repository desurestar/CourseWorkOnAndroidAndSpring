package ru.zagrebin.culinaryblog.data.repository

import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import ru.zagrebin.culinaryblog.data.local.dao.DraftDao
import ru.zagrebin.culinaryblog.data.local.dao.PostDao
import ru.zagrebin.culinaryblog.data.local.toDraft
import ru.zagrebin.culinaryblog.data.local.toEntity
import ru.zagrebin.culinaryblog.data.local.toModel
import ru.zagrebin.culinaryblog.data.local.toDraftEntity
import ru.zagrebin.culinaryblog.data.remote.api.PostApi
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.PostFilters
import ru.zagrebin.culinaryblog.model.PostAuthor
import ru.zagrebin.culinaryblog.model.PostIngredientLine
import ru.zagrebin.culinaryblog.model.PostStep
import ru.zagrebin.culinaryblog.model.PostTag
import ru.zagrebin.culinaryblog.model.PostUpdateRequest
import ru.zagrebin.culinaryblog.model.STATUS_DRAFT
import ru.zagrebin.culinaryblog.model.TagItem

class PostRepositoryImpl @Inject constructor(
    private val api: PostApi,
    private val postDao: PostDao,
    private val draftDao: DraftDao,
    private val gson: Gson
): PostRepository {
    override suspend fun getPublishedPosts(
        page: Int,
        pageSize: Int,
        filters: PostFilters?
    ): Result<PaginatedResult<PostCard>> = withContext(Dispatchers.IO) {
        val normalizedFilters = filters?.normalizedForType(filters.postType)
        return@withContext try {
            val resp = api.getPublishedPosts(
                page = page,
                pageSize = pageSize,
                postType = normalizedFilters?.postType,
                cookingTimeMin = normalizedFilters?.cookingTimeMin,
                cookingTimeMax = normalizedFilters?.cookingTimeMax,
                caloriesMin = normalizedFilters?.caloriesMin,
                caloriesMax = normalizedFilters?.caloriesMax,
                tags = normalizedFilters?.tagList()
            )
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                val likedIds = postDao.getLikedIds().toSet()
                val items = body.results?.map { dto -> dto.toModel() } ?: emptyList()
                val mergedItems = items.map { card ->
                    val liked = card.liked || likedIds.contains(card.id)
                    card.copy(liked = liked)
                }
                val nextPage = body.next?.let { Uri.parse(it).getQueryParameter("page")?.toIntOrNull() }
                val shouldCache = normalizedFilters == null || normalizedFilters.isEmpty(normalizedFilters.postType)
                if (shouldCache && page == 1) postDao.clear()
                if (shouldCache) {
                    postDao.insertAll(mergedItems.map { it.toEntity() })
                }
                Result.success(PaginatedResult(mergedItems, nextPage))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            val cached = applyLocalFilters(postDao.getAll().map { it.toModel() }, normalizedFilters)
            if (cached.isNotEmpty()) {
                Result.failure(OfflineCacheException(cached))
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun getCachedPosts(): List<PostCard> = withContext(Dispatchers.IO) {
        postDao.getAll().map { it.toModel() }
    }

    override suspend fun getPost(id: Long): Result<PostFull> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getPost(id)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            val cached = postDao.getById(id)?.toModel()
            if (cached != null) {
                Result.success(toFullFromCard(cached))
            } else {
                Result.failure(e)
            }
        }
    }

    override suspend fun getTags(search: String?): Result<List<TagItem>> = withContext(Dispatchers.IO) {
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

    override suspend fun getIngredients(search: String?): Result<List<IngredientItem>> = withContext(Dispatchers.IO) {
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

    override suspend fun uploadImage(
        type: String,
        fileName: String,
        content: ByteArray,
        mimeType: String
    ): Result<String> {
        return withContext(Dispatchers.IO) {
            try {
                val requestBody = content.toRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)
                val resp = api.upload(type, part)
                if (resp.isSuccessful) {
                    val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                    Result.success(body.url)
                } else {
                    Result.failure(RuntimeException("Server error: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun createPost(request: PostCreateRequest): Result<PostCard> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.createPost(request)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updatePost(postId: Long, request: PostUpdateRequest): Result<PostFull> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.updatePost(postId, request)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
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

    override suspend fun saveDraft(request: PostCreateRequest): Result<PostDraft> = withContext(Dispatchers.IO) {
        try {
            val entityId = draftDao.upsert(request.toDraftEntity(gson))
            val saved = draftDao.getById(entityId) ?: return@withContext Result.failure(
                RuntimeException("Failed to persist draft")
            )
            Result.success(saved.toDraft(gson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateDraft(id: Long, request: PostCreateRequest): Result<PostDraft> = withContext(Dispatchers.IO) {
        try {
            val persistedId = draftDao.upsert(request.toDraftEntity(gson, draftId = id))
            val saved = draftDao.getById(persistedId) ?: return@withContext Result.failure(
                RuntimeException("Draft not found after update")
            )
            Result.success(saved.toDraft(gson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDraft(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            draftDao.delete(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDrafts(authorId: Long?): Result<List<PostDraft>> = withContext(Dispatchers.IO) {
        try {
            if (authorId == null) return@withContext Result.success(emptyList())
            val drafts = draftDao.getByAuthor(authorId).map { it.toDraft(gson) }
            Result.success(drafts)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDraft(id: Long): Result<PostDraft> = withContext(Dispatchers.IO) {
        try {
            val draft = draftDao.getById(id) ?: return@withContext Result.failure(
                NoSuchElementException("Draft not found for id: $id")
            )
            Result.success(draft.toDraft(gson))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getServerDrafts(): Result<List<PostCard>> = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d(TAG, "Fetching server drafts from /api/posts/mine/drafts")
            val resp = api.getMyDrafts(page = 1, pageSize = 100)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body == null) {
                    Log.w(TAG, "Server drafts response body is null")
                    return@withContext Result.failure(RuntimeException("Empty body"))
                }
                val drafts = body.results?.map { it.toModel() } ?: emptyList()
                Log.d(TAG, "Successfully fetched ${drafts.size} server drafts")
                Result.success(drafts)
            } else {
                val errorMsg = "Server error: ${resp.code()}"
                Log.e(TAG, "Failed to fetch server drafts: $errorMsg")
                Result.failure(RuntimeException(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception while fetching server drafts", e)
            Result.failure(e)
        }
    }

    override suspend fun syncDrafts(authorId: Long): Result<Int> = withContext(Dispatchers.IO) {
        var synced = 0
        var lastError: Exception? = null
        // Only sync pending or failed drafts
        val pendingDrafts = draftDao.getByAuthorAndSyncState(authorId, "PENDING") +
                           draftDao.getByAuthorAndSyncState(authorId, "FAILED")
        
        Log.d(TAG, "Starting sync for ${pendingDrafts.size} pending drafts for user $authorId")
        
        pendingDrafts.forEach { entity ->
            try {
                val draft = entity.toDraft(gson)
                // Ensure clientId is set in the request
                val requestWithClientId = draft.request.copy(
                    clientId = draft.request.clientId ?: entity.clientId
                )
                
                draftDao.updateSyncState(entity.id, "IN_SYNC", System.currentTimeMillis())
                Log.d(TAG, "Syncing draft ${entity.id} with clientId: ${requestWithClientId.clientId}")
                val resp = api.createPost(requestWithClientId)
                
                if (resp.isSuccessful) {
                    val postCard = resp.body()
                    if (postCard != null) {
                        // Mark as synced with server ID
                        draftDao.markSynced(entity.id, postCard.id, "SYNCED", System.currentTimeMillis())
                        Log.d(TAG, "Successfully synced draft ${entity.id} -> server post ${postCard.id}")
                    }
                    synced++
                } else {
                    draftDao.updateSyncState(entity.id, "FAILED", System.currentTimeMillis())
                    val errorMsg = "Server error: ${resp.code()}"
                    Log.e(TAG, "Failed to sync draft ${entity.id}: $errorMsg")
                    lastError = RuntimeException(errorMsg)
                }
            } catch (e: Exception) {
                draftDao.updateSyncState(entity.id, "FAILED", System.currentTimeMillis())
                Log.e(TAG, "Exception syncing draft ${entity.id}", e)
                lastError = e
            }
        }
        val error = lastError
        return@withContext when {
            error == null -> {
                Log.d(TAG, "Successfully synced $synced drafts")
                Result.success(synced)
            }
            synced == 0 -> {
                Log.e(TAG, "Failed to sync any drafts", error)
                Result.failure(error)
            }
            else -> {
                Log.w(TAG, "Partially synced $synced drafts, but some failed", error)
                Result.failure(RuntimeException("Synced $synced drafts; some failed", error))
            }
        }
    }

    override suspend fun clearDrafts(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching { draftDao.deleteAll() }
    }

    override suspend fun like(postId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = api.like(postId)
            if (resp.isSuccessful) {
                postDao.markLiked(postId)
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            postDao.markLiked(postId)
            Result.failure(RuntimeException(OFFLINE_LIKE_CACHED))
        }
    }

    override suspend fun unlike(postId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val resp = api.unlike(postId)
            if (resp.isSuccessful) {
                postDao.markUnliked(postId)
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            postDao.markUnliked(postId)
            Result.failure(RuntimeException(OFFLINE_UNLIKE_CACHED))
        }
    }

    override suspend fun getLikedPostIds(): Result<Set<Long>> = withContext(Dispatchers.IO) {
        try {
            Result.success(postDao.getLikedIds().toSet())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun toFullFromCard(card: PostCard): PostFull = PostFull(
        id = card.id,
        postType = card.postType,
        status = "offline",
        title = card.title,
        excerpt = card.excerpt,
        content = card.excerpt,
        coverUrl = card.coverUrl,
        createdAt = card.publishedAt,
        updatedAt = card.publishedAt,
        author = PostAuthor(
            id = card.authorId,
            displayName = card.authorName,
            avatarUrl = card.authorAvatarUrl,
            subscribed = null
        ),
        tags = card.tags?.mapIndexed { index, tag -> PostTag(id = index.toLong(), name = tag, color = null) } ?: emptyList(),
        ingredients = emptyList<PostIngredientLine>(),
        steps = emptyList<PostStep>(),
        likesCount = card.likesCount,
        liked = card.liked,
        viewsCount = card.viewsCount ?: 0L,
        calories = card.calories,
        cookingTimeMinutes = card.cookingTimeMinutes
    )

    private suspend fun cacheDraftLocally(request: PostCreateRequest): Result<Unit> {
        val result = runCatching { draftDao.upsert(request.toDraftEntity(gson)) }
        return result.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = {
                Result.failure(
                    RuntimeException(
                        "Draft was created but failed to save locally; it will not appear in profile until cached",
                        it
                    )
                )
            }
        )
    }

    private fun applyLocalFilters(items: List<PostCard>, filters: PostFilters?): List<PostCard> =
        PostFilters.filter(items, filters, filters?.postType ?: PostFilters.RECIPE_POST_TYPE)

    private companion object {
        const val TAG = "PostRepositoryImpl"
    }
}
