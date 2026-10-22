package com.codeunlock.ui.lock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.data.usage.UsageManager
import com.codeunlock.service.FocusTimerService
import com.codeunlock.service.overlay.FloatingFocusTimerOverlay
import com.codeunlock.ui.MainActivity
import com.codeunlock.ui.navigation.AppRoute
import com.codeunlock.ui.screens.lock.LockScreen
import com.codeunlock.ui.screens.lock.LockScreenState
import com.codeunlock.ui.theme.CodeUnlockTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.activity.OnBackPressedCallback

/**
 * LockActivity — the overlay shown when a restricted app is blocked.
 *
 * Features:
 *   1. Displays Kodi the Coding Fox with contextual speech and mood:
 *      - Focus Mode: Focus mood (cyan glowing headphones), "Stay in Focus!", and sprint timer.
 *      - Limit Exceeded: Happy mood, "Time's up for [App]! Daily limit reached."
 *      - Distraction Shield: Encouraging quotes to solve problems and earn screen time.
 *   2. Temporarily suppresses FloatingFocusTimerOverlay so it doesn't overlap the lock screen.
 *   3. Live countdown updates during active focus sessions.
 *   4. Direct one-tap navigation back to the Focus Session.
 *   5. Traps back gesture so users cannot pop back into the blocked app.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    private val lockViewModel: LockViewModel by viewModels()

    private val appName: String
        get() = intent?.getStringExtra(EXTRA_APP_NAME).orEmpty()

    private val appPackage: String?
        get() = intent?.getStringExtra(EXTRA_APP_PACKAGE)?.takeIf { it.isNotBlank() }

    override fun onStart() {
        super.onStart()
        // Suppress the floating pill so it doesn't collide with the full-screen lock overlay
        FloatingFocusTimerOverlay.setSuppressed(true)
    }

    override fun onStop() {
        super.onStop()
        // Restore the floating timer pill when the user leaves the lock screen
        FloatingFocusTimerOverlay.setSuppressed(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Trap back gesture: never allow popping back into the blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openMainFlow(AppRoute.Dashboard.route)
            }
        })

        val settings = BlockingSettingsStore.read(this)
        val availability = appPackage?.let { UnlockSessionStore.getAvailability(this, it) }

        val usedMinutesToday = appPackage?.let {
            UsageManager.getTodayUsageMinutes(this, it)
        } ?: 0L

        val decision = if (appPackage != null) {
            UsageManager.shouldBlock(this, appPackage!!)
        } else null

        val dailyLimitMinutes = decision?.limitMinutes ?: 0

        setContent {
            val themeMode by BlockingSettingsStore.themeModeFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                BlockingSettingsStore.THEME_LIGHT -> false
                BlockingSettingsStore.THEME_DARK -> true
                else -> systemDark
            }

            // Live observing FocusTimerService state
            val remainingSec by FocusTimerService.remainingSeconds.collectAsState()
            val focusTimeFormatted = remember(remainingSec) { FocusTimerService.formatTime(remainingSec) }
            val focusSubject by FocusTimerService.activeSubject.collectAsState()
            val isFocusRunning by FocusTimerService.isRunning.collectAsState()
            val isBreakTime by FocusTimerService.isBreakTime.collectAsState()

            val isFocusBlocked = isFocusRunning && !isBreakTime
            val isLimitExceeded = (dailyLimitMinutes > 0 && usedMinutesToday >= dailyLimitMinutes) ||
                decision?.reason == UsageManager.BlockDecision.Reason.DAILY_LIMIT_EXCEEDED

            val resolvedName = appName.ifBlank { "Restricted App" }

            val lockScreenState = LockScreenState(
                appName = resolvedName,
                remainingUnlockMinutes = availability?.remainingMinutes
                    ?: UnlockSessionStore.getRemainingUnlockMinutes(this, appPackage),
                emergencyUnlockEnabled = settings.emergencyUnlockEnabled && !isFocusBlocked,
                usedMinutesToday = usedMinutesToday,
                dailyLimitMinutes = dailyLimitMinutes,
                statusMessage = if (isFocusBlocked) {
                    "Focus session active on ${focusSubject.ifBlank { "Coding Sprint" }}.\nDistracting apps are locked to protect your flow!"
                } else if (availability?.hasActiveUnlock == true) {
                    "Unlock time is active. Return to dashboard to continue."
                } else if (isLimitExceeded) {
                    "Daily limit reached: ${formatMins(usedMinutesToday)} spent of ${formatMins(dailyLimitMinutes.toLong())} allowed today."
                } else {
                    "Solve a coding problem to unlock access."
                },
                footerMessage = if (isFocusBlocked) {
                    "Focus mode locked. Complete your coding sprint to unlock."
                } else {
                    buildFooterMessage(
                        emergencyEnabled = settings.emergencyUnlockEnabled,
                        canUseEmergencyUnlock = availability?.canUseEmergencyUnlock
                            ?: UnlockSessionStore.canUseEmergencyUnlock(this)
                    )
                },
                isFocusBlocked = isFocusBlocked,
                focusSubject = focusSubject,
                focusTimeRemainingFormatted = focusTimeFormatted,
                isLimitExceeded = isLimitExceeded
            )

            CodeUnlockTheme(darkTheme = isDark) {
                LockScreen(
                    state = lockScreenState,
                    onSolveToUnlock = {
                        openMainFlow(
                            lockViewModel.nextProblemRoute.value
                                ?: AppRoute.Problem.createRoute(0)
                        )
                    },
                    onBackToDashboard = { openMainFlow(AppRoute.Dashboard.route) },
                    onReturnToFocusSession = { openMainFlow(AppRoute.Focus.route) },
                    onEmergencyUnlock = if (settings.emergencyUnlockEnabled && !isFocusBlocked) {
                        {
                            lockViewModel.requestEmergencyUnlock(appPackage) { granted ->
                                if (granted) {
                                    com.codeunlock.data.notification.NotificationHelper
                                        .showUnlockGranted(this, 30, appName.ifBlank { null })
                                    Toast.makeText(this, "Emergency unlock granted for 30 minutes.", Toast.LENGTH_SHORT).show()
                                    openMainFlow(AppRoute.Dashboard.route)
                                } else {
                                    Toast.makeText(this, "Emergency unlock already used today or disabled.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else null
                )
            }
        }

        // Pre-fetch next problem so the button route is ready immediately
        lockViewModel.loadNextProblem()
    }

    private fun openMainFlow(route: String) {
        startActivity(
            MainActivity.createIntent(context = this, initialRoute = route).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

    private fun buildFooterMessage(emergencyEnabled: Boolean, canUseEmergencyUnlock: Boolean): String = when {
        !emergencyEnabled -> "Emergency unlock is disabled in your settings."
        canUseEmergencyUnlock -> "Emergency unlock is available once today."
        else -> "Emergency unlock already used today."
    }

    private fun formatMins(mins: Long): String {
        if (mins >= 60) return "${mins / 60}h ${mins % 60}m"
        return "${mins}m"
    }

    companion object {
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_APP_PACKAGE = "extra_app_package"

        fun createIntent(context: Context, appName: String, appPackage: String? = null): Intent =
            Intent(context, LockActivity::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                if (!appPackage.isNullOrBlank()) putExtra(EXTRA_APP_PACKAGE, appPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
