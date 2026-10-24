package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PairingStatus
import com.example.data.model.UserRole
import com.example.ui.components.DraggableFloatingSosButton
import com.example.ui.components.ExtraTimeDialog
import com.example.ui.components.PremiumBottomBar
import com.example.ui.components.PrivacyDialog
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.PremiumBackground
import com.example.ui.viewmodel.VesperaUiState
import com.example.ui.viewmodel.VesperaViewModel

/**
 * Root shell. All five tabs share one living [PremiumBackground], one frosted
 * floating navigation pill, and the always-available draggable SOS orb.
 *
 * "Overlay" screens (Insights, Location History) are pushed over the tabs and
 * dismissed with the system back gesture. "Phòng học tập trung" và "Liên kết
 * gia đình" da bi cat theo yeu cau — noi dung cua chung khong con o dau het.
 */
@Composable
fun MainAppScreen(
    viewModel: VesperaViewModel,
    uiState: VesperaUiState,
    onOpenUsageSettings: () -> Unit,
    onRequestRuntimePermissions: (Array<String>) -> Unit = {},
    onOpenSpecialPermission: (com.example.service.PermissionManager.PermissionItem) -> Unit = {},
    onRecheckPermissions: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isChild = uiState.userProfile?.role == UserRole.CHILD
    // Ban 60.0: khi CHUA ghep noi thi app chi mo dung man Tong quan (cho nhap
    // / hien ma) va Cai dat (quyen he thong, so cuu ho, dang xuat). Vi tri,
    // Gio dung va Chat deu can nguoi ben kia; mo ra khi chua ghep chi la mot
    // man trong khong co ai, nen chan bang OnlyWhenPaired.
    val isPaired = uiState.activePairing != null &&
        uiState.activePairing.status == PairingStatus.PAIRED
    var selectedTab by remember { mutableIntStateOf(0) }
    var overlayScreen by remember { mutableStateOf<String?>(null) }
    var showExtraTimeDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var autoShownPermissions by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val missingRequired = uiState.permissionSnapshot.count { !it.granted && it.required }

    // Mở Trung tâm quyền một lần khi vào app mà còn thiếu quyền bắt buộc (đúng thời điểm).
    LaunchedEffect(uiState.permissionSnapshot.isNotEmpty(), missingRequired) {
        if (!autoShownPermissions && uiState.permissionSnapshot.isNotEmpty() && missingRequired > 0) {
            autoShownPermissions = true
            showPermissionDialog = true
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    // Mo tab Vi tri thi do GPS cua MAY NAY ngay tuc thoi va do lai moi 15 giay.
    // Truoc day diem chi xuat hien sau khi nguoi dung bam "lam moi", nen nguoi
    // mo app luc chua ghep noi thay ban do trong khi may van do duoc.
    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) return@LaunchedEffect
        viewModel.broadcastSelfTelemetry()
        while (true) {
            delay(15_000)
            viewModel.broadcastSelfTelemetry()
        }
    }

    val navItems = listOf(
        Triple("Tổng quan", Icons.Default.Home, 0),
        Triple("Vị trí", Icons.Default.LocationOn, 1),
        Triple("Giờ dùng", Icons.Default.Schedule, 2),
        Triple("Chat", Icons.Default.Chat, 3),
        Triple("Cài đặt", Icons.Default.Settings, 4)
    )

    PremiumBackground(modifier = Modifier.fillMaxSize()) {
        if (uiState.isSosAlertActive) {
            com.example.ui.components.SosEmergencyWarningDialog(
                message = uiState.activeSosMessage,
                channel = uiState.currentSosChannel,
                statusMessage = uiState.sosStatusMessage.ifBlank {
                    com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE
                },
                position = uiState.sosPosition,
                onDismiss = { viewModel.dismissSosAlert() }
            )
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                Box(modifier = Modifier.navigationBarsPadding().padding(bottom = 6.dp)) {
                    PremiumBottomBar(
                        items = navItems,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    )
                }
            }
        ) { innerPadding ->
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (missingRequired > 0) {
                    com.example.ui.components.PermissionCenterBanner(
                        missingRequired = missingRequired,
                        onOpen = { showPermissionDialog = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> if (isChild) {
                        StudentMainScreen(
                            uiState = uiState,
                            onSendZenlyBump = { viewModel.sendZenlyBump(context) },
                            onOpenChat = { selectedTab = 3 },
                            onRequestExtraTime = { showExtraTimeDialog = true },
                            onRequestPrivacyClick = { showPrivacyDialog = true },
                            onSignOut = { viewModel.signOut() }
                        )
                    } else {
                        DashboardScreen(
                            uiState = uiState,
                            onPairDirectly = { code -> viewModel.pairDirectly(code) },
                            onApproveExtraTime = { reqId, mins -> viewModel.respondToExtraTime(reqId, true, mins) },
                            onRejectExtraTime = { reqId -> viewModel.respondToExtraTime(reqId, false, 0) },
                            onApprovePrivacy = { reqId, childId -> viewModel.respondToPrivacyRequest(reqId, childId, true) },
                            onRejectPrivacy = { reqId -> viewModel.respondToPrivacyRequest(reqId, "", false) },
                            onRequestExtraTimeClick = { showExtraTimeDialog = true },
                            onRequestPrivacyClick = { showPrivacyDialog = true },
                            onNavigateToTab = { index -> selectedTab = index },
                            onSelectEmotion = { emotion -> viewModel.setSelectedEmotion(emotion) },
                            onSendZenlyBump = { viewModel.sendZenlyBump(context) },
                            onClearMessages = { viewModel.clearMessages() },
                            onOpenLocationHistory = { overlayScreen = "history" },
                            onOpenAiChat = { overlayScreen = "ai" }
                        )
                    }
                    1 -> OnlyWhenPaired(isPaired, isChild) {
                        LocationScreen(
                            uiState = uiState,
                            onRefreshLocation = { viewModel.broadcastSelfTelemetry() },
                            onRequestPrivacyClick = { showPrivacyDialog = true },
                            onSendZenlyBump = { viewModel.sendZenlyBump(context) },
                            onToggleLocationHistory = { enabled -> viewModel.toggleLocationHistory(enabled) },
                            onOpenLocationHistory = { overlayScreen = "history" },
                            onRecheckPermissions = { viewModel.refreshPermissions() }
                        )
                    }
                    2 -> OnlyWhenPaired(isPaired, isChild) {
                        ScreenTimeScreen(
                            uiState = uiState,
                            onOpenUsageSettings = onOpenUsageSettings,
                            onRequestExtraTimeClick = { showExtraTimeDialog = true },
                            // "Sửa lịch" phải đưa tới nơi CHỨA lịch (thẻ Chặn ứng dụng
                            // nằm ở tab Cài đặt).
                            onEditScheduleClick = { selectedTab = 4 },
                            onToggleBackgroundService = { viewModel.toggleBackgroundService(context) },
                            onStartFamilyStudy = { viewModel.startFamilyStudy(it) },
                            onStopFamilyStudy = { viewModel.stopFamilyStudy() },
                            onOpenInsights = { overlayScreen = "insights" },
                            onProposeAgreement = { viewModel.proposeAgreement(it) },
                            onRespondToAgreement = { agr, accept -> viewModel.respondToAgreement(agr, accept) },
                        )
                    }
                    3 -> OnlyWhenPaired(isPaired, isChild) {
                        ChatScreen(
                            uiState = uiState,
                            onSendMessage = { text, url, type -> viewModel.sendMessage(text, url, type) },
                            onToggleStar = { id, star -> viewModel.toggleMessageStar(id, star) },
                            onSelectEmotion = { emotion -> viewModel.setSelectedEmotion(emotion) },
                            onConnectMeshPeer = { peerId -> viewModel.connectMeshPeer(peerId) },
                            onSendMeshMessage = { peerId, text -> viewModel.sendMeshMessage(peerId, text) }
                        )
                    }
                    4 -> SettingsScreen(
                        uiState = uiState,
                        onOpenUsageSettings = onOpenUsageSettings,
                        onUnpair = { viewModel.unpairConnection() },
                        onSignOut = { viewModel.signOut() },
                        onToggleDarkMode = { enabled -> viewModel.setDarkMode(enabled) },
                        onSaveEmergencyPhone = { phone -> viewModel.saveEmergencyPhoneNumber(phone) },
                        onSaveSosSchedule = { restricted, startH, endH -> viewModel.saveSosSchedule(restricted, startH, endH) },
                        onUpdateProfile = { name, photo -> viewModel.updateUserProfile(name, photo) },
                        onUpdateBlockRules = { packages, reminderOnly, allowedApps, minutes, windowCode ->
                            viewModel.updateBlockRules(
                                packages, reminderOnly, allowedApps, minutes, windowCode
                            )
                        },
                    )
                }
                }
            }
        }

        // ── Overlay screens ────────────────────────────────────────────────
        // Phai ve SAU Scaffold. Box ve theo thu tu con em: node ghi sau nam
        // tren. Dat node nay truoc Scaffold thi noi dung tab boi den len man
        // overlay va chan luon cu chi cham vao cong tac / nut bam o duoi.
        // Nen cua overlay BAT BUOC phai kin: PremiumBackground ve hoa lan
        // + scrim day du, va no nam duoi man overlay de tab ben duoi khong
        // boi xuyen qua (lau nay dung DialogSurface phang thi mat nen theo
        // thiet ke mau).
        val overlay = overlayScreen
        if (overlay != null) {
            BackHandler { overlayScreen = null }
            val close = { overlayScreen = null }
            PremiumBackground(modifier = Modifier.fillMaxSize()) {
                when (overlay) {
                    "insights" -> InsightsScreen(
                        uiState = uiState,
                        onBack = close,
                        onOpenUsageSettings = onOpenUsageSettings
                    )
                    "history" -> LocationHistoryScreen(
                        uiState = uiState,
                        onBack = close,
                        onToggleLocationHistory = { enabled -> viewModel.toggleLocationHistory(enabled) }
                    )
                    "ai" -> AiChatScreen(onBack = close)
                }
            }
        }

        // Draggable floating SOS orb — available on every screen
        DraggableFloatingSosButton(
            currentChannel = uiState.currentSosChannel,
            isSosAllowedNow = viewModel.isSosAllowedNow(),
            zaloInstalled = uiState.zaloInstalled,
            hasCallPermission = uiState.hasCallPermission,
            emergencyPhone = uiState.emergencyPhoneNumber,
            position = uiState.sosPosition,
            statusMessage = uiState.sosStatusMessage.ifBlank { null },
            onSendSosToFamily = { viewModel.sendSmartSosToFamily() },
            onSendSosToRescue = { hotline -> viewModel.sendSmartSosToRescue(hotline) },
            onCallFamily = { channel -> viewModel.callFamilyForSos(channel) },
            onOpenFamilySms = { viewModel.openFamilySmsDraft() },
            onSheetOpened = { viewModel.prepareSosConsole() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // O Chat, hàng soạn tin nằm sát thanh điều hướng, đúng chỗ quả
                // cầu SOS đang đứng: chạm "Gửi" là mở bảng cứu nạn, tin nhắn
                // không bao giờ đi. Tab 3 nhấc quả cầu lên khỏi ô soạn tin.
                .padding(end = 16.dp, bottom = if (selectedTab == 3) 152.dp else 104.dp)
        )
    }

    if (showExtraTimeDialog) {
        ExtraTimeDialog(
            onDismiss = { showExtraTimeDialog = false },
            onSubmit = { mins, reason ->
                showExtraTimeDialog = false
                viewModel.requestExtraTime(mins, reason)
            }
        )
    }

    if (showPrivacyDialog) {
        PrivacyDialog(
            onDismiss = { showPrivacyDialog = false },
            onSubmit = { startD, startH, startM, endD, endH, endM, reason ->
                showPrivacyDialog = false
                viewModel.requestPrivacyHours(startD, startH, startM, endD, endH, endM, reason)
            }
        )
    }

    if (showPermissionDialog) {
        com.example.ui.components.PermissionCenterDialog(
            items = uiState.permissionSnapshot,
            onRequestRuntime = { perms -> onRequestRuntimePermissions(perms) },
            onOpenSpecial = { item -> onOpenSpecialPermission(item) },
            onRecheck = {
                onRecheckPermissions()
                if (uiState.permissionSnapshot.count { !it.granted && it.required } == 0) {
                    showPermissionDialog = false
                }
            },
            onDismiss = { showPermissionDialog = false }
        )
    }
}

