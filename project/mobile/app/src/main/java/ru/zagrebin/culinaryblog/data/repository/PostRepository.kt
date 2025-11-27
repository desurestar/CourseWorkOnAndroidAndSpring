package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.model.PostCard


interface PostRepository {
    suspend fun getPublishedPosts(): Result<List<PostCard>>
}