package com.example.runtime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

private val DarkColors = darkColorScheme(
    primary = Lime,
    onPrimary = Charcoal,
    primaryContainer = Lime,
    onPrimaryContainer = Charcoal,
    secondary = Lime,
    onSecondary = Charcoal,
    secondaryContainer = Lime,
    onSecondaryContainer = Charcoal,
    tertiary = Lime,
    onTertiary = Charcoal,
    background = Ink,
    onBackground = InkText,
    surface = InkSurface,
    onSurface = InkText,
    surfaceVariant = InkContainer,
    onSurfaceVariant = InkTextVariant,
    surfaceTint = InkSurface,
    surfaceBright = InkContainerHigh,
    surfaceDim = Ink,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = InkContainerLow,
    surfaceContainer = InkContainer,
    surfaceContainerHigh = InkContainerHigh,
    surfaceContainerHighest = InkContainerHighest,
    inverseSurface = PaperSurface,
    inverseOnSurface = PaperText,
    inversePrimary = Charcoal,
    outline = InkOutline,
    outlineVariant = InkOutlineVariant,
    error = ErrorDark,
    onError = Charcoal,
)

private val LightColors = lightColorScheme(
    primary = Charcoal,
    onPrimary = Lime,
    primaryContainer = Lime,
    onPrimaryContainer = Charcoal,
    secondary = Charcoal,
    onSecondary = Lime,
    secondaryContainer = Lime,
    onSecondaryContainer = Charcoal,
    tertiary = Charcoal,
    onTertiary = Lime,
    background = Paper,
    onBackground = PaperText,
    surface = PaperSurface,
    onSurface = PaperText,
    surfaceVariant = PaperContainer,
    onSurfaceVariant = PaperTextVariant,
    surfaceTint = PaperSurface,
    surfaceBright = PaperSurface,
    surfaceDim = PaperContainerHigh,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperContainerLow,
    surfaceContainer = PaperContainer,
    surfaceContainerHigh = PaperContainerHigh,
    surfaceContainerHighest = PaperContainerHighest,
    inverseSurface = Charcoal,
    inverseOnSurface = InkText,
    inversePrimary = Lime,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = ErrorLight,
    onError = PaperSurface,
)

val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun RunTimeTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = themeMode.isDark()
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