/**
 * Chan duong dan vao ba tab can nguoi ben kia (Vi tri / Gio dung / Chat) khi
 * may CHUA ghep noi. Mo ra khi chua ghep thi ca ba chi la man trong khong co
 * ai: khong co ai de do toa do, khong co noi quy ap len may nao, khong co dau
 * ben kia de nhan tin. Thay vi mot man trong, nguoi dung thay dung mot dong
 * giai thich va van ve duoc Tong quan de ghep.
 */
@Composable
private fun OnlyWhenPaired(
    isPaired: Boolean,
    isChild: Boolean,
    content: @Composable () -> Unit
) {
    if (isPaired) content() else UnpairedPlaceholder(isChild)
}

@Composable
private fun UnpairedPlaceholder(isChild: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp)
            .testTag("unpaired_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isChild)
                "Máy này chưa ghép nối.\n\n" +
                    "Ở mục Tổng quan có MÃ của con — đưa mã đó cho bố mẹ " +
                    "nhập ở máy của họ là liên kết tự về máy con, con không phải bấm gì. " +
                    "Ghép xong thì Bản đồ, Giờ dùng và Chat mới mở."
            else
                "Bạn chưa ghép nối với máy học sinh nào.\n\n" +
                    "Ở mục Tổng quan có ô nhập mã đang " +
                    "hiện trên máy của con. Ghép xong thì Bản đồ, Giờ dùng và Chat mới có dữ liệu.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
