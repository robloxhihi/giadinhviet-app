package com.example.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/**
 * PremiumTheme — the visual foundation for "Gia Đình Việt".
 *
 * Two award-winning skins, both built from the same token contract:
 *  • SPACE_NIGHT  — deep nebula gradient, twinkling starfield, crescent moon,
 *                   frosted-glass cards with cool blue rim light.
 *  • DREAMY_NATURE — soft sky gradient, drifting clouds, warm sun bloom,
 *                   living green canopy and pearl-glass cards.
 *
 * Every screen reads colours through these tokens, so switching the theme
 * instantly re-skins the whole app.
 */
@Immutable
data class PremiumPalette(
    val isDark: Boolean,
    val key: String,
    // Layered background gradient (top -> bottom)
    val backgroundGradient: List<Color>,
    // Opaque fallback for surfaces that must not be translucent (dialogs, sheets)
    val opaqueSurface: Color,
    // Frosted glass card
    val glassFillTop: Color,
    val glassFillBottom: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    // Brand gradients
    val primaryGradient: List<Color>,
    val accentGradient: List<Color>,
    val heroGradient: List<Color>,
    // Accents
    val primary: Color,
    val primaryBright: Color,
    val violet: Color,
    val accent: Color,
    val emerald: Color,
    val amber: Color,
    val rose: Color,
    // Typography
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    // Ambient decoration
    val starColor: Color,
    val cloudColor: Color,
    val glowPrimary: Color,
    val glowSecondary: Color,
    val canopyColor: Color
) {
    val glassFill: Color get() = glassFillBottom
    val primaryGradientBrush: Brush get() = Brush.horizontalGradient(primaryGradient)
    val backgroundBrush: Brush get() = Brush.verticalGradient(backgroundGradient)
    val heroBrush: Brush get() = Brush.linearGradient(heroGradient)
}

// ─────────────────────────────────────────────────────────────────────────────
// SPACE NIGHT (dark) — "Bầu trời đêm huyền ảo"
// ─────────────────────────────────────────────────────────────────────────────
val SpaceNightPalette = PremiumPalette(
    isDark = true,
    key = "space",
    // DEN THAT: day 0xFF000000 va mot ramp rat sat, de du duoc anh nen thi
    // giao dien van doc la "den" chu khong phai "xanh dem sang mau".
    backgroundGradient = listOf(
        Color(0xFF000000),
        Color(0xFF030407),
        Color(0xFF05060A),
        Color(0xFF040508),
        Color(0xFF000000)
    ),
    opaqueSurface = Color(0xFF04050A),
    // The card fill must be nearly OPAQUE. At 0xC2/0xD9 the moon glow and the
    // starfield behind the card shine through the label and the text becomes
    // unreadable (the "bi chong nen" complaint from the screenshots).
    glassFillTop = Color(0xF80A0E16),
    glassFillBottom = Color(0xF406080E),
    // Champagne rim: the single "premium" signature on every glass card.
    glassBorder = Color(0x33C8A96A),
    glassHighlight = Color(0x59FFFFFF),
    primaryGradient = listOf(Color(0xFF4F8DFD), Color(0xFF2A5BF0)),
    accentGradient = listOf(Color(0xFF7C6FC0), Color(0xFF2563EB)),
    heroGradient = listOf(Color(0xFF16203A), Color(0xFF0A0E1A)),
    primary = Color(0xFF3B82F6),
    primaryBright = Color(0xFF639CF2),
    violet = Color(0xFF7C6FC0),
    accent = OceanBlue,
    emerald = Color(0xFF2E9268),
    amber = Color(0xFFC79346),
    rose = Color(0xFFC26178),
    textPrimary = Color(0xFFF5F7FB),
    textSecondary = Color(0xFFB4BECE),
    textMuted = Color(0xFF8F99AB),
    starColor = Color(0xFFDCE6F5),
    cloudColor = Color(0xFFFFFFFF),
    glowPrimary = Color(0xFF24409A),
    glowSecondary = Color(0xFFA8895C),
    canopyColor = Color(0xFF141F49)
)

