package com.exodi.aycut.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46E5),
    onPrimary = Color.White,
    background = Color(0xFFF8F8FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF17171C),
    onSurface = Color(0xFF17171C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C74FF),
    onPrimary = Color(0xFF17171C),
    background = Color(0xFF121216),
    surface = Color(0xFF1B1B21),
    onBackground = Color(0xFFECECF2),
    onSurface = Color(0xFFECECF2),
)

@Composable
fun AycutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}