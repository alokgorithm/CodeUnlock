package com.codeunlock.service

import android.content.Context
import java.util.Calendar

/**
 * BlockedAttemptsStore — tracks how many times the blocker fired today.
 *
 * PRD §7.4: wellbeing_daily.blocked_attempt_count
 * PRD §9.1: ranking formula penalises blocked_attempts_last_7d * 0.5
 * PRD §14: "Log all blocked attempts for analytics"
 */
object BlockedAttemptsStore {

    private const val PREFS = "codeunlock_blocked_attempts"
    private const val KEY_COUNT = "today_count"
    private const val KEY_EPOCH_DAY = "epoch_day"

    fun increment(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val today = todayEpochDay()
        val stored = prefs.getLong(KEY_EPOCH_DAY, -1L)
        val current = if (stored == today) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit()
            .putLong(KEY_EPOCH_DAY, today)
            .putInt(KEY_COUNT, current + 1)
            .apply()
    }

    fun getTodayCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getLong(KEY_EPOCH_DAY, -1L)
        return if (stored == todayEpochDay()) prefs.getInt(KEY_COUNT, 0) else 0
    }

    fun resetToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_EPOCH_DAY, todayEpochDay())
            .putInt(KEY_COUNT, 0)
            .apply()
    }

    private fun todayEpochDay(): Long = System.currentTimeMillis() / 86_400_000L
}

/**
 * ServiceAliveStore — PRD §14 device integrity.
 * "Detect if Accessibility Service is killed → alert user"
 * Dashboard reads isAlive() to show a warning banner.
 */
object ServiceAliveStore {

    private const val PREFS = "codeunlock_service_state"
    private const val KEY_ALIVE = "accessibility_service_alive"
    private const val KEY_LAST_ALIVE_TS = "last_alive_ts"
    private const val STALE_THRESHOLD_MS = 5 * 60 * 1_000L // 5 minutes

    @Volatile
    private var lastMemoryHeartbeat = 0L

    fun markAlive(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALIVE, true)
            .putLong(KEY_LAST_ALIVE_TS, System.currentTimeMillis())
            .apply()
    }

    /** Keep heartbeat fresh without disk churn (throttled to at most once per 60 seconds). */
    fun heartbeat(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastMemoryHeartbeat >= 60_000L) {
            lastMemoryHeartbeat = now
            markAlive(context)
        }
    }

    fun markDead(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ALIVE, false)
            .apply()
    }

    /** Returns false if service is dead OR last heartbeat was >5 min ago. */
    fun isAlive(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val alive = prefs.getBoolean(KEY_ALIVE, false)
        val lastTs = prefs.getLong(KEY_LAST_ALIVE_TS, 0L)
        return alive && (System.currentTimeMillis() - lastTs < STALE_THRESHOLD_MS)
    }
}