// ─────────────────────────────────────────────────────────────────────────────
// DREAMY NATURE (light) — "Khu vườn trong mơ"
// ─────────────────────────────────────────────────────────────────────────────
val DreamyNaturePalette = PremiumPalette(
    isDark = false,
    key = "nature",
    backgroundGradient = listOf(
        Color(0xFFCFE6FF),
        Color(0xFFE4F2FF),
        Color(0xFFF4FAFF),
        Color(0xFFEAF7EE),
        Color(0xFFDDF0E2)
    ),
    opaqueSurface = Color(0xFFF7FBFF),
    // 0xB3 (70%) day du de anh vuon cong + mat nuoc boi xuyen qua phan duoi
    // cua THE, va chu `bodySmall` mau xam la lien tuc ben duoi. "Thuy tinh"
    // o day nen den tu viEN + bong dang, khong phai de noi dung thau qua.
    glassFillTop = Color(0xF5FFFFFF),
    glassFillBottom = Color(0xE8FFFFFF),
    glassBorder = Color(0x99BEDCFA),
    glassHighlight = Color(0xFFFFFFFF),
    primaryGradient = listOf(Color(0xFF4C93FF), Color(0xFF2563EB)),
    accentGradient = listOf(Color(0xFF2E7DF7), Color(0xFF2563EB)),
    heroGradient = listOf(Color(0xFF7FC4FF), Color(0xFFB8E0FF)),
    primary = Color(0xFF2563EB),
    primaryBright = Color(0xFF4C93FF),
    violet = Color(0xFF7C6CF6),
    accent = OceanBlue,
    emerald = Color(0xFF16A34A),
    amber = Color(0xFFF59E0B),
    rose = Color(0xFFE11D48),
    textPrimary = Color(0xFF0C2A4A),
    textSecondary = Color(0xFF46658A),
    textMuted = Color(0xFF7B93B0),
    starColor = Color(0xFFFFFFFF),
    cloudColor = Color(0xFFFFFFFF),
    glowPrimary = Color(0xFF7FC4FF),
    glowSecondary = Color(0xFF9BE7C4),
    canopyColor = Color(0xFF8ED8A6)
)

val LocalPremiumPalette = staticCompositionLocalOf { SpaceNightPalette }

private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

/**
 * The living backdrop used behind every screen.
 *
 * The artwork is a real photograph (res/drawable/bg_night.jpg, bg_nature.jpg)
 * that matches the two design mock-ups; the Canvas on top only adds the moving
 * starfield, so the moon itself comes from the picture (showMoon = false).
 */
@Composable
fun PremiumBackground(
    modifier: Modifier = Modifier,
    showMoon: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val palette = LocalPremiumPalette.current
    // Truoc day nen dong nay co `rememberInfiniteTransition`: mot gia tri Float
    // doi moi 60 lan/giay khien Canvas full-man-hinh ve lai lien tuc, ke ca khi
    // khong ai nhin. Do tren may thap la toan bo app rung khi cuon. Sao gio
    // tinh: chi so sang theo `phase` co dinh, khong con animation.
    val wallpaper = asyncWallpaper(if (palette.isDark) R.drawable.bg_night else R.drawable.bg_nature)

    val stars = remember {
        val rnd = kotlin.random.Random(20261016)
        List(130) {
            Star(
                x = rnd.nextFloat(),
                // 0.30: stars live in the header band only. At 0.92 they were
                // sprinkled over the whole screen, i.e. behind every card.
                y = rnd.nextFloat() * 0.30f,
                radius = 0.7f + rnd.nextFloat() * 2.1f,
                phase = rnd.nextFloat()
            )
        }
    }

    // Mot dung hoa that (draw/bg_night.jpg, bg_nature.jpg) — khong phai thu
    // cong. Canvas duoi chi la lop bo sung (dai sao), con scrim den rat day
    // de hoa lan chi la mot lop sach ben duoi: app doc len la DEN, khong phai
    // "mau xanh cua rung", va mot man cover duoi tuyet doi khong boi xuyen
    // qua man tren (lau nay chinh la cam giac "chong nen").
    // Scrim: giup chu tren the thuy tinh dat du tong am 4.5:1 tren anh.
    val scrim = if (palette.isDark) {
        Brush.verticalGradient(listOf(Color(0xF2010207), Color(0xD9010207), Color(0xF5000000)))
    } else {
        Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF), Color(0x4DFFFFFF)))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundBrush)
    ) {
        // `wallpaper` null trong vai chuc ms dau: nen luc do la gradient cua
        // `backgroundBrush` — man hinh co noi dung ngay thay vi trang den.
        wallpaper?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(scrim))
        // Chim di qua anh: dai sao mo (toi) — khong ve trang/nhiem sang o che
        // do sang, vi hoa lan cong + mat nuoc trong anh da la "chup rong".
        if (palette.isDark) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawSpaceNight(palette, stars, showMoon)
            }
        }
        content()
    }
}

/**
 * Giai ma anh nen 1024x1792 OFF the main thread.
 *
 * `painterResource` giai ma ngay trong frame dau tien: do tren may thap la
 * mot frame 1,1 giay va 54 frame bi bo (log Choreographer hom 2026-10-22),
 * tuc la cam giac "app mo rat cham" truoc khi kip nhin thay gi. RGB_565 giam
 * nua bo nho va thoi gian giai ma so voi ARGB_8888 mac dinh.
 */
