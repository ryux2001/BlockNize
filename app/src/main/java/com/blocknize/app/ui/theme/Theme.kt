package com.blocknize.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp

@Composable
fun BlockNizeTheme(accentColor: Int, content: @Composable () -> Unit) {
    val chosenAccent = Color(accentColor)
    val accent = if (chosenAccent.luminance() < 0.18f) lerp(chosenAccent, Color.White, 0.72f) else chosenAccent
    val onAccent = if (accent.luminance() > 0.179f) Color.Black else Color.White
    val accentContainer = lerp(Color(0xFF101010), accent, 0.18f)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = accent,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = accent,
            tertiary = accent,
            onTertiary = onAccent,
            tertiaryContainer = accentContainer,
            onTertiaryContainer = accent,
            background = Color.Black,
            onBackground = Color.White,
            surface = Color(0xFF101010),
            onSurface = Color.White,
            surfaceVariant = Color(0xFF191919),
            onSurfaceVariant = Color(0xFFB8B8B8),
            outline = Color(0xFF747474),
            outlineVariant = Color(0xFF303030),
            surfaceTint = Color.Transparent,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF101010),
            surfaceContainer = Color(0xFF151515),
            surfaceContainerHigh = Color(0xFF1C1C1C),
            surfaceContainerHighest = Color(0xFF252525)
        ),
        content = content
    )
}
