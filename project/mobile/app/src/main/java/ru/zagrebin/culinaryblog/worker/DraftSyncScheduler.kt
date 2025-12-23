package ru.zagrebin.culinaryblog.worker

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Helper to schedule and trigger draft synchronization
 */
object DraftSyncScheduler {
    
    /**
     * Schedule periodic sync (runs every 6 hours when connected to network)
     */
    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<DraftSyncWorker>(
            6, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // flex interval
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DraftSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    /**
     * Trigger immediate sync (useful after creating a draft when network is available)
     */
    fun triggerImmediateSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val oneTimeWork = OneTimeWorkRequestBuilder<DraftSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "${DraftSyncWorker.WORK_NAME}_immediate",
            ExistingWorkPolicy.REPLACE,
            oneTimeWork
        )
    }

    /**
     * Cancel all sync work
     */
    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DraftSyncWorker.WORK_NAME)
        WorkManager.getInstance(context).cancelUniqueWork("${DraftSyncWorker.WORK_NAME}_immediate")
    }
}
