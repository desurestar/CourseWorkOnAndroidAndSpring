package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import ru.zagrebin.culinaryblog.data.remote.dto.PostCardDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostDto

interface PostApi {
    @GET("posts")
    suspend fun getPublishedPosts(): Response<List<PostCardDto>>
}