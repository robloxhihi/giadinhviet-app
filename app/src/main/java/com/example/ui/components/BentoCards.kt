package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmotionTag
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.SpaceCardSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextOnAccent
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    testTag: String = "bento_card",
    content: @Composable () -> Unit
) {
    val palette = com.example.ui.theme.Premium
    val shape = RoundedCornerShape(22.dp)
    val fill = if (backgroundColor == Color.Unspecified) {
        Brush.verticalGradient(listOf(palette.glassFillTop, palette.glassFillBottom))
    } else {
        Brush.verticalGradient(listOf(backgroundColor, backgroundColor))
    }
    val rim = if (borderColor == Color.Unspecified) {
        Brush.linearGradient(
            listOf(
                palette.glassHighlight.copy(alpha = 0.60f),
                palette.glassBorder
            )
        )
    } else {
        Brush.linearGradient(listOf(borderColor.copy(alpha = 0.75f), borderColor))
    }
    Box(
        modifier = modifier
            .testTag(testTag)
            .shadow(
                elevation = 8.dp,
                shape = shape,
                clip = false,
                ambientColor = palette.glowPrimary.copy(alpha = 0.30f),
                spotColor = palette.glowPrimary.copy(alpha = 0.40f)
            )
            .clip(shape)
            .background(fill)
            .border(1.dp, rim, shape)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
fun BentoStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: String,
    subtitle: String? = null,
    testTag: String = "stat_card"
) {
    BentoCard(
        modifier = modifier,
        testTag = testTag
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun BentoRadarCard(
    modifier: Modifier = Modifier,
    partnerName: String,
    distanceKm: String = "Gần đây",
    batteryPct: Int,
    isCharging: Boolean,
    speedKmh: Float,
    isPrivacyActive: Boolean,
    lastUpdateText: String,
    onTap: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    BentoCard(
        modifier = modifier,
        onClick = onTap,
        testTag = "radar_bento_card"
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isPrivacyActive) NeonAmber else NeonEmerald)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPrivacyActive) "KHUNG GIỜ RIÊNG TƯ" else "ĐỊNH VỊ REALTIME (ZENLY)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPrivacyActive) NeonAmber else NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = lastUpdateText,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Radar Visual Screen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                SpaceCardElevated,
                                SpaceCardSurface
                            )
                        )
                    )
                    .border(1.dp, SpaceCardBorder, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Radar Rings
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val maxRadius = size.height * 0.42f

                    // Concentric rings
                    drawCircle(
                        color = NeonViolet.copy(alpha = 0.15f),
                        radius = maxRadius,
                        center = center
                    )
                    drawCircle(
                        color = NeonViolet.copy(alpha = 0.25f),
                        radius = maxRadius * 0.65f,
                        center = center
                    )
                    drawCircle(
                        color = NeonViolet.copy(alpha = 0.35f),
                        radius = maxRadius * 0.35f,
                        center = center
                    )

                    // Pulse wave
                    if (!isPrivacyActive) {
                        drawCircle(
                            color = NeonCyan.copy(alpha = pulseAlpha),
                            radius = maxRadius * pulseScale,
                            center = center
                        )
                    }
                }

                // Center Node: Partner Avatar / Pin
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(NeonViolet, NeonCyan)
                                )
                            )
                            .border(2.dp, TextOnAccent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = partnerName.take(1).uppercase().ifEmpty { "V" },
                            style = MaterialTheme.typography.titleLarge,
                            color = TextOnAccent,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SpaceCardElevated)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isPrivacyActive) "Riêng tư 🛡️" else "$distanceKm • ${batteryPct}% ${if (isCharging) "⚡" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub info metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricChip(label = "Tốc độ", value = "${speedKmh.toInt()} km/h")
                MetricChip(label = "Pin", value = "$batteryPct% ${if (isCharging) "⚡" else ""}")
                MetricChip(label = "Trạng thái", value = if (isPrivacyActive) "Ẩn vị trí" else "Chuẩn GPS")
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: String) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun BentoProgressBar(
    usedMinutes: Int,
    targetMinutes: Int,
    modifier: Modifier = Modifier
) {
    val pct = (usedMinutes.toFloat() / targetMinutes.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val color = when {
        pct >= 0.9f -> NeonRose
        pct >= 0.7f -> NeonAmber
        else -> NeonCyan
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Đã dùng: $usedMinutes phút",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "Mục tiêu: $targetMinutes phút",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(SpaceCardElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(pct)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonViolet, color)
                        )
                    )
            )
        }
    }
}

@Composable
fun EmotionSelectorRow(
    selectedEmotion: EmotionTag?,
    onSelectEmotion: (EmotionTag) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        EmotionTag.values().forEach { emotion ->
            val isSelected = selectedEmotion == emotion
            val tint = when (emotion) {
                EmotionTag.HAPPY -> NeonEmerald
                EmotionTag.NORMAL -> NeonCyan
                EmotionTag.STUDY_STRESS -> NeonAmber
                EmotionTag.NEED_SUPPORT -> NeonRose
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) tint.copy(alpha = 0.25f) else SpaceCardElevated)
                    .border(
                        1.dp,
                        if (isSelected) tint else SpaceCardBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectEmotion(emotion) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = emotion.emoji, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = emotion.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) TextPrimary else TextMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun IosDynamicIslandPill(
    partnerName: String,
    safeZone: String,
    batteryPct: Int,
    isCharging: Boolean,
    activeApp: String,
    appMins: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(DialogSurface)
            .border(1.dp, SpaceCardBorder, RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(NeonEmerald)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "$partnerName • $safeZone",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "Dynamic Island (iOS Preview)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonCyan.copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$activeApp: ${appMins}m",
                        color = NeonCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$batteryPct% ${if (isCharging) "⚡" else "🔋"}",
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun LiveBackgroundTelemetryCard(
    partnerName: String,
    safeZone: String,
    topApp: String,
    topAppMins: Int,
    totalMins: Int,
    isBackgroundActive: Boolean,
    modifier: Modifier = Modifier
) {
    BentoCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = if (isBackgroundActive) NeonEmerald.copy(alpha = 0.6f) else SpaceCardBorder
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = if (isBackgroundActive) "🟢" else "⚪", fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "GIÁM SÁT NGẦM THỜI GIAN THỰC",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isBackgroundActive) NeonEmerald else TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NeonViolet.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = safeZone,
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Dịch vụ Vespera Foreground Service thu thập ngầm tọa độ GPS và thời gian ứng dụng đang chạy:",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceCardElevated)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "Ứng dụng mở nhiều nhất", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = topApp,
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(text = "$topAppMins phút hôm nay", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceCardElevated)
                        .padding(10.dp)
                ) {
                    Column {
                        Text(text = "Tổng giờ màn hình", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$totalMins phút",
                            style = MaterialTheme.typography.titleMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Đồng bộ liên tục", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }
    }
}

