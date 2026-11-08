package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val UtilitarianDarkColorScheme = darkColorScheme(
    primary = TechEmerald,
    onPrimary = Color(0xFF00210E),
    primaryContainer = TechEmeraldDim,
    onPrimaryContainer = TechEmerald,
    secondary = TechCyan,
    onSecondary = Color(0xFF001E2F),
    tertiary = TechAmber,
    onTertiary = Color(0xFF261900),
    tertiaryContainer = TechAmberDim,
    onTertiaryContainer = TechAmber,
    error = TechCrimson,
    onError = Color.White,
    errorContainer = TechCrimsonDim,
    onErrorContainer = Color(0xFFFFB4A1),
    background = TrueBlack,
    onBackground = TextPrimaryDark,
    surface = SurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    outline = OutlineStrongDark,
    outlineVariant = OutlineSubtleDark
)

private val UtilitarianLightColorScheme = lightColorScheme(
    primary = TechEmeraldLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F5E3),
    onPrimaryContainer = Color(0xFF003819),
    secondary = Color(0xFF006699),
    onSecondary = Color.White,
    tertiary = Color(0xFFB27800),
    onTertiary = Color.White,
    error = Color(0xFFD52B00),
    onError = Color.White,
    background = PaperWhite,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFFB0B0AA),
    outlineVariant = OutlineSubtleLight
)

// Industrial sharp/minimal radii for Swiss grid layout
val UtilitarianShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(10.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Defaults to OLED True Black Utilitarian theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) UtilitarianDarkColorScheme else UtilitarianLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = UtilitarianShapes,
        content = content
    )
}
