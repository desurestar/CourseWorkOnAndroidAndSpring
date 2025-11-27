package ru.zagrebin.culinaryblog.data.repository;

import ru.zagrebin.culinaryblog.data.remote.api.PostApi;
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.PostCard
import javax.inject.Inject

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
}
