package ru.zagrebin.culinaryblog.data.repository;

import javax.inject.Inject
import ru.zagrebin.culinaryblog.data.remote.api.PostApi;
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.TagItem

class PostRepositoryImpl @Inject constructor(
    private val api: PostApi
): PostRepository {
    override suspend fun getPublishedPosts(): Result<List<PostCard>> {
        return try {
            val resp = api.getPublishedPosts()
            if (resp.isSuccessful) {
                val body = resp.body() ?: emptyList()
                Result.success(body.map { it.toModel() })
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getPost(id: Long): Result<PostFull> {
        return try {
            val resp = api.getPost(id)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTags(search: String?): Result<List<TagItem>> {
        return try {
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

    override suspend fun getIngredients(search: String?): Result<List<IngredientItem>> {
        return try {
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

    override suspend fun createPost(request: PostCreateRequest): Result<PostCard> {
        return try {
            val resp = api.createPost(request)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
