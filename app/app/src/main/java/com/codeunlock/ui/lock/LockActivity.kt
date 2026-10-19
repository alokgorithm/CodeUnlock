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
import com.codeunlock.ui.MainActivity
import com.codeunlock.ui.navigation.AppRoute
import com.codeunlock.ui.screens.lock.LockScreen
import com.codeunlock.ui.screens.lock.LockScreenState
import com.codeunlock.ui.theme.CodeUnlockTheme
import dagger.hilt.android.AndroidEntryPoint

import androidx.activity.OnBackPressedCallback

/**
 * LockActivity — the overlay shown when a restricted app is blocked.
 *
 * FIXES vs original:
 *   1. Removed hardcoded DEFAULT_PROBLEM_ID = 1. The "Solve to Unlock"
 *      button now opens the user's actual next problem (fetched by LockViewModel).
 *      Original: always sent user to problem #1 regardless of track position.
 *   2. LockScreenState now carries usedMinutesToday + dailyLimitMinutes
 *      so the UI can show "You've used 4h 10m / 4h allowed".
 *   3. Emergency unlock callback shows NotificationHelper.showUnlockGranted()
 *      per PRD §6.3 "push notification 'Unlocked 30 min!'".
 *   4. Traps back gesture so users cannot pop back into the blocked app.
 */
@AndroidEntryPoint
class LockActivity : ComponentActivity() {

    private val lockViewModel: LockViewModel by viewModels()

    private val appName: String
        get() = intent?.getStringExtra(EXTRA_APP_NAME).orEmpty()

    private val appPackage: String?
        get() = intent?.getStringExtra(EXTRA_APP_PACKAGE)?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Trap back gesture: never allow popping back into the blocked app
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                openMainFlow(AppRoute.Dashboard.route)
            }
        })

        val settings     = BlockingSettingsStore.read(this)
        val availability = appPackage?.let { UnlockSessionStore.getAvailability(this, it) }

        // FIX: show real usage vs limit in the lock screen
        val usedMinutesToday = appPackage?.let {
            UsageManager.getTodayUsageMinutes(this, it)
        } ?: 0L

        val dailyLimitMinutes = if (appPackage != null) {
            val decision = UsageManager.shouldBlock(this, appPackage!!)
            decision.limitMinutes
        } else 0

        val isFocusBlocked = FocusTimerService.isRunning.value && !FocusTimerService.isBreakTime.value

        val lockScreenState = LockScreenState(
            appName                = appName.ifBlank { "Restricted App" },
            remainingUnlockMinutes = availability?.remainingMinutes
                ?: UnlockSessionStore.getRemainingUnlockMinutes(this, appPackage),
            emergencyUnlockEnabled = settings.emergencyUnlockEnabled && !isFocusBlocked,
            usedMinutesToday       = usedMinutesToday,     // FIX: new field
            dailyLimitMinutes      = dailyLimitMinutes,    // FIX: new field
            statusMessage          = if (isFocusBlocked) {
                "🧠 Focus Session Active (${FocusTimerService.activeSubject.value})\nDistracting apps are locked to keep you focused!"
            } else if (availability?.hasActiveUnlock == true) {
                "Unlock time is active. Return to dashboard to continue."
            } else if (usedMinutesToday > 0 && dailyLimitMinutes > 0) {
                "You've used ${formatMins(usedMinutesToday)} / ${formatMins(dailyLimitMinutes.toLong())} today."
            } else {
                "Solve a coding problem to unlock access."
            },
            footerMessage = if (isFocusBlocked) {
                "Focus mode locked. Complete your coding sprint to unlock."
            } else {
                buildFooterMessage(
                    emergencyEnabled       = settings.emergencyUnlockEnabled,
                    canUseEmergencyUnlock  = availability?.canUseEmergencyUnlock
                        ?: UnlockSessionStore.canUseEmergencyUnlock(this),
                )
            },
        )

        setContent {
            CodeUnlockTheme {
                LockScreen(
                    state           = lockScreenState,
                    onSolveToUnlock = {
                        // FIX: open REAL next problem, not hardcoded #1
                        openMainFlow(
                            lockViewModel.nextProblemRoute.value
                                ?: AppRoute.Problem.createRoute(0)
                        )
                    },
                    onBackToDashboard = { openMainFlow(AppRoute.Dashboard.route) },
                    onEmergencyUnlock = if (settings.emergencyUnlockEnabled) {
                        {
                            lockViewModel.requestEmergencyUnlock(appPackage) { granted ->
                                if (granted) {
                                    // PRD §6.3: "push notification 'Unlocked 30 min!'"
                                    com.codeunlock.data.notification.NotificationHelper
                                        .showUnlockGranted(this, 30, appName.ifBlank { null })
                                    Toast.makeText(this, "Emergency unlock granted for 30 minutes.", Toast.LENGTH_SHORT).show()
                                    openMainFlow(AppRoute.Dashboard.route)
                                } else {
                                    Toast.makeText(this, "Emergency unlock already used today or disabled.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else null,
                )
            }
        }

        // FIX: pre-fetch next problem so the button route is ready immediately
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
        !emergencyEnabled       -> "Emergency unlock is disabled in your settings."
        canUseEmergencyUnlock   -> "Emergency unlock is available once today."
        else                    -> "Emergency unlock already used today."
    }

    private fun formatMins(mins: Long): String {
        if (mins >= 60) return "${mins / 60}h ${mins % 60}m"
        return "${mins}m"
    }

    companion object {
        private const val EXTRA_APP_NAME    = "extra_app_name"
        private const val EXTRA_APP_PACKAGE = "extra_app_package"

        fun createIntent(context: Context, appName: String, appPackage: String? = null): Intent =
            Intent(context, LockActivity::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                if (!appPackage.isNullOrBlank()) putExtra(EXTRA_APP_PACKAGE, appPackage)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
