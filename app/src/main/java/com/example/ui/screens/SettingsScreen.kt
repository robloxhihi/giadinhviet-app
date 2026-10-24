package com.example.ui.screens

import com.example.service.TelemetryAndUsageHelper
import com.example.service.sos.PermissionHandler


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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PairingStatus
import com.example.data.model.UserRole
import com.example.ui.components.BlockRulesCard
import com.example.ui.components.BentoCard
import com.example.ui.components.isAppBlockerEnabled
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.VesperaUiState

@Composable
fun SettingsScreen(
    uiState: VesperaUiState,
    onOpenUsageSettings: () -> Unit,
    onUnpair: () -> Unit,
    onSignOut: () -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onSaveEmergencyPhone: (String) -> Unit = { _ -> },
    onSaveSosSchedule: (Boolean, Int, Int) -> Unit = { _, _, _ -> },
    onUpdateProfile: (String, String) -> Unit = { _, _ -> },
    onUpdateBlockRules: (List<String>, Boolean, List<String>, Int, String) -> Unit = { _, _, _, _, _ -> },
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollState = rememberScrollState()
    var showUnpairDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var newDisplayName by remember(uiState.userProfile?.displayName) { mutableStateOf(uiState.userProfile?.displayName ?: "") }

    var emergencyPhoneInput by remember(uiState.emergencyPhoneNumber) { mutableStateOf(uiState.emergencyPhoneNumber) }
    var isSosRestricted by remember(uiState.isSosRestricted) { mutableStateOf(uiState.isSosRestricted) }
    var sosStartHour by remember(uiState.sosStartHour) { mutableStateOf(uiState.sosStartHour.toString()) }
    var sosEndHour by remember(uiState.sosEndHour) { mutableStateOf(uiState.sosEndHour.toString()) }

    val userProfile = uiState.userProfile

    // ── 3 trạng thái ghép nối (sửa lỗi pháp lý) ──────────────────────────
    // VesperaUiState KHÔNG có cờ "pairing đã tải", nên suy điều kiện an toàn
    // nhất từ chính hồ sơ: profile.activePairingId khác rỗng mà activePairing
    // vẫn null ⇒ bản ghi ghép nối CHƯA VỀ = CHƯA XÁC ĐỊNH (fail-safe, cấm ghi
    // tức thì). Chỉ khi profile nói rõ không có liên kết nào (id rỗng, đã tải
    // hồ sơ) mới được coi là CHƯA GHÉP NỐI thật sự và cho phép ghi trực tiếp.
    val pairing = uiState.activePairing
    val isPaired = pairing != null && pairing.status == PairingStatus.PAIRED
    val isDefinitelyUnpaired =
        userProfile != null && userProfile.activePairingId.isEmpty() && pairing == null
    val isPairingResolving = !isPaired && !isDefinitelyUnpaired

    // ── Quyền chặn app: đọc 1 lần + chỉ đọc lại khi ON_RESUME ────────────
    // Thay cho việc gọi isAppBlockerEnabled(context) (Settings.Secure) trực
    // tiếp trong composition, chạy lại mỗi lần recompose.
    var blockerServiceEnabled by remember { mutableStateOf(isAppBlockerEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                blockerServiceEnabled = isAppBlockerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        Text(
            text = if (isPaired) "CÀI ĐẶT & GIAO DIỆN" else "CÀI ĐẶT",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Text(
            // Dòng chữ dưới tiêu đề phải theo đúng những gì màn này còn. Trước đây
            // nó hứa "tùy chỉnh giao diện sáng tối, màu sắc & quyền hệ thống" ngay cả
            // khi máy chưa ghép nối — lúc mà cả ba thứ đó đều chưa có chỗ để dùng.
            text = if (isPaired)
                "Tùy chỉnh giao diện sáng tối, màu sắc & quyền hệ thống"
            else
                "Máy này chưa ghép nối — vào đây chỉ để đăng xuất khỏi máy.\n" +
                    "Quyền hệ thống, nội quy chặn ứng dụng, giao diện và cài đặt SOS " +
                    "sẽ trở lại ngay khi ghép nối xong ở mục Tổng quan.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // "Ghép nối gia đình" da bi cat theo yeu cau. Ngat ghep noi van con o
        // phoi duoi cua man nay (onUnpair) va ma ket noi van hien o the ghep
        // noi cua tab Tong quan / man hoc sinh.

        Spacer(modifier = Modifier.height(16.dp))

        // CHƯA GHÉP NỐI = màn Cài đặt chỉ còn việc ra khỏi máy (thẻ "TÀI KHOẢN &
        // BẢO MẬT" bên dưới). Sáu thẻ ở giữa — quyền hệ thống, nội quy chặn ứng
        // dụng, giao diện, hồ sơ, quản lý ghép nối, cứu hộ/SOS — đều quản lý một
        // quan hệ chưa tồn tại, nên chúng biến mất hẳn chứ không hiện dạng mờ.
        // Đổi ý kiến thì chỉ cần bỏ cặp ngoặc bao quanh khối này.
        if (isPaired) {
        val telemetryHelper = TelemetryAndUsageHelper(context)
        // Đọc trạng thái quyền từ uiState (được refresh realtime khi app resume) — không tự tính lại cục bộ
        val hasUsage = uiState.hasUsagePermission
        val allPermissionsGranted = uiState.hasLocationPermission && uiState.hasSmsPermission &&
                uiState.hasNotificationPermission && hasUsage


        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val denied = results.filterValues { !it }.keys.toList()
            if (denied.isEmpty()) {
                android.widget.Toast.makeText(context, "Đã cấp tất cả quyền hệ thống thành công!", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(context, "Còn ${denied.size} quyền chưa được cấp.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        BentoCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = if (allPermissionsGranted) NeonEmerald.copy(alpha = 0.5f) else NeonViolet.copy(alpha = 0.6f)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QUẢN LÝ QUYỀN HỆ THỐNG",
                            style = MaterialTheme.typography.titleLarge,
                            color = if (allPermissionsGranted) NeonEmerald else NeonViolet,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allPermissionsGranted) "Bảo vệ an toàn & Giám sát cứu hộ 24/7 đã sẵn sàng" else "Cần cấp đủ quyền để tính năng SOS và Định vị hoạt động chuẩn xác",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (allPermissionsGranted) NeonEmerald.copy(alpha = 0.15f) else NeonViolet.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allPermissionsGranted) Icons.Default.Verified else Icons.Default.Security,
                            contentDescription = null,
                            tint = if (allPermissionsGranted) NeonEmerald else NeonViolet,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val locationOk = uiState.hasLocationPermission
                val smsOk = uiState.hasSmsPermission
                val notifOk = uiState.hasNotificationPermission

                PermissionStatusRow(
                    icon = Icons.Default.LocationOn,
                    title = "Vị trí GPS chính xác",
                    subtitle = "Định vị học sinh thời gian thực & cứu hộ SOS",
                    isGranted = locationOk
                )
                PermissionStatusRow(
                    icon = Icons.Default.Phone,
                    title = "Gửi tin nhắn SMS khẩn cấp",
                    subtitle = "Tự động gửi tin nhắn SMS cứu nạn khi mất mạng",
                    isGranted = smsOk
                )
                PermissionStatusRow(
                    icon = Icons.Default.Notifications,
                    title = "Thông báo đẩy & Cảnh báo",
                    subtitle = "Phát cảnh báo khẩn cấp và chuông reo tức thì",
                    isGranted = notifOk
                )
                PermissionStatusRow(
                    icon = Icons.Default.Schedule,
                    title = "Truy cập dữ liệu sử dụng",
                    subtitle = "Giám sát thời gian biểu và ứng dụng học tập",
                    isGranted = hasUsage
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Unified 1-Touch Action (Dồn vô 1 chạm)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!allPermissionsGranted) {
                        Button(
                            onClick = {
                                val missing = com.example.service.sos.PermissionHandler.getMissingPermissions(context)
                                if (missing.isNotEmpty()) {
                                    permissionLauncher.launch(missing)
                                } else if (!hasUsage) {
                                    onOpenUsageSettings()
                                    android.widget.Toast.makeText(context, "Vui lòng cho phép 'Truy cập dữ liệu sử dụng' để hoàn tất!", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("grant_permissions_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Cấp tất cả quyền (1 chạm)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = NeonEmerald.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonEmerald.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = NeonEmerald,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Đã cấp đầy đủ quyền • Hệ thống sẵn sàng 24/7",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonEmerald
                                )
                            }
                        }
                    }

                    // Direct Link to Android Settings (Merged here)
                    OutlinedButton(
                        onClick = {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SpaceCardBorder)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mở Cài đặt Ứng dụng Android",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App blocking rules (Accessibility based)
        if (isPairingResolving) {
            // CHƯA CHẮC CHẮN có đối tác (pairing đang tải / hồ sơ chưa về):
            // ẩn toàn card, không vẽ bất kỳ nút nào có thể ghi luật tức thì.
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "NỘI QUY ỨNG DỤNG",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonAmber,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Đang tải thông tin ghép nối… Vui lòng chờ để xác nhận đối tác duyệt nội quy trước khi lưu luật.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else {
            BlockRulesCard(
                schedule = uiState.usageSchedule,
                blockerServiceEnabled = blockerServiceEnabled,
                saveState = uiState.blockRulesSaveState,
                savedAtMillis = uiState.blockRulesSavedAtMillis,
                audit = uiState.ruleAudit,
                errorText = uiState.blockRulesError,
                onUpdate = onUpdateBlockRules,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme & Color Customization Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "GIAO DIỆN & BẢNG MÀU",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (uiState.isDarkMode) "Chế độ tối (Dark Mode)" else "Chế độ sáng (Light Mode)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    androidx.compose.material3.Switch(
                        checked = uiState.isDarkMode,
                        onCheckedChange = onToggleDarkMode
                    )
                }

            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile & Role Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(NeonViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userProfile?.displayName?.take(1)?.uppercase()
                                .orEmpty().ifEmpty { "?" },
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = userProfile?.displayName?.takeIf { it.isNotBlank() }
                                ?: "Chưa đăng nhập",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            // Dung hai noi khac nhau cho "chua co email" va "chua
                            // dang nhap". Truoc day mot dong in ca hai, nen may
                            // dang nhat van bao "may nay chua dang nhap".
                            text = when {
                                userProfile?.email?.isNotBlank() == true -> userProfile?.email.orEmpty()
                                userProfile != null ->
                                    "Tài khoản này chưa có email lưu trên máy chủ."
                                else -> "Máy chưa đăng nhập tài khoản nào."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                    TextButton(onClick = { showEditProfileDialog = true }) {
                        Text("Sửa")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))


                if (userProfile?.role == UserRole.CHILD && userProfile.studentCode.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Mã nhận diện học sinh: ${userProfile.studentCode}",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    // Edit Profile Dialog
    if (showEditProfileDialog) {
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Đổi tên tài khoản") },
            text = {
                OutlinedTextField(
                    value = newDisplayName,
                    onValueChange = { newDisplayName = it },
                    label = { Text("Tên hiển thị mới") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEditProfileDialog = false
                        onUpdateProfile(newDisplayName, userProfile?.photoUrl ?: "")
                    }
                ) {
                    Text("Lưu", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            containerColor = DialogSurface
        )
    }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Management Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "QUẢN LÝ GHÉP NỐI THIẾT BỊ",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isPaired) {
                    val p = uiState.activePairing!!
                    val isParent = userProfile?.role == UserRole.PARENT
                    val partnerTitle = if (isParent) "Đang kết nối với Học sinh: ${p.childName}" else "Đang kết nối với Phụ huynh: ${p.parentName}"
                    Text(
                        text = partnerTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mã kết nối học sinh: ${p.studentCode} • Trạng thái: Đã liên kết an toàn",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NeonEmerald
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showUnpairDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("unpair_btn"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpaceCardElevated,
                            contentColor = NeonRose
                        )
                    ) {
                        Icon(imageVector = Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hủy liên kết thiết bị", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "Hiện tại chưa có ghép nối nào đang hoạt động.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CẤU HÌNH CỨU HỘ KHẨN CẤP & CÀI ĐẶT NÚT SOS
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonRose,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "CỨU HỘ KHẨN CẤP & CÀI ĐẶT NÚT SOS",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonRose,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Số điện thoại nhận tin SMS khẩn cấp:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Được dùng để gửi SMS kèm link Google Maps khi máy không còn Internet và không nối được mesh.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = emergencyPhoneInput,
                        onValueChange = { emergencyPhoneInput = it },
                        placeholder = { Text("Ví dụ: 0912345678", color = TextMuted) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveEmergencyPhone(emergencyPhoneInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRose)
                    ) {
                        Text("Lưu", color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Thông tin SOS (Luôn hoạt động 24/7)
                Text(
                    text = "Nút SOS luôn hoạt động 24/7 để đảm bảo an toàn tuyệt đối.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonEmerald,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        }  // hết khối "if (isPaired)" — những gì ở dưới hiện cả khi chưa ghép nối

        // Account Actions
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "TÀI KHOẢN & BẢO MẬT",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonViolet,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpaceCardElevated,
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đăng xuất tài khoản Google")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier.fillMaxWidth().testTag("delete_account_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpaceCardElevated,
                        contentColor = NeonRose
                    )
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ngừng dùng trên máy này (đăng xuất)")
                }
            }
        }


        Spacer(modifier = Modifier.height(32.dp))
    }

    // Unpair Dialog
    if (showUnpairDialog) {
        AlertDialog(
            onDismissRequest = { showUnpairDialog = false },
            title = { Text("Xác nhận hủy liên kết?") },
            text = {
                Text("Khi hủy liên kết, toàn bộ quyền chia sẻ vị trí realtime, lịch giờ và tin nhắn sẽ lập tức bị thu hồi trên cả hai máy.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpairDialog = false
                        onUnpair()
                    }
                ) {
                    Text("Hủy liên kết ngay", color = NeonRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairDialog = false }) {
                    Text("Quay lại", color = TextSecondary)
                }
            },
            containerColor = DialogSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }

    // "Delete account" Dialog
    //
    // NUT NAY KHONG XOA TAI KHOAN. `firestore.rules` da chan:
    //   match /users/{userId} { allow delete: if false }
    // nen khong client nao xoa duoc ho so cua minh, va noi "xóa vĩnh viễn theo
    // chính sách quyền riêng tư" roi chi dang xuat la noi mot duong lam mot
    // duong — nguoi dung tuong data da het trong khi van no nam tren may chủ.
    // Doi thanh noi dung THAT: dang xuat + huong dan thu tuc xoa day du.
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("Ngừng dùng trên máy này?") },
            text = {
                Text(
                    "Nút này ĐĂNG XUẤT khỏi máy này — máy sẽ không còn đọc được vị trí, " +
                        "tin nhắn và nội quy của gia đình đã ghép nối.\n\n" +
                        "Nó KHÔNG xóa hồ sơ đang lưu trên máy chủ: quy tắc bảo mật của ứng " +
                        "dụng chặn mọi yêu cầu tự xóa dữ liệu từ điện thoại. " +
                        // Khi chưa ghép nối thì thẻ "QUẢN LÝ GHÉP NỐI" (và nút "Hủy liên
                        // kết" trong đó) đã bị ẩn, nên câu "nút ... ở trên" trước đây dẫn
                        // người đọc tới một chỗ không còn tồn tại trên màn hình.
                        if (isPaired)
                            "Muốn xóa hẳn thì phải gỡ liên kết trước bằng nút “Hủy liên kết” ở " +
                                "thẻ Quản lý ghép nối trên đây (thu hồi quyền chia sẻ trên cả " +
                                "hai máy), rồi yêu cầu người vận hành xóa tài khoản trong bảng " +
                                "điều khiển Firebase."
                        else
                            "Máy này chưa ghép nối nên không còn liên kết nào để gỡ — đăng xuất " +
                                "là cắt hết đường đọc dữ liệu trên máy. Hồ sơ trên máy chủ chỉ " +
                                "người vận hành xóa được, qua bảng điều khiển Firebase."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAccountDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Đăng xuất ngay", color = NeonRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Quay lại", color = TextSecondary)
                }
            },
            containerColor = DialogSurface,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}

@Composable
fun PermissionStatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) NeonEmerald.copy(alpha = 0.12f) else NeonRose.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) NeonEmerald else NeonRose,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isGranted) NeonEmerald.copy(alpha = 0.15f) else NeonRose.copy(alpha = 0.15f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isGranted) NeonEmerald.copy(alpha = 0.4f) else NeonRose.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isGranted) NeonEmerald else NeonRose,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isGranted) "Đã cấp" else "Chưa cấp",
                    fontSize = 12.5.sp,
                    color = if (isGranted) NeonEmerald else NeonRose,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

