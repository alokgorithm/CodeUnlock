package com.codeunlock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.codeunlock.service.FocusTimerService
import com.codeunlock.ui.navigation.AppRoute
import com.codeunlock.ui.navigation.CodeUnlockNavHost
import com.codeunlock.ui.theme.CodeUnlockTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val initialRoute: String?
        get() = intent?.getStringExtra(EXTRA_INITIAL_ROUTE)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val routeFromIntent = initialRoute
        if (!routeFromIntent.isNullOrBlank()) {
            pendingRoute.value = routeFromIntent
        } else if (FocusTimerService.isRunning.value) {
            pendingRoute.value = AppRoute.Focus.route
        }

        try {
            setContent {
                CodeUnlockTheme {
                    CodeUnlockNavHost(
                        initialRoute = initialRoute
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("CodeUnlock", "Fatal crash in setContent", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val route = intent.getStringExtra(EXTRA_INITIAL_ROUTE)
        if (!route.isNullOrBlank()) {
            pendingRoute.value = route
        } else if (FocusTimerService.isRunning.value) {
            pendingRoute.value = AppRoute.Focus.route
        }
    }

    override fun onResume() {
        super.onResume()
        if (FocusTimerService.isRunning.value && pendingRoute.value == null) {
            pendingRoute.value = AppRoute.Focus.route
        }
    }

    companion object {
        const val EXTRA_INITIAL_ROUTE = "extra_initial_route"
        val pendingRoute = MutableStateFlow<String?>(null)

        fun createIntent(
            context: Context,
            initialRoute: String? = null
        ): Intent {
            return Intent(context, MainActivity::class.java).apply {
                if (!initialRoute.isNullOrBlank()) {
                    putExtra(EXTRA_INITIAL_ROUTE, initialRoute)
                }
            }
        }
    }
}
