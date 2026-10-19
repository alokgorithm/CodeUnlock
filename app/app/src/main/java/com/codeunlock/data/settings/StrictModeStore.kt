package com.codeunlock.data.settings

import android.content.Context
import com.codeunlock.service.FocusTimerService

/**
 * StrictModeStore — anti-bypass and uninstaller-protection state management.
 *
 * Inspired by Regain's Strict / Nuclear Mode:
 * - Anti-Uninstall: prevents deactivation of Device Administrator
 * - Settings Guard: blocks accessing phone Settings / App Info during active locks
 * - Reboot Persistence: resumes lock screen immediately on phone reboot
 */
object StrictModeStore {

    private const val PREFS_NAME = "codeunlock_strict_mode"
    private const val KEY_STRICT_MODE = "strict_mode_enabled"
    private const val KEY_PREVENT_UNINSTALL = "prevent_uninstall_enabled"
    private const val KEY_SETTINGS_GUARD = "settings_guard_enabled"
    private const val KEY_LOCK_UNTIL_MILLIS = "lock_until_millis"

    @Volatile private var cachedStrictMode: Boolean? = null
    @Volatile private var cachedSettingsGuard: Boolean? = null

    fun isStrictModeEnabled(context: Context): Boolean {
        cachedStrictMode?.let { return it }
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_STRICT_MODE, false)
        cachedStrictMode = value
        return value
    }

    fun setStrictModeEnabled(context: Context, enabled: Boolean) {
        cachedStrictMode = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_STRICT_MODE, enabled)
            .apply()
    }

    fun isPreventUninstallEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PREVENT_UNINSTALL, false)
    }

    fun setPreventUninstallEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREVENT_UNINSTALL, enabled)
            .apply()
    }

    fun isSettingsGuardEnabled(context: Context): Boolean {
        cachedSettingsGuard?.let { return it }
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETTINGS_GUARD, false)
        cachedSettingsGuard = value
        return value
    }

    fun setSettingsGuardEnabled(context: Context, enabled: Boolean) {
        cachedSettingsGuard = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETTINGS_GUARD, enabled)
            .apply()
    }

    fun getLockUntilMillis(context: Context): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LOCK_UNTIL_MILLIS, 0L)
    }

    fun setLockUntilMillis(context: Context, timestamp: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LOCK_UNTIL_MILLIS, timestamp)
            .apply()
    }

    fun clearStrictLock(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LOCK_UNTIL_MILLIS)
            .apply()
    }

    /**
     * Returns true if anti-bypass protection is actively engaged:
     * Either strict mode is toggled ON and lock time has not expired,
     * or a Coding Focus session is actively running.
     */
    fun isProtectionActive(context: Context): Boolean {
        if (!isStrictModeEnabled(context)) return false
        val lockUntil = getLockUntilMillis(context)
        val now = System.currentTimeMillis()
        val isTimerRunning = FocusTimerService.isRunning.value
        return isTimerRunning || (lockUntil > now)
    }
}
