package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.zagrebin.culinaryblog.data.remote.dto.PostCardDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostFullDto
import ru.zagrebin.culinaryblog.data.remote.dto.IngredientDto
import ru.zagrebin.culinaryblog.data.remote.dto.PaginatedResponseDto
import ru.zagrebin.culinaryblog.data.remote.dto.TagDto
import ru.zagrebin.culinaryblog.model.PostCreateRequest

interface PostApi {
    @GET("posts")
    suspend fun getPublishedPosts(): Response<List<PostCardDto>>

    @GET("posts/{id}")
    suspend fun getPost(
        @Path("id") id: Long,
        @Query("currentUserId") currentUserId: Long? = null
    ): Response<PostFullDto>

    @GET("tags")
    suspend fun getTags(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 16,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<TagDto>>

    @GET("ingredients")
    suspend fun getIngredients(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<IngredientDto>>

    @POST("posts")
    suspend fun createPost(@Body request: PostCreateRequest): Response<PostCardDto>
}
