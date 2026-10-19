package com.codeunlock.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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
    primary = Color(0xFF4456D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF101A4F),

    secondary = Color(0xFF006B5B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB7F6E9),
    onSecondaryContainer = Color(0xFF00201A),

    tertiary = Color(0xFF9A5B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEBB),
    onTertiaryContainer = Color(0xFF311A00),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF161B2B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF161B2B),
    surfaceVariant = Color(0xFFE1E6F3),
    onSurfaceVariant = Color(0xFF444C63),

    outline = Color(0xFF747D96),
    outlineVariant = Color(0xFFC4CADB),
    scrim = Color(0x99000000),

    inverseSurface = Color(0xFF2A3042),
    inverseOnSurface = Color(0xFFF1F3FA),
    inversePrimary = Color(0xFFBBC3FF)
)

@Composable
fun CodeUnlockTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
