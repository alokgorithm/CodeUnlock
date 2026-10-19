package com.codeunlock.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.service.FocusTimerService
import java.util.Calendar
import java.util.Locale

/**
 * UsageManager — the completely missing core of PRD §6.1/§6.2.
 *
 * PRD §6.1 exact flow:
 *   Foreground app detected (AccessibilityService)
 *   → UsageManager.shouldBlock(packageName)   ← THIS CLASS
 *   → If TRUE → Launch LockActivity overlay
 *   → User cannot dismiss without unlocking
 *
 * PRD §6.2 unlock logic (evaluated here):
 *   IF now < unlockUntil            → allow  (active session in UnlockSessionStore)
 *   ELSE IF freeUsed == false       → allow  (handled in AppBlockerService)
 *   ELSE IF emergencyUsed == false  → block  (LockActivity shows emergency button)
 *   ELSE                            → block
 *
 * shouldBlock() returns true when:
 *   A. App is in restricted list AND has no active unlock, OR
 *   B. App exceeded its per-app daily time limit AND has no active unlock
 */
object UsageManager {

    private const val TAG = "CodeUnlock.UsageManager"

    /** Mode → daily limit per restricted app in minutes (PRD §5 onboarding modes) */
    private val MODE_DAILY_LIMIT_MINUTES = mapOf(
        "relaxed"  to 420,   // 6–8 h → 7 h
        "focused"  to 240,   // 4 h (default recommended)
        "hardcore" to 120,   // 2 h
        "custom"   to 0,     // 0 allowed → requires coding unlock / per-app limit
        // Legacy onboarding aliases backwards-compatibility:
        "strict"   to 120,
        "hard"     to 240,
        "normal"   to 420,
        "free"     to 0,
    )

    // ── Data types ───────────────────────────────────────────────

    data class BlockDecision(
        val shouldBlock: Boolean,
        val reason: Reason,
        val usedMinutesToday: Long = 0L,
        val limitMinutes: Int = 0,
    ) {
        enum class Reason {
            NOT_RESTRICTED,         // not in restricted list → pass
            HAS_ACTIVE_UNLOCK,      // valid session exists → pass
            WITHIN_DAILY_LIMIT,     // under daily quota → pass
            NO_USAGE_PERMISSION,    // can't read UsageStats → pass (avoid over-blocking)
            RESTRICTED_NO_BUDGET,   // in list + no unlock + no time budget → block
            DAILY_LIMIT_EXCEEDED,   // daily quota exhausted + no unlock → block
            FOCUS_SESSION_ACTIVE,   // Focus Mode / Pomodoro session in progress → block
        }
    }

    // Default distracting entertainment and social packages blocked during Focus Sessions
    val DISTRACTING_PACKAGES = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.instagram.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",      // TikTok
        "com.facebook.katana",
        "com.reddit.frontpage",
        "com.netflix.mediaclient",
        "com.snapchat.android",
        "com.twitch.android.app",
        "com.pinterest",
        "com.discord",
        "com.valvesoftware.android.steam.community",
        "com.spotify.music"
    )

    fun isDistractingApp(packageName: String): Boolean {
        if (DISTRACTING_PACKAGES.contains(packageName)) return true
        val lower = packageName.lowercase(Locale.ROOT)
        return lower.contains("youtube") ||
               lower.contains("instagram") ||
               lower.contains("tiktok") ||
               lower.contains("musically") ||
               lower.contains("twitter") ||
               lower.contains("facebook") ||
               lower.contains("reddit") ||
               lower.contains("netflix") ||
               lower.contains("twitch")
    }

    data class AppUsageStat(
        val packageName: String,
        val appName: String,
        val durationSeconds: Long,
        val lastUsedMs: Long,
        val category: String,       // social | entertainment | productivity | other
    )

    // ── Primary entry point ──────────────────────────────────────
    // Called by AppBlockerService on every TYPE_WINDOW_STATE_CHANGED event.

    fun shouldBlock(context: Context, packageName: String): BlockDecision {
        val isFocusActive = FocusTimerService.isRunning.value && !FocusTimerService.isBreakTime.value
        val settings = BlockingSettingsStore.read(context)

        // 0. Focus Session: Strict focus lock across all restricted and distracting apps (e.g. YouTube)
        if (isFocusActive && (settings.restrictedApps.contains(packageName) || isDistractingApp(packageName))) {
            Log.i(TAG, "Focus Mode is ACTIVE — strictly blocking distraction: $packageName")
            return BlockDecision(
                shouldBlock = true,
                reason = BlockDecision.Reason.FOCUS_SESSION_ACTIVE,
                usedMinutesToday = getTodayUsageMinutes(context, packageName),
                limitMinutes = 0
            )
        }

        // 1. Is this app restricted?
        if (!settings.restrictedApps.contains(packageName)) {
            return BlockDecision(false, BlockDecision.Reason.NOT_RESTRICTED)
        }

        // 2. Active coding / emergency / free unlock? → allow.
        //    Checks both global unlocks (__global__) and per-package unlocks.
        if (UnlockSessionStore.hasActiveUnlock(context, packageName)) {
            return BlockDecision(false, BlockDecision.Reason.HAS_ACTIVE_UNLOCK)
        }

        // 3. No UsageStats permission → can't measure time.
        //    Still block by list membership so restricted apps stay blocked.
        if (!hasUsageStatsPermission(context)) {
            Log.w(TAG, "PACKAGE_USAGE_STATS not granted — blocking $packageName by list only")
            return BlockDecision(true, BlockDecision.Reason.RESTRICTED_NO_BUDGET)
        }

        // 4. Resolve this app's daily time budget.
        val limitMinutes = resolveLimit(settings, packageName)

        // No budget (app is restricted with zero allowed screen time) → block.
        if (limitMinutes <= 0) {
            return BlockDecision(
                shouldBlock = true,
                reason = BlockDecision.Reason.RESTRICTED_NO_BUDGET,
                usedMinutesToday = getTodayUsageMinutes(context, packageName),
                limitMinutes = 0,
            )
        }

        // 5. Compare today's foreground time against the budget.
        val usedMinutes = getTodayUsageMinutes(context, packageName)

        return if (usedMinutes < limitMinutes) {
            BlockDecision(
                shouldBlock = false,
                reason = BlockDecision.Reason.WITHIN_DAILY_LIMIT,
                usedMinutesToday = usedMinutes,
                limitMinutes = limitMinutes,
            )
        } else {
            Log.d(TAG, "$packageName: ${usedMinutes}m used / ${limitMinutes}m limit — blocking")
            BlockDecision(
                shouldBlock = true,
                reason = BlockDecision.Reason.DAILY_LIMIT_EXCEEDED,
                usedMinutesToday = usedMinutes,
                limitMinutes = limitMinutes,
            )
        }
    }

    // ── Foreground usage today (minutes) ─────────────────────────

    fun getTodayUsageMinutes(context: Context, packageName: String): Long {
        if (!hasUsageStatsPermission(context)) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (start, now) = todayBounds()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return 0L
        val totalMs = stats.filter { it.packageName == packageName }.sumOf { it.totalTimeInForeground }
        return totalMs / 60_000L
    }

    // ── Full per-app usage map — used by WellbeingSyncWorker ─────

    fun collectTodayUsageMap(context: Context): Map<String, AppUsageStat> {
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm  = context.packageManager
        val (start, now) = todayBounds()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now) ?: return emptyMap()

        return stats
            .filter { it.totalTimeInForeground > 0 && it.packageName.isNotBlank() }
            .filter { s ->
                !s.packageName.startsWith("android") &&
                !s.packageName.startsWith("com.android.") &&
                !s.packageName.startsWith("com.google.android.permissioncontroller")
            }
            .associate { stat ->
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                }.getOrElse { "[Uninstalled] " + stat.packageName.substringAfterLast('.').capitalized() }

                stat.packageName to AppUsageStat(
                    packageName     = stat.packageName,
                    appName         = label,
                    durationSeconds = stat.totalTimeInForeground / 1_000L,
                    lastUsedMs      = stat.lastTimeUsed,
                    category        = resolveCategory(stat.packageName),
                )
            }
    }

    // ── Pickup count proxy via ACTIVITY_RESUMED events ───────────

    fun getTodayPickupCount(context: Context): Int {
        if (!hasUsageStatsPermission(context)) return 0
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (start, now) = todayBounds()
        val events = usm.queryEvents(start, now) ?: return 0
        var count = 0
        val ev = UsageEvents.Event()
        while (events.hasNextEvent()) { events.getNextEvent(ev); if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED) count++ }
        return count
    }

    // ── Permission helper ─────────────────────────────────────────

    fun hasUsageStatsPermission(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    // ── Internal helpers ──────────────────────────────────────────

    private fun resolveLimit(settings: BlockingSettingsStore.BlockingSettings, pkg: String): Int {
        val override = settings.perAppLimits[pkg]
        if (override != null && override >= 0) {
            return override
        }
        val normalizedMode = settings.usageMode.lowercase(Locale.ROOT).trim()
        return MODE_DAILY_LIMIT_MINUTES[normalizedMode] ?: 240
    }

    private fun todayBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis to System.currentTimeMillis()
    }

    private fun resolveCategory(pkg: String) = when {
        SOCIAL.any { pkg.startsWith(it) }         -> "social"
        ENTERTAINMENT.any { pkg.startsWith(it) }  -> "entertainment"
        PRODUCTIVITY.any { pkg.startsWith(it) }   -> "productivity"
        else -> "other"
    }

    private val SOCIAL = setOf(
        "com.instagram.android", "com.facebook.katana", "com.twitter.android",
        "com.snapchat.android", "com.pinterest", "com.linkedin.android",
        "com.whatsapp", "org.telegram.messenger", "com.discord",
        "com.zhiliaoapp.musically",
    )
    private val ENTERTAINMENT = setOf(
        "com.google.android.youtube", "com.netflix.mediaclient",
        "com.spotify.music", "com.reddit.frontpage", "com.twitch.android.app",
    )
    private val PRODUCTIVITY = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook",
        "com.slack", "com.atlassian.android.jira.core",
    )

    private fun String.capitalized() =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
