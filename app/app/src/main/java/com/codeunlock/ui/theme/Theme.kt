package com.codeunlock.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C8CFF),
    onPrimary = Color(0xFF0B1020),
    primaryContainer = Color(0xFF1A2245),
    onPrimaryContainer = Color(0xFFDDE1FF),

    secondary = Color(0xFF00D1B2),
    onSecondary = Color(0xFF06201B),
    secondaryContainer = Color(0xFF10342D),
    onSecondaryContainer = Color(0xFFB7F6E9),

    tertiary = Color(0xFFFFB86B),
    onTertiary = Color(0xFF2A1700),
    tertiaryContainer = Color(0xFF4A2D00),
    onTertiaryContainer = Color(0xFFFFDEBB),

    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5F1D1D),
    onErrorContainer = Color(0xFFFFDAD6),

    background = Color(0xFF0B1020),
    onBackground = Color(0xFFE9ECF8),
    surface = Color(0xFF12182B),
    onSurface = Color(0xFFE9ECF8),
    surfaceVariant = Color(0xFF20283D),
    onSurfaceVariant = Color(0xFFBDC6E0),

    outline = Color(0xFF4F5A7A),
    outlineVariant = Color(0xFF313A56),
    scrim = Color(0xCC000000),

    inverseSurface = Color(0xFFE9ECF8),
    inverseOnSurface = Color(0xFF12182B),
    inversePrimary = Color(0xFF4456D9)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4361EE),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF2FF),
    onPrimaryContainer = Color(0xFF1E3A8A),

    secondary = Color(0xFF10B981),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6F4EA),
    onSecondaryContainer = Color(0xFF065F46),

    tertiary = Color(0xFFF59E0B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF78350F),

    error = Color(0xFFEF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF991B1B),

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),

    outline = Color(0xFF94A3B8),
    outlineVariant = Color(0xFFE2E8F0),
    scrim = Color(0x99000000),

    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF8FAFC),
    inversePrimary = Color(0xFF93C5FD)
)

@Composable
fun CodeUnlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
