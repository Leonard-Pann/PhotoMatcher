package com.bobby.vaudoushorter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Colors = darkColorScheme(
    primary = Mint, onPrimary = Ink, secondary = Mint, tertiary = Coral,
    background = Ink, surface = Panel, surfaceVariant = Color(0xFF272B36),
    onSurfaceVariant = Muted, onBackground = Color(0xFFF6F5FA),
    onSurface = Color(0xFFF6F5FA), error = Coral, outline = Color(0xFF393D49)
)

@Composable
fun VaudouShorterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Typography, content = content)
}
