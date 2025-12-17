package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): Result<UserProfile>
    suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile>
    suspend fun uploadAvatar(fileName: String, content: ByteArray, mimeType: String): Result<String>
}
