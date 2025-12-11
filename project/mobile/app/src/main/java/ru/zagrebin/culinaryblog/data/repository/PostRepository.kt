package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.IngredientItem
import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostFull
import ru.zagrebin.culinaryblog.model.TagItem


interface PostRepository {
    suspend fun getPublishedPosts(): Result<List<PostCard>>
    suspend fun getPost(id: Long): Result<PostFull>
    suspend fun getTags(search: String? = null): Result<List<TagItem>>
    suspend fun getIngredients(search: String? = null): Result<List<IngredientItem>>
    suspend fun uploadImage(type: String, fileName: String, content: ByteArray, mimeType: String): Result<String>
    suspend fun createPost(request: PostCreateRequest): Result<PostCard>
}
