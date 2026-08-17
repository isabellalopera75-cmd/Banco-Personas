package com.tuapp.bancopersonas.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = WinPlayPink,
    secondary = WinPlayPurple,
    tertiary = WinPlayPurple,
    background = WinPlayDarkBg,
    surface = WinPlaySurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = WinPlayText,
    onSurface = WinPlayText,
    surfaceVariant = WinPlaySurface,
    onSurfaceVariant = WinPlayTextMuted
)

private val LightColorScheme = DarkColorScheme // Force dark mode for gaming theme

@Composable
fun RegistroccTheme(
    darkTheme: Boolean = true, // Force dark theme
    dynamicColor: Boolean = false, // Disable dynamic colors to keep brand identity
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}