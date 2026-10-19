package com.codeunlock.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.codeunlock.ui.navigation.CodeUnlockNavHost
import com.codeunlock.ui.theme.CodeUnlockTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val initialRoute: String?
        get() = intent?.getStringExtra(EXTRA_INITIAL_ROUTE)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

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

    companion object {
        private const val EXTRA_INITIAL_ROUTE = "extra_initial_route"

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
