package ru.zagrebin.culinaryblog.data.repository

import android.net.Uri
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
import ru.zagrebin.culinaryblog.model.PostAuthor
import ru.zagrebin.culinaryblog.model.PostIngredientLine
import ru.zagrebin.culinaryblog.model.PostStep
import ru.zagrebin.culinaryblog.model.PostTag
import ru.zagrebin.culinaryblog.model.PostUpdateRequest
import ru.zagrebin.culinaryblog.model.TagItem

class PostRepositoryImpl @Inject constructor(
    private val api: PostApi,
    private val postDao: PostDao,
    private val draftDao: DraftDao,
    private val gson: Gson
): PostRepository {
    override suspend fun getPublishedPosts(page: Int, pageSize: Int): Result<PaginatedResult<PostCard>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.getPublishedPosts(page = page, pageSize = pageSize)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                val likedIds = postDao.getLikedIds().toSet()
                val items = body.results?.map { dto -> dto.toModel() } ?: emptyList()
                val nextPage = body.next?.let { Uri.parse(it).getQueryParameter("page")?.toIntOrNull() }
                if (page == 1) postDao.clear()
                postDao.insertAll(items.map { it.toEntity().copy(liked = likedIds.contains(it.id)) })
                Result.success(PaginatedResult(items, nextPage))
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            val cached = postDao.getAll().map { it.toModel() }
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
                if (request.status == DRAFT_STATUS) {
                    draftDao.upsert(request.toDraftEntity(gson, draftId = body.id))
                }
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

    override suspend fun getDrafts(): Result<List<PostDraft>> = withContext(Dispatchers.IO) {
        try {
            Result.success(draftDao.getAll().map { it.toDraft(gson) })
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
        liked = false,
        viewsCount = card.viewsCount ?: 0L,
        calories = card.calories,
        cookingTimeMinutes = card.cookingTimeMinutes
    )

    companion object {
        private const val DRAFT_STATUS = "draft"
    }
}
