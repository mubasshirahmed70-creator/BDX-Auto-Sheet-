package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HackerRedBlackColorScheme = darkColorScheme(
    primary = HackerNeonRed,
    onPrimary = Color.White,
    primaryContainer = HackerBloodDark,
    onPrimaryContainer = Color(0xFFF4F4F5),
    secondary = HackerCrimson,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E1E24),
    onSecondaryContainer = Color(0xFFE4E4E7),
    background = HackerObsidian,
    onBackground = Color(0xFFF4F4F5),
    surface = HackerCardSurface,
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = HackerCardVariant,
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF27272A),
    error = Color(0xFFEF4444),
    errorContainer = Color(0xFF2D1215),
    onErrorContainer = Color(0xFFFECDD3)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    // Sleek Matte Black theme with tasteful Red accents
    MaterialTheme(
        colorScheme = HackerRedBlackColorScheme,
        typography = Typography,
        content = content
    )
}
