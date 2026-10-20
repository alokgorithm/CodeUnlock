package com.codeunlock.data.settings

import android.content.Context
import org.json.JSONObject

/**
 * Shared local store for blocking/onboarding settings.
 *
 * This is intentionally simple and synchronous because it is used by:
 * - Compose onboarding flow
 * - navigation start-destination resolution
 * - AccessibilityService blocker logic
 *
 * The service layer cannot depend on suspend APIs during event handling, so
 * SharedPreferences is a practical fit for this phase.
 */
object BlockingSettingsStore {

    private const val PREFS_NAME = "codeunlock_settings"

    const val KEY_RESTRICTED_APPS = "restricted_apps"
    const val KEY_PER_APP_LIMITS = "per_app_limits"
    const val KEY_USAGE_MODE = "usage_mode"
    const val KEY_DAILY_SOLVE_TARGET = "daily_solve_target"
    const val KEY_EMERGENCY_UNLOCK_ENABLED = "emergency_unlock_enabled"
    const val KEY_FREE_UNLOCK_ENABLED = "free_unlock_enabled"
    const val KEY_FIRST_SETUP_COMPLETE = "is_first_setup_complete"
    const val KEY_LAST_SAVED_AT = "last_saved_at"
    const val KEY_LEETCODE_USERNAME = "leetcode_username"
    const val KEY_CODEFORCES_HANDLE = "codeforces_handle"

    private const val DEFAULT_USAGE_MODE = "strict_lock"
    private const val DEFAULT_DAILY_SOLVE_TARGET = 3

    data class BlockingSettings(
        val restrictedApps: Set<String> = emptySet(),
        val perAppLimits: Map<String, Int> = emptyMap(),
        val usageMode: String = DEFAULT_USAGE_MODE,
        val dailySolveTarget: Int = DEFAULT_DAILY_SOLVE_TARGET,
        val emergencyUnlockEnabled: Boolean = true,
        val freeUnlockEnabled: Boolean = false,
        val isFirstSetupComplete: Boolean = false,
        val lastSavedAt: Long = 0L,
        val leetcodeUsername: String? = null,
        val codeforcesHandle: String? = null
    ) {
        val hasRestrictedApps: Boolean
            get() = restrictedApps.isNotEmpty()
    }

