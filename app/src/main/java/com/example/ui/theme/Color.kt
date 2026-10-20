package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════════════════════
//  GIA ĐÌNH VIỆT — PREMIUM COLOUR TOKENS
//
//  Static brand accents live here as plain values (safe to use inside
//  Canvas / DrawScope lambdas). Anything that must adapt to the active skin
//  is exposed as a @Composable accessor that reads LocalPremiumPalette.
// ═══════════════════════════════════════════════════════════════════════════

/** Brand blue — the app's signature colour in both skins. */
val FacebookBlue = Color(0xFF2E7DF7)
val OceanBlue = Color(0xFF2563EB)
val OceanDeepBlue = Color(0xFF1D4ED8)
val OceanAzure = Color(0xFF38BDF8)

/** Accents tuned to stay legible on the deep-space AND the dreamy-nature skin.
 *  "Gemstone" tier: saturated neon has been muted to deep-jewel tones that
 *  remain distinguishable from each other on the near-black backdrop. */
val NeonViolet = Color(0xFF7C6FC0)
val NeonCyan = Color(0xFF3E93B4)
val NeonEmerald = Color(0xFF2E9268)
val NeonAmber = Color(0xFFC79346)
val NeonRose = Color(0xFFC26178)

/** Champagne-gold — the "million-dollar" premium accent. Brand blue stays the
 *  primary action colour; gold is reserved for high-end highlights and rims. */
val ChampagneGold = Color(0xFFC8A96A)
val ChampagneGoldBright = Color(0xFFD4B478)

// Alternate palettes (kept for backwards compatibility with older screens)
val EmeraldPrimary = Color(0xFF1FB25A)
val SunsetPrimary = Color(0xFFE8734A)
val PurplePrimary = Color(0xFF7C5CFF)

// Emotion & status accents
val EmotionHappy = NeonEmerald
val EmotionNormal = FacebookBlue
val EmotionStudyStress = NeonCyan
val EmotionNeedSupport = NeonRose

val TextOnAccent = Color(0xFFFFFFFF)

// ── Legacy flat tokens (still referenced by dialogs/activities) ────────────
val LightBackground = Color(0xFFE9F4FF)
val LightSurface = Color(0xFFF7FBFF)
val LightCardSurface = Color(0xFFFFFFFF)
val LightCardElevated = Color(0xFFE3EFFF)
val LightCardBorder = Color(0xFFBEDCFA)
val LightTextPrimary = Color(0xFF0C2A4A)
val LightTextSecondary = Color(0xFF46658A)
val LightTextMuted = Color(0xFF7B93B0)

// DEN that, khong phai navy. Nguoi dung yeu cau app "thien ve den" nen nen
// tang 1 (DarkBackground) la mau den tuyet doi va cac tang kia chi cach
// nhau vai do L* — du de phan biet the voi nen, du it de giao dien van la
// "den" khi anh nen hoa lan chay qua ben duoi.
val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF04050A)
val DarkCardSurface = Color(0xFF0A0E16)
val DarkCardElevated = Color(0xFF12161F)
val DarkCardBorder = Color(0x40C8A96A)
// Contrast on DarkCardSurface: TextPrimary 15.6:1, TextSecondary 9.9:1,
// TextMuted 6.5:1 (all pass AA; muted passes even on the dialog surface).
val DarkTextPrimary = Color(0xFFF5F7FB)
val DarkTextSecondary = Color(0xFFB4BECE)
val DarkTextMuted = Color(0xFF8F99AB)

// Containers for the dark ColorScheme (referenced from Theme.kt, no literals there)
val DarkPrimaryContainer = Color(0xFF152347)
val DarkOnPrimaryContainer = Color(0xFFD9E5FF)

// ═══════════════════════════════════════════════════════════════════════════
//  Composition-aware accessors
// ═══════════════════════════════════════════════════════════════════════════

/** The active premium skin. */
val Premium: PremiumPalette
    @Composable
    @ReadOnlyComposable
    get() = LocalPremiumPalette.current

val TextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.textPrimary

val TextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.textSecondary

val TextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.textMuted

val SpaceCardSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.glassFill

val SpaceCardElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = if (Premium.isDark) Color(0xFF12161F) else Color(0xF7FFFFFF)

val SpaceCardBorder: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.glassBorder

/** Opaque surface for dialogs, sheets and anything that must not be see-through. */
val DialogSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.opaqueSurface

/** Screen background. Screens sit on [PremiumBackground], so this is transparent. */
val SpaceDarkBackground: Color
    @Composable
    @ReadOnlyComposable
    get() = Color.Transparent

val SpaceDarkSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = Premium.opaqueSurface

val PrimaryGradient: List<Color>
    @Composable
    @ReadOnlyComposable
    get() = Premium.primaryGradient

val AccentGradient: List<Color>
    @Composable
    @ReadOnlyComposable
    get() = Premium.accentGradient
