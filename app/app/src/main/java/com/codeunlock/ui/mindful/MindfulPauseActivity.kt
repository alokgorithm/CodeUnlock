package com.codeunlock.ui.mindful

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.codeunlock.data.settings.BlockingSettingsStore
import com.codeunlock.data.settings.ReelsBlockingStore
import com.codeunlock.data.unlock.UnlockSessionStore
import com.codeunlock.service.ConsumedTimeStore
import com.codeunlock.service.DailyProgressSyncWorker
import com.codeunlock.ui.MainActivity
import com.codeunlock.ui.navigation.AppRoute
import com.codeunlock.ui.screens.mindful.MindfulPauseScreen
import com.codeunlock.ui.theme.CodeUnlockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MindfulPauseActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appName = intent.getStringExtra(EXTRA_APP_NAME).orEmpty().ifBlank { "Restricted App" }
        val appPackage = intent.getStringExtra(EXTRA_APP_PACKAGE).orEmpty()
        val pauseSeconds = ReelsBlockingStore.read(this).mindfulPauseSeconds

        setContent {
            val themeMode by BlockingSettingsStore.themeModeFlow.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val isDark = when (themeMode) {
                BlockingSettingsStore.THEME_LIGHT -> false
                BlockingSettingsStore.THEME_DARK -> true
                else -> systemDark
            }

            CodeUnlockTheme(darkTheme = isDark) {
                MindfulPauseScreen(
                    appName = appName,
                    pauseDurationSeconds = pauseSeconds,
                    onGrantMinutes = { minutes ->
                        // Grant intentional micro session
                        UnlockSessionStore.grantCodingUnlock(
                            context = applicationContext,
                            appPackage = appPackage.ifBlank { null },
                            durationMinutes = minutes
                        )
                        ConsumedTimeStore.addPending(applicationContext, minutes)
                        DailyProgressSyncWorker.flushNow(applicationContext)

                        Toast.makeText(this, "Unlocked for $minutes minutes ⏱️", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onSolveProblem = {
                        val mainIntent = MainActivity.createIntent(
                            context = this,
                            initialRoute = AppRoute.Tracks.route
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        startActivity(mainIntent)
                        finish()
                    },
                    onDismiss = {
                        // Go back to home launcher screen
                        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                            addCategory(Intent.CATEGORY_HOME)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(homeIntent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_APP_PACKAGE = "extra_app_package"

        fun createIntent(context: Context, appName: String, appPackage: String): Intent {
            return Intent(context, MindfulPauseActivity::class.java).apply {
                putExtra(EXTRA_APP_NAME, appName)
                putExtra(EXTRA_APP_PACKAGE, appPackage)
            }
        }
    }
}
