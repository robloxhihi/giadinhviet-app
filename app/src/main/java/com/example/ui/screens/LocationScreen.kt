package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PairingStatus
import com.example.data.model.UserRole
import com.example.data.model.hasRealFix
import com.example.service.PermissionManager
import com.example.service.blocker.ConsentPolicy
import com.example.service.map.FixPrecision
import com.example.ui.components.BentoCard
import com.example.ui.components.BentoStatCard
import com.example.ui.components.GoogleMapsStudentLocationCard
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocationScreen(
    uiState: VesperaUiState,
    onRefreshLocation: () -> Unit,
    onRequestPrivacyClick: () -> Unit,
    onSendZenlyBump: () -> Unit,
    onToggleLocationHistory: (Boolean) -> Unit,
    onOpenLocationHistory: () -> Unit = {},
    onRecheckPermissions: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationPermissionNames = PermissionManager.runtimePermissionsFor("location")
    val locationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // Ket qua tra ve LECH thoi diem cap nhat quyen, nen phai doc lai tu
        // Android roi moi do GPS: neu khong thi man hinh van bao "chưa cấp
        // quyền" ngay sau khi nguoi dung vua bam "Cho phép".
        onRecheckPermissions()
        if (result.values.any { it }) onRefreshLocation()
    }
    val isChild = uiState.userProfile?.role == UserRole.CHILD
    val isPaired = uiState.activePairing != null && uiState.activePairing.status == PairingStatus.PAIRED
    val partnerLoc = uiState.partnerLocation
    val myLoc = uiState.myLocation
    val partnerName = if (isChild) uiState.activePairing?.parentName.orEmpty().ifEmpty { "Phụ huynh" }
                      else uiState.activePairing?.childName.orEmpty().ifEmpty { "Học sinh" }

    val isPrivacy = partnerLoc?.isPrivacyActive == true
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    // Tuyen duong di chuyen gan day (cu -> moi) de ve polyline tren ban do.
    // Loc bo moi diem 0/0 hoac ngoai khoang hop le — khong ve diem bia.
    // `pairings/<id>/location_history` chua ping cua CA HAI may, nen the "Thiet
    // bi nay" phai chi dung diem cua chinh may dang mo app; neu khong phu huynh
    // thay duong ke cua con ve chen vao ban do cua minh.
    val selfUserId = myLoc?.userId.orEmpty()
    val selfHistory = uiState.locationHistory.filter { it.userId == selfUserId }
    val historyOwners = uiState.locationHistory.map { it.userId }.distinct().size
    val routePoints: List<Pair<Double, Double>> = selfHistory
        .sortedBy { it.timestamp }
        .filter { isValidFix(it.latitude, it.longitude) }
        .map { it.latitude to it.longitude }

    // "Da o day" cua the ban do = thoi gian diem dung gan nhat, tinh ca phan
    // duration da cong don trong luc ghi — neu khong no luon bat dau tu 0 sau
    // moi lan ping du tre o nguyen cho do hang gio.
    val selfStayMillis = selfHistory.firstOrNull()?.let {
        (System.currentTimeMillis() - it.timestamp).coerceAtLeast(0L) + it.duration.coerceAtLeast(0L)
    } ?: -1L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Định vị GPS",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isPaired) "Theo dõi máy đã ghép nối" else "Chưa ghép nối — đây là vị trí của CHÍNH máy bạn",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection Badge — chi la trang thai, khong con nut doi demo
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isPaired) NeonEmerald.copy(alpha = 0.2f) else NeonAmber.copy(alpha = 0.2f))
                        .border(1.2.dp, if (isPaired) NeonEmerald else NeonAmber, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isPaired) "Đã kết nối" else "Chưa ghép nối",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPaired) NeonEmerald else NeonAmber,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onRefreshLocation,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpaceCardElevated,
                        contentColor = FacebookBlue
                    ),
                    modifier = Modifier.size(38.dp).testTag("refresh_location_btn"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Làm mới",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Journey intelligence entry point
        com.example.ui.components.GlassButton(
            text = "Hành trình & Vùng an toàn",
            icon = Icons.Default.History,
            onClick = onOpenLocationHistory,
            tint = NeonCyan,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LocationScanCard(
            hasPermission = uiState.hasLocationPermission,
            servicesEnabled = uiState.isLocationServicesEnabled,
            vpnActive = uiState.isVpnActive,
            hasFix = myLoc != null && myLoc.hasRealFix,
            accuracyMeters = myLoc?.accuracy ?: 0f,
            lastUpdatedAt = myLoc?.updatedAt ?: 0L,
            onGrant = { locationPermissionLauncher.launch(locationPermissionNames) },
            onOpenLocationSettings = {
                runCatching { context.startActivity(PermissionManager.locationServicesIntent()) }
            },
            onOpenAppSettings = {
                runCatching {
                    context.startActivity(PermissionManager.backgroundLocationSettingsIntent(context))
                }
            },
            onRescan = {
                onRecheckPermissions()
                onRefreshLocation()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // UNPAIRED STATE: Concise, professional status card (NO fake demo data!)

        if (!isPaired) {
            BentoCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = NeonAmber.copy(alpha = 0.6f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NeonAmber.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Chưa ghép nối học sinh",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Nhập mã học sinh tại màn hình Tổng quan để theo dõi vị trí của con.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Display THIS device's REAL GPS location on the real map.
            // Khong con gia du lieu: thieu fix => toa do that (0.0/0.0) va the hien
            // trang thai "dang do GPS" thay vi dat marker o mot noi biu.
            val activeLoc = myLoc
            val hasSelfFix = activeLoc != null && activeLoc.hasRealFix
            val displayLat = activeLoc?.latitude ?: 0.0
            val displayLng = activeLoc?.longitude ?: 0.0
            val displayAcc = activeLoc?.accuracy ?: 0f
            val displaySpd = activeLoc?.speed ?: 0.0f
            val displayBat = activeLoc?.batteryLevel ?: 0
            val displayChg = activeLoc?.isCharging ?: false

            Text(
                text = "Vị trí máy này — chưa có máy nào đang được theo dõi",
                style = MaterialTheme.typography.labelSmall,
                color = FacebookBlue,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            GoogleMapsStudentLocationCard(
                studentName = "Thiết bị này",
                latitude = displayLat,
                longitude = displayLng,
                safeZoneName = when {
                    !hasSelfFix -> "Chưa dò được GPS"
                    displaySpd > 5f -> "Đang di chuyển"
                    else -> "Đang định vị"
                },
                batteryLevel = displayBat,
                isCharging = displayChg,
                speedKmh = displaySpd,
                isPrivacyActive = false,
                routePoints = routePoints,
                stayDurationMillis = selfStayMillis,
                lastUpdatedAt = activeLoc?.updatedAt ?: 0L,
                accuracyMeters = displayAcc,
                hasRealFix = hasSelfFix,
                onRetryLocation = onRefreshLocation
            )

            Spacer(modifier = Modifier.height(14.dp))

        } else {
            // PAIRED STATE: Display partner's real location
            if (isPrivacy) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NeonAmber.copy(alpha = 0.15f))
                        .border(1.dp, NeonAmber, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonAmber,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Khung giờ riêng tư",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tọa độ GPS đang được ẩn an toàn theo thỏa thuận.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            if (partnerLoc != null) {
                // pairing/<id>/location_history cat ping cua CA HAI may. Ve tran no
                // duoi ten mot nguoi la ghep du lieu sai nguoi, nen loc theo userId
                // cua may dang hien; khong co diem nao cua ho thi khong ve gi ca.
                val partnerHistory = uiState.locationHistory.filter { it.userId == partnerLoc.userId }
                val partnerRoute = partnerHistory
                    .sortedBy { it.timestamp }
                    .filter { isValidFix(it.latitude, it.longitude) }
                    .map { it.latitude to it.longitude }
                val partnerStayMillis = partnerHistory.maxByOrNull { it.timestamp }?.let { newest ->
                    (System.currentTimeMillis() - newest.timestamp).coerceAtLeast(0L) +
                        newest.duration.coerceAtLeast(0L)
                } ?: -1L

                GoogleMapsStudentLocationCard(
                    studentName = partnerName,
                    latitude = partnerLoc.latitude,
                    longitude = partnerLoc.longitude,
                    safeZoneName = partnerLoc.currentSafeZone.ifEmpty { "Chưa xác định được khu vực" },
                    batteryLevel = partnerLoc.batteryLevel,
                    isCharging = partnerLoc.isCharging,
                    speedKmh = partnerLoc.speed,
                    isPrivacyActive = isPrivacy,
                    routePoints = partnerRoute,
                    stayDurationMillis = partnerStayMillis,
                    lastUpdatedAt = partnerLoc.updatedAt,
                    accuracyMeters = partnerLoc.accuracy,
                    headingDegrees = null,
                    // May ben kia chua do duoc GPS => the "dang do GPS" + nut Thu lai,
                    // khong bao gio dat marker o toa do bia.
                    hasRealFix = partnerLoc.hasRealFix,
                    onRetryLocation = onRefreshLocation
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Zenly Bump Action
                // Nhat nay bay qua document `pairings/{id}` (truong lastBumpAt),
                // khong phai bo dem trong bo nho may bam nut nhu ban cu.
                Button(
                    onClick = onSendZenlyBump,
                    modifier = Modifier.fillMaxWidth().testTag("zenly_bump_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpaceCardElevated,
                        contentColor = NeonAmber
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Chạm tay Zenly Bump • " +
                            bumpStateLabel(uiState.lastBumpAt, uiState.lastBumpByName, System.currentTimeMillis()),
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // Paired, but partner has not sent location yet
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = null,
                            tint = FacebookBlue,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Đang đợi tín hiệu GPS",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Thiết bị học sinh cần bật GPS và mở ứng dụng để cập nhật.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRefreshLocation,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FacebookBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Làm mới ngay", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Spacer(modifier = Modifier.height(10.dp))

        // Nút tải bản đồ offline 34 tỉnh thành & Toàn quốc (TẢI THẬT)
        var showOfflineMapDialog by remember { mutableStateOf(false) }
        val downloadedProvinces = remember { mutableStateOf(setOf<String>()) }
        val downloadProgress = remember { mutableStateOf<Pair<Int, Int>?>(null) }
        val downloadingProvince = remember { mutableStateOf<String?>(null) }
        val lastDownloadMessage = remember { mutableStateOf<String?>(null) }
        val cancelDownload = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
        val scope = rememberCoroutineScope()

        Button(
            onClick = { showOfflineMapDialog = true },
            modifier = Modifier.fillMaxWidth().testTag("offline_map_download_btn"),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SpaceCardElevated,
                contentColor = NeonCyan
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Icon(imageVector = Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Tải bản đồ offline 34 tỉnh thành & Toàn quốc", fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lịch sử di chuyển (siêu nhỏ gọn • 5 lần)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lịch sử di chuyển",
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (historyOwners > 1)
                    "${uiState.locationHistory.size} bản ghi · ${historyOwners} máy"
                else
                    "${uiState.locationHistory.size} bản ghi",
                style = MaterialTheme.typography.labelSmall,
                color = FacebookBlue,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Cong tac ghi lich su dat ngay o tab Vi tri. Truoc day no nur o man
        // "Hanh trinh" va lop khac che mat nen nguoi dung khong bat/tat duoc,
        // cung khong thay trang thai hien tai la gi.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SpaceCardElevated)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (uiState.isLocationHistoryEnabled)
                    if (isPaired) "Đang tự ghi khi con di chuyển"
                    else "Đang tự ghi vị trí máy này khi di chuyển"
                else
                    "Đã TẮT ghi — không lưu thêm điểm mới",
                style = MaterialTheme.typography.labelSmall,
                color = if (uiState.isLocationHistoryEnabled) NeonEmerald else NeonAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.isLocationHistoryEnabled,
                onCheckedChange = onToggleLocationHistory
            )
        }

        // Ai la nguoi tat/bat, tu luc nao — in thang ra de khong ai tat dinh vi
        // tham duoc nua. Truoc day co quan nay nam trong SharedPreferences nen
        // ben kia tat la ben nay khong he biet.
        uiState.activePairing?.let { p ->
            if (p.historyChangedByRole != null && p.historyChangedAt > 0L) {
                Text(
                    text = ConsentPolicy.label(p.historyChangedByRole) +
                        (if (p.locationHistoryEnabled) " đã bật lại" else " đã TẮT") +
                        " ghi lịch sử lúc " +
                        java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.US)
                            .format(java.util.Date(p.historyChangedAt)) +
                        " — máy bên kia cũng thấy dòng này.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (uiState.locationHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpaceCardElevated)
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có lịch sử di chuyển.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        } else {
            var currentEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000L)
                    currentEpochMillis = System.currentTimeMillis()
                }
            }

            val vnTz = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            val vnCalendar = java.util.Calendar.getInstance(vnTz)

            // List cua pairing co the tron ping hai may -> ghi ten chu nhan cua
            // tung diem de phu huynh khong doc nhàm. List mot chu thi khong lam dai dong.
            val mixedHistory = historyOwners > 1

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                uiState.locationHistory.forEachIndexed { index, entry ->
                    // `timestamp` = ping CUOI CUNG cua diem dung, `duration` = tong
                    // thoi gian o do da duoc cong don luc ghi, `timeStr` = gio ping
                    // DAU TIEN. Lay (now - timestamp) lam "da o day" la hai so mau
                    // thuan nhau tren cung mot dong ("Tu 21:33 den 23:14" di kem
                    // "2p 13s"). Tinh dung ban chat cua diem dung.
                    val newerSameOwner = uiState.locationHistory.take(index)
                        .any { it.userId == entry.userId }
                    val ongoing = !newerSameOwner
                    val endTimeMillis = if (ongoing) currentEpochMillis else entry.timestamp
                    val stayMillis = entry.duration.coerceAtLeast(0L) +
                        if (ongoing) (currentEpochMillis - entry.timestamp).coerceAtLeast(0L) else 0L

                    val elapsedSeconds = (stayMillis / 1000L).coerceAtLeast(0L)
                    val durationMins = elapsedSeconds / 60L
                    val durationSecs = elapsedSeconds % 60L
                    val durationHours = durationMins / 60L
                    val durationStr = when {
                        durationHours > 0 -> "${durationHours}g ${durationMins % 60}p"
                        durationMins > 0 -> "${durationMins}p ${durationSecs}s"
                        else -> "${durationSecs}s"
                    }

                    vnCalendar.timeInMillis = endTimeMillis
                    val endH = vnCalendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val endM = vnCalendar.get(java.util.Calendar.MINUTE)
                    val endS = vnCalendar.get(java.util.Calendar.SECOND)
                    val endFormattedTime =
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", endH, endM, endS)

                    // Siêu nhỏ gọn (gấp 5 lần so với trước)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = SpaceCardElevated),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, SpaceCardBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${index + 1}. " +
                                            entry.placeName.ifEmpty { "Không rõ địa điểm (chỉ có tọa độ)" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                if (mixedHistory) {
                                    Text(
                                        text = "Máy: ${entry.userName.ifEmpty { "chưa ghi danh" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "Từ ${entry.timeStr.ifEmpty { "00:00:00" }} đến $endFormattedTime • Đã ở đây: $durationStr",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = NeonEmerald,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = entry.dateStr.ifEmpty { "Hôm nay" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    fontSize = 9.sp
                                )
                                Text(
                                    text = "Lat/Lng",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonViolet,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Offline Map Download Dialog (34 provinces & Vietnam) — tải tile THẬT
        if (showOfflineMapDialog) {
            val provinces = com.example.service.map.OfflineMapDownloader.provinces

            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    if (downloadingProvince.value == null) showOfflineMapDialog = false
                },
                containerColor = com.example.ui.theme.DialogSurface,
                title = {
                    Text(text = "Tải bản đồ Offline 34 Tỉnh Thành", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Tải ảnh bản đồ vệ tinh (lớp Google, z11–z13) để xem khi không có Internet. " +
                                    "Quá trình tải thật — sẽ báo đúng số ô thành công/lỗi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        downloadProgress.value?.let { (done, total) ->
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { if (total == 0) 0f else done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                            )
                            Text(
                                "Đang tải ${downloadingProvince.value ?: ""}: $done/$total ô",
                                fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold
                            )
                        }
                        lastDownloadMessage.value?.let {
                            Text(it, fontSize = 11.sp, color = NeonEmerald, fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Tải toàn quốc (tải thật, tuần tự từng tỉnh; bấm lại để hủy)
                        Button(
                            onClick = {
                                if (downloadingProvince.value != null) {
                                    cancelDownload.set(true)
                                } else {
                                    cancelDownload.set(false)
                                    lastDownloadMessage.value = null
                                    scope.launch {
                                        var ok = 0
                                        var fail = 0
                                        for (prov in provinces) {
                                            if (cancelDownload.get()) break
                                            downloadingProvince.value = prov.name
                                            downloadProgress.value = 0 to 0
                                            val res = com.example.service.map.OfflineMapDownloader.downloadRegion(
                                                context = context,
                                                region = prov,
                                                cancelFlag = cancelDownload,
                                                onProgress = { d, t -> downloadProgress.value = d to t }
                                            )
                                            if (res.downloaded > 0) {
                                                ok++
                                                downloadedProvinces.value = downloadedProvinces.value + prov.name
                                            } else fail++
                                        }
                                        lastDownloadMessage.value = "Toàn quốc: đã tải $ok tỉnh, $fail tỉnh lỗi."
                                        downloadProgress.value = null
                                        downloadingProvince.value = null
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (downloadingProvince.value != null) NeonRose else FacebookBlue
                            )
                        ) {
                            Icon(
                                imageVector = if (downloadingProvince.value != null) Icons.Default.Stop else Icons.Default.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (downloadingProvince.value != null) "Hủy tải toàn quốc" else "Tải toàn quốc Việt Nam (Offline)",
                                fontWeight = FontWeight.Bold, fontSize = 12.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        provinces.forEach { prov ->
                            val isDownloaded = downloadedProvinces.value.contains(prov.name)
                            val isThisDownloading = downloadingProvince.value == prov.name
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SpaceCardElevated)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = prov.name, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Button(
                                    onClick = {
                                        if (downloadingProvince.value != null) return@Button
                                        downloadingProvince.value = prov.name
                                        downloadProgress.value = 0 to 0
                                        lastDownloadMessage.value = null
                                        scope.launch {
                                            val res = com.example.service.map.OfflineMapDownloader.downloadRegion(
                                                context = context,
                                                region = prov,
                                                onProgress = { d, t -> downloadProgress.value = d to t }
                                            )
                                            if (res.isSuccess || res.downloaded > 0) {
                                                downloadedProvinces.value = downloadedProvinces.value + prov.name
                                            }
                                            lastDownloadMessage.value = "${prov.name}: ${res.message}"
                                            downloadProgress.value = null
                                            downloadingProvince.value = null
                                        }
                                    },
                                    shape = RoundedCornerShape(4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when {
                                            isThisDownloading -> NeonCyan.copy(alpha = 0.3f)
                                            isDownloaded -> NeonEmerald.copy(alpha = 0.2f)
                                            else -> FacebookBlue
                                        },
                                        contentColor = if (isDownloaded) NeonEmerald else Color.White
                                    ),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) {
                                    if (isDownloaded) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                    Text(
                                        text = when {
                                            isThisDownloading -> "Đang tải…"
                                            isDownloaded -> "Đã tải"
                                            else -> "Tải"
                                        },
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { if (downloadingProvince.value == null) showOfflineMapDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = FacebookBlue)
                    ) {
                        Text("Đóng")
                    }
                }
            )
        }
    }
}

/**
 * Toa do nay den tu cam bien that khong? 0/0 la "chua do duoc GPS"; ngoai khoang
 * trai dat la du lieu hong. Dung cho viec loc tuyen du dinh ve polyline.
 */
private fun isValidFix(latitude: Double, longitude: Double): Boolean =
    !(latitude == 0.0 && longitude == 0.0) &&
        latitude >= -90.0 && latitude <= 90.0 &&
        longitude >= -180.0 && longitude <= 180.0

/**
 * Nhan "chạm tay" doc tu document ghep noi. Man Vi tri va man Tong quan dung
 * chung ham nay de hai noi khong in ra hai cach noi khac nhau cung mot so lieu.
 */
internal fun bumpStateLabel(at: Long, byName: String, nowMillis: Long): String {
    if (at <= 0L) return "chưa ai chạm lần nào"
    val who = byName.ifEmpty { "máy bên kia" }
    val mins = (nowMillis - at) / 60_000L
    val ago = when {
        mins < 1 -> "vừa chạm"
        mins < 60 -> "${mins} phút trước"
        mins < 1440 -> "${mins / 60} giờ trước"
        else -> "hơn một ngày trước"
    }
    return "$who: $ago"
}

/**
 * Trạng thái thật của việc dò vị trí trên CHÍNH máy này.
 *
 * Man Vi tri tung chi in "Chưa dò được GPS" khi nguoi dung tu chot quyen, nen
 * khong con duong nao de mo dung ra: thieu quyen, tat dich vu dinh vi cua may
 * va chua co fix la ba chuyen khac nhau, phai noi tung cai va co nut xu ly
 * tung cai. Toa do mau thi tuyet doi khong co — thieu fix la in trang thai
 * "dang do", khong dat marker.
 */
@Composable
private fun LocationScanCard(
    hasPermission: Boolean,
    servicesEnabled: Boolean?,
    vpnActive: Boolean?,
    hasFix: Boolean,
    accuracyMeters: Float,
    lastUpdatedAt: Long,
    onGrant: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRescan: () -> Unit
) {
    val state = when {
        !hasPermission -> ScanState(
            tint = NeonAmber,
            title = "Chưa cấp quyền vị trí cho app",
            detail = "Máy chưa cho phép đọc GPS nên không có tọa độ nào cả. App không vẽ điểm mẫu " +
                "và không suy vị trí từ địa chỉ IP."
        )

        servicesEnabled == false -> ScanState(
            tint = NeonAmber,
            title = "Quyền đã cấp, nhưng máy đang tắt dịch vụ định vị",
            detail = "Bật Định vị (Location) trong Cài đặt của máy thì app mới dò được GPS."
        )

        servicesEnabled == null -> ScanState(
            tint = TextMuted,
            title = "Chưa kiểm tra được dịch vụ định vị của máy",
            detail = "Bấm Kiểm tra lại để đọc trạng thái thật từ Android."
        )

        !hasFix -> ScanState(
            tint = NeonCyan,
            title = "Đang dò vị trí thật…",
            detail = "Lần đầu có thể mất 30–60 giây; đứng gần cửa sổ hoặc ngoài trời thì nhanh hơn. " +
                "Chưa dò được thì bản đồ chưa hiện điểm nào."
        )

        else -> ScanState(
            tint = NeonEmerald,
            title = "Định vị thật đang chạy",
            detail = "Độ chính xác ${FixPrecision.accuracyShortLabel(accuracyMeters)}" +
                (if (lastUpdatedAt > 0L)
                    " • cập nhật ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(lastUpdatedAt))}"
                else "")
        )
    }

    BentoCard(modifier = Modifier.fillMaxWidth(), borderColor = state.tint.copy(alpha = 0.6f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(state.tint.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasFix) Icons.Default.GpsFixed else Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = state.tint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = state.tint,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    !hasPermission -> {
                        ScanActionButton("Cấp quyền vị trí", onGrant)
                        ScanActionButton("Mở Cài đặt app", onOpenAppSettings)
                    }

                    servicesEnabled == false -> ScanActionButton("Mở Cài đặt định vị", onOpenLocationSettings)
                    else -> ScanActionButton("Kiểm tra lại", onRescan)
                }
            }

            if (vpnActive == true) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Máy đang vào mạng qua VPN. Vị trí lấy từ GPS của máy, không suy từ địa chỉ IP, " +
                        "nên VPN không làm sai tọa độ — số liệu vẫn gửi lên bình thường.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }
    }
}

private data class ScanState(
    val tint: Color,
    val title: String,
    val detail: String
)

@Composable
private fun ScanActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = SpaceCardElevated,
            contentColor = FacebookBlue
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}
