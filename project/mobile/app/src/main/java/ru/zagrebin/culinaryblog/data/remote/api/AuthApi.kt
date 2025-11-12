package ru.zagrebin.culinaryblog.data.remote.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import ru.zagrebin.culinaryblog.data.remote.dto.AuthRequest
import ru.zagrebin.culinaryblog.data.remote.dto.AuthResponse
import ru.zagrebin.culinaryblog.data.remote.dto.RegisterRequest

interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: AuthRequest): Response<AuthResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>
}