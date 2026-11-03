package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    remember {
        AppThemeManager.init(context)
        true
    }
    val activePreset by AppThemeManager.currentTheme.collectAsState()

    val dynamicScheme = remember(activePreset) {
        darkColorScheme(
            primary = activePreset.accentBrightCompose,
            onPrimary = Color.White,
            primaryContainer = activePreset.accentSoftBgCompose,
            onPrimaryContainer = Color(0xFFF4F4F5),
            secondary = activePreset.accentCompose,
            onSecondary = Color.White,
            secondaryContainer = activePreset.elevatedCompose,
            onSecondaryContainer = Color(0xFFE4E4E7),
            background = activePreset.bgCompose,
            onBackground = Color(0xFFF4F4F5),
            surface = activePreset.cardCompose,
            onSurface = Color(0xFFF4F4F5),
            surfaceVariant = activePreset.elevatedCompose,
            onSurfaceVariant = activePreset.textSecondaryCompose,
            outline = activePreset.cardBorderCompose,
            error = Color(0xFFEF4444),
            errorContainer = Color(0xFF2D1215),
            onErrorContainer = Color(0xFFFECDD3)
        )
    }

    MaterialTheme(
        colorScheme = dynamicScheme,
        typography = Typography,
        content = content
    )
}
