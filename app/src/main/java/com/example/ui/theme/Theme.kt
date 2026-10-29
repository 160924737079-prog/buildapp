package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.domain.ThemePreference

private val LightColors = lightColorScheme(
    primary = Emerald600,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Navy900,
    secondary = Navy800,
    onSecondary = Color.White,
    secondaryContainer = Slate100,
    onSecondaryContainer = Navy900,
    tertiary = WarningAmber600,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmber100,
    onTertiaryContainer = Navy900,
    error = AlertRed600,
    onError = Color.White,
    errorContainer = AlertRed100,
    onErrorContainer = AlertRed600,
    background = Slate50,
    onBackground = Navy900,
    surface = Color.White,
    onSurface = Navy900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate600,
    outline = Slate200
)

private val DarkColors = darkColorScheme(
    primary = Emerald500,
    onPrimary = Navy950,
    primaryContainer = Emerald700,
    onPrimaryContainer = Emerald100,
    secondary = Slate200,
    onSecondary = Navy900,
    secondaryContainer = Navy800,
    onSecondaryContainer = Slate100,
    tertiary = WarningAmber500,
    onTertiary = Navy950,
    tertiaryContainer = Color(0xFF451A03),
    onTertiaryContainer = WarningAmber100,
    error = AlertRed500,
    onError = Color.White,
    errorContainer = Color(0xFF450A0A),
    onErrorContainer = AlertRed100,
    background = Navy950,
    onBackground = Slate50,
    surface = Navy900,
    onSurface = Slate50,
    surfaceVariant = Navy800,
    onSurfaceVariant = Slate400,
    outline = Navy700
)

@Composable
fun ExpenseProTheme(
    themePreference: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
