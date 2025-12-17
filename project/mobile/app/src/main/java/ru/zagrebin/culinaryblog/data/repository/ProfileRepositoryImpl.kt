package ru.zagrebin.culinaryblog.data.repository

import javax.inject.Inject
import ru.zagrebin.culinaryblog.data.remote.api.AuthApi
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.UserProfile

class ProfileRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val postRepository: PostRepository
) : ProfileRepository {
    override suspend fun getProfile(): Result<UserProfile> {
        return try {
            val resp = api.me()
            if (resp.isSuccessful) {
                val body = resp.body() ?: return Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile> {
        return try {
            val resp = api.update(request)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadAvatar(
        fileName: String,
        content: ByteArray,
        mimeType: String
    ): Result<String> {
        return postRepository.uploadImage("avatar", fileName, content, mimeType)
    }
}
