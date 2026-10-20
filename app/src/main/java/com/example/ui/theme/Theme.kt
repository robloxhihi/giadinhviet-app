package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Gia Đình Việt — premium shapes.
 * Generous radii give the frosted-glass cards their modern, award-show feel.
 */
val PremiumShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private fun PremiumPalette.toColorScheme() = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = TextOnAccent,
        primaryContainer = DarkPrimaryContainer,
        onPrimaryContainer = DarkOnPrimaryContainer,
        secondary = cyan,
        onSecondary = Color(0xFF04202B),
        secondaryContainer = Color(0x332E7DF7),
        onSecondaryContainer = textPrimary,
        tertiary = violet,
        onTertiary = TextOnAccent,
        background = DarkBackground,
        onBackground = textPrimary,
        surface = opaqueSurface,
        onSurface = textPrimary,
        surfaceVariant = DarkCardElevated,
        onSurfaceVariant = textSecondary,
        surfaceContainer = DarkSurface,
        surfaceContainerHigh = DarkCardSurface,
        // Solid champagne rim for Material3 controls (Switch/OutlinedButton/
        // TextField/BorderStroke); glassBorder stays translucent for glass panels.
        outline = ChampagneGold.copy(alpha = 0.62f),
        outlineVariant = ChampagneGold.copy(alpha = 0.25f),
        error = rose,
        onError = TextOnAccent,
        scrim = Color(0xCC000000)
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = TextOnAccent,
        primaryContainer = Color(0xFFDCE9FF),
        onPrimaryContainer = Color(0xFF123A7A),
        secondary = cyan,
        onSecondary = TextOnAccent,
        secondaryContainer = Color(0x332E7DF7),
        onSecondaryContainer = textPrimary,
        tertiary = violet,
        onTertiary = TextOnAccent,
        background = opaqueSurface,
        onBackground = textPrimary,
        surface = opaqueSurface,
        onSurface = textPrimary,
        surfaceVariant = Color(0xFFEAF4FF),
        onSurfaceVariant = textSecondary,
        surfaceContainer = Color(0xFFF1F8FF),
        surfaceContainerHigh = Color(0xFFE6F1FF),
        outline = glassBorder,
        outlineVariant = Color(0x66BEDCFA),
        error = rose,
        onError = TextOnAccent,
        scrim = Color(0x66000000)
    )
}

/** Re-tints the active skin so the "Giao diện" picker keeps working. */
private fun PremiumPalette.tinted(accent: Color, accentBright: Color, secondary: Color): PremiumPalette =
    copy(
        primary = accent,
        primaryBright = accentBright,
        cyan = secondary,
        primaryGradient = listOf(accentBright, accent),
        accentGradient = listOf(secondary, accentBright)
    )

fun premiumPaletteFor(darkTheme: Boolean, colorTheme: String): PremiumPalette {
    val base = if (darkTheme) SpaceNightPalette else DreamyNaturePalette
    return when (colorTheme) {
        "emerald" -> base.tinted(Color(0xFF12A150), Color(0xFF3BD27C), Color(0xFF3BD27C))
        "sunset" -> base.tinted(Color(0xFFE8734A), Color(0xFFFF9E6B), Color(0xFFFBBF24))
        "purple" -> base.tinted(Color(0xFF7C5CFF), Color(0xFFA88BFF), Color(0xFF4CC9F0))
        else -> base
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colorTheme: String = "blue",
    content: @Composable () -> Unit
) {
    val palette = remember(darkTheme, colorTheme) { premiumPaletteFor(darkTheme, colorTheme) }
    val colorScheme = remember(palette) { palette.toColorScheme() }

    CompositionLocalProvider(LocalPremiumPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VesperaTypography,
            shapes = PremiumShapes,
            content = content
        )
    }
}
