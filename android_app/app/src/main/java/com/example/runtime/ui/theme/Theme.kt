package com.example.runtime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RunTimeColorScheme = lightColorScheme(
    primary = Green,
    onPrimary = White,
    primaryContainer = LightGreen,
    onPrimaryContainer = DarkGreen,
    secondary = Green,
    onSecondary = White,
    secondaryContainer = LightGreen,
    onSecondaryContainer = DarkGreen,
    tertiary = DarkGreen,
    onTertiary = White,
    tertiaryContainer = LightGreen,
    onTertiaryContainer = DarkGreen,
    background = White,
    onBackground = TextBlack,
    surface = White,
    onSurface = TextBlack,
    surfaceVariant = LightGrey,
    onSurfaceVariant = TextGrey,
    surfaceTint = White,
    surfaceBright = White,
    surfaceDim = LightGrey,
    surfaceContainerLowest = White,
    surfaceContainerLow = White,
    surfaceContainer = White,
    surfaceContainerHigh = OffWhite,
    surfaceContainerHighest = LightGrey,
    outline = BorderGrey,
    outlineVariant = BorderGrey,
)

@Composable
fun RunTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RunTimeColorScheme,
        typography = Typography,
        content = content
    )
}
