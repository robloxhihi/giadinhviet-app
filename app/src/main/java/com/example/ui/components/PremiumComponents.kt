package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalPremiumPalette
import com.example.ui.theme.OceanBlue
import com.example.ui.theme.OceanDeepBlue
import com.example.ui.theme.Premium
import com.example.ui.theme.PrimaryGradient
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * A frosted-glass surface. This is the single building block behind every
 * card in the redesigned app — dark nebula glass or pearl glass depending on
 * the active skin.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    glow: Color? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    val palette = Premium
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .shadow(
                elevation = if (glow != null) 14.dp else 8.dp,
                shape = shape,
                clip = false,
                ambientColor = glow ?: palette.glowPrimary.copy(alpha = 0.35f),
                spotColor = glow ?: palette.glowPrimary.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(palette.glassFillTop, palette.glassFillBottom))
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        glow?.copy(alpha = 0.55f) ?: palette.glassHighlight.copy(alpha = 0.65f),
                        palette.glassBorder
                    )
                ),
                shape = shape
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding)
    ) {
        content()
    }
}

/** Signature blue gradient call-to-action with a soft halo. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    colors: List<Color> = PrimaryGradient,
    contentColor: Color = Color.White,
    height: Dp = 52.dp,
    fontSize: Int = 15
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .height(height)
            .shadow(
                elevation = if (enabled) 14.dp else 0.dp,
                shape = shape,
                clip = false,
                ambientColor = colors.first().copy(alpha = 0.55f),
                spotColor = colors.last().copy(alpha = 0.65f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    if (enabled) colors else colors.map { it.copy(alpha = 0.45f) }
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = fontSize.sp
            )
        }
    }
}

/** Quiet secondary button that still feels premium (glass + tinted text). */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = Premium.primaryBright,
    height: Dp = 48.dp,
    fontSize: Int = 14
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(Premium.glassFillTop)
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(7.dp))
            }
            Text(text = text, color = tint, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
        }
    }
}


@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tint: Color? = null
) {
    val accent = tint ?: Premium.primaryBright
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
fun PremiumChip(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(if (filled) tint.copy(alpha = 0.22f) else Premium.glassFillTop)
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(30.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Circular progress with a gradient sweep — used for focus score, usage, XP. */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 108.dp,
    strokeWidth: Dp = 11.dp,
    colors: List<Color> = PrimaryGradient,
    content: @Composable () -> Unit = {}
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring"
    )
    val palette = Premium
    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = palette.glassBorder.copy(alpha = 0.45f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                brush = Brush.sweepGradient(colors + colors.first()),
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}

/** Compact KPI tile used across dashboard, insights and location history. */
@Composable
fun StatTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    tint: Color = Premium.primaryBright,
    onClick: (() -> Unit)? = null
) {
    GlassCard(
        modifier = modifier,
        cornerRadius = 20.dp,
        glow = tint.copy(alpha = 0.30f),
        onClick = onClick,
        contentPadding = 14.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tint.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/** Soft progress rail used for usage bars and category breakdowns. */
@Composable
fun GradientProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    colors: List<Color> = PrimaryGradient,
    height: Dp = 9.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "bar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(LocalPremiumPalette.current.glassBorder.copy(alpha = 0.35f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(Brush.horizontalGradient(colors))
        )
    }
}

/** Rounded avatar with a live status dot. */
@Composable
fun PremiumAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    online: Boolean = true,
    colors: List<Color> = PrimaryGradient
) {
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Brush.linearGradient(colors))
                .border(1.5.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
        if (online) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Premium.emerald)
                    .border(2.dp, Premium.opaqueSurface, CircleShape)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

/** Frosted header used by the full-screen feature destinations. */
@Composable
fun PremiumTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val palette = Premium
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.glassFillTop)
                    .border(1.dp, palette.glassBorder, CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
        trailing?.invoke()
    }
}

/** Floating frosted pill navigation bar — the app's signature bottom chrome. */
@Composable
fun PremiumBottomBar(
    items: List<Triple<String, ImageVector, Int>>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Premium
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.glowPrimary.copy(alpha = 0.45f),
                spotColor = palette.glowPrimary.copy(alpha = 0.55f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        palette.glassFillTop.copy(alpha = if (palette.isDark) 0.85f else 0.94f),
                        palette.glassFillBottom.copy(alpha = if (palette.isDark) 0.92f else 0.98f)
                    )
                )
            )
            .border(1.dp, palette.glassBorder, shape)
            .padding(horizontal = 6.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (label, icon, index) ->
                val selected = selectedIndex == index
                // Mau trang 10.sp tren pill OceanBlue dat 4.5:1; o day giu
                // nguyen cach dung OceanBlue cho trang thai chon de khong tu
                // lam giam tong am cua nhan.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (selected) {
                                Brush.horizontalGradient(listOf(OceanBlue, OceanDeepBlue))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (selected) Color.White else palette.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = if (selected) Color.White else palette.textMuted,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** Minimal weekly bar chart (used by Screen Time insights). */
@Composable
fun WeeklyBarChart(
    values: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    accent: Color = Premium.primaryBright,
    goalMinutes: Int? = null
) {
    val max = (values.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEach { (label, minutes) ->
            val fraction = (minutes.toFloat() / max.toFloat()).coerceIn(0.06f, 1f)
            val overGoal = goalMinutes != null && minutes > goalMinutes
            val animated by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(750, easing = FastOutSlowInEasing),
                label = "weekly-$label"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (minutes > 0) "${minutes}p" else "–",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 9.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(18.dp)
                        .height(84.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Premium.glassBorder.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp * animated)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.verticalGradient(
                                    if (overGoal) listOf(Premium.rose, Premium.amber)
                                    else listOf(accent.copy(alpha = 0.95f), accent.copy(alpha = 0.55f))
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
