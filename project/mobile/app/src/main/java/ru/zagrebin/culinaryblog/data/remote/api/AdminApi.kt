package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import ru.zagrebin.culinaryblog.data.remote.dto.AdminPostDto
import ru.zagrebin.culinaryblog.data.remote.dto.AdminUserDto
import ru.zagrebin.culinaryblog.data.remote.dto.CreateIngredientRequest
import ru.zagrebin.culinaryblog.data.remote.dto.CreateTagRequest
import ru.zagrebin.culinaryblog.data.remote.dto.IngredientDto
import ru.zagrebin.culinaryblog.data.remote.dto.PaginatedResponseDto
import ru.zagrebin.culinaryblog.data.remote.dto.TagDto
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateStatusRequest
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateUserRoleRequest

interface AdminApi {
    @GET("admin/posts")
    suspend fun getPosts(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<AdminPostDto>>

    @PUT("admin/posts/{id}/status")
    suspend fun updatePostStatus(
        @Path("id") id: Long,
        @Body request: UpdateStatusRequest
    ): Response<AdminPostDto>

    @DELETE("admin/posts/{id}")
    suspend fun deletePost(@Path("id") id: Long): Response<Unit>

    @GET("admin/ingredients")
    suspend fun getIngredients(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<IngredientDto>>

    @POST("admin/ingredients")
    suspend fun createIngredient(@Body request: CreateIngredientRequest): Response<IngredientDto>

    @DELETE("admin/ingredients/{id}")
    suspend fun deleteIngredient(@Path("id") id: Long): Response<Unit>

    @GET("admin/tags")
    suspend fun getTags(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 50,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<TagDto>>

    @POST("admin/tags")
    suspend fun createTag(@Body request: CreateTagRequest): Response<TagDto>

    @DELETE("admin/tags/{id}")
    suspend fun deleteTag(@Path("id") id: Long): Response<Unit>

    @GET("admin/users")
    suspend fun getUsers(
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 30,
        @Query("search") search: String? = null
    ): Response<PaginatedResponseDto<AdminUserDto>>

    @PUT("admin/users/{id}/role")
    suspend fun updateUserRole(
        @Path("id") id: Long,
        @Body request: UpdateUserRoleRequest
    ): Response<AdminUserDto>

    @DELETE("admin/users/{id}")
    suspend fun deleteUser(@Path("id") id: Long): Response<Unit>
}
