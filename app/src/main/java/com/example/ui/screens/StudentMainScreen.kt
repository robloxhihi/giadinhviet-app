package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.PairingRequest
import com.example.data.model.PairingStatus
import com.example.data.model.UserRole
import com.example.ui.components.BentoCard
import com.example.ui.components.BentoProgressBar
import com.example.ui.components.BentoStatCard
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

/**
 * Dedicated Student Screen: Focuses purely on displaying the unique student code
 * for parents to connect, running background GPS & usage telemetry, and showing
 * connection status.
 */
@Composable
fun StudentMainScreen(
    uiState: VesperaUiState,
    onAcceptPairing: (PairingRequest) -> Unit,
    onRejectPairing: (String) -> Unit,
    onSendZenlyBump: () -> Unit,
    onOpenChat: () -> Unit,
    onRequestExtraTime: () -> Unit,
    onRequestPrivacyClick: () -> Unit = {},
    onSignOut: () -> Unit,
    onRegenerateCode: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val userProfile = uiState.userProfile
    // "" = tai khoan chua duoc cap ma. Truoc day the hien "HS-CONNECT" thay vi
    // trong, va hoc sinh mang cai ma gia do i nhau dang ky.
    val studentCode = userProfile?.studentCode.orEmpty()
    val isPaired = uiState.activePairing != null && uiState.activePairing.status == PairingStatus.PAIRED
    val parentName = uiState.activePairing?.parentName?.ifEmpty { "Phụ huynh" } ?: "Phụ huynh"
    val myLoc = uiState.myLocation

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Profile Header
        if (uiState.partnerLocation?.isSos == true) {
            com.example.ui.components.SosAlertBanner(
                message = "CẢNH BÁO SOS: ${uiState.partnerLocation?.userName} đang gặp nguy hiểm!"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(FacebookBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        // Chu dau cua ten that; "—" khi chua co ten. Truoc day
                        // o nay hard-code "H" ("Học sinh") nen du ten la "Bin"
                        // van thay mot chu H lac loi.
                        Text(
                            text = (userProfile?.displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString())
                                ?: "—",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                    }
                    // Cham mau = ket noi cua MAY NAY do duoc that (NetworkMonitor).
                    // Xam = chua do duoc. Truoc day no luon xanh la, khong theo
                    // bat cu trang thai nao.
                    val online = uiState.isOnline
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(
                                when (online) {
                                    true -> NeonEmerald
                                    false -> NeonRose
                                    null -> TextMuted
                                }
                            )
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = userProfile?.displayName?.ifEmpty { "Học sinh" } ?: "Học sinh",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Học sinh • ${studentCode.ifEmpty { "chưa có mã" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = FacebookBlue
                    )
                }
            }
            IconButton(onClick = onSignOut) {
                Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection badge — status only. It used to be clickable and swapped
        // to a fake pairing, which a judge could hit by accident.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (isPaired) NeonEmerald.copy(alpha = 0.2f) else NeonAmber.copy(alpha = 0.2f))
                .border(1.2.dp, if (isPaired) NeonEmerald else NeonAmber, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isPaired) Icons.Default.Link else Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = if (isPaired) NeonEmerald else NeonAmber,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isPaired) "Đã kết nối với $parentName" else "Chưa ghép nối",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPaired) NeonEmerald else NeonAmber,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Student Code Card
        BentoCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("MÃ KẾT NỐI HỌC SINH", style = MaterialTheme.typography.labelMedium, color = FacebookBlue, fontWeight = FontWeight.Bold)
                    if (studentCode.isNotEmpty()) {
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Mã học sinh", studentCode)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Đã sao chép: $studentCode", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = FacebookBlue)
                        ) {
                            Text("Sao chép", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpaceCardElevated)
                        .border(1.dp, FacebookBlue.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = studentCode.ifEmpty {
                            "Chưa có mã — đăng xuất rồi đăng nhập lại để máy chủ cấp mã"
                        },
                        style = if (studentCode.isNotEmpty()) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.bodyMedium,
                        color = if (studentCode.isNotEmpty()) TextPrimary else TextMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (studentCode.isNotEmpty()) 3.sp else 0.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (studentCode.isNotEmpty())
                        "Đưa mã này cho phụ huynh nhập để hoàn tất ghép nối."
                    else
                        "Mã do máy chủ cấp khi tạo tài khoản học sinh. Chưa có mã nghĩa là " +
                            "chưa đăng nhập được — chưa ghép nối bằng mã nào.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
        }

        // Pending requests
        if (uiState.pendingRequests.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text("YÊU CẦU GHÉP NỐI", style = MaterialTheme.typography.labelSmall, color = NeonAmber, fontWeight = FontWeight.Bold)
            uiState.pendingRequests.forEach { req ->
                BentoCard(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), borderColor = NeonAmber) {
                    Column {
                        Text("Phụ huynh: ${req.parentName}", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onRejectPairing(req.id) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = NeonRose)) { Text("Từ chối") }
                            Button(onClick = { onAcceptPairing(req) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black)) { Text("Đồng ý") }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        if (isPaired) {
            val partnerLoc = uiState.partnerLocation
            val parentName = uiState.activePairing?.parentName?.ifEmpty { "Phụ huynh" } ?: "Phụ huynh"
            
            // Paired: Parent Info Card
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("PHỤ HUYNH KẾT NỐI", style = MaterialTheme.typography.labelSmall, color = NeonViolet, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(parentName, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenChat, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)) { Text("💬 Nhắn tin") }
                        Button(onClick = onSendZenlyBump, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = NeonAmber)) { Text("⚡ Bump") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onRequestPrivacyClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = NeonEmerald)
                    ) {
                        Text("🛡️ Xin Khung giờ riêng tư (Bảo vệ vị trí)", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            // Paired: Usage Card
            val sched = uiState.usageSchedule
            // 0 = gia dinh CHUA dat han muc, khong phai "muc tieu 120 phut".
            val target = sched?.dailyTargetMinutes ?: 0
            // Doc so phut THAT tu may nay (quyen Usage Access). Khong bo sung
            // `sched.usedMinutes`: truong do khong ai ghi lai sau khi tao tai
            // lieu noi quy nen no in het con so cua ngay ghep noi.
            val used = com.example.data.model.todayUsedMinutes(uiState.detailedAppUsage)
            val remaining = (target - used).coerceAtLeast(0)
            
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("THỜI GIAN SỬ DỤNG", style = MaterialTheme.typography.labelSmall, color = NeonEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    BentoProgressBar(usedMinutes = used, targetMinutes = target)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = if (target > 0) "Còn lại: $remaining ph" else "Chưa đặt mục tiêu",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (target > 0) "Mục tiêu: $target ph" else "Mục tiêu: chưa đặt",
                            color = TextMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = onRequestExtraTime, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = NeonEmerald)) {
                        Text("➕ Xin thêm thời gian")
                    }
                }
            }
        } else {
            // Device Stats (Only show when not paired or as secondary info)
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text("TRẠNG THÁI THIẾT BỊ", style = MaterialTheme.typography.labelSmall, color = NeonEmerald, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    // Pin: chi hien % khi doc duoc so that tu telemetry. Null hoac
                    // 0 = chua do duoc, khong duoc bịa "85%" nhu truoc day.
                    val battery = myLoc?.batteryLevel ?: 0
                    val hasBattery = myLoc != null && battery > 0
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BentoStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.GpsFixed,
                            iconTint = NeonCyan,
                            title = "GPS",
                            value = if (myLoc != null) "Hoạt động" else "Đang dò",
                            subtitle = "Tự động cập nhật"
                        )
                        BentoStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.BatteryChargingFull,
                            iconTint = NeonEmerald,
                            title = "PIN",
                            value = if (hasBattery) "$battery%" else "—",
                            subtitle = if (hasBattery) "Thiết bị học sinh" else "Chưa đọc được pin"
                        )
                    }
                }
            }
        }
    }
}
