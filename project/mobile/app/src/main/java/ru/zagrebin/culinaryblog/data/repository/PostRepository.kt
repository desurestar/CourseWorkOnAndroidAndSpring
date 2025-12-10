package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.PostCard
import ru.zagrebin.culinaryblog.model.PostFull


interface PostRepository {
    suspend fun getPublishedPosts(): Result<List<PostCard>>
    suspend fun getPost(id: Long): Result<PostFull>
}
