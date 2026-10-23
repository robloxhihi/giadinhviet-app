package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.example.ui.theme.TextMuted
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
fun BentoProgressBar(
    usedMinutes: Int,
    targetMinutes: Int,
    modifier: Modifier = Modifier
) {
    // Chua dat han muc = khong co duoc de do ti le. Thanh day 100% mau do la
    // mot cai noi-dung-khong (used/1 = tran), nen de trong + mau trung tinh.
    val pct = if (targetMinutes > 0) (usedMinutes.toFloat() / targetMinutes.toFloat()).coerceIn(0f, 1f) else 0f
    val color = when {
        targetMinutes <= 0 -> TextMuted
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
                text = if (targetMinutes > 0) "Mục tiêu: $targetMinutes phút" else "Chưa đặt mục tiêu",
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
