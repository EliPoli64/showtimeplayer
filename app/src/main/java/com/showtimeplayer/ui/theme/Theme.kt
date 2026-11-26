package com.showtimeplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

// Dark grey base, greyish-blue primary, light-grey accents.
private val GreyBluePrimary = Color(0xFF6E8CA3)
private val GreyBlueOnPrimary = Color(0xFF0B1319)
private val GreyBluePrimaryContainer = Color(0xFF2B3E4C)
private val GreyBlueOnPrimaryContainer = Color(0xFFC7DCEA)

private val LightGreySecondary = Color(0xFF9AA5AF)
private val LightGreyOnSecondary = Color(0xFF1B2026)
private val LightGreySecondaryContainer = Color(0xFF2F343B)
private val LightGreyOnSecondaryContainer = Color(0xFFC8CDD3)

private val GreyBlueTertiary = Color(0xFF8FA7BC)
private val GreyBlueOnTertiary = Color(0xFF0D151C)
private val GreyBlueTertiaryContainer = Color(0xFF263A48)
private val GreyBlueOnTertiaryContainer = Color(0xFFC9DEEB)

private val DarkColors = darkColorScheme(
    primary = GreyBluePrimary,
    onPrimary = GreyBlueOnPrimary,
    primaryContainer = GreyBluePrimaryContainer,
    onPrimaryContainer = GreyBlueOnPrimaryContainer,
    inversePrimary = Color(0xFF3A5060),
    secondary = LightGreySecondary,
    onSecondary = LightGreyOnSecondary,
    secondaryContainer = LightGreySecondaryContainer,
    onSecondaryContainer = LightGreyOnSecondaryContainer,
    tertiary = GreyBlueTertiary,
    onTertiary = GreyBlueOnTertiary,
    tertiaryContainer = GreyBlueTertiaryContainer,
    onTertiaryContainer = GreyBlueOnTertiaryContainer,
    background = Color(0xFF16181C),
    onBackground = Color(0xFFE2E5E9),
    surface = Color(0xFF1C1F24),
    onSurface = Color(0xFFE2E5E9),
    surfaceVariant = Color(0xFF2B3038),
    onSurfaceVariant = Color(0xFFB1B9C2),
    surfaceTint = GreyBluePrimary,
    inverseSurface = Color(0xFFE2E5E9),
    inverseOnSurface = Color(0xFF1B2026),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF4A0A0A),
    errorContainer = Color(0xFF4A2020),
    onErrorContainer = Color(0xFFFFB4B4),
    outline = Color(0xFF3C434B),
    outlineVariant = Color(0xFF2C3138),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF3B414B),
    surfaceDim = Color(0xFF121316),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF252A31),
    surfaceContainerHighest = Color(0xFF2A2F37),
    surfaceContainerLow = Color(0xFF191C21),
    surfaceContainerLowest = Color(0xFF0F1114),
)

// Light counterpart keeps the same greyish-blue / light-grey accent language on
// light-grey surfaces, so the theme reads consistently in both modes.
private val LightColors = lightColorScheme(
    primary = Color(0xFF4A6E8C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE1F0),
    onPrimaryContainer = Color(0xFF122333),
    inversePrimary = Color(0xFF8FB4CE),
    secondary = Color(0xFF54626D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E3EA),
    onSecondaryContainer = Color(0xFF17232D),
    tertiary = Color(0xFF4A6A80),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE5F5),
    onTertiaryContainer = Color(0xFF122430),
    background = Color(0xFFF5F6F8),
    onBackground = Color(0xFF1A1E23),
    surface = Color(0xFFF5F6F8),
    onSurface = Color(0xFF1A1E23),
    surfaceVariant = Color(0xFFE0E4E8),
    onSurfaceVariant = Color(0xFF464E56),
    surfaceTint = Color(0xFF4A6E8C),
    inverseSurface = Color(0xFF2E3339),
    inverseOnSurface = Color(0xFFEDEFF1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF737B83),
    outlineVariant = Color(0xFFC3C9CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFF5F6F8),
    surfaceDim = Color(0xFFD6D9DD),
    surfaceContainer = Color(0xFFEAECEF),
    surfaceContainerHigh = Color(0xFFE4E6EA),
    surfaceContainerHighest = Color(0xFFDEE0E4),
    surfaceContainerLow = Color(0xFFF0F1F3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
)

@Composable
fun ShowtimePlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
