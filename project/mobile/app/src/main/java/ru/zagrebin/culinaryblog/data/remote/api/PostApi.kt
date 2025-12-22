package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import ru.zagrebin.culinaryblog.data.remote.dto.PostCardDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostDto
import ru.zagrebin.culinaryblog.data.remote.dto.PostFullDto
import ru.zagrebin.culinaryblog.data.remote.dto.IngredientDto
import ru.zagrebin.culinaryblog.data.remote.dto.PaginatedResponseDto
import ru.zagrebin.culinaryblog.data.remote.dto.TagDto
import ru.zagrebin.culinaryblog.data.remote.dto.UploadResponseDto
import ru.zagrebin.culinaryblog.model.PostCreateRequest
import ru.zagrebin.culinaryblog.model.PostUpdateRequest

interface PostApi {
    @GET("posts")
    suspend fun getPublishedPosts(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 6,
        @Query("post_type") postType: String? = null,
        @Query("cooking_time_min") cookingTimeMin: Int? = null,
        @Query("cooking_time_max") cookingTimeMax: Int? = null,
        @Query("calories_min") caloriesMin: Int? = null,
        @Query("calories_max") caloriesMax: Int? = null,
        @Query("tags") tags: List<String>? = null
    ): Response<PaginatedResponseDto<PostCardDto>>

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

    @Multipart
    @POST("uploads/{type}")
    suspend fun upload(
        @Path("type") type: String,
        @Part file: MultipartBody.Part
    ): Response<UploadResponseDto>

    @POST("posts")
    suspend fun createPost(@Body request: PostCreateRequest): Response<PostCardDto>

    @PUT("posts/{id}")
    suspend fun updatePost(
        @Path("id") id: Long,
        @Body request: PostUpdateRequest
    ): Response<PostFullDto>

    @DELETE("posts/{id}")
    suspend fun deletePost(@Path("id") id: Long): Response<Unit>

    @POST("posts/{id}/like")
    suspend fun like(@Path("id") id: Long): Response<Unit>

    @DELETE("posts/{id}/like")
    suspend fun unlike(@Path("id") id: Long): Response<Unit>
}