@Composable
private fun asyncWallpaper(resId: Int): ImageBitmap? {
    var bitmap by remember(resId) { mutableStateOf<ImageBitmap?>(null) }
    val resources = LocalContext.current.resources
    LaunchedEffect(resId) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                BitmapFactory.decodeResource(resources, resId, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpaceNight(
    palette: PremiumPalette,
    stars: List<Star>,
    showMoon: Boolean
) {
    val w = size.width
    val h = size.height

    // Nebula blooms. Kept very faint: these sit BEHIND the content and every
    // extra point of alpha shows up as a smudge under card text. The secondary
    // bloom now carries the champagne cast (palette.glowSecondary) so the
    // darkness reads "warm-black luxury", not "neon game".
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glowPrimary.copy(alpha = 0.09f), Color.Transparent),
            center = Offset(w * 0.12f, h * 0.10f),
            radius = w * 0.85f
        ),
        radius = w * 0.85f,
        center = Offset(w * 0.12f, h * 0.10f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.glowSecondary.copy(alpha = 0.06f), Color.Transparent),
            center = Offset(w * 0.95f, h * 0.30f),
            radius = w * 0.75f
        ),
        radius = w * 0.75f,
        center = Offset(w * 0.95f, h * 0.30f)
    )
    // Lotus / horizon glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF203A85).copy(alpha = 0.10f), Color.Transparent),
            center = Offset(w * 0.5f, h * 1.02f),
            radius = w * 0.9f
        ),
        radius = w * 0.9f,
        center = Offset(w * 0.5f, h * 1.02f)
    )

    // Starfield — only in the top band, so no dot ever lands behind a card.
    stars.forEach { star ->
        val a = 0.20f + 0.80f * ((sin(star.phase * 6.2831853f) + 1f) / 2f)
        drawCircle(
            color = palette.starColor.copy(alpha = a * 0.32f),
            radius = star.radius,
            center = Offset(star.x * w, star.y * h)
        )
    }

    // Crescent moon — small, glued to the top-right corner, weak halo.
    // The old version (r = 0.072w, halo r*3.4 at 0.30 alpha, cy = 0.085h) put a
    // pale blob exactly where the first row of buttons sits. This pass shrinks
    // the disc further and halves the halo so the moon is a jewel, not a lamp.
    if (showMoon) {
        val cx = w * 0.86f
        val cy = h * 0.045f
        val r = w * 0.038f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(palette.starColor.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(cx, cy),
                radius = r * 2.2f
            ),
            radius = r * 2.2f,
            center = Offset(cx, cy)
        )
        val moon = Path().apply { addOval(Rect(center = Offset(cx, cy), radius = r)) }
        val cut = Path().apply {
            addOval(Rect(center = Offset(cx + r * 0.52f, cy - r * 0.30f), radius = r * 0.98f))
        }
        moon.op(moon, cut, PathOperation.Difference)
        drawPath(moon, color = Color(0xFFF2F6FF).copy(alpha = 0.72f))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDreamyNature(
    palette: PremiumPalette,
    drift: Float
) {
    val w = size.width
    val h = size.height

    // Warm sun bloom
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF4D6).copy(alpha = 0.85f), Color.Transparent),
            center = Offset(w * 0.80f, h * 0.06f),
            radius = w * 0.55f
        ),
        radius = w * 0.55f,
        center = Offset(w * 0.80f, h * 0.06f)
    )

    // Drifting clouds (wrapped horizontally)
    val clouds = listOf(
        Triple(0.15f, 0.10f, 0.30f),
        Triple(0.55f, 0.17f, 0.24f),
        Triple(0.88f, 0.09f, 0.20f),
        Triple(0.32f, 0.26f, 0.17f),
        Triple(0.72f, 0.33f, 0.14f)
    )
    clouds.forEachIndexed { index, (x, y, r) ->
        val shift = ((x + drift * (0.35f + index * 0.12f)) % 1.25f) - 0.12f
        val cx = shift * w
        val cy = y * h
        val radius = r * w
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.cloudColor.copy(alpha = 0.92f),
                    palette.cloudColor.copy(alpha = 0.35f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = radius
            ),
            radius = radius,
            center = Offset(cx, cy)
        )
    }

    // Green canopy at the horizon
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.canopyColor.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(w * 0.12f, h * 1.05f),
            radius = w * 0.85f
        ),
        radius = w * 0.85f,
        center = Offset(w * 0.12f, h * 1.05f)
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(palette.canopyColor.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(w * 0.92f, h * 1.08f),
            radius = w * 0.80f
        ),
        radius = w * 0.80f,
        center = Offset(w * 0.92f, h * 1.08f)
    )
    // Water shimmer
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color(0x66FFFFFF), Color.Transparent),
            startY = h * 0.82f,
            endY = h
        ),
        topLeft = Offset(0f, h * 0.82f),
        size = Size(w, h * 0.18f)
    )
}
