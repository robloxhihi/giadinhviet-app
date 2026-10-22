package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.GradientProgressBar
import com.example.ui.components.PremiumChip
import com.example.ui.components.PremiumTopBar
import com.example.ui.components.RingProgress
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.components.WeeklyBarChart
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.Premium
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.VesperaUiState
import kotlin.math.roundToInt

/**
 * "Thấu hiểu thời gian" — screen-time intelligence.
 *
 * Turns the raw usage telemetry into a focus score, a weekly rhythm, an app
 * breakdown and actionable wellbeing advice for the whole family.
 */
@Composable
fun InsightsScreen(
    uiState: VesperaUiState,
    onBack: () -> Unit,
    onOpenUsageSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    // Han muc phut PHAI do gia dinh dat. `?: 120` cu bien mot nha chua tung
    // chon gi thanh "le 120 phut/ngay" roi tinh diem tu con so do.
    val target = uiState.usageSchedule?.dailyTargetMinutes?.takeIf { it > 0 }
    val todayMinutes = remember(uiState.detailedAppUsage) {
        com.example.data.model.todayUsedMinutes(uiState.detailedAppUsage)
    }
    val week = uiState.weeklyUsage
    val weekTotal = week.sumOf { it.second }
    // `weeklyUsage` luc nao cung co 7 cot, nhung cot bang 0 co the nghia la
    // hom do may chua duoc do — trung binh chi lay tren ngay co du lieu.
    val measuredDays = week.count { it.second > 0 }
    val weekAvg = if (measuredDays == 0) 0 else weekTotal / measuredDays
    val overTargetDays = if (target == null) 0 else week.count { it.second > target }

    // Focus score: chi tinh khi du hai dieu kien that — gia dinh da dat han muc
    // va may da co it nhat mot ngay duoc do.
    val focusScore: Float? = if (target == null || measuredDays == 0) null else {
        val overshoot = ((todayMinutes - target).coerceAtLeast(0).toFloat() / target)
        val consistency = 1f - (overTargetDays.toFloat() / measuredDays)
        ((0.65f * (1f - overshoot.coerceIn(0f, 1f)) + 0.35f * consistency) * 100f)
            .coerceIn(0f, 100f)
    }
    val scoreMissingReason = when {
        target == null -> "Chưa đặt hạn mức phút/ngày ở màn Nội quy nên không có mốc để chấm."
        else -> "Chưa có ngày nào được đo — cần quyền \"Truy cập dữ liệu sử dụng\" và máy đã dùng hôm nay."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("insights_screen")
    ) {
        PremiumTopBar(
            title = "Thấu hiểu thời gian",
            subtitle = "Phân tích thói quen số của gia đình",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(18.dp))

        if (!uiState.hasUsagePermission) {
            GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.amber) {
                Column {
                    SectionHeader(
                        title = "Chưa có quyền truy cập dữ liệu sử dụng",
                        subtitle = "Cấp quyền để mở khoá phân tích chi tiết",
                        icon = Icons.Default.Insights,
                        tint = Premium.amber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    GradientButton(
                        text = "Mở cài đặt Android để cấp quyền",
                        onClick = onOpenUsageSettings,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Focus score ───────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.primaryBright) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingProgress(
                    progress = (focusScore ?: 0f) / 100f,
                    ringSize = 118.dp,
                    strokeWidth = 12.dp,
                    colors = when {
                        focusScore == null -> listOf(TextMuted, TextMuted)
                        focusScore >= 75f -> listOf(Premium.emerald, Premium.cyan)
                        focusScore >= 50f -> Premium.primaryGradient
                        else -> listOf(Premium.amber, Premium.rose)
                    }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            // Khong co du kien that thi in "—", not la mot con so
                            // tinh tu mac dinh bi bịa.
                            text = focusScore?.toInt()?.toString() ?: "—",
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "ĐIỂM", style = MaterialTheme.typography.labelSmall, color = TextSecondary, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = focusScore?.let { focusLabel(it) } ?: "Chưa tính được điểm",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = focusScore?.let { focusAdvice(it, todayMinutes, target ?: 0) }
                            ?: scoreMissingReason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PremiumChip(text = "$todayMinutes ph hôm nay", tint = Premium.primaryBright, filled = true)
                        PremiumChip(
                            text = if (measuredDays == 0) "Chưa có ngày nào được đo"
                            else "TB $weekAvg ph/ngày ($measuredDays ngày)",
                            tint = Premium.violet
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Weekly rhythm ─────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Nhịp sử dụng trong tuần",
                    subtitle = if (target == null)
                        "Chưa đặt mục tiêu phút/ngày — soạn ở màn Nội quy"
                    else "Mục tiêu mỗi ngày: $target phút",
                    icon = Icons.Default.Schedule,
                    tint = Premium.cyan
                )
                Spacer(modifier = Modifier.height(16.dp))
                WeeklyBarChart(
                    values = week,
                    accent = Premium.primaryBright,
                    goalMinutes = target
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tổng tuần: $weekTotal phút",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = if (target == null) "Chưa có mốc để so"
                        else "$overTargetDays ngày vượt mục tiêu",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (overTargetDays > 0) Premium.rose else Premium.emerald,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── KPI tiles ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                label = "HÔM NAY",
                value = "$todayMinutes ph",
                hint = if (target == null) "Chưa đặt mục tiêu" else "Mục tiêu $target ph",
                tint = Premium.primaryBright
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Insights,
                label = "CÒN LẠI",
                // "—" khi chua dat han muc: `(0 - today).coerceAtLeast(0)` se
                // in ra "0 ph con lai" va gia dinh tuong rang con da het slot.
                value = if (target == null) "—" else "${(target - todayMinutes).coerceAtLeast(0)} ph",
                hint = if (target == null) "Chưa có ngân sách ngày" else "Trong ngân sách",
                tint = Premium.emerald
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Category breakdown ────────────────────────────────────────────
        if (uiState.usageCategories.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader(
                        title = "Phân loại theo nhóm ứng dụng",
                        subtitle = if (uiState.usageCategories.size > 6)
                            "6/${uiState.usageCategories.size} nhóm — chỉ hiện 6 nhóm nhiều thời lượng nhất"
                        else "Học tập, giải trí, mạng xã hội…",
                        icon = Icons.Default.Apps,
                        tint = Premium.violet
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    uiState.usageCategories.take(6).forEachIndexed { index, category ->
                        val tint = categoryTint(index)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(tint)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = category.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = "${category.minutes} ph",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                // `percentage` la ti le 0..1 — truoc day in thang
                                // ra keu "%" nen nhom dung 100% thi may ghi "1%".
                                text = "${(category.percentage * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        GradientProgressBar(
                            progress = category.percentage,
                            colors = listOf(tint, tint.copy(alpha = 0.55f)),
                            height = 6.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Top apps ──────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Ứng dụng dùng nhiều nhất",
                    subtitle = if (uiState.detailedAppUsage.size > 8)
                        "8/${uiState.detailedAppUsage.size} ứng dụng — đo trực tiếp trên thiết bị"
                    else "Dữ liệu đo trực tiếp trên thiết bị",
                    icon = Icons.Default.Apps,
                    tint = Premium.amber
                )
                Spacer(modifier = Modifier.height(14.dp))
                if (uiState.detailedAppUsage.isEmpty()) {
                    Text(
                        text = "Chưa ghi nhận ứng dụng nào sử dụng trên 1 phút. Dữ liệu sẽ xuất hiện khi dịch vụ giám sát nền hoạt động.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    val maxMinutes = (uiState.detailedAppUsage.maxOfOrNull { it.minutesUsed } ?: 1).coerceAtLeast(1)
                    uiState.detailedAppUsage.take(8).forEach { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Premium.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = app.appName.take(1).uppercase(),
                                    color = Premium.primaryBright,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                GradientProgressBar(
                                    progress = app.minutesUsed.toFloat() / maxMinutes.toFloat(),
                                    colors = listOf(Premium.primaryBright, Premium.violet),
                                    height = 6.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "${app.minutesUsed} ph",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

private fun categoryTint(index: Int): Color {
    // Token mới, sắp xếp để 2 màu kề nhau (kể cả wrap) khác hue rõ trên nền tối
    val palette = listOf(
        FacebookBlue,
        NeonAmber,
        NeonViolet,
        NeonEmerald,
        NeonCyan,
        NeonRose
    )
    return palette[index % palette.size]
}

private fun focusLabel(score: Float): String = when {
    score >= 85f -> "Rất cân bằng"
    score >= 70f -> "Khá cân bằng"
    score >= 50f -> "Cần chú ý"
    else -> "Cần điều chỉnh"
}

private fun focusAdvice(score: Float, today: Int, target: Int): String = when {
    score >= 85f -> "Thói quen số của gia đình đang rất lành mạnh. Tiếp tục duy trì nhé!"
    score >= 70f -> "Vẫn trong tầm kiểm soát. Hôm nay đã dùng $today/$target phút."
    score >= 50f -> "Có vài ngày vượt mục tiêu. Hãy thử đặt giờ nghỉ cho con."
    else -> "Thời gian màn hình đang cao hơn mục tiêu. Nên rà soát lại giới hạn."
}
