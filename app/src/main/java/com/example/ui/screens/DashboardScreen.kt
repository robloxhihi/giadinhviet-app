package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EmotionTag
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

@Composable
fun DashboardScreen(
    uiState: VesperaUiState,
    onPairDirectly: (String) -> Unit,
    onAcceptPairing: (PairingRequest) -> Unit,
    onRejectPairing: (String) -> Unit,
    onApproveExtraTime: (String, Int) -> Unit,
    onRejectExtraTime: (String) -> Unit,
    onApprovePrivacy: (String, String) -> Unit,
    onRejectPrivacy: (String) -> Unit,
    onRequestExtraTimeClick: () -> Unit,
    onRequestPrivacyClick: () -> Unit,
    onNavigateToTab: (Int) -> Unit,
    onSelectEmotion: (EmotionTag) -> Unit,
    onSendZenlyBump: () -> Unit,
    onClearMessages: () -> Unit,
    onOpenLocationHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var inputStudentCode by remember { mutableStateOf("") }

    // Khong Toast thong bao o day nua. MainAppScreen da co Snackbar toan cuc
    // (no thanh cong thi moi hien du 2 dong chu, Toast cat ngat lam 1), va
    // Toast ben Dashboard goi onClearMessages() tru khi Snackbar kip chay ->
    // cung mot loi, o tab Tong quan bi cat, o tab khac lai hien Snackbar. Mot
    // duong ra duy nhat cho moi thong bao.
    val userProfile = uiState.userProfile
    val isChild = userProfile?.role == UserRole.CHILD
    val activePairing = uiState.activePairing
    val isPaired = activePairing != null && activePairing.status == PairingStatus.PAIRED
    val studentCode = userProfile?.studentCode.orEmpty().ifEmpty { "HS-CONNECT" }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // User Profile Header with Role Switch Button
            if (uiState.partnerLocation?.isSos == true) {
                com.example.ui.components.SosAlertBanner(
                    message = "CẢNH BÁO SOS: ${uiState.partnerLocation?.userName} đang gặp nguy hiểm!",
                    onCallSos = {
                        val roomId = uiState.activePairing?.id.orEmpty().ifEmpty { "room_${uiState.userProfile?.id}" }
                        val targetName = uiState.partnerLocation?.userName.orEmpty().ifEmpty { "Học sinh" }
                        com.example.ui.call.CallActivity.startCall(
                            context = context,
                            roomId = roomId,
                            targetName = targetName,
                            isCaller = true,
                            isVideo = true,
                            isSos = true
                        )
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // PENDING PRIVACY REQUESTS
            val pendingPrivacy = uiState.privacyRequests.filter { it.status == com.example.data.model.RequestStatus.PENDING }
            if (pendingPrivacy.isNotEmpty() && !isChild) {
                pendingPrivacy.forEach { req ->
                    BentoCard(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        borderColor = NeonAmber
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = NeonAmber,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Yêu cầu khung giờ riêng tư",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Học sinh ${req.childName} xin bảo vệ riêng tư: từ ${req.startHour}:00 đến ${req.endHour}:00 ${if (req.startDate.isNotEmpty()) "ngày ${req.startDate}" else ""}.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            if (req.reason.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Lý do: ${req.reason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "💡 Lưu ý: Khi duyệt, vị trí GPS được ẩn an toàn nhưng kênh Chat và Gọi điện vẫn hoạt động bình thường.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onRejectPrivacy(req.id) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = SpaceCardElevated, contentColor = NeonRose)
                                ) {
                                    Text("Từ chối")
                                }
                                Button(
                                    onClick = { onApprovePrivacy(req.id, req.childId) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald, contentColor = Color.Black)
                                ) {
                                    Text("Phê duyệt", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
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
                            Text(
                                text = if (isChild) "H" else "P",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                        // Facebook Active Online Green Dot
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(NeonEmerald)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = userProfile?.displayName?.ifEmpty { if (isChild) "Học sinh" else "Phụ huynh" } ?: if (isChild) "Học sinh" else "Phụ huynh",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isChild) "Học sinh • $studentCode" else "Vai trò: Phụ huynh 🛡️",
                            style = MaterialTheme.typography.labelSmall,
                            color = FacebookBlue
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Quick Role Switch Button (Luôn đổi vai trò được mọi lúc!)

                    Spacer(modifier = Modifier.width(8.dp))

                    // Connection Badge — trang thai that, khong bam de doi
                    // (kieu "doi" truoc day la ghep noi gia, kho bi bat loi).
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isPaired) NeonEmerald.copy(alpha = 0.2f) else NeonAmber.copy(alpha = 0.2f))
                            .border(
                                1.2.dp,
                                if (isPaired) NeonEmerald else NeonAmber,
                                RoundedCornerShape(10.dp)
                            )
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
                                text = if (isPaired) "Đã kết nối" else "Chưa ghép nối",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPaired) NeonEmerald else NeonAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

        Spacer(modifier = Modifier.height(16.dp))

        // "Premium quick actions" da bi cat theo yeu cau bo tinh nang Lien ket
        // gia dinh: tile HANH HINH mo Location History van con duong di rieng
        // o tab Vi tri, nen man nay khong can dai ly nua.

        // UNPAIRED STATE: Concise pairing cards (No mock demo data!)
        if (!isPaired) {
            if (isChild) {
                // Child: Display Real Student Code
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "child_student_code_card"
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mã kết nối học sinh",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = NeonCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = ClipData.newPlainText("Mã học sinh", studentCode)
                                    clipboard?.setPrimaryClip(clip)
                                    Toast.makeText(context, "Đã sao chép: $studentCode", Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SpaceCardElevated,
                                    contentColor = NeonCyan
                                ),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sao chép", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SpaceCardElevated)
                                .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = studentCode,
                                style = MaterialTheme.typography.headlineMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Đưa mã này cho phụ huynh nhập để hoàn tất ghép nối.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Pending requests from parents
                if (uiState.pendingRequests.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Yêu cầu ghép nối (${uiState.pendingRequests.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonAmber,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    uiState.pendingRequests.forEach { req ->
                        BentoCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            borderColor = NeonAmber
                        ) {
                            Column {
                                Text(
                                    text = "Phụ huynh: ${req.parentName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = req.parentEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { onRejectPairing(req.id) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SpaceCardElevated,
                                            contentColor = NeonRose
                                        )
                                    ) {
                                        Text("Từ chối")
                                    }
                                    Button(
                                        onClick = { onAcceptPairing(req) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonEmerald,
                                            contentColor = Color.Black
                                        )
                                    ) {
                                        Text("Đồng ý", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Parent: Enter Student Code & Pairing Suite
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "parent_enter_code_card"
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FamilyRestroom,
                                contentDescription = null,
                                tint = FacebookBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Ghép nối thiết bị học sinh",
                                style = MaterialTheme.typography.labelMedium,
                                color = FacebookBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val myDeviceCode = uiState.userProfile?.studentCode.orEmpty()

                        OutlinedTextField(
                            value = inputStudentCode,
                            onValueChange = { inputStudentCode = it.uppercase() },
                            label = { Text("Nhập mã độc quyền của con (vd: HS-XXXXX)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("student_code_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = FacebookBlue,
                                unfocusedBorderColor = SpaceCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = FacebookBlue
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick fill with this device's unique code
                            if (myDeviceCode.isNotEmpty() && myDeviceCode != "HS-CONNECT") {
                                Button(
                                    onClick = {
                                        inputStudentCode = myDeviceCode
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpaceCardElevated,
                                        contentColor = FacebookBlue
                                    )
                                ) {
                                    Text("Mã máy này: $myDeviceCode", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    if (inputStudentCode.isNotBlank()) {
                                        onPairDirectly(inputStudentCode)
                                        inputStudentCode = ""
                                    } else {
                                        Toast.makeText(context, "Vui lòng nhập mã học sinh!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1.3f).height(46.dp).testTag("send_pair_request_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FacebookBlue,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(imageVector = Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Ghép nối ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Chỉ cần nhập mã học sinh từ máy của con là ghép nối tức thì, không cần mã xác thực.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // PARENT MONITORING SUITE: Bảng giám sát con (Chưa ghép nối)
                BentoCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = NeonAmber.copy(alpha = 0.5f)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trung tâm giám sát học sinh",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NeonAmber.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ĐANG CHỜ KẾT NỐI",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = NeonAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid 4 chỉ số con
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.LocationOn,
                                iconTint = NeonCyan,
                                title = "VỊ TRÍ CON",
                                value = "Chưa kết nối",
                                subtitle = "Đang chờ thiết bị",
                                testTag = "child_unpaired_loc_stat"
                            )

                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.BatteryChargingFull,
                                iconTint = NeonEmerald,
                                title = "PIN THIẾT BỊ CON",
                                value = "—",
                                subtitle = "Chưa liên kết",
                                testTag = "child_unpaired_bat_stat"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Schedule,
                                iconTint = FacebookBlue,
                                title = "GIỜ DÙNG CỦA CON",
                                value = "—",
                                subtitle = "Mục tiêu: 120 ph",
                                testTag = "child_unpaired_usage_stat"
                            )

                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.HourglassTop,
                                iconTint = NeonAmber,
                                title = "TRẠNG THÁI AN TOÀN",
                                value = "Chờ tín hiệu",
                                subtitle = "Tự động cập nhật",
                                testTag = "child_unpaired_safe_stat"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Dữ liệu vị trí và pin sẽ tự động cập nhật sau khi ghép nối thành công.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CẤU HÌNH GIỚI HẠN THỜI GIAN CHO CON (Parental Screen Time Controls)
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Thiết lập mục tiêu giờ dùng cho con",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Phụ huynh có thể cài đặt trước giới hạn thời gian màn hình mỗi ngày để rèn luyện thói quen tự giác cho con:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val target = uiState.usageSchedule?.dailyTargetMinutes ?: 120

                        Column {
                            Text(
                                text = "Mục tiêu hiện tại: $target phút/ngày",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonCyan,
                                fontWeight = FontWeight.Bold
                            )
                            // Thanh keo han muc tung dat o day va goi thang
                            // len máy chủ. Đây là nhánh "CHUA ghep noi": nội quy
                            // chưa có chỗ để lưu nên mỗi lần kéo là một lần
                            // thất bại, kéo xong thì giá trị quay về cũ. Ở đây
                            // chỉ hiển thị — soạn thì ở card Nội quy trong
                            // Cài đặt, nơi có nút Gửi đề xuất và nhật ký.
                            Text(
                                text = "Chưa ghép nối nên chưa lưu được hạn mức này. " +
                                    "Hạn mức chỉ đổi được ở mục Nội quy ứng dụng trong Cài đặt, " +
                                    "nơi có nút Gửi đề xuất và nhật ký thay đổi.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { onNavigateToTab(4) },
                                modifier = Modifier.fillMaxWidth().height(42.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "Đổi hạn mức trong Cài đặt ➔",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }


                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onNavigateToTab(2) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = NeonCyan
                            )
                        ) {
                            Text("Đi đến Quản lý Giờ dùng & Phân loại ứng dụng ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // CÀI ĐẶT AN TOÀN SOS & KÊNH NGOẠI TUYẾN
                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = NeonRose,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "An toàn khẩn cấp & cứu hộ ngoại tuyến",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonRose,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Hệ thống SOS thông minh: Tự động gửi tọa độ GPS qua Internet, Mesh hoặc SMS khi khẩn cấp.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Số cứu hộ SMS:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(
                                text = uiState.emergencyPhoneNumber.ifEmpty { "Chưa lưu số người thân" },
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonEmerald,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Khung giờ con được bấm SOS:", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                            Text(
                                text = if (uiState.isSosRestricted) "${uiState.sosStartHour}:00 - ${uiState.sosEndHour}:00" else "Toàn thời gian (24/7)",
                                style = MaterialTheme.typography.labelMedium,
                                color = NeonAmber,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = { onNavigateToTab(4) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = NeonRose
                            )
                        ) {
                            Text("Cài đặt số điện thoại & Khung giờ SOS ➔", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real Telemetry of THIS device
                val myLoc = uiState.myLocation
                val totalTodayMins = uiState.detailedAppUsage.sumOf { it.minutesUsed }

                BentoCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            text = "Trạng thái thiết bị phụ huynh (máy này)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonEmerald,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.GpsFixed,
                                iconTint = NeonCyan,
                                title = "GPS MÁY NÀY",
                                value = if (myLoc != null) "Hoạt động" else "Đang dò",
                                subtitle = if (myLoc != null) "±${myLoc.accuracy.toInt()}m" else "Vệ tinh",
                                testTag = "device_gps_stat"
                            )

                            BentoStatCard(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Default.Schedule,
                                iconTint = NeonEmerald,
                                title = "GIỜ DÙNG MÁY NÀY",
                                value = "$totalTodayMins phút",
                                subtitle = "Hôm nay",
                                testTag = "device_usage_stat"
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = { onNavigateToTab(1) },
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = NeonCyan
                            )
                        ) {
                            Icon(imageVector = Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Xem bản đồ GPS & Lịch sử di chuyển", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        } else {
            // PAIRED STATE: Real Active Connection Bento-Grid
            val partnerLoc = uiState.partnerLocation
            val partnerName = if (isChild) activePairing.parentName else activePairing.childName

            // Partner Summary Card
            BentoCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = partnerName,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = partnerLoc?.currentSafeZone?.ifEmpty { "Khu vực an toàn" } ?: "Đang trực tuyến",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonCyan
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = NeonEmerald,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (partnerLoc != null) "${partnerLoc.batteryLevel}%" else "—",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onSendZenlyBump,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = NeonAmber
                            )
                        ) {
                            Text("⚡ Bump (${uiState.lastBumpCount})", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onNavigateToTab(3) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpaceCardElevated,
                                contentColor = NeonCyan
                            )
                        ) {
                            Text("💬 Nhắn tin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onNavigateToTab(1) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FacebookBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Text("📍 Bản đồ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val sched = uiState.usageSchedule
                val target = sched?.dailyTargetMinutes ?: 120
                // So phut cua NGUOI BEN KIA phai lay tu telemetry ho do phat
                // len (`totalUsedMinutes` = tong do chinh may ho tinh), khong
                // lay `sched.usedMinutes` — truong nay khong ai ghi lai sau khi
                // tao tai lieu nen no in het gia tri cua ngay ghep noi.
                val used = (partnerLoc?.totalUsedMinutes ?: 0)
                    .let { if (it > 0) it else (sched?.usedMinutes ?: 0) }
                val remaining = (target - used).coerceAtLeast(0)

                BentoStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HourglassTop,
                    iconTint = NeonEmerald,
                    title = "CÒN LẠI HÔM NAY",
                    value = "$remaining ph",
                    subtitle = "Mục tiêu: $target ph",
                    testTag = "remaining_stat_card"
                )

                BentoStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    iconTint = NeonCyan,
                    title = "ĐÃ DÙNG",
                    value = "$used ph",
                    subtitle = "Của người đã ghép nối, trong ngày",
                    testTag = "used_stat_card"
                )
            }
        }
    }
}
}
