package com.codeunlock

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.codeunlock.data.notification.NotificationHelper
import com.codeunlock.service.DailyProgressSyncWorker
import com.codeunlock.service.WellbeingSyncWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point for the CodeUnlock Android app.
 *
 * Hilt uses this class to generate the application-level dependency graph.
 */
@HiltAndroidApp
class CodeUnlockApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        try {
            NotificationHelper.createChannels(this)
        } catch (e: Exception) {
            android.util.Log.e("CodeUnlock", "Failed to create notification channels", e)
        }
        try {
            WellbeingSyncWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("CodeUnlock", "Failed to schedule WellbeingSyncWorker", e)
        }
        try {
            DailyProgressSyncWorker.schedule(this)
        } catch (e: Exception) {
            android.util.Log.e("CodeUnlock", "Failed to schedule DailyProgressSyncWorker", e)
        }
    }
}
