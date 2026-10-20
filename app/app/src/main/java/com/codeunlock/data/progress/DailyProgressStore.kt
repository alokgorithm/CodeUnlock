package com.codeunlock.data.progress

import android.content.Context
import com.codeunlock.data.unlock.UnlockSessionStore
import java.util.Calendar

object DailyProgressStore {
    private const val PREFS_NAME = "codeunlock_daily_progress"
    private const val KEY_SOLVED_COUNT_PREFIX = "solved_count_"
    private const val KEY_SOLVED_IDS_PREFIX = "solved_ids_"
    private const val KEY_UNLOCKED_MINUTES_PREFIX = "unlocked_min_"
    private const val KEY_PLATFORM_SOLVED_PREFIX = "platform_solved_"

    fun getTodayKey(): String {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    /**
     * Records a problem solve for today.
     * Guarantees idempotent counting by problemId.
     * Returns the updated problems solved count for today.
     */
    fun recordSolve(context: Context, problemId: Int, unlockMinutes: Int = 30): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val solvedIdsKey = KEY_SOLVED_IDS_PREFIX + today
        val solvedCountKey = KEY_SOLVED_COUNT_PREFIX + today
        val unlockedMinKey = KEY_UNLOCKED_MINUTES_PREFIX + today

        val existingIds = prefs.getStringSet(solvedIdsKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        val wasAlreadyRecorded = existingIds.contains(problemId.toString())
        existingIds.add(problemId.toString())

        val currentCount = prefs.getInt(solvedCountKey, 0)
        val newCount = if (!wasAlreadyRecorded) maxOf(currentCount + 1, existingIds.size) else maxOf(currentCount, existingIds.size)
        val currentMinutes = prefs.getInt(unlockedMinKey, 0)

        prefs.edit()
            .putStringSet(solvedIdsKey, existingIds)
            .putInt(solvedCountKey, newCount)
            .putInt(unlockedMinKey, currentMinutes + (if (!wasAlreadyRecorded) unlockMinutes else 0))
            .apply()

        return newCount
    }

    /**
     * Get the count of problems solved today.
     * If 0, check if there is an active coding unlock session from today, which means at least 1 solve occurred.
     */
    fun getProblemsSolvedToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val stored = prefs.getInt(KEY_SOLVED_COUNT_PREFIX + today, 0)
        val lcSolves = prefs.getInt(KEY_PLATFORM_SOLVED_PREFIX + "leetcode_" + today, 0)
        val cfSolves = prefs.getInt(KEY_PLATFORM_SOLVED_PREFIX + "codeforces_" + today, 0)
        val effective = maxOf(stored, lcSolves + cfSolves)
        if (effective > 0) return effective

        // Check if an active coding unlock session is present today
        val hasActiveCoding = UnlockSessionStore.readActiveSessions(context).any {
            it.unlockType == UnlockSessionStore.UnlockType.CODING
        }
        if (hasActiveCoding) {
            prefs.edit().putInt(KEY_SOLVED_COUNT_PREFIX + today, 1).apply()
            return 1
        }

        return 0
    }

    fun getUnlockMinutesEarnedToday(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        return prefs.getInt(KEY_UNLOCKED_MINUTES_PREFIX + today, 0)
    }

    fun getSolvedProblemIdsToday(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val raw = prefs.getStringSet(KEY_SOLVED_IDS_PREFIX + today, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun setPlatformSolvesToday(context: Context, platform: String, count: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        val currentStored = prefs.getInt(KEY_SOLVED_COUNT_PREFIX + today, 0)
        prefs.edit()
            .putInt(KEY_PLATFORM_SOLVED_PREFIX + platform + "_" + today, count)
            .putInt(KEY_SOLVED_COUNT_PREFIX + today, maxOf(currentStored, count))
            .apply()
    }

    fun getPlatformSolvesToday(context: Context, platform: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val today = getTodayKey()
        return prefs.getInt(KEY_PLATFORM_SOLVED_PREFIX + platform + "_" + today, 0)
    }
}
