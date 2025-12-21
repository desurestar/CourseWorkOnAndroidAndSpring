package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PaginatedResult
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostDraft
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.TagItem


interface PostRepository {
    suspend fun getPublishedPosts(page: Int = 1, pageSize: Int = 6): Result<PaginatedResult<PostCard>>
    suspend fun getCachedPosts(): List<PostCard>
    suspend fun getPost(id: Long): Result<PostFull>
    suspend fun getTags(search: String? = null): Result<List<TagItem>>
    suspend fun getIngredients(search: String? = null): Result<List<IngredientItem>>
    suspend fun uploadImage(type: String, fileName: String, content: ByteArray, mimeType: String): Result<String>
    suspend fun createPost(request: PostCreateRequest): Result<PostCard>
    suspend fun saveDraft(request: PostCreateRequest): Result<PostDraft>
    suspend fun getDrafts(): Result<List<PostDraft>>
    suspend fun getDraft(id: Long): Result<PostDraft>
    suspend fun like(postId: Long): Result<Unit>
    suspend fun unlike(postId: Long): Result<Unit>
    suspend fun getLikedPostIds(): Result<Set<Long>>
}
