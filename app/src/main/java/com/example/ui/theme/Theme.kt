package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = CosmicDarkBg,
    primaryContainer = CardSurface,
    onPrimaryContainer = NeonCyan,
    secondary = ElectricBlue,
    onSecondary = CosmicDarkBg,
    secondaryContainer = CardSurface,
    onSecondaryContainer = ElectricBlue,
    tertiary = NeonPurple,
    background = CosmicDarkBg,
    onBackground = TextPrimary,
    surface = DeepSpaceSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline = CardSurfaceBorder,
    error = CrimsonAlert
)

private val LightColorScheme = DarkColorScheme // Aiza defaults to sleek cosmic dark UI

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
