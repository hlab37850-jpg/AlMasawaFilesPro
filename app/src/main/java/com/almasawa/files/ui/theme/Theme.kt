package com.almasawa.files.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.almasawa.files.data.settings.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E6B6A),
    secondary = Color(0xFFB8862F),
    tertiary = Color(0xFF3A6EA5),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD1CF),
    secondary = Color(0xFFE6B965),
    tertiary = Color(0xFF8DB7E8),
)

@Composable
fun AlMasawaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
