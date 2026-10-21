package com.codeunlock.data.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.data.focus.FocusSessionStore
import com.codeunlock.service.BlockedAttemptsStore
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
        "strict_lock" to 0,     // 0 = zero quota, coding problem required
        "strict"      to 0,     // alias
        "custom"      to 0,     // 0 = requires coding unlock
        "hardcore"    to 0,     // 0 = strict zero quota
        "focused"     to 30,    // 30 min daily grace quota before lock
        "relaxed"     to 60,    // 60 min daily quota before lock
        "hard"        to 0,
        "normal"      to 60,
        "free"        to 0,
    )

    data class LiveHabitsStats(
        val totalScreenTimeMinutes: Long,
        val totalScreenTimeFormatted: String,
        val restrictedMinutes: Long,
        val pickupCount: Int,
        val liveScore: Int,
        val focusMinutes: Long,
        val blockedAttempts: Int,
        val reclaimedMinutes: Long
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

        val hasPerAppLimit = settings.perAppLimits.containsKey(packageName)
        val isRestricted = settings.restrictedApps.contains(packageName)

        // 1. Is this app restricted or does it have an active daily limit?
        if (!isRestricted && !hasPerAppLimit) {
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

        // No budget (app is restricted with zero allowed screen time and no per-app limit override) → block.
        if (limitMinutes <= 0 && isRestricted && !hasPerAppLimit) {
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
        if (packageName == context.packageName) return 0L
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val (start, now) = todayBounds()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now) ?: return 0L
        val totalMs = stats.filter { it.packageName == packageName }.maxOfOrNull { it.totalTimeInForeground } ?: 0L
        val maxPossibleMs = (now - start).coerceAtLeast(0L)
        return minOf(totalMs, maxPossibleMs) / 60_000L
    }

    // ── Full per-app usage map — used by WellbeingSyncWorker ─────

    fun collectTodayUsageMap(context: Context): Map<String, AppUsageStat> {
        if (!hasUsageStatsPermission(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm  = context.packageManager
        val (start, now) = todayBounds()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now) ?: return emptyMap()
        val maxPossibleSec = ((now - start).coerceAtLeast(0L)) / 1000L

        return stats
            .filter { it.totalTimeInForeground > 0 && it.packageName.isNotBlank() }
            .filter { s ->
                s.packageName != context.packageName &&
                !s.packageName.startsWith("android") &&
                !s.packageName.startsWith("com.android.") &&
                !s.packageName.startsWith("com.google.android.permissioncontroller")
            }
            .groupBy { it.packageName }
            .mapValues { (_, entries) -> entries.maxOf { it.totalTimeInForeground } }
            .mapNotNull { (pkg, totalMs) ->
                val durationSec = minOf(totalMs / 1000L, maxPossibleSec)
                if (durationSec <= 0L) return@mapNotNull null
                val label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrElse { "[Uninstalled] " + pkg.substringAfterLast('.').capitalized() }

                pkg to AppUsageStat(
                    packageName     = pkg,
                    appName         = label,
                    durationSeconds = durationSec,
                    lastUsedMs      = System.currentTimeMillis(),
                    category        = resolveCategory(pkg),
                )
            }.toMap()
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

    // ── Live habits & phone usage calculation ───────────────────

    fun calculateLiveStats(context: Context): LiveHabitsStats {
        if (!hasUsageStatsPermission(context)) {
            return LiveHabitsStats(0L, "0m", 0L, 0, 100, 0L, 0, 0L)
        }
        val usageMap = collectTodayUsageMap(context)
        val totalSeconds = usageMap.values.fold(0L) { acc, stat -> acc + stat.durationSeconds }
        val totalMinutes = totalSeconds / 60
        val pickupCount = getTodayPickupCount(context)
        val restrictedApps = BlockingSettingsStore.getRestrictedApps(context)

        val restrictedSeconds = usageMap.values
            .filter { restrictedApps.contains(it.packageName) }
            .fold(0L) { acc, stat -> acc + stat.durationSeconds }
        val restrictedMinutes = restrictedSeconds / 60

        val blockedAttempts = BlockedAttemptsStore.getTodayCount(context)
        val focusMinutes = FocusSessionStore.getTodayFocusMinutes(context).toLong()
        val reclaimedMinutes = (blockedAttempts * 12L) + focusMinutes

        val excessivePickups = (pickupCount - 30).coerceAtLeast(0)
        val baseScore = 100 - (if (totalMinutes > 0) (restrictedMinutes.toFloat() / totalMinutes.toFloat() * 50) else 0f) - (excessivePickups * 2)
        val liveScore = baseScore.coerceIn(0f, 100f).toInt()

        val formattedTime = if (totalMinutes >= 60) {
            "${totalMinutes / 60}h ${totalMinutes % 60}m"
        } else {
            "${totalMinutes}m"
        }

        return LiveHabitsStats(
            totalScreenTimeMinutes = totalMinutes,
            totalScreenTimeFormatted = formattedTime,
            restrictedMinutes = restrictedMinutes,
            pickupCount = pickupCount,
            liveScore = liveScore,
            focusMinutes = focusMinutes,
            blockedAttempts = blockedAttempts,
            reclaimedMinutes = reclaimedMinutes
        )
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
        return MODE_DAILY_LIMIT_MINUTES[normalizedMode] ?: 0
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

    // ── Granular Daily & Weekly Usage Analytics ──────────────────

    data class DayUsageSummary(
        val dayLabel: String,         // "M", "T", "W", "T", "F", "S", "S"
        val dayName: String,          // "Mon", "Tue", ...
        val dateText: String,         // "Tue, 20 Oct"
        val dayStartMillis: Long,
        val dayEndMillis: Long,
        val distractingMinutes: Long,
        val productiveMinutes: Long,
        val othersMinutes: Long,
        val totalMinutes: Long,
        val isToday: Boolean,
        val isSelected: Boolean
    )

    data class AppUsageDetail(
        val packageName: String,
        val appName: String,
        val durationSeconds: Long,
        val durationFormatted: String,
        val category: AppCategory,
        val limitMinutes: Int?,       // null if no limit set
        val isBlocking: Boolean,      // true if duration >= limitMinutes
        val distractingSeconds: Long = 0L,
        val productiveSeconds: Long = 0L,
        val othersSeconds: Long = 0L
    )

    data class UsageStatsSummary(
        val isDaily: Boolean,
        val headerTitle: String,      // "8h 36m"
        val headerSubtitle: String,   // "Today - Tue, 20 Oct" or "14 Oct - 20 Oct"
        val distractingMinutes: Long,
        val distractingFormatted: String,
        val productiveMinutes: Long,
        val productiveFormatted: String,
        val othersMinutes: Long,
        val othersFormatted: String,
        val totalMinutes: Long,
        val daysOfWeek: List<DayUsageSummary>,
        val apps: List<AppUsageDetail>
    )

    fun getUsageStatsSummary(
        context: Context,
        isDaily: Boolean,
        targetDateMillis: Long = System.currentTimeMillis(),
        filterCategory: AppCategory? = null,
        searchQuery: String = ""
    ): UsageStatsSummary {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val pm = context.packageManager
        val settings = BlockingSettingsStore.read(context)

        if (usm == null || !hasUsageStatsPermission(context)) {
            val emptyDays = listOf("M", "T", "W", "T", "F", "S", "S").map {
                DayUsageSummary(it, it, "", 0L, 0L, 0L, 0L, 0L, 0L, isToday = false, isSelected = false)
            }
            return UsageStatsSummary(
                isDaily = isDaily,
                headerTitle = "0m",
                headerSubtitle = if (isDaily) "No Usage Permission" else "Permission Required",
                distractingMinutes = 0L,
                distractingFormatted = "0m",
                productiveMinutes = 0L,
                productiveFormatted = "0m",
                othersMinutes = 0L,
                othersFormatted = "0m",
                totalMinutes = 0L,
                daysOfWeek = emptyDays,
                apps = emptyList()
            )
        }

        val cal = Calendar.getInstance().apply {
            timeInMillis = targetDateMillis
            firstDayOfWeek = Calendar.MONDAY
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        val mondayMillis = cal.timeInMillis

        val now = System.currentTimeMillis()
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = todayCal.timeInMillis

        val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
        val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val daysOfWeek = mutableListOf<DayUsageSummary>()
        val dailyAppSecMaps = mutableListOf<Map<String, Long>>()

        for (i in 0..6) {
            val dCal = Calendar.getInstance().apply {
                timeInMillis = mondayMillis
                add(Calendar.DAY_OF_MONTH, i)
            }
            val dStart = dCal.timeInMillis
            val dEnd = dStart + 86_400_000L - 1L

            var dayDistractingSec = 0L
            var dayProductiveSec = 0L
            var dayOthersSec = 0L
            val dayPkgSecMap = mutableMapOf<String, Long>()

            if (dStart <= now) {
                val queryEnd = minOf(dEnd, now)
                val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, dStart, queryEnd) ?: emptyList()
                // Deduplicate: Android may return multiple stat buckets for the same package
                // within a single day query. Take the MAX foreground time per package, not the SUM,
                // to avoid double-counting that produces impossible values (>24h per day).
                val dedupedByPkg = stats
                    .filter { it.totalTimeInForeground > 0 && isValidPackage(context, it.packageName) }
                    .groupBy { it.packageName }
                    .mapValues { (_, entries) -> entries.maxOf { it.totalTimeInForeground } }

                val maxPossibleDaySec = ((queryEnd - dStart).coerceAtLeast(0L) / 1000L).coerceAtMost(86400L)

                dedupedByPkg.forEach { (pkg, foregroundMs) ->
                    val cat = AppCategoryStore.getCategory(context, pkg, settings.restrictedApps.contains(pkg))
                    val sec = minOf(foregroundMs / 1000L, maxPossibleDaySec)
                    if (sec > 0L) {
                        dayPkgSecMap[pkg] = sec
                        when (cat) {
                            AppCategory.DISTRACTING -> dayDistractingSec += sec
                            AppCategory.PRODUCTIVE -> dayProductiveSec += sec
                            AppCategory.OTHERS -> dayOthersSec += sec
                        }
                    }
                }
            }
            dailyAppSecMaps.add(dayPkgSecMap)

            val dDistractingMin = dayDistractingSec / 60
            val dProductiveMin = dayProductiveSec / 60
            val dOthersMin = dayOthersSec / 60
            // Cap per-day total to 24 hours max — Android UsageStats can
            // occasionally report inflated numbers due to overlapping buckets
            val dTotalMin = (dDistractingMin + dProductiveMin + dOthersMin).coerceAtMost(1440L)

            val dateText = java.text.SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(java.util.Date(dStart))
            val isToday = dStart == todayStart
            val isSelected = if (isDaily) {
                targetDateMillis in dStart..dEnd
            } else {
                false
            }

            daysOfWeek.add(
                DayUsageSummary(
                    dayLabel = dayLabels[i],
                    dayName = dayNames[i],
                    dateText = dateText,
                    dayStartMillis = dStart,
                    dayEndMillis = dEnd,
                    distractingMinutes = dDistractingMin,
                    productiveMinutes = dProductiveMin,
                    othersMinutes = dOthersMin,
                    totalMinutes = dTotalMin,
                    isToday = isToday,
                    isSelected = isSelected
                )
            )
        }

        val baseHeaderSubtitle: String
        val appDurationMap = mutableMapOf<String, Long>()
        val totalDistractingMin: Long
        val totalProductiveMin: Long
        val totalOthersMin: Long
        val totalMin: Long

        if (isDaily) {
            val selectedIdx = daysOfWeek.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
                ?: daysOfWeek.indexOfFirst { it.isToday }.takeIf { it >= 0 }
                ?: 0
            val selectedDay = daysOfWeek[selectedIdx]
            val dateFmt = selectedDay.dateText
            baseHeaderSubtitle = if (selectedDay.isToday) "Today - $dateFmt" else dateFmt

            // App durations directly from the selected day's query map
            val dayMap = dailyAppSecMaps.getOrElse(selectedIdx) { emptyMap() }
            appDurationMap.putAll(dayMap)

            totalDistractingMin = selectedDay.distractingMinutes
            totalProductiveMin = selectedDay.productiveMinutes
            totalOthersMin = selectedDay.othersMinutes
            totalMin = selectedDay.totalMinutes
        } else {
            val startFmt = java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(java.util.Date(mondayMillis))
            val sundayEnd = mondayMillis + (7 * 86_400_000L) - 1L
            val endFmt = java.text.SimpleDateFormat("d MMM", Locale.getDefault()).format(java.util.Date(sundayEnd))
            baseHeaderSubtitle = "$startFmt - $endFmt"

            // App durations summed across all 7 days of the week
            for (dayMap in dailyAppSecMaps) {
                for ((pkg, sec) in dayMap) {
                    appDurationMap[pkg] = (appDurationMap[pkg] ?: 0L) + sec
                }
            }

            totalDistractingMin = daysOfWeek.sumOf { it.distractingMinutes }
            totalProductiveMin = daysOfWeek.sumOf { it.productiveMinutes }
            totalOthersMin = daysOfWeek.sumOf { it.othersMinutes }
            totalMin = totalDistractingMin + totalProductiveMin + totalOthersMin
        }

        // Dynamically reflect the selected category filter in hero time and subtitle
        val (displayedMinutes, categorySuffix) = when (filterCategory) {
            AppCategory.DISTRACTING -> totalDistractingMin to " • Distracting"
            AppCategory.PRODUCTIVE -> totalProductiveMin to " • Productive"
            AppCategory.OTHERS -> totalOthersMin to " • Others"
            null -> totalMin to ""
        }

        val headerTitle = formatMinutes(displayedMinutes)
        val headerSubtitle = baseHeaderSubtitle + categorySuffix

        val appDetails = appDurationMap.mapNotNull { (pkg, sec) ->
            if (sec <= 0L) return@mapNotNull null
            val isRestricted = settings.restrictedApps.contains(pkg)
            val cat = AppCategoryStore.getCategory(context, pkg, isRestricted)

            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrElse { pkg.substringAfterLast('.').capitalized() }

            val limitMin = settings.perAppLimits[pkg]
            val durationMin = sec / 60
            val isBlocking = limitMin != null && limitMin > 0 && durationMin >= limitMin

            AppUsageDetail(
                packageName = pkg,
                appName = label,
                durationSeconds = sec,
                durationFormatted = formatSeconds(sec),
                category = cat,
                limitMinutes = limitMin,
                isBlocking = isBlocking,
                distractingSeconds = if (cat == AppCategory.DISTRACTING) sec else 0L,
                productiveSeconds = if (cat == AppCategory.PRODUCTIVE) sec else 0L,
                othersSeconds = if (cat == AppCategory.OTHERS) sec else 0L
            )
        }

        val filteredApps = appDetails
            .filter { filterCategory == null || it.category == filterCategory }
            .filter { searchQuery.isBlank() || it.appName.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
            .sortedByDescending { it.durationSeconds }

        return UsageStatsSummary(
            isDaily = isDaily,
            headerTitle = headerTitle,
            headerSubtitle = headerSubtitle,
            distractingMinutes = totalDistractingMin,
            distractingFormatted = formatMinutes(totalDistractingMin),
            productiveMinutes = totalProductiveMin,
            productiveFormatted = formatMinutes(totalProductiveMin),
            othersMinutes = totalOthersMin,
            othersFormatted = formatMinutes(totalOthersMin),
            totalMinutes = totalMin,
            daysOfWeek = daysOfWeek,
            apps = filteredApps
        )
    }

    private fun isValidPackage(context: Context, pkg: String): Boolean {
        if (pkg.isBlank()) return false
        if (pkg == context.packageName) return false // Never count CodeUnlock itself as distraction/phone screen time
        if (pkg.startsWith("android")) return false
        if (pkg.startsWith("com.android.systemui")) return false
        if (pkg.startsWith("com.google.android.permissioncontroller")) return false
        return true
    }

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }

    fun formatSeconds(seconds: Long): String {
        val mins = seconds / 60
        val hrs = mins / 60
        val remMins = mins % 60
        return when {
            hrs > 0 && remMins > 0 -> "${hrs}h ${remMins}m"
            hrs > 0 -> "${hrs}h"
            remMins > 0 -> "${remMins}m"
            else -> "${seconds}s"
        }
    }

}
