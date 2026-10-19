package com.codeunlock.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.codeunlock.api.dto.WellbeingSyncRequest
import com.codeunlock.api.dto.AppUsageEntry
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.data.usage.UsageManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * WellbeingSyncWorker — PRD §8.3.
 *
 * "Every 15 minutes while device is active (batched)"
 * "End-of-day summary push"
 * "POST /api/wellbeing/sync with: Array of {apppackage, durationseconds, pickup_count}"
 *
 * Data collected and pushed per PRD §6.5.1:
 *   - totalscreentime, perappusage (package, duration, pickups)
 *   - blocked_attempt_count, unlock_count, longest_session_app
 *
 * Previously completely missing — this is the only place the Android app
 * sends wellbeing data to Supabase.
 */
@HiltWorker
class WellbeingSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CodeUnlockRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "WellbeingSyncWorker running")

        if (!UsageManager.hasUsageStatsPermission(context)) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — skipping wellbeing sync")
            return Result.success()
        }

        return try {
            val today          = LocalDate.now().toString()
            val usageMap       = UsageManager.collectTodayUsageMap(context)
            val pickupCount    = UsageManager.getTodayPickupCount(context)
            val totalScreenMins = (usageMap.values.sumOf { it.durationSeconds } / 60).toInt()

            val appEntries = usageMap.values
                .filter { it.durationSeconds > 30 }   // ignore sub-30s flickers
                .map { stat ->
                    AppUsageEntry(
                        appPackage      = stat.packageName,
                        appName         = stat.appName,
                        durationSeconds = stat.durationSeconds.toInt(),
                        pickupCount     = 0,            // UsageStats has no per-app pickup count
                        category        = stat.category,
                    )
                }

            val blockedAttemptCount = BlockedAttemptsStore.getTodayCount(context)
            val unlockCount         = UnlockSessionStore.readActiveSessions(context).size
            val longestApp          = usageMap.values.maxByOrNull { it.durationSeconds }?.packageName

            val request = WellbeingSyncRequest(
                date                    = today,
                apps                    = appEntries,
                totalScreenTimeMinutes  = totalScreenMins,
                pickupCount             = pickupCount,
                blockedAttemptCount     = blockedAttemptCount,
                unlockCount             = unlockCount,
                longestSessionApp       = longestApp,
            )

            repository.syncWellbeing(request)
                .onSuccess { resp ->
                    Log.d(TAG, "Wellbeing synced — score: ${resp.wellbeingScore}")
                    // Prune expired unlock sessions while we're at it
                    UnlockSessionStore.pruneExpired(context)
                }
                .onFailure { Log.w(TAG, "Wellbeing sync failed: ${it.message}") }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WellbeingSyncWorker error", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "CodeUnlock.WellbeingSync"
        private const val WORK_NAME = "codeunlock_wellbeing_sync_periodic"

        /**
         * Schedule 15-minute periodic sync.
         * Call from Application.onCreate() and BootReceiver.
         * PRD §8.3: "Every 15 minutes while device is active"
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WellbeingSyncWorker>(
                repeatInterval = 15,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Log.d(TAG, "WellbeingSyncWorker scheduled (every 15 min)")
        }

        /** Trigger an immediate one-shot sync (e.g. on app open). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WellbeingSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
