package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PairingStatus
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientProgressBar
import com.example.ui.components.PremiumChip
import com.example.ui.components.PremiumTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatTile
import com.example.ui.theme.Premium
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.VesperaUiState

/**
 * "Hành trình & Vùng an toàn" — location intelligence.
 *
 * A stylised route trace, a chronological journey timeline, frequent places
 * and a safe-zone (geofence) manager with live radius control.
 */
@Composable
fun LocationHistoryScreen(
    uiState: VesperaUiState,
    onBack: () -> Unit,
    onToggleLocationHistory: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()

    // `pairings/<id>/location_history` là collection CHUNG của cả hai máy. Đọc
    // thẳng ra danh sách sẽ vẽ hành trình của con lẫn ping của máy phụ huynh,
    // nên phải lọc theo chủ nhân của thiết bị đang được theo dõi ở đây.
    val isPaired = uiState.activePairing?.status == PairingStatus.PAIRED
    val isChild = uiState.userProfile?.role == com.example.data.model.UserRole.CHILD
    val partnerName = if (isChild) uiState.activePairing?.parentName.orEmpty().ifEmpty { "Phụ huynh" }
                      else uiState.activePairing?.childName.orEmpty().ifEmpty { "Học sinh" }
    val trackedUserId = if (isPaired) uiState.partnerLocation?.userId else uiState.myLocation?.userId
    val history = trackedUserId?.let { id ->
        uiState.locationHistory.filter { it.userId == id }
    } ?: uiState.locationHistory

    val todayStr = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
        .format(java.util.Date())

    // Danh sach vung an toan da bi xoa khoi man hinh nay: no tung la chuoi baa
    // ("Trường học", "Nhà riêng", "Nhà bà ngoại") chi ton tai trong bo nho.
    // May chu chua co collection nao cho vung an toan, nen o day khong con
    // bien nao de "minh hoa" — thay vao do la the trang thai that ben duoi.

    val stopsToday = history.count { it.dateStr == todayStr }
    val routeFixCount = history.count { it.latitude != 0.0 || it.longitude != 0.0 }
    val maxSpeed = history.maxOfOrNull { it.speed } ?: 0f
    // CHỈ tính các điểm có telemetry thật (batteryLevel > 0). LocationData mặc định
    // batteryLevel = 0 khi chưa gửi pin, tính gộp cả điểm 0 sẽ kéo trung bình thấp hơn thực.
    val batteryReadings = history.map { it.batteryLevel }.filter { it > 0 }
    val avgBattery = if (batteryReadings.isEmpty()) 0 else batteryReadings.average().toInt()
    // Chi xep hang nhung diem THAT SU co ten. `placeName` rong nghia la may
    // do duoc toa do nhung chua dich duoc thanh dia chi — gop chung vao thi
    // sinh ra mot "dia diem quen thuoc" khong ten voi dem luu diem lon nhat.
    val frequentPlaces = history.filter { it.placeName.isNotBlank() }
        .groupBy { it.placeName }
        .mapValues { it.value.size }
        .toList()
        .sortedByDescending { it.second }
        .take(4)
    val unnamedFixes = history.count { it.placeName.isBlank() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("location_history_screen")
    ) {
        PremiumTopBar(
            title = if (isPaired) "Hành trình của $partnerName" else "Hành trình của máy này",
            subtitle = if (isPaired) "Lịch sử di chuyển & vùng an toàn"
                       else "Chưa ghép nối — đây là lịch sử của CHÍNH máy bạn",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Bao cao trang thai ghi that, de nguoi dung biet cong tac dang bat hay
        // da tat — khong chi thay ngon den mau o phia duoi.
        if (!uiState.isLocationHistoryEnabled) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = Premium.amber.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = Premium.amber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Đang TẮT ghi lịch sử — danh sách dưới đây là điểm cũ, " +
                            "máy sẽ không lưu thêm cho tới khi bật lại.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Premium.amber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Route trace ───────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.accent, contentPadding = 0.dp) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Premium.glowPrimary.copy(alpha = 0.35f),
                                Premium.glowSecondary.copy(alpha = 0.25f)
                            )
                        )
                    )
            ) {
                val accentInk = Premium.accent
                val accentViolet = Premium.violet
                val accentEmerald = Premium.emerald
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    // soft grid
                    var x = 0f
                    while (x < w) {
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(x, 0f), Offset(x, h), 1f)
                        x += w / 8f
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, y), Offset(w, y), 1f)
                        y += h / 5f
                    }

                    // Tuyen duong THAT: toa do do duoc quy ve vung hien thi.
                    // Truoc day khi co <2 diem, mang ve 4 diem bieu dien — nguoi
                    // doc nhàm do la hanh trinh cua con trong khi may chua ghi
                    // du mot diem. Thieu du lieu thi de trong, khong boi them.
                    val fixes = history.sortedBy { it.timestamp }
                        .filter { it.latitude != 0.0 || it.longitude != 0.0 }
                        .take(24)
                    val lats = fixes.map { it.latitude }
                    val lngs = fixes.map { it.longitude }
                    val latMin = lats.minOrNull() ?: 0.0
                    val latMax = lats.maxOrNull() ?: 0.0
                    val lngMin = lngs.minOrNull() ?: 0.0
                    val lngMax = lngs.maxOrNull() ?: 0.0
                    val latSpan = latMax - latMin
                    val lngSpan = lngMax - lngMin
                    val points = if (fixes.size < 2) emptyList() else fixes.mapIndexed { index, entry ->
                        val fx = if (lngSpan > 1e-9)
                            0.08f + 0.84f * ((entry.longitude - lngMin) / lngSpan).toFloat()
                        else 0.08f + 0.84f * (index.toFloat() / (fixes.size - 1))
                        val fy = if (latSpan > 1e-9)
                            0.88f - 0.70f * ((entry.latitude - latMin) / latSpan).toFloat()
                        else 0.5f
                        Offset(fx * w, fy * h)
                    }
                    if (points.size >= 2) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.20f),
                        style = Stroke(width = 12f, cap = StrokeCap.Round)
                    )
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(accentInk, accentViolet)),
                        style = Stroke(width = 5f, cap = StrokeCap.Round)
                    )
                    points.forEachIndexed { index, point ->
                        val isLast = index == points.lastIndex
                        drawCircle(
                            color = if (isLast) accentEmerald else Color.White,
                            radius = if (isLast) 9f else 5.5f,
                            center = point
                        )
                        if (isLast) {
                            drawCircle(
                                color = accentEmerald.copy(alpha = 0.35f),
                                radius = 18f,
                                center = point
                            )
                        }
                    }
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    PremiumChip(
                        text = if (uiState.isLocationHistoryEnabled) "Đang ghi lịch sử" else "Tạm dừng ghi",
                        tint = if (uiState.isLocationHistoryEnabled) Premium.emerald else Premium.amber,
                        filled = true
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    PremiumChip(
                        text = "Hôm nay: $stopsToday điểm dừng",
                        tint = Premium.primaryBright,
                        filled = true
                    )
                }

                if (routeFixCount < 2) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Chưa đủ điểm để vẽ tuyến đường",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Máy mới lưu được $routeFixCount điểm định vị có toạ độ hợp lệ. " +
                                "Đường nối sẽ hiện ra khi thiết bị di chuyển và ghi thêm điểm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── History toggle ────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Premium.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = Premium.primaryBright, modifier = Modifier.size(19.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Ghi lịch sử di chuyển",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tự động lưu lại khi di chuyển hơn 5 mét",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = uiState.isLocationHistoryEnabled,
                    onCheckedChange = { onToggleLocationHistory(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Premium.primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── KPIs ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Place,
                label = "ĐIỂM DỪNG",
                value = "$stopsToday",
                hint = "Hôm nay",
                tint = Premium.primaryBright
            )
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Speed,
                label = "TỐC ĐỘ MAX",
                value = if (maxSpeed > 0f) "${maxSpeed.toInt()} km/h" else "—",
                hint = if (history.isEmpty()) "Chưa có điểm định vị nào"
                    else "Trong toàn bộ lịch sử",
                tint = if (maxSpeed > 0f) Premium.amber else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.BatteryChargingFull,
                label = "PIN TRUNG BÌNH",
                value = if (avgBattery > 0) "$avgBattery%" else "—",
                hint = "Trung bình các điểm có số pin",
                tint = if (avgBattery > 0) Premium.emerald else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Frequent places ───────────────────────────────────────────────
        if (frequentPlaces.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader(
                        title = "Địa điểm quen thuộc",
                        subtitle = "Xếp hạng theo tần suất ghé thăm",
                        icon = Icons.Default.Place,
                        tint = Premium.accent
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val maxCount = frequentPlaces.first().second.coerceAtLeast(1)
                    frequentPlaces.forEach { (place, count) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = place,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                text = "$count lần",
                                style = MaterialTheme.typography.labelSmall,
                                color = Premium.primaryBright,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        GradientProgressBar(
                            progress = count.toFloat() / maxCount.toFloat(),
                            colors = listOf(Premium.accent, Premium.violet),
                            height = 6.dp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        } else if (unnamedFixes > 0) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SectionHeader(
                        title = "Địa điểm quen thuộc",
                        subtitle = "Chưa dịch được điểm nào thành tên",
                        icon = Icons.Default.Place,
                        tint = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "$unnamedFixes điểm định vị đã ghi được tọa độ nhưng chưa tra ra " +
                            "địa chỉ, nên chưa xếp hạng được địa điểm nào. Cần quyền vị trí " +
                            "và mạng để dịch tọa độ thành tên.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── Safe zones: trang thai that ───────────────────────────────────
        // Truoc day the nay dung danh sach ba ("Trường học 300m", "Nhà riêng
        // 250m", "Nhà bà ngoại 400m") co cong tac bat/tat, thanh keo ban kinh
        // va nut "Them vung" — tat ca chi sua mot danh sach trong bo nho, roi
        // bi xoan khi thoat man hinh. May chu khong co collection vung an toan
        // nao, va app cung khong dang ki GeofencingClient o dau. Giu lai mot
        // noi dung thang: chua co, va chua co canh bao.
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Vùng an toàn",
                    subtitle = "Chưa có vùng nào trên máy này",
                    icon = Icons.Default.LocationOn,
                    tint = TextMuted
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "App chưa lưu vùng an toàn nào và chưa bật cảnh báo ra khỏi vùng. " +
                        "Màn hình này chỉ hiển thị những điểm định vị thật mà máy đã gửi, " +
                        "cùng nhãn nơi đang đứng lấy từ địa chỉ thực.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ── Timeline ──────────────────────────────────────────────────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Dòng thời gian di chuyển",
                    subtitle = if (history.isEmpty()) "Chưa có dữ liệu" else "${history.size} mốc gần nhất",
                    icon = Icons.Default.Timeline,
                    tint = Premium.violet
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (history.isEmpty()) {
                    Text(
                        text = if (isPaired)
                            "Lịch sử sẽ xuất hiện sau khi máy con di chuyển và dịch vụ định vị nền gửi dữ liệu về."
                        else
                            "Chưa ghép nối nên đây là lịch sử của chính máy này. Máy chưa ghi lại điểm nào — " +
                                "danh sách sẽ có dữ liệu sau khi thiết bị di chuyển hơn 5 mét khi đang bật ghi lịch sử.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                } else {
                    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(1000L)
                            nowMillis = System.currentTimeMillis()
                        }
                    }
                    val vnTz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
                    val vnCal = java.util.Calendar.getInstance(vnTz)
                    history.take(12).forEachIndexed { index, entry ->
                        // `timestamp` = ping CUOI CUNG cua diem dung, `duration` = tong
                        // thoi gian o do da cong don luc ghi, `timeStr` = gio ping DAU
                        // TIEN. Diem moi nhat cua mot chu van đang mở nên tinh den bay gio.
                        val newerSameOwner = history.take(index).any { it.userId == entry.userId }
                        val ongoing = !newerSameOwner
                        val endMillis = if (ongoing) nowMillis else entry.timestamp
                        val stayMillis = entry.duration.coerceAtLeast(0L) +
                            if (ongoing) (nowMillis - entry.timestamp).coerceAtLeast(0L) else 0L
                        val staySecs = (stayMillis / 1000L).coerceAtLeast(0L)
                        val stayStr = when {
                            staySecs / 3600L > 0 -> "${staySecs / 3600L}g ${staySecs / 60L % 60}p"
                            staySecs / 60L > 0 -> "${staySecs / 60L}p ${staySecs % 60L}s"
                            else -> "${staySecs}s"
                        }
                        vnCal.timeInMillis = endMillis
                        val endStr = String.format(
                            java.util.Locale.getDefault(), "%02d:%02d:%02d",
                            vnCal.get(java.util.Calendar.HOUR_OF_DAY),
                            vnCal.get(java.util.Calendar.MINUTE),
                            vnCal.get(java.util.Calendar.SECOND)
                        )
                        Row(modifier = Modifier.fillMaxWidth()) {
                            // Rail
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(26.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(if (index == 0) Premium.primaryBright else Premium.violet.copy(alpha = 0.55f))
                                )
                                if (index != history.take(12).lastIndex) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(58.dp)
                                            .background(Premium.glassBorder.copy(alpha = 0.5f))
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = entry.placeName.ifEmpty { "Không rõ địa điểm — chỉ có tọa độ" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // Gio ket qua o dong "Tu ... den ..." ben duoi. In
                                // muon o goi cung bi nut SOS che mat va lap lai van
                                // mot con so.
                                Text(
                                    text = "Từ ${entry.timeStr.ifEmpty { "--:--" }} đến $endStr • " +
                                        if (ongoing) "đang ở đây $stayStr" else "ở đây $stayStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Premium.emerald
                                )
                                Text(
                                    text = entry.dateStr.ifEmpty { "" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // batteryLevel == 0 = điểm không có telemetry → "—" xám, không in "0%"
                                    PremiumChip(
                                        text = if (entry.batteryLevel > 0) "${entry.batteryLevel}%" else "—",
                                        tint = if (entry.batteryLevel > 0) Premium.emerald else TextMuted
                                    )
                                    // Sạc pin: icon vector Bolt thay cho ký hiệu
                                    // emoji trước đây, đứng ngay cạnh chip %.
                                    if (entry.batteryLevel > 0 && entry.isCharging) {
                                        Icon(
                                            imageVector = Icons.Default.Bolt,
                                            contentDescription = null,
                                            tint = Premium.emerald,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    PremiumChip(text = "${entry.speed.toInt()} km/h", tint = Premium.amber)
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
