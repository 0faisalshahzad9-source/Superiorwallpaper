package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = SuperiorPurple,
    onPrimary = Color.White,
    primaryContainer = SuperiorPurpleLight.copy(alpha = 0.2f),
    onPrimaryContainer = SuperiorPurpleLight,
    secondary = SuperiorPink,
    onSecondary = Color.White,
    secondaryContainer = SuperiorPinkLight.copy(alpha = 0.2f),
    onSecondaryContainer = SuperiorPinkLight,
    tertiary = PremiumGold,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkCardBorder,
    surfaceTint = SuperiorPurple
  )

private val LightColorScheme =
  lightColorScheme(
    primary = SuperiorPurple,
    onPrimary = Color.White,
    secondary = SuperiorPink,
    onSecondary = Color.White,
    background = Color(0xFFF9F8FC),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1B1B22),
    onSurface = Color(0xFF1B1B22),
  )

@Composable
fun SuperiorWallpapersTheme(
  darkTheme: Boolean = true, // Dark theme by default for wallpaper gallery
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}

// Alias for backwards compatibility
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  SuperiorWallpapersTheme(darkTheme = darkTheme, content = content)
}