    fun read(context: Context): BlockingSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val perAppLimits = prefs.getString(KEY_PER_APP_LIMITS, null)?.let { jsonStr ->
            runCatching {
                val json = JSONObject(jsonStr)
                val map = mutableMapOf<String, Int>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    map[key] = json.getInt(key)
                }
                map.toMap()
            }.getOrNull()
        } ?: emptyMap()

        return BlockingSettings(
            restrictedApps = prefs.getStringSet(KEY_RESTRICTED_APPS, emptySet())
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet(),
            perAppLimits = perAppLimits,
            usageMode = prefs.getString(KEY_USAGE_MODE, DEFAULT_USAGE_MODE) ?: DEFAULT_USAGE_MODE,
            dailySolveTarget = prefs.getInt(KEY_DAILY_SOLVE_TARGET, DEFAULT_DAILY_SOLVE_TARGET),
            emergencyUnlockEnabled = prefs.getBoolean(KEY_EMERGENCY_UNLOCK_ENABLED, true),
            freeUnlockEnabled = prefs.getBoolean(KEY_FREE_UNLOCK_ENABLED, true),
            isFirstSetupComplete = prefs.getBoolean(KEY_FIRST_SETUP_COMPLETE, false),
            lastSavedAt = prefs.getLong(KEY_LAST_SAVED_AT, 0L),
            leetcodeUsername = prefs.getString(KEY_LEETCODE_USERNAME, null)?.takeIf { it.isNotBlank() },
            codeforcesHandle = prefs.getString(KEY_CODEFORCES_HANDLE, null)?.takeIf { it.isNotBlank() }
        )
    }

    fun write(
        context: Context,
        settings: BlockingSettings
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val perAppLimitsJson = if (settings.perAppLimits.isNotEmpty()) {
            val json = JSONObject()
            settings.perAppLimits.forEach { (pkg, limit) ->
                json.put(pkg, limit)
            }
            json.toString()
        } else {
            null
        }

        prefs.edit()
            .putStringSet(KEY_RESTRICTED_APPS, settings.restrictedApps.toSet())
            .putString(KEY_PER_APP_LIMITS, perAppLimitsJson)
            .putString(KEY_USAGE_MODE, settings.usageMode)
            .putInt(KEY_DAILY_SOLVE_TARGET, settings.dailySolveTarget.coerceAtLeast(2))
            .putBoolean(KEY_EMERGENCY_UNLOCK_ENABLED, settings.emergencyUnlockEnabled)
            .putBoolean(KEY_FREE_UNLOCK_ENABLED, settings.freeUnlockEnabled)
            .putBoolean(KEY_FIRST_SETUP_COMPLETE, settings.isFirstSetupComplete)
            .putLong(KEY_LAST_SAVED_AT, settings.lastSavedAt)
            .putString(KEY_LEETCODE_USERNAME, settings.leetcodeUsername)
            .putString(KEY_CODEFORCES_HANDLE, settings.codeforcesHandle)
            .apply()
    }

    fun markOnboardingComplete(
        context: Context,
        restrictedApps: Set<String>,
        usageMode: String,
        dailySolveTarget: Int,
        emergencyUnlockEnabled: Boolean,
        freeUnlockEnabled: Boolean,
        perAppLimits: Map<String, Int> = emptyMap(),
        leetcodeUsername: String? = null,
        codeforcesHandle: String? = null
    ) {
        val existing = read(context)
        write(
            context = context,
            settings = BlockingSettings(
                restrictedApps = restrictedApps,
                perAppLimits = perAppLimits,
                usageMode = usageMode,
                dailySolveTarget = dailySolveTarget,
                emergencyUnlockEnabled = emergencyUnlockEnabled,
                freeUnlockEnabled = freeUnlockEnabled,
                isFirstSetupComplete = true,
                lastSavedAt = System.currentTimeMillis(),
                leetcodeUsername = leetcodeUsername ?: existing.leetcodeUsername,
                codeforcesHandle = codeforcesHandle ?: existing.codeforcesHandle
            )
        )
    }

    fun updatePlatformHandles(
        context: Context,
        leetcode: String?,
        codeforces: String?
    ) {
        val current = read(context)
        write(
            context = context,
            settings = current.copy(
                leetcodeUsername = leetcode?.trim()?.takeIf { it.isNotBlank() },
                codeforcesHandle = codeforces?.trim()?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun isOnboardingComplete(context: Context): Boolean {
        return read(context).isFirstSetupComplete
    }

    fun getRestrictedApps(context: Context): Set<String> {
        return read(context).restrictedApps
    }

    fun getAppLimits(context: Context): Map<String, Int> {
        return read(context).perAppLimits
    }

    fun setAppLimit(context: Context, packageName: String, limitMinutes: Int) {
        val current = read(context)
        val updatedLimits = current.perAppLimits.toMutableMap()
        if (limitMinutes > 0) {
            updatedLimits[packageName] = limitMinutes
        } else {
            updatedLimits.remove(packageName)
        }
        val updatedRestricted = if (limitMinutes > 0) {
            current.restrictedApps + packageName
        } else {
            current.restrictedApps
        }
        write(
            context = context,
            settings = current.copy(
                perAppLimits = updatedLimits,
                restrictedApps = updatedRestricted
            )
        )
    }

    fun removeAppLimit(context: Context, packageName: String) {
        val current = read(context)
        val updatedLimits = current.perAppLimits.toMutableMap()
        updatedLimits.remove(packageName)
        write(
            context = context,
            settings = current.copy(perAppLimits = updatedLimits)
        )
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}
