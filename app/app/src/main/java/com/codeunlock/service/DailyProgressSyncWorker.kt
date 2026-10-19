package com.codeunlock.service

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.codeunlock.api.dto.ConsumeUnlockTimeRequest
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.unlock.UnlockSessionStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * DailyProgressSyncWorker — PRD §8.3 / §6.2.
 *
 * Previously completely missing. The app was tracking unlock minutes
 * locally in UnlockSessionStore but never sending the consumed time
 * back to the backend via POST /api/daily-progress/consume.
 *
 * Result: the Supabase `daily_progress.unlock_minutes_used` column was
 * always 0, so the ranking formula (which penalises blocked attempts and
 * reads screen time from the DB) had no real data.
 *
 * This worker runs every 5 minutes while unlock sessions are active
 * and flushes the pending consumed-minutes delta to the backend.
 */
@HiltWorker
class DailyProgressSyncWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: CodeUnlockRepository,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val pending = ConsumedTimeStore.drainPending(context)
        if (pending == 0) return Result.success()

        Log.d(TAG, "Syncing $pending consumed unlock minutes to backend")

        return repository.consumeUnlockTime(minutesUsed = pending)
            .fold(
                onSuccess = {
                    Log.d(TAG, "Consumed time synced: $pending min, total used: ${it.unlockMinutesUsed}")
                    Result.success()
                },
                onFailure = {
                    Log.w(TAG, "Consume sync failed — re-queuing $pending min: ${it.message}")
                    // Re-add the minutes so they're retried next run
                    ConsumedTimeStore.addPending(context, pending)
                    Result.retry()
                },
            )
    }

    companion object {
        private const val TAG = "CodeUnlock.DPSync"
        private const val WORK_NAME = "codeunlock_daily_progress_sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyProgressSyncWorker>(
                repeatInterval = 5,
                repeatIntervalTimeUnit = TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        /** Flush immediately — call when an unlock session expires. */
        fun flushNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailyProgressSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}

/**
 * ConsumedTimeStore — local accumulator for unlock minutes consumed.
 *
 * Call addPending() every time the app increments consumed unlock time
 * (e.g. when a timer tick fires in the unlock session). drainPending()
 * atomically reads-then-resets the counter for sending to the backend.
 */
object ConsumedTimeStore {
    private const val PREFS = "codeunlock_consumed_time"
    private const val KEY_PENDING = "pending_minutes"

    /** Record that N additional minutes of unlock time were just consumed. */
    fun addPending(context: Context, minutes: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_PENDING, 0)
        prefs.edit().putInt(KEY_PENDING, current + minutes).apply()
    }

    /** Read the pending count and reset to 0. Returns the amount to send. */
    fun drainPending(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY_PENDING, 0)
        if (pending > 0) prefs.edit().putInt(KEY_PENDING, 0).apply()
        return pending
    }
}
