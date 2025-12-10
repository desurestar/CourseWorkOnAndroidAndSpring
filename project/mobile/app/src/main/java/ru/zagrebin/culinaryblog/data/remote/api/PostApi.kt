package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import ru.zagrebin.culinaryblog.data.remote.dto.PostCardDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostFullDto

interface PostApi {
    @GET("posts")
    suspend fun getPublishedPosts(): Response<List<PostCardDto>>

    @GET("posts/{id}")
    suspend fun getPost(
        @Path("id") id: Long,
        @Query("currentUserId") currentUserId: Long? = null
    ): Response<PostFullDto>
}
