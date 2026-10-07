package dev.keeps.library.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val light = lightColorScheme(
    primary = Color(0xFF284C3E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EACD), onPrimaryContainer = Color(0xFF20362B),
    secondary = Color(0xFF666B51), secondaryContainer = Color(0xFFE9EBD9),
    background = Color(0xFFF7F8F2), surface = Color(0xFFF7F8F2),
    surfaceContainer = Color(0xFFEEF1E8), surfaceContainerHigh = Color(0xFFE6EADD),
    outlineVariant = Color(0xFFD8DED1),
)
private val dark = darkColorScheme(
    primary = Color(0xFFB7D4A9), onPrimary = Color(0xFF183325),
    primaryContainer = Color(0xFF314D3D), onPrimaryContainer = Color(0xFFE0ECCD),
    secondary = Color(0xFFC7CAB1), background = Color(0xFF111B15), surface = Color(0xFF111B15),
    surfaceContainer = Color(0xFF1D2820), surfaceContainerHigh = Color(0xFF29352B),
)

@Composable fun KeepsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, content = content)
}
