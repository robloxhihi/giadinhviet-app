package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.TagFaces
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserRole
import com.example.service.blocker.ConsentPolicy
import com.example.ui.components.BentoCard
import com.example.ui.components.BentoProgressBar
import com.example.ui.components.BentoStatCard
import com.example.ui.components.IosDynamicIslandPill
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.SpaceDarkBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.VesperaUiState
import kotlin.math.roundToInt

@Composable
fun ScreenTimeScreen(
    uiState: VesperaUiState,
    onOpenUsageSettings: () -> Unit,
    onRequestExtraTimeClick: () -> Unit,
    onEditScheduleClick: () -> Unit,
    onToggleBackgroundService: () -> Unit,
    onStartFocusSession: () -> Unit = {},
    onStopFocusSession: () -> Unit = {},
    // "Phong hoc tap trung" da bi cat theo yeu cau. Nut o cuoi the Tap trung
    // cua tab bay gio mo "Thau hieu thoi gian" - man overlay duy nhat con lai
    // cua Screen Time.
    onOpenInsights: () -> Unit = {},
    onProposeAgreement: (Int) -> Unit = {},
    onRespondToAgreement: (com.example.data.model.ScreenTimeAgreement, Boolean) -> Unit = { _, _ -> },
    onRespondRuleChange: (Boolean) -> Unit = {},
    onWithdrawRuleProposal: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val isChild = uiState.userProfile?.role == UserRole.CHILD
    val sched = uiState.usageSchedule
    val target = sched?.dailyTargetMinutes ?: 120
    // So phut THAT tu may, khong phai con so 45 nam im trong tai lieu tu luc
    // ghep noi — neu lay `sched.usedMinutes` thi man hinh bao "da dung 45 phut"
    // ngay ca khi hom do con chua mo may.
    val used = com.example.data.model.todayUsedMinutes(
        uiState.detailedAppUsage,
        sched?.usedMinutes ?: 0
    )
    val remaining = (target - used).coerceAtLeast(0)

    // Trung bình chỉ tính trên ngày có dữ liệu thật (> 0); tuần trống → nói "chưa có dữ liệu".
    val activeDayMinutes = uiState.weeklyUsage.map { it.second }.filter { it > 0 }
    val weeklyAvgText = if (activeDayMinutes.isEmpty()) {
        "Chưa có dữ liệu sử dụng"
    } else {
        "Trung bình ${activeDayMinutes.average().roundToInt()} ph/ngày"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Agreement Section
        if (uiState.activePairing != null) {
            Spacer(modifier = Modifier.height(16.dp))
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(text = "THỎA THUẬN GIỜ DÙNG", style = MaterialTheme.typography.titleSmall, color = NeonViolet, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isChild) {
                        // Hiển thị đề xuất từ mẹ hoặc nút để con đề xuất
                        val pending = uiState.agreementRequests.filter { it.status == com.example.data.model.RequestStatus.PENDING && it.proposerRole == com.example.data.model.UserRole.PARENT }
                        if (pending.isNotEmpty()) {
                            pending.forEach { agreement ->
                                Text("Mẹ đề xuất: ${agreement.requestedMinutes} phút/ngày")
                                Row {
                                    Button(onClick = { onRespondToAgreement(agreement, true) }) { Text("Đồng ý") }
                                    Button(onClick = { onRespondToAgreement(agreement, false) }) { Text("Từ chối") }
                                }
                            }
                        } else {
                            Text("Chưa có đề xuất mới.")
                        }
                    } else {
                        // Phụ huynh đề xuất
                        Button(onClick = { onProposeAgreement(120) }) { Text("Đề xuất 120 phút") }
                    }
                }
            }
        }
        // ── Phieu noi quy cho duyet (danh sach chan app / che do chan) ─────
        // Dat o man hinh chinh de ben duoc de xuat KHONG phai vao Cai dat tim
        // mãi. Ben goi de xuat chi thay chu "cho duyet", khong thay nut Dong y.
        val myRoleNow = uiState.userProfile?.role
        val ruleSchedule = uiState.usageSchedule
        val consentLine = ConsentPolicy.summary(ruleSchedule, myRoleNow)
        if (consentLine != null && uiState.activePairing != null) {
            Spacer(modifier = Modifier.height(16.dp))
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "PHIẾU NỘI QUY",
                        style = MaterialTheme.typography.titleSmall,
                        color = NeonViolet,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = consentLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    if (ruleSchedule != null && ConsentPolicy.canVote(ruleSchedule, myRoleNow)) {
                        Text(
                            text = "Đồng ý thì nội quy này mới chạy trên máy con. " +
                                "Từ chối thì giữ nguyên nội quy cũ — cả hai máy đều thấy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRespondRuleChange(true) }) { Text("Đồng ý") }
                            OutlinedButton(onClick = { onRespondRuleChange(false) }) { Text("Từ chối") }
                        }
                    } else if (ruleSchedule != null && ConsentPolicy.canWithdraw(ruleSchedule, myRoleNow)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onWithdrawRuleProposal) {
                            Text("Rút đề xuất đang chờ")
                        }
                    }
                }
            }
        }

        if (uiState.isIosPreviewMode && uiState.activePairing != null) {
            val partnerLoc = uiState.partnerLocation
            val partnerName = if (isChild) uiState.activePairing.parentName
                              else uiState.activePairing.childName
            if (partnerLoc != null) {
                // Pill nhận batteryPct/appMins kiểu Int nên "chưa có dữ liệu" không nhét
                // vào ô pin được — máy con chưa gửi số thì tách sang khung bên dưới.
                IosDynamicIslandPill(
                    partnerName = partnerName,
                    safeZone = partnerLoc.currentSafeZone.ifEmpty { "Khu vực an toàn" },
                    batteryPct = partnerLoc.batteryLevel,
                    isCharging = partnerLoc.isCharging,
                    activeApp = partnerLoc.topActiveApp.ifEmpty { "Chưa đọc được dữ liệu" },
                    appMins = partnerLoc.topActiveAppMinutes
                )
            } else {
                // Khung pill thật về giao diện nhưng ghi rõ trạng thái rỗng, không bịa 80% pin.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                        .background(DialogSurface)
                        .border(1.dp, SpaceCardBorder, RoundedCornerShape(32.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "$partnerName • Chưa có dữ liệu pin/ứng dụng từ máy con",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Header
        Text(
            text = "QUẢN LÝ THỜI GIAN",
            style = MaterialTheme.typography.displayMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Lịch sinh hoạt gia đình & Thống kê ứng dụng",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Overview Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tiến độ sử dụng hôm nay",
                        style = MaterialTheme.typography.titleSmall,
                        color = FacebookBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Còn $remaining phút",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                BentoProgressBar(
                    usedMinutes = used,
                    targetMinutes = target
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isChild) {
                        Button(
                            onClick = onRequestExtraTimeClick,
                            modifier = Modifier.fillMaxWidth().testTag("req_extra_time_screentime_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FacebookBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xin thêm thời gian sử dụng", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onEditScheduleClick,
                            modifier = Modifier.fillMaxWidth().testTag("edit_sched_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = FacebookBlue
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            // Nut nay chi chuyen sang tab Cai dat — noi "Tùy
                            // chỉnh lịch" ngay tai day la het: noi quy soan o
                            // ben kia, va co the dang co phieu cho duyet.
                            Text("Mở Nội quy ứng dụng trong Cài đặt")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Pomodoro Focus Study Mode Card
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (uiState.isFocusSessionActive) NeonCyan else SpaceCardBorder
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "🎯", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Chế độ học tập Pomodoro",
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.isFocusSessionActive) NeonCyan.copy(alpha = 0.2f) else SpaceCardElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (uiState.isFocusSessionActive)
                                "Đang học • còn ${formatFocusClock(uiState.focusSessionSecondsRemaining)}"
                            else "Sẵn sàng",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isFocusSessionActive) NeonCyan else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (uiState.isFocusSessionActive)
                        "Đồng hồ đang chạy thật — còn ${formatFocusClock(uiState.focusSessionSecondsRemaining)}. Điện thoại sẽ nhắc nếu bạn thoát ra chơi game."
                    else
                        "Kích hoạt phiên học 25 phút Pomodoro giúp rèn luyện thói quen tự giác, không xao nhãng.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                if (uiState.isFocusSessionActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    val totalMin = uiState.focusSessionTotalMinutes.coerceAtLeast(1)
                    BentoProgressBar(
                        usedMinutes = (totalMin - uiState.focusSessionMinutesRemaining).coerceIn(0, totalMin),
                        targetMinutes = totalMin
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (uiState.isFocusSessionActive) onStopFocusSession() else onStartFocusSession()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("toggle_focus_mode_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isFocusSessionActive) SpaceCardElevated else NeonCyan,
                        contentColor = if (uiState.isFocusSessionActive) NeonRose else Color.Black
                    )
                ) {
                    Text(
                        text = if (uiState.isFocusSessionActive)
                            "Kết thúc phiên học tập"
                        else "Bắt đầu 25 phút tập trung 🎯",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Nut mo "Thau hieu thoi gian" - tung la loi vao "Phong hoc tap
                // trung" (da bi cat). Viet bang Icons.Insights (extended) de
                // khong phai them import moi.
                OutlinedButton(
                    onClick = onOpenInsights,
                    modifier = Modifier.fillMaxWidth().testTag("open_insights_btn"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Insights,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = NeonCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thấu hiểu thời gian • biểu đồ • gợi ý", color = NeonCyan)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly Trend Bento Bar Chart
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BIỂU ĐỒ SỬ DỤNG TRONG TUẦN",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonViolet,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = weeklyAvgText,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    val maxMins = 160f
                    // `weeklyUsage` la mang co dinh theo thu tu T2..CN, nen
                    // "hom nay" la MO COT theo thu tu do — khong phai co CN.
                    // Cũ so ten thu cuu "CN" nen cot noi sang luc nao cung la
                    // cot Chu nhat, bat ke hom do la thu may.
                    val todayIndex = when (java.util.Calendar.getInstance()
                        .get(java.util.Calendar.DAY_OF_WEEK)
                    ) {
                        java.util.Calendar.MONDAY -> 0
                        java.util.Calendar.TUESDAY -> 1
                        java.util.Calendar.WEDNESDAY -> 2
                        java.util.Calendar.THURSDAY -> 3
                        java.util.Calendar.FRIDAY -> 4
                        java.util.Calendar.SATURDAY -> 5
                        else -> 6 // Calendar.SUNDAY
                    }
                    uiState.weeklyUsage.forEachIndexed { index, weekEntry ->
                        val (day, mins) = weekEntry
                        val ratio = (mins / maxMins).coerceIn(0.15f, 1f)
                        val isToday = index == todayIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "$mins",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) NeonCyan else TextMuted,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(22.dp)
                                    .height((70 * ratio).dp)
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(if (isToday) NeonCyan else NeonViolet.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = day,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isToday) TextPrimary else TextSecondary,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // UsageStats Real Breakdown Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PHÂN LOẠI ỨNG DỤNG (USAGE ACCESS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (!uiState.hasUsagePermission) {
                        Text(
                            text = "Chưa cấp quyền ⚠️",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!uiState.hasUsagePermission) {
                    // Explanatory card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SpaceCardElevated)
                            .border(1.dp, SpaceCardBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yêu cầu quyền UsageStats từ Android",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Để thống kê chính xác thời gian thực dùng cho Mạng xã hội, Học tập và Trò chơi, bạn cần bật quyền 'Truy cập dữ liệu sử dụng' trong Cài đặt hệ thống.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onOpenUsageSettings,
                                modifier = Modifier.fillMaxWidth().testTag("open_usage_settings_btn"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mở Cài đặt Android để cấp quyền", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // Real Categories list
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.usageCategories.forEach { cat ->
                            CategoryUsageItem(
                                title = cat.categoryName,
                                minutes = cat.minutes,
                                percentage = cat.percentage
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Background Foreground Service Status & Control Card
        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (uiState.isBackgroundServiceRunning) NeonEmerald else SpaceCardBorder
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = if (uiState.isBackgroundServiceRunning) "🟢" else "⚪", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dịch vụ chạy ngầm GPS & Giờ dùng",
                            style = MaterialTheme.typography.titleSmall,
                            color = if (uiState.isBackgroundServiceRunning) NeonEmerald else TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.isBackgroundServiceRunning) NeonEmerald.copy(alpha = 0.2f) else SpaceCardElevated)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (uiState.isBackgroundServiceRunning) "Đang chạy" else "Đang tắt",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (uiState.isBackgroundServiceRunning) NeonEmerald else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Android Foreground Service cập nhật vị trí GPS vệ sinh và thống kê liên tục khi khóa máy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onToggleBackgroundService,
                    modifier = Modifier.fillMaxWidth().testTag("toggle_bg_service_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isBackgroundServiceRunning) SpaceCardElevated else NeonEmerald,
                        contentColor = if (uiState.isBackgroundServiceRunning) NeonRose else Color.Black
                    )
                ) {
                    Text(
                        text = if (uiState.isBackgroundServiceRunning) "Dừng dịch vụ chạy ngầm" else "Kích hoạt chạy ngầm liên tục 🚀",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Detailed Per-App Usage Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHI TIẾT TỪNG ỨNG DỤNG ĐÃ DÙNG (${uiState.detailedAppUsage.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Text(
                        text = "Hôm nay",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (uiState.detailedAppUsage.isEmpty()) {
                    Text(
                        text = "Chưa ghi nhận ứng dụng nào sử dụng trên 1 phút.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uiState.detailedAppUsage.forEach { app ->
                            DetailedAppUsageRow(app = app)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CategoryUsageItem(
    title: String,
    minutes: Int,
    percentage: Float
) {
    val barColor = when (title) {
        "Mạng xã hội" -> NeonViolet
        "Học tập" -> NeonCyan
        "Trò chơi" -> NeonRose
        else -> NeonEmerald
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Text(text = "$minutes phút", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(SpaceCardElevated)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percentage.coerceIn(0.05f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
fun DetailedAppUsageRow(app: com.example.data.model.DetailedAppUsage) {
    val categoryColor = when (app.category) {
        "Mạng xã hội" -> NeonViolet
        "Học tập" -> NeonCyan
        "Trò chơi" -> NeonRose
        else -> NeonEmerald
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SpaceCardElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(categoryColor.copy(alpha = 0.2f))
                    .border(1.dp, categoryColor.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.appName.take(1).uppercase(),
                    color = categoryColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = app.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = categoryColor,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${app.minutesUsed} phút",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Hôm nay",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

/** "12:05" — dong ho Pomodoro phai nhay duoc tung giay tren man hinh. */
private fun formatFocusClock(totalSeconds: Int): String {
    val s = totalSeconds.coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}

