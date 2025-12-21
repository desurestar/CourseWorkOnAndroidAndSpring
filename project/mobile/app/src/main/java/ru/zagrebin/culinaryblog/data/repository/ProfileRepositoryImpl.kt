package ru.zagrebin.culinaryblog.data.repository

import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.zagrebin.culinaryblog.data.local.dao.UserProfileDao
import ru.zagrebin.culinaryblog.data.local.toModel
import ru.zagrebin.culinaryblog.data.local.toEntity
import ru.zagrebin.culinaryblog.data.remote.api.AuthApi
import ru.zagrebin.culinaryblog.data.remote.api.UserApi
import ru.zagrebin.culinaryblog.data.remote.dto.UpdateProfileRequest
import ru.zagrebin.culinaryblog.data.remote.dto.toModel
import ru.zagrebin.culinaryblog.model.SubscriptionStatus
import ru.zagrebin.culinaryblog.model.UserProfile

class ProfileRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val postRepository: PostRepository,
    private val userProfileDao: UserProfileDao,
    private val userApi: UserApi
) : ProfileRepository {
    override suspend fun getProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.me()
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                val profile = body.toModel()
                userProfileDao.save(profile.toEntity())
                Result.success(profile)
            } else {
                loadCachedOrFail("Server error: ${resp.code()}")
            }
        } catch (e: Exception) {
            loadCachedOrFail(e.message ?: "Network error")
        }
    }

    override suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = api.update(request)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                val profile = body.toModel()
                userProfileDao.save(profile.toEntity())
                Result.success(profile)
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

    override suspend fun getUserProfile(userId: Long): Result<UserProfile> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = userApi.getUser(userId)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSubscription(userId: Long): Result<SubscriptionStatus> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = userApi.getSubscription(userId)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun subscribe(userId: Long): Result<SubscriptionStatus> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = userApi.subscribe(userId)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unsubscribe(userId: Long): Result<SubscriptionStatus> = withContext(Dispatchers.IO) {
        return@withContext try {
            val resp = userApi.unsubscribe(userId)
            if (resp.isSuccessful) {
                val body = resp.body() ?: return@withContext Result.failure(RuntimeException("Empty body"))
                Result.success(body.toModel())
            } else {
                Result.failure(RuntimeException("Server error: ${resp.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun loadCachedOrFail(message: String): Result<UserProfile> {
        val cached = userProfileDao.getProfile()?.toModel()
        return cached?.let { Result.success(it) } ?: Result.failure(RuntimeException(message))
    }
}
