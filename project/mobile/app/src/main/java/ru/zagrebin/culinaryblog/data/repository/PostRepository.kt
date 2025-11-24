package ru.zagrebin.culinaryblog.data.repository
import ru.zagrebin.culinaryblog.data.remote.api.PostApi
import ru.zagrebin.culinaryblog.data.remote.dto.toDomain
import ru.zagrebin.culinaryblog.model.Post
import javax.inject.Inject

class PostRepository @Inject constructor(
    private val api: PostApi
) {

    suspend fun getPosts(): List<Post> {
        return api.getPosts().map { it.toDomain() }
    }
}