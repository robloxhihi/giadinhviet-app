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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryUnknown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.History
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
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
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
import com.example.data.model.FamilyMessage
import com.example.data.model.PairingStatus
import com.example.data.model.RequestStatus
import com.example.data.model.RuleAuditEntry
import com.example.data.model.UserRole
import com.example.service.blocker.ConsentPolicy
import com.example.ui.components.BentoCard
import com.example.ui.components.BentoProgressBar
import com.example.ui.components.BentoStatCard
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
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
    onOpenLocationHistory: () -> Unit = {},
    onOpenAiChat: () -> Unit = {}
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
    // khong co mac dinh: `studentCode` rong nghia la tai khoan nay chua duoc
    // cap ma (phụ huynh, hoac ho so chua tai xong). Truoc day the hien
    // "HS-CONNECT" khi chua co ma — nguoi dung tuong do la ma that cua minh.
    val studentCode = userProfile?.studentCode.orEmpty()

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
                                text = buildString {
                                    append("Học sinh ")
                                    append(req.childName.ifEmpty { "nhà mình" })
                                    append(" xin bảo vệ riêng tư: ")
                                    append(
                                        if (req.startHour < 0 || req.endHour < 0) "chưa nêu khung giờ cụ thể"
                                        else "từ ${req.startHour}:${"%02d".format(req.startMinute)} " +
                                            "đến ${req.endHour}:${"%02d".format(req.endMinute)}"
                                    )
                                    if (req.startDate.isNotEmpty()) append(" ngày ${req.startDate}")
                                    append(".")
                                },
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
                                text = "Lưu ý: Khi duyệt, vị trí GPS được ẩn an toàn nhưng kênh Chat và Gọi điện vẫn hoạt động bình thường.",
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
                    // Ten hien thi cua chinh may nay; de trong thi goi theo vai
                    // tro, khong bao gio tu che ra ten.
                    val displayName = userProfile?.displayName?.trim()
                        ?.ifEmpty { if (isChild) "Học sinh" else "Phụ huynh" }
                        ?: if (isChild) "Học sinh" else "Phụ huynh"
                    Box {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(FacebookBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            // Chu trong avatar phai la chu cai DAU TIEN cua ten
                            // that. Truoc day gim dinh "H"/"P" theo vai tro, nen
                            // moi nguoi dung deu chung mot chu va nguoi ten
                            // "hah" van thay chu "P" — mot chu doan mau.
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp
                            )
                        }
                        // Diem "trực tuyến" phai chay theo NetworkMonitor. Truoc
                        // day no son xanh CO DINH: may mat mang cung bao dang
                        // online. null = chua kiem tra xong thi bao xam, khong
                        // doan la offline.
                        val onlineTint = when (uiState.isOnline) {
                            true -> NeonEmerald
                            false -> NeonRose
                            null -> TextMuted
                        }
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .clip(CircleShape)
                                .background(onlineTint)
                                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                .align(Alignment.BottomEnd)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isChild) "Học sinh • ${studentCode.ifEmpty { "chưa có mã" }}"
                            else "Vai trò: Phụ huynh",
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

        // Cho dat tro ly AI o VUNG CHET cua bang dieu tra: no doc dung so lieu
        // app do tren may nay, nen chi huu ich voi nguoi nam noi quy.
        //
        // Ban 60.0: the nay con bi chan sau `isPaired`. Khi CHUA ghep noi thi
        // man nay chi duoc phep con mot thu: the nhap ma cua con. Tro ly AI,
        // thong ke, noi quy... xuat hien day du ngay sau khi ghep xong.
        if (!isChild && isPaired) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                glow = NeonViolet
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = NeonViolet,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trợ lý gia đình",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Số liệu dùng máy hôm nay được lưu trên máy này và gửi kèm mỗi câu hỏi, " +
                                "nên AI trả lời theo con số thật chứ không phải lời khuyên chung.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GradientButton(
                        text = "Mở chat với trợ lý AI",
                        icon = Icons.Default.AutoAwesome,
                        onClick = onOpenAiChat,
                        modifier = Modifier.fillMaxWidth().testTag("open_ai_chat_button")
                    )
                }
            }
        }

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

                            if (studentCode.isNotEmpty()) {
                                Button(
                                    onClick = {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clip = ClipData.newPlainText("Mã học sinh", studentCode)
                                        clipboard?.setPrimaryClip(clip)
                                        Toast.makeText(
                                            context,
                                            "Đã sao chép: $studentCode",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpaceCardElevated,
                                        contentColor = NeonCyan
                                    ),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 2.dp
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sao chép", fontSize = 12.sp)
                                }
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
                                text = studentCode.ifEmpty {
                                    "Chưa có mã — đăng xuất rồi đăng nhập lại để cấp mã"
                                },
                                style = if (studentCode.isNotEmpty())
                                    MaterialTheme.typography.headlineMedium
                                else MaterialTheme.typography.bodyMedium,
                                color = if (studentCode.isNotEmpty()) TextPrimary else TextSecondary,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = if (studentCode.isNotEmpty()) 3.sp else 0.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (studentCode.isNotEmpty())
                                "Đưa mã này cho phụ huynh nhập để hoàn tất ghép nối."
                            else
                                "Mã này do máy chủ cấp khi tài khoản học sinh được tạo. " +
                                "Chưa có nghĩa là chưa đăng nhập được — chưa ghép nối được bằng mã nào.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
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

                        // Gợi ý hình dạng mã ngay trên máy, trước khi gọi Firestore.
                        // Quyet dinh cuoi cung la `VesperaRepository.studentCodePattern`
                        // (sinh tu STUDENT_CODE_ALPHABET + STUDENT_CODE_LENGTH). Doan duoi
                        // day viet lai bang ky tu tuong duong — [2-9A-HJ-NP-Z] la chinh no,
                        // bo 0/1/I/O — va `PairingFlowRegressionTest` gop hai lai mot de
                        // chung khong the lac nhau.
                        val codeShape = Regex("^HS-[2-9A-HJ-NP-Z]{8}$")
                        val typedCode = inputStudentCode.trim()
                        val codeShapeOk = codeShape.matches(typedCode)

                        OutlinedTextField(
                            value = inputStudentCode,
                            onValueChange = { inputStudentCode = it.uppercase() },
                            label = { Text("Nhập mã đang hiện trên máy của con (HS-XXXXXXXX)") },
                            singleLine = true,
                            isError = typedCode.isNotEmpty() && !codeShapeOk,
                            supportingText = {
                                if (typedCode.isNotEmpty() && !codeShapeOk) {
                                    Text(
                                        text = "Mã gồm HS- rồi 8 ký tự, không có các chữ 0, 1, I, O.",
                                        color = NeonRose
                                    )
                                }
                            },
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

                        Button(
                            onClick = {
                                onPairDirectly(typedCode)
                                inputStudentCode = ""
                            },
                            enabled = codeShapeOk && !uiState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("send_pair_request_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FacebookBlue,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isLoading) "Đang ghép nối…" else "Ghép nối ngay",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Nhập mã là ghép nối tức thì — máy con không phải bấm " +
                                "Đồng ý, và cũng không có mã xác thực nào cả. Nếu hai máy " +
                                "đang mở app cùng lúc thì liên kết về máy con trong khoảng " +
                                "20 giây.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Ban 60.0: CAT HET bon the phu o day. Khi CHUA ghep noi, man
                // nay tung nhem them "Trung tam giam sat hoc sinh", "Thiet lap
                // muc tieu gio dung cho con", "An toan khan cap & cuu ho ngoai
                // tuyen" va "Trang thai thiet bi phu huynh" — ca bon chi noi
                // "chua ghep noi" roi day nguoi dung di choi khac. Luc do thi
                // nguoi dung can DUNG mot thu: o nhap ma cua con. Ghep xong,
                // du lieu that ve nen man hinh day du (nhanh else ben duoi).
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
                            // May con CHUA gui diem dinh vi nao thi khong duoc
                            // phep in ra "Đang trực tuyến": do la mot trang thai
                            // khong co bang chung. Truoc day dong nay cung chinh
                            // la ly do man hinh bao ket noi trong khi may con chua
                            // tung gui du lieu ve.
                            Text(
                                text = when {
                                    partnerLoc == null ->
                                        if (isChild) "Chưa nhận được vị trí từ máy phụ huynh"
                                        else "Chưa nhận được vị trí từ máy con"

                                    partnerLoc.currentSafeZone.isEmpty() ->
                                        "Chỉ có tọa độ — chưa dịch được tên khu vực"

                                    else -> partnerLoc.currentSafeZone
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (partnerLoc == null) TextMuted else NeonCyan
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (partnerLoc == null) Icons.Default.BatteryUnknown
                                    else Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = if (partnerLoc == null) TextMuted else NeonEmerald,
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
                        // Icon vector thay vi emoji. Emoji do he dieu hanh ve
                        // ra, nen cung mot nut o may khac nhau cho ra hinh
                        // khac nhau (va trong anh chang man con bi lech size
                        // so voi chu). Icon thi app kiem soat duoc mau.
                        DashboardActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.TouchApp,
                            containerColor = SpaceCardElevated,
                            contentColor = NeonAmber,
                            label = "Chạm tay • " + bumpStateLabel(
                                uiState.lastBumpAt, uiState.lastBumpByName, System.currentTimeMillis()
                            ),
                            onClick = onSendZenlyBump
                        )
                        DashboardActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.Chat,
                            containerColor = SpaceCardElevated,
                            contentColor = NeonCyan,
                            label = "Nhắn tin",
                            onClick = { onNavigateToTab(3) }
                        )
                        DashboardActionButton(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Default.LocationOn,
                            containerColor = FacebookBlue,
                            contentColor = Color.White,
                            label = "Bản đồ",
                            onClick = { onNavigateToTab(1) }
                        )
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
                // 0 = CHUA dat han muc (mac dinh cua UsageSchedule), khong phai
                // "muc tieu 0 phut" va cung khong phai "120 phut" nhu truoc day.
                val target = sched?.dailyTargetMinutes ?: 0
                // So phut cua NGUOI BEN KIA phai lay tu telemetry ho do phat
                // len (`totalUsedMinutes` = tong do chinh may ho tinh), khong
                // lay `sched.usedMinutes` — truong nay khong ai ghi lai sau khi
                // tao tai lieu nen no in het gia tri cua ngay ghep noi. Truoc
                // day khi may con bao 0 phut, code quay lai dung `usedMinutes`
                // cu — dung thu chinh comment nghem cam. Thieu telemetry thi
                // noi thang la chua co, khong gia vao so.
                val partnerMinutes = partnerLoc?.totalUsedMinutes
                val used = partnerMinutes ?: 0
                val hasPartnerUsage = partnerMinutes != null
                val remaining = (target - used).coerceAtLeast(0)

                BentoStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.HourglassTop,
                    iconTint = NeonEmerald,
                    title = "CÒN LẠI HÔM NAY",
                    value = if (!hasPartnerUsage || target <= 0) "—" else "$remaining ph",
                    subtitle = when {
                        !hasPartnerUsage -> "Chưa nhận được giờ dùng hôm nay từ máy con"
                        target <= 0 -> "Gia đình chưa đặt mục tiêu phút"
                        else -> "Mục tiêu: $target ph"
                    },
                    testTag = "remaining_stat_card"
                )

                BentoStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    iconTint = NeonCyan,
                    title = "ĐÃ DÙNG",
                    value = if (hasPartnerUsage) "$used ph" else "—",
                    subtitle = if (hasPartnerUsage) "Máy con tự đo và gửi lên"
                               else "Máy con chưa gửi số liệu hôm nay",
                    testTag = "used_stat_card"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ── Nửa dưới màn hình từng trống hoàn toàn ─────────────────────
            // Ba the duoi day chi dung so lieu THAT da co trong `uiState`. The
            // nao khong co noi dung thi bien han — mot khung "Chưa có gì"
            // chi lam man hinh cang trong.

            // (0) Noi quy dang chay. The nay LUON hien, vi mot cap cha con nao
            // cung co mot trang thai noi quy — ke ca trang thai "chua dat".
            // Day la ly do no dung dau: ba the duoi chi moc khi co phieu xin,
            // co tin nhan hoac co nhat ky, nen may ngay dau mo app thi half
            // man hinh van trong.
            val schedActive = uiState.usageSchedule
            val historyEditor = activePairing.historyChangedByName.ifEmpty {
                when (activePairing.historyChangedByRole) {
                    UserRole.CHILD -> "Con"
                    UserRole.PARENT -> "Phụ huynh"
                    null -> ""
                    else -> ""
                }
            }
            val pendingPrivacy = uiState.privacyRequests
                .count { it.status == RequestStatus.PENDING }
            BentoCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                onClick = { onNavigateToTab(4) },
                testTag = "dashboard_active_rules"
            ) {
                Column {
                    DashboardCardHeading(
                        icon = Icons.Default.Security,
                        tint = if (schedActive?.isActive == true) NeonEmerald else TextMuted,
                        title = "NỘI QUY ĐANG ÁP DỤNG",
                        subtitle = when {
                            schedActive == null ->
                                "Gia đình chưa lưu bảng nội quy nào cho cặp này"

                            !schedActive.isActive ->
                                "Đang tắt — máy con chưa chặn app theo bảng này"

                            else -> ConsentPolicy.describe(schedActive)
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Gom chu vao mot `val` thay vi noi `+` ke ben `if/else`:
                    // `else "dang tat" + if ...` doc la " + " thuoc ve nhanh
                    // else, de sai dang dau va kho doi.
                    val historyLine = buildString {
                        append("Ghi điểm di chuyển: ")
                        append(if (uiState.isLocationHistoryEnabled) "đang bật" else "đang tắt")
                        if (historyEditor.isNotEmpty()) append(" — người đổi gần nhất: $historyEditor")
                    }
                    Text(
                        text = historyLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (pendingPrivacy > 0 && !isChild) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$pendingPrivacy phiếu xin giờ trong quyền riêng chờ bạn duyệt",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeonAmber
                        )
                    }
                    if (uiState.blockRulesSavedAtMillis > 0L) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Bảng nội quy lưu " + minutesAgoLabel(
                                uiState.blockRulesSavedAtMillis, System.currentTimeMillis()
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
            }

            // (1) Phieu xin them gio. `extraTimeRequests` duoc listener nap vao
            // tu luc mo app nhung CHUA TUNG duoc in ra dau, va hai tham so
            // onApproveExtraTime / onRejectExtraTime cua man nay nam chet.
            // Nghia la con xin them gio thi bo me khong bao gio thay.
            val pendingExtra = uiState.extraTimeRequests
                .filter { it.status == RequestStatus.PENDING }
            if (pendingExtra.isNotEmpty() && !isChild) {
                BentoCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    borderColor = NeonAmber
                ) {
                    Column {
                        DashboardCardHeading(
                            icon = Icons.Default.Schedule,
                            tint = NeonAmber,
                            title = "CHỜ BẠN DUYỆT",
                            subtitle = "${pendingExtra.size} phiếu xin thêm giờ từ máy con"
                        )
                        pendingExtra.take(3).forEach { req ->
                            val who = req.childName.ifEmpty { "Con" }
                            val mins = req.minutesRequested
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (mins > 0) "$who xin thêm $mins phút"
                                else "$who xin thêm giờ, nhưng phiếu không kèm số phút",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = req.reason.ifEmpty { "Con chưa nêu lý do." },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Duyệt một phiếu không có số phút là tự tay đặt
                                // hạn mức 0, nen nut nay chi mo khi co con so.
                                if (mins > 0) {
                                    Button(
                                        onClick = { onApproveExtraTime(req.id, mins) },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonEmerald.copy(alpha = 0.18f),
                                            contentColor = NeonEmerald
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Duyệt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { onRejectExtraTime(req.id) },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Từ chối", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // (2) Tin nhan gan nhat. `messages` chi duoc ve o tab Chat, nen
            // bo mo app o Tong quan khong biet co tin dang doc.
            val lastMessage: FamilyMessage? = uiState.messages.maxByOrNull { it.createdAt }
            if (lastMessage != null) {
                BentoCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    onClick = { onNavigateToTab(3) },
                    testTag = "dashboard_last_message"
                ) {
                    Column {
                        DashboardCardHeading(
                            icon = Icons.Default.Chat,
                            tint = NeonCyan,
                            title = "TRÒ CHUYỆN GẦN NHẤT",
                            subtitle = lastMessage.senderName.ifEmpty { "Máy bên kia" } +
                                    " • " + minutesAgoLabel(lastMessage.createdAt, System.currentTimeMillis())
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = lastMessage.text.ifEmpty {
                                emotionLabel(lastMessage.emotion)?.let { "Gửi trạng thái: $it" }
                                    ?: "(tin nhắn không có chữ)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2
                        )
                    }
                }
            }

            // (3) Nhat ky noi quy. Danh sach nay o tab Cai dat rat day du, nhung
            // nguoi mo app dau tien lai o Tong quan — noi can biet "ai vua doi
            // noi quy cua con".
            val recentAudit = uiState.ruleAudit
                .sortedByDescending { it.createdAt }
                .take(3)
            if (recentAudit.isNotEmpty()) {
                BentoCard(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                    onClick = { onNavigateToTab(4) },
                    testTag = "dashboard_rule_audit"
                ) {
                    Column {
                        DashboardCardHeading(
                            icon = Icons.Default.History,
                            tint = NeonViolet,
                            title = "NHẬT KÝ NỘI QUY",
                            subtitle = "Ba thay đổi gần nhất giữa hai máy"
                        )
                        recentAudit.forEach { entry ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = ruleAuditLabel(entry),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = minutesAgoLabel(entry.createdAt, System.currentTimeMillis()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        }
                    }
                }
            }

            // Dong bo lan cuoi — chi in khi listener THUC SU keo duoc du lieu
            // ve (lastRealtimeSyncAt = 0 nghia la chua lan nao).
            if (uiState.lastRealtimeSyncAt > 0L) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dữ liệu chung với máy con cách đây " +
                                minutesAgoLabel(uiState.lastRealtimeSyncAt, System.currentTimeMillis()),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
            }
        }
    }
}
}

/**
 * Hang nut co icon trong the Thong tin may con. Truoc day nut nao cung dung
 * mot ky tu bieu cam lam hieu. Ky do he dieu hanh ve, nen cung mot nut o hai
 * may ra hai hinh va kich co lech so voi chu ben canh; icon vector thi app
 * giu duoc mau va kich co.
 */
@Composable
private fun DashboardActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Nhan de chung cho cac the moi o duoi man Tong quan: icon + ten + mot dong phu. */
@Composable
private fun DashboardCardHeading(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

/**
 * Cach doc mot moc thoi gian thanh "X phút trước". Cung kieu noi voi
 * `bumpStateLabel` o man Vi tri de ba noi tren man hinh khong in ra ba cach
 * noi khac nhau cho cung mot khoang thoi gian.
 */
private fun minutesAgoLabel(at: Long, nowMillis: Long): String {
    if (at <= 0L) return "chưa có mốc thời gian"
    val mins = (nowMillis - at) / 60_000L
    return when {
        mins < 1 -> "vừa xong"
        mins < 60 -> "$mins phút trước"
        mins < 1440 -> "${mins / 60} giờ trước"
        else -> "hơn một ngày trước"
    }
}

/**
 * Mot dong nhat ky noi quy. `action` la chuoi luu trong Firestore, khong phai
 * enum, nen gia tri la phai in nguyen van chu khong duoc doan thanh tieng Viet
 * — doan sai la bien mot lan tu choi thanh mot lan duyet.
 */
private fun ruleAuditLabel(entry: RuleAuditEntry): String {
    val who = entry.actorName.ifEmpty {
        if (entry.actorRole == UserRole.CHILD) "Con" else "Phụ huynh"
    }
    val verb = when (entry.action) {
        "de_xuat" -> "đề xuất"
        "dong_y" -> "đã duyệt"
        "tu_choi" -> "từ chối"
        "rut" -> "đã rút"
        "ap_dung" -> "áp dụng"
        "lich_su" -> "lưu lịch sử"
        else -> entry.action.ifEmpty { "thay đổi" }
    }
    val detail = entry.detail
    return if (detail.isEmpty()) "$who $verb" else "$who $verb · $detail"
}

/**
 * Nhan dien `FamilyMessage.emotion` — chuoi luu trong Firestore la TEN cua
 * `EmotionTag` (vi du "STUDY_STRESS"). Truoc day the nay in thang ten enum ra
 * man hinh, khong ai doc hieu. Tra ve tieng Viet cua trang thai do, hoac null
 * neu chuoi khong tron voi enum nao; o day khong dung emoji vi tab Tong quan
 * da quy uoc chi dung icon vector.
 */
private fun emotionLabel(raw: String?): String? =
    raw?.takeIf { it.isNotBlank() }?.let { name ->
        EmotionTag.values().firstOrNull { it.name == name }?.label
            ?: "không rõ (${name.lowercase()})"
    }

