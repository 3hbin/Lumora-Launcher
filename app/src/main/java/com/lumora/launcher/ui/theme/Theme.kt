package com.lumora.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.lumora.launcher.data.ThemeMode

private val LumoraLightColors = lightColorScheme(
    primary = Color(0xFF5B5FEF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FF),
    onPrimaryContainer = Color(0xFF14145A),
    secondary = Color(0xFF7B61FF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFF9C6ADE),
    background = Color(0xFFF8F7FF),
    surface = Color(0xFFF8F7FF),
    surfaceVariant = Color(0xFFE4E1F0),
    onSurface = Color(0xFF1B1B23),
    onSurfaceVariant = Color(0xFF464554)
)

private val LumoraDarkColors = darkColorScheme(
    primary = Color(0xFFC0C1FF),
    onPrimary = Color(0xFF24247A),
    primaryContainer = Color(0xFF3C3CB0),
    onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFCFBCFF),
    onSecondary = Color(0xFF3A1F8E),
    secondaryContainer = Color(0xFF5238A6),
    tertiary = Color(0xFFE0B6FF),
    background = Color(0xFF121218),
    surface = Color(0xFF121218),
    surfaceVariant = Color(0xFF464554),
    onSurface = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFFC7C4D4)
)

@Composable
fun LumoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> LumoraDarkColors
        else -> LumoraLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LumoraTypography,
        content = content
    )
}
