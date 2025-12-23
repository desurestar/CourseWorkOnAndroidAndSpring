package ru.zagrebin.culinaryblog.data.repository

import retrofit2.Response
import ru.zagrebin.culinaryblog.data.remote.api.AuthApi
import ru.zagrebin.culinaryblog.data.remote.dto.AuthRequest
import ru.zagrebin.culinaryblog.data.remote.dto.AuthResponse
import ru.zagrebin.culinaryblog.data.remote.dto.RegisterRequest
import ru.zagrebin.culinaryblog.data.storage.TokenStorage
import java.io.IOException
import javax.inject.Inject

class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val tokenStorage: TokenStorage
) {
    suspend fun login(username: String, password: String): Result<Response<AuthResponse>> {
    return try {
        val response = api.login(AuthRequest(username, password))
        Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    suspend fun register(username: String, email: String, password: String): Result<Response<AuthResponse>> {
        return try {
            val response = api.register(RegisterRequest(username, email, password))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    fun getCurrentUserId(): Long? {
        return tokenStorage.getUserId()
    }
}