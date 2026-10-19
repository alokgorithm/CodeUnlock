package com.codeunlock.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.codeunlock.api.repository.CodeUnlockRepository
import com.codeunlock.data.session.SessionManager
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.settings.ReelsBlockingStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.data.usage.UsageManager
import com.codeunlock.ui.lock.LockActivity
import com.codeunlock.ui.mindful.MindfulPauseActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AppBlockerService — complete implementation of PRD §6.1 enforcement.
 *
 * ENHANCEMENTS (Phase 1):
 *   1. Routes through UsageManager.shouldBlock() checking restricted list AND time limits.
 *   2. Free unlock calls POST /api/unlock/free in live mode.
 *   3. Blocked attempt counter incremented for every blocked launch.
 *   4. ServiceAliveStore heartbeat so dashboard gets fresh status without stale timeout.
 *   5. Consumed unlock time tracking via ConsumedTimeStore.addPending() so minutes sync to Supabase.
 *   6. ReelsShortsInterceptor: surgical short-form blocking for Instagram Reels and YouTube Shorts.
 *   7. Mindful Pause (Breathing Interstitial): intentional friction before opening restricted apps.
 */
@AndroidEntryPoint
class AppBlockerService : AccessibilityService() {

    @Inject lateinit var repository: CodeUnlockRepository
    @Inject lateinit var sessionManager: SessionManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var reelsInterceptor: ReelsShortsInterceptor
    private lateinit var settingsGuardInterceptor: SettingsGuardInterceptor

    private var lastBlockedPackage: String? = null
    private var lastLaunchTimestamp: Long = 0L

    private var lastUnlockedPackage: String? = null
    private var lastUnlockedPackageTimestamp: Long = 0L

    private var lastMindfulPausePackage: String? = null
    private var lastMindfulPauseTimestamp: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        reelsInterceptor = ReelsShortsInterceptor(this)
        settingsGuardInterceptor = SettingsGuardInterceptor(this)
        serviceInfo = serviceInfo?.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = flags or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 150
        }
        Log.i(TAG, "AppBlockerService connected with high-performance throttled inspection ✓")
        ServiceAliveStore.markAlive(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()?.trim().orEmpty()
        // Fast-path: Ignore empty packages and our own app immediately to avoid UI loops
        if (packageName.isBlank() || packageName == applicationContext.packageName) return

        // Keep heartbeat alive at most once per 60s
        ServiceAliveStore.heartbeat(applicationContext)

        // Delegate surgical Reels and Shorts inspection ONLY when package is Instagram or YouTube
        if (::reelsInterceptor.isInitialized &&
            (packageName == ReelsShortsInterceptor.PACKAGE_INSTAGRAM || packageName == ReelsShortsInterceptor.PACKAGE_YOUTUBE)) {
            reelsInterceptor.onAccessibilityEvent(event)
        }

        // Delegate Settings Guard anti-bypass inspection ONLY for Settings packages
        if (::settingsGuardInterceptor.isInitialized &&
            (packageName.contains("settings", ignoreCase = true) || packageName == "com.android.packageinstaller")) {
            settingsGuardInterceptor.onAccessibilityEvent(event)
        }

        // Only run full app-level blocking evaluation on window state switches
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return

        if (shouldIgnorePackage(packageName)) return

        // ── PRD §6.1: UsageManager.shouldBlock(packageName) ──────
        val decision = UsageManager.shouldBlock(applicationContext, packageName)

        // Track consumed unlock time if this app is currently unlocked
        trackConsumedTime(packageName, decision.reason == UsageManager.BlockDecision.Reason.HAS_ACTIVE_UNLOCK)

        if (!decision.shouldBlock) return

        val isFocusActive = FocusTimerService.isRunning.value && !FocusTimerService.isBreakTime.value
        if (!isFocusActive) {
            // ── PRD §6.2: free unlock — 1 min automatic, once/day ────
            // Returns true if a free unlock was granted → app may open.
            if (tryGrantFreeUnlock(packageName)) return

            // Check if Mindful Pause (Breathing Interstitial) should be displayed
            val reelsSettings = ReelsBlockingStore.read(applicationContext)
            if (reelsSettings.mindfulPauseEnabled && shouldShowMindfulPause(packageName)) {
                Log.d(TAG, "Triggering Mindful Pause for $packageName")
                launchMindfulPause(packageName)
                return
            }
        }

        // Rate-limit overlay launches to avoid rapid relaunches on same package
        if (!shouldLaunchNow(packageName)) return

        // Increment blocked attempt counter for PRD §7.4 / §9.1 anti-cheat
        BlockedAttemptsStore.increment(applicationContext)

        Log.d(TAG, "Blocking $packageName (${decision.reason}, ${decision.usedMinutesToday}m/${decision.limitMinutes}m)")
        launchLockOverlay(packageName)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        // PRD §14: "Detect if Accessibility Service is killed → alert user"
        ServiceAliveStore.markDead(applicationContext)
        Log.w(TAG, "AppBlockerService destroyed — protection inactive")
    }

    // ── Unlock usage tracking ──────────────────────────────────────
    private fun trackConsumedTime(currentPackage: String, hasActiveUnlock: Boolean) {
        val now = System.currentTimeMillis()
        if (lastUnlockedPackage != null && lastUnlockedPackageTimestamp > 0L) {
            val elapsedMinutes = ((now - lastUnlockedPackageTimestamp) / 60_000L).toInt()
            if (elapsedMinutes > 0) {
                ConsumedTimeStore.addPending(applicationContext, elapsedMinutes)
                lastUnlockedPackageTimestamp = now
            }
        }

        if (hasActiveUnlock) {
            if (lastUnlockedPackage != currentPackage) {
                lastUnlockedPackage = currentPackage
                lastUnlockedPackageTimestamp = now
            }
        } else {
            lastUnlockedPackage = null
            lastUnlockedPackageTimestamp = 0L
        }
    }

    // ── Free unlock handler ───────────────────────────────────────
    private fun tryGrantFreeUnlock(packageName: String): Boolean {
        val settings = BlockingSettingsStore.read(applicationContext)
        if (!settings.freeUnlockEnabled) return false
        if (!UnlockSessionStore.canUseFreeUnlock(applicationContext, packageName)) return false

        val localSession = UnlockSessionStore.grantFreeUnlock(
            context         = applicationContext,
            appPackage      = packageName,
            durationMinutes = UnlockSessionStore.DEFAULT_FREE_UNLOCK_MINUTES,
        ) ?: return false

        // Record 1 minute consumed unlock time
        ConsumedTimeStore.addPending(applicationContext, UnlockSessionStore.DEFAULT_FREE_UNLOCK_MINUTES)
        Log.d(TAG, "Free unlock granted locally for $packageName (1 min consumed logged)")

        if (sessionManager.currentState().hasRealSession) {
            serviceScope.launch {
                repository.freeUnlock(packageName)
                    .onSuccess { resp ->
                        UnlockSessionStore.cacheServerUnlock(applicationContext, resp.unlockSession)
                        DailyProgressSyncWorker.flushNow(applicationContext)
                        Log.d(TAG, "Free unlock synced to backend")
                    }
                    .onFailure {
                        Log.w(TAG, "Free unlock backend sync failed (local still active): ${it.message}")
                    }
            }
        }

        return true
    }

    // ── Mindful Pause helpers ─────────────────────────────────────
    private fun shouldShowMindfulPause(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        if (lastMindfulPausePackage == pkg && now - lastMindfulPauseTimestamp < MINDFUL_PAUSE_COOLDOWN_MS) {
            return false
        }
        lastMindfulPausePackage = pkg
        lastMindfulPauseTimestamp = now
        return true
    }

    private fun launchMindfulPause(packageName: String) {
        val appName = resolveAppName(packageName)
        startActivity(
            MindfulPauseActivity.createIntent(
                context = applicationContext,
                appName = appName,
                appPackage = packageName
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        )
    }

    // ── Package filter ────────────────────────────────────────────
    private fun shouldIgnorePackage(pkg: String): Boolean {
        if (pkg.isBlank() || pkg == applicationContext.packageName) return true
        // Never ignore user-facing browser/apps even if prefixed with com.android.*
        if (pkg == "com.android.chrome") return false

        // Ignore core OS, system UI, installer, and permission controller
        if (pkg == "com.android.systemui" || pkg == "android") return true
        if (pkg.startsWith("com.google.android.permissioncontroller") ||
            pkg.startsWith("com.google.android.packageinstaller") ||
            pkg.startsWith("com.android.packageinstaller")) return true

        // Ignore default home launcher so the user can always see their home screen
        if (isDefaultLauncher(pkg)) return true

        return false
    }

    private fun isDefaultLauncher(pkg: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, 0)
        resolveInfo?.activityInfo?.packageName == pkg
    }.getOrDefault(false)

    // ── Rate-limit overlay launch ─────────────────────────────────
    private fun shouldLaunchNow(pkg: String): Boolean {
        val now = System.currentTimeMillis()
        if (lastBlockedPackage == pkg && now - lastLaunchTimestamp < RELAUNCH_COOLDOWN_MS) return false
        lastBlockedPackage = pkg
        lastLaunchTimestamp = now
        return true
    }

    // ── Launch LockActivity overlay ───────────────────────────────
    private fun launchLockOverlay(packageName: String) {
        val appName = resolveAppName(packageName)
        startActivity(
            LockActivity.createIntent(
                context    = applicationContext,
                appName    = appName,
                appPackage = packageName,
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
        )
    }

    private fun resolveAppName(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrElse {
        pkg.substringAfterLast('.')
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }

    companion object {
        private const val TAG = "CodeUnlock.AppBlocker"
        private const val RELAUNCH_COOLDOWN_MS = 1_500L
        private const val MINDFUL_PAUSE_COOLDOWN_MS = 45_000L
    }
}
