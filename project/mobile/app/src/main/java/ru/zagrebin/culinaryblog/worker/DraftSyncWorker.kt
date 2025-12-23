package ru.zagrebin.culinaryblog.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ru.zagrebin.culinaryblog.data.repository.PostRepository
import ru.zagrebin.culinaryblog.data.repository.AuthRepository

/**
 * WorkManager worker that syncs pending drafts to the server.
 * Runs when network is available.
 */
@HiltWorker
class DraftSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val currentUserId = authRepository.getCurrentUserId()
            if (currentUserId == null) {
                Log.d(TAG, "No user logged in, skipping sync")
                return Result.success()
            }

            Log.d(TAG, "Starting draft sync for user $currentUserId")
            val result = postRepository.syncDrafts(currentUserId)
            
            result.fold(
                onSuccess = { count ->
                    Log.d(TAG, "Successfully synced $count drafts")
                    Result.success()
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to sync drafts", error)
                    // Retry on network or server errors
                    if (error.message?.contains("Server error") == true || 
                        error is java.net.UnknownHostException ||
                        error is java.net.SocketTimeoutException) {
                        Result.retry()
                    } else {
                        Result.failure()
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during sync", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DraftSyncWorker"
        const val WORK_NAME = "draft_sync"
    }
}
