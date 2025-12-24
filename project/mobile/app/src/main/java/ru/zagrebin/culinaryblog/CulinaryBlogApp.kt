package ru.zagrebin.culinaryblog

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import ru.zagrebin.culinaryblog.worker.DraftSyncScheduler
import javax.inject.Inject

@HiltAndroidApp
class CulinaryBlogApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory
    
    override fun onCreate() {
        super.onCreate()
        // Schedule periodic draft sync
        DraftSyncScheduler.schedulePeriodicSync(this)
        DraftSyncScheduler.triggerImmediateSync(this)
    }
    
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
