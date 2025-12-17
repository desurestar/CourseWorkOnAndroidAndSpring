package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import ru.zagrebin.culinaryblog.data.remote.dto.AuthRequest
import ru.zagrebin.culinaryblog.data.remote.dto.AuthResponse
import ru.zagrebin.culinaryblog.data.remote.dto.RegisterRequest
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.data.remote.dto.UserDto

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun me(): Response<UserDto>

    @PUT("auth/me")
    suspend fun update(@Body request: UpdateProfileRequest): Response<UserDto>
}
