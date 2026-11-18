package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.core.ThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = AzureAccent,
    onPrimary = NavyDeep,
    primaryContainer = SkyContainerDark,
    onPrimaryContainer = SkySoft,
    secondary = RoyalBlue,
    onSecondary = Color.White,
    secondaryContainer = NavyCard,
    onSecondaryContainer = SkySoft,
    tertiary = EmeraldVerified,
    background = SlateBgDark,
    onBackground = TextPrimaryDark,
    surface = SlateSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SlateVariantDark,
    onSurfaceVariant = TextSecondaryDark,
    error = CrimsonError,
    onError = Color.White,
    outline = Color(0xFF283B5E)
)

private val LightColorScheme = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = SkySoft,
    onPrimaryContainer = NavyDeep,
    secondary = NavySurface,
    onSecondary = Color.White,
    secondaryContainer = SlateVariantLight,
    onSecondaryContainer = NavyDeep,
    tertiary = EmeraldVerified,
    background = SlateBgLight,
    onBackground = TextPrimaryLight,
    surface = SlateSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    error = CrimsonError,
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LukumanFreeChatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
