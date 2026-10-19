package com.codeunlock.data.settings

import android.content.Context

/**
 * Preferences store for surgical distraction blocking (Instagram Reels, YouTube Shorts)
 * and Mindful Friction (Pause Before Open) inspired by Regain.
 */
object ReelsBlockingStore {

    private const val PREFS_NAME = "codeunlock_reels_settings"

    const val KEY_REELS_BLOCKED = "reels_blocked"
    const val KEY_SHORTS_BLOCKED = "shorts_blocked"
    const val KEY_ONE_VIDEO_RULE = "one_video_rule"
    const val KEY_MINDFUL_PAUSE = "mindful_pause_enabled"
    const val KEY_MINDFUL_PAUSE_SECONDS = "mindful_pause_seconds"

    data class ReelsSettings(
        val reelsBlocked: Boolean = true,
        val shortsBlocked: Boolean = true,
        val oneVideoRule: Boolean = true,
        val mindfulPauseEnabled: Boolean = true,
        val mindfulPauseSeconds: Int = 5
    )

    @Volatile
    private var cachedSettings: ReelsSettings? = null

    fun read(context: Context): ReelsSettings {
        cachedSettings?.let { return it }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val settings = ReelsSettings(
            reelsBlocked = prefs.getBoolean(KEY_REELS_BLOCKED, true),
            shortsBlocked = prefs.getBoolean(KEY_SHORTS_BLOCKED, true),
            oneVideoRule = prefs.getBoolean(KEY_ONE_VIDEO_RULE, true),
            mindfulPauseEnabled = prefs.getBoolean(KEY_MINDFUL_PAUSE, true),
            mindfulPauseSeconds = prefs.getInt(KEY_MINDFUL_PAUSE_SECONDS, 5)
        )
        cachedSettings = settings
        return settings
    }

    fun write(context: Context, settings: ReelsSettings) {
        cachedSettings = settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_REELS_BLOCKED, settings.reelsBlocked)
            .putBoolean(KEY_SHORTS_BLOCKED, settings.shortsBlocked)
            .putBoolean(KEY_ONE_VIDEO_RULE, settings.oneVideoRule)
            .putBoolean(KEY_MINDFUL_PAUSE, settings.mindfulPauseEnabled)
            .putInt(KEY_MINDFUL_PAUSE_SECONDS, settings.mindfulPauseSeconds)
            .apply()
    }
}
