package ru.zagrebin.culinaryblog.data.repository

import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.model.SubscriptionStatus
import ru.zagrebin.culinaryblog.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(): Result<UserProfile>
    suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile>
    suspend fun uploadAvatar(fileName: String, content: ByteArray, mimeType: String): Result<String>
    suspend fun getUserProfile(userId: Long): Result<UserProfile>
    suspend fun getSubscription(userId: Long): Result<SubscriptionStatus>
    suspend fun subscribe(userId: Long): Result<SubscriptionStatus>
    suspend fun unsubscribe(userId: Long): Result<SubscriptionStatus>
    suspend fun getFollowers(userId: Long): Result<List<UserProfile>>
    suspend fun getFollowing(userId: Long): Result<List<UserProfile>>
}
