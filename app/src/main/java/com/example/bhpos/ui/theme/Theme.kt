package com.example.bhpos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SafetyAmberDark,
    onPrimary = SafetyAmberOn,
    primaryContainer = SafetyAmber,
    onPrimaryContainer = SafetyAmberDark,
    secondary = HeavySlate,
    onSecondary = HeavySlateOn,
    secondaryContainer = HeavySlateContainer,
    onSecondaryContainer = HeavySlateLight,
    tertiary = EmeraldInward,
    onTertiary = EmeraldInwardOn,
    tertiaryContainer = EmeraldInwardContainer,
    error = CrimsonOutward,
    onError = CrimsonOutwardOn,
    background = ConcreteCanvas,
    onBackground = CharcoalTextPrimary,
    surface = ConcreteCanvas,
    onSurface = CharcoalTextPrimary,
    surfaceVariant = ConcreteSurfaceContainerHighest,
    onSurfaceVariant = SlateTextSecondary,
    outline = ConcreteBorder,
    inverseSurface = InverseSurface,
    inverseOnSurface = InverseOnSurface
)

private val DarkColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = SafetyAmberOn,
    primaryContainer = SafetyAmberDark,
    onPrimaryContainer = SafetyAmberContainer,
    secondary = HeavySlateContainer,
    onSecondary = HeavySlate,
    tertiary = EmeraldInwardFixed,
    onTertiary = EmeraldInwardOnFixed,
    error = CrimsonOutward,
    background = InverseSurface,
    onBackground = InverseOnSurface,
    surface = InverseSurface,
    onSurface = InverseOnSurface
)

@Composable
fun BHPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
