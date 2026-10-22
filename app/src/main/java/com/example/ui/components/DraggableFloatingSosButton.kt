package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalPolice
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.sos.FallbackSmsManager
import com.example.service.sos.SosChannelUsed
import com.example.service.sos.SosPlan
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.SpaceCardSurface
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Nút SOS Nổi di chuyển được (Draggable Floating SOS Button):
 * - Xuất hiện ở mọi giao diện, nhỏ gọn, kéo thả linh hoạt trên màn hình.
 * - Nhấn vào mở bảng điều khiển cứu nạn với 2 lựa chọn rõ ràng:
 *   [1] Gửi SOS tới người thân (có cơ chế đếm ngược 5s chống bấm nhầm), KÈM
 *       hai duong di tiep theo: soan tin nhắn DA dien san toa do, hoac goi.
 *   [2] Gửi SOS & Gọi tới Lực lượng cứu hộ (112, 115, 114, 113).
 * - Kenh goi phu theo may: co Zalo thi co nut "Gọi qua Zalo", khong co thi
 *   chi con "Gọi điện thoại thường" (so nguoi than da luu trong danh ba cua
 *   app). Quyet dinh nam o `SosPlan.callOptions` — xem `SosPlanTest`.
 * - Vi tri (vĩ độ / kinh độ / địa điểm) hien ngay trong bang va duoc doc to
 *   sau 2 giay.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraggableFloatingSosButton(
    currentChannel: SosChannelUsed,
    isSosAllowedNow: Boolean,
    /** null = chua kip kiem tra; false = may khong cai Zalo. */
    zaloInstalled: Boolean?,
    hasCallPermission: Boolean,
    emergencyPhone: String,
    position: SosPlan.Position?,
    /** "Đang chuyển hướng đến cứu hộ" — hien khi may dang mo ban quay so/soan tin. */
    statusMessage: String?,
    onSendSosToFamily: () -> Unit,
    onSendSosToRescue: (hotline: String) -> Unit,
    onCallFamily: (SosPlan.CallChannel) -> Unit,
    onOpenFamilySms: () -> Unit,
    /** Goi khi mo bang: xin diem dinh vi + dia chi may nay, khong dung so lieu cu. */
    onSheetOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSosSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Nút tròn kéo thả
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            NeonRose,
                            Color(0xFFB91C1C)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                .clickable {
                    showSosSheet = true
                    onSheetOpened()
                }
                .padding(8.dp)
                .testTag("floating_sos_button"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "SOS Khẩn cấp",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "SOS",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }

    if (showSosSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSosSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SpaceCardSurface
        ) {
            SosBottomSheetContent(
                currentChannel = currentChannel,
                isSosAllowedNow = isSosAllowedNow,
                zaloInstalled = zaloInstalled,
                hasCallPermission = hasCallPermission,
                emergencyPhone = emergencyPhone,
                position = position,
                statusMessage = statusMessage,
                onDismiss = { showSosSheet = false },
                onSendSosToFamily = {
                    onSendSosToFamily()
                    showSosSheet = false
                },
                onSendSosToRescue = { hotline ->
                    onSendSosToRescue(hotline)
                    showSosSheet = false
                },
                onCallFamily = { channel ->
                    onCallFamily(channel)
                    showSosSheet = false
                },
                onOpenFamilySms = {
                    onOpenFamilySms()
                    showSosSheet = false
                }
            )
        }
    }
}

@Composable
fun SosBottomSheetContent(
    currentChannel: SosChannelUsed,
    isSosAllowedNow: Boolean,
    zaloInstalled: Boolean?,
    hasCallPermission: Boolean,
    emergencyPhone: String,
    position: SosPlan.Position?,
    statusMessage: String?,
    onDismiss: () -> Unit,
    onSendSosToFamily: () -> Unit,
    onSendSosToRescue: (hotline: String) -> Unit,
    onCallFamily: (SosPlan.CallChannel) -> Unit,
    onOpenFamilySms: () -> Unit
) {
    val scrollState = rememberScrollState()
    var countdownSeconds by remember { mutableIntStateOf(5) }
    var isCountingDown by remember { mutableStateOf(false) }

    LaunchedEffect(isCountingDown) {
        if (isCountingDown) {
            while (countdownSeconds > 0) {
                delay(1000L)
                countdownSeconds--
            }
            if (countdownSeconds == 0) {
                onSendSosToFamily()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .testTag("sos_bottom_sheet")
    ) {
        // Tiêu đề
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(NeonRose.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = NeonRose,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "TRỢ GIÚP KHẨN CẤP (SOS)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hệ thống Cứu hộ Thông minh 3 Tầng",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Vi tri cau cuu: ba dong vi do / kinh do / dia diem + loi doc tu dong
        // sau 2 giay. Dat CAU DAU tien trong bang de nguoi dung nhin thay no
        // truoc khi bam bat ky nut nao.
        SosRescuePositionPanel(position = position)

        statusMessage?.let { notice ->
            Spacer(modifier = Modifier.height(10.dp))
            SosStatusStrip(text = notice)
        }

        // Chỉ báo kênh cứu hộ tự động
        // Chi bao "Trực tuyến"/"ngoai tuyen" khi co nguoc that: `currentChannel`
        // do SmartSosDispatcher day len tu NetworkMonitor + ket qua gui lan cuoi.
        // "50m" da boc bo vi Wi-Fi P2P khong tra khoang cach ve app.
        val channelBadgeText = when (currentChannel) {
            SosChannelUsed.INTERNET_CLOUD -> "🌐 Tầng 1: Internet Cloud (Firebase)"
            SosChannelUsed.OFFLINE_MESH_P2P -> "📡 Tầng 2: Mạng P2P Mesh ngoại tuyến"
            SosChannelUsed.FALLBACK_SMS -> "✉️ Tầng 3: Kênh SMS Dự phòng"
        }
        val channelBadgeColor = when (currentChannel) {
            SosChannelUsed.INTERNET_CLOUD -> NeonCyan
            SosChannelUsed.OFFLINE_MESH_P2P -> NeonEmerald
            SosChannelUsed.FALLBACK_SMS -> NeonAmber
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(channelBadgeColor.copy(alpha = 0.12f))
                .border(1.dp, channelBadgeColor.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = channelBadgeColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = channelBadgeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = channelBadgeColor,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (!isSosAllowedNow) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonAmber.copy(alpha = 0.15f))
                    .padding(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lưu ý: Đang ngoài khung giờ SOS quy định, nhưng bạn vẫn có thể gửi cứu hộ trong trường hợp nguy hiểm thực tế.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonAmber
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // [LỰA CHỌN 1]: GỬI SOS CHO NGƯỜI THÂN
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sos_choice_family_card"),
            colors = CardDefaults.cardColors(containerColor = SpaceCardElevated),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonRose.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FamilyRestroom,
                        contentDescription = null,
                        tint = NeonRose,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LỰA CHỌN 1: BÁO ĐỘNG NGƯỜI THÂN",
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonRose,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gửi vị trí GPS và báo động khẩn cấp tới phụ huynh",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isCountingDown) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Đang chuẩn bị phát tín hiệu SOS sau...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Text(
                            text = "$countdownSeconds",
                            style = MaterialTheme.typography.displayMedium,
                            color = NeonRose,
                            fontWeight = FontWeight.Black
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    isCountingDown = false
                                    countdownSeconds = 5
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                            ) {
                                Text("Hủy bỏ")
                            }
                            Button(
                                onClick = {
                                    isCountingDown = false
                                    onSendSosToFamily()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonRose)
                            ) {
                                Text("Gửi ngay lập tức", color = Color.White)
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = { isCountingDown = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_trigger_family_sos"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonRose),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "GỬI SOS CHO NGƯỜI THÂN (5s)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Duong 2a: soan SAN tin nhắn cuu nan kem toa do. Dung
                // ACTION_SENDTO nen may chua cap quyen SMS van dung duoc —
                // chi bam "Gui" trong ung dung tin nhan cua may.
                OutlinedButton(
                    onClick = onOpenFamilySms,
                    enabled = emergencyPhone.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_open_family_sms"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Soạn tin nhắn cứu nạn kèm toạ độ")
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (emergencyPhone.isBlank())
                        SosPlan.NO_SAVED_NUMBER
                    else
                        "Tin nhắn đã điền sẵn vĩ độ, kinh độ và địa điểm — bấm Gửi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hoặc gọi ngay cho người thân:",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )

                // Quyet dinh "may co Zalo hay khong" nam o SosPlan; UI chi ve
                // nut theo dung danh sach do tra ve.
                SosPlan.callOptions(zaloInstalled == true, emergencyPhone).forEach { option ->
                    Spacer(modifier = Modifier.height(8.dp))
                    if (option.isZalo) {
                        OutlinedButton(
                            onClick = { onCallFamily(option.channel) },
                            enabled = option.enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_call_via_zalo"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.label)
                        }
                    } else {
                        Button(
                            onClick = { onCallFamily(option.channel) },
                            enabled = option.enabled,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_call_saved_number"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonEmerald,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option.label, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = option.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // [LỰA CHỌN 2]: GỬI SOS CHO LỰC LƯỢNG CỨU HỘ
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sos_choice_rescue_card"),
            colors = CardDefaults.cardColors(containerColor = SpaceCardElevated),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, SpaceCardBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "LỰA CHỌN 2: LỰC LƯỢNG CỨU HỘ",
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gửi SMS định dạng khẩn cấp & Mở cuộc gọi cứu nạn",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val hotlines = listOf(
                    Triple("112", "Cứu nạn, Cứu hộ Quốc gia", NeonRose),
                    Triple("115", "Cấp cứu Y tế Khẩn cấp", NeonEmerald),
                    Triple("114", "Cứu hỏa & Cứu sập đổ", NeonAmber),
                    Triple("113", "Công an & An ninh trật tự", NeonCyan)
                )

                hotlines.forEach { (number, desc, color) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DialogSurface)
                            .border(1.dp, SpaceCardBorder, RoundedCornerShape(10.dp))
                            .clickable { onSendSosToRescue(number) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("hotline_item_$number"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = number,
                                    color = color,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Hotline $number",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Gọi $number",
                            tint = color,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                // Noi TRUOC ket qua de nguoi dung khong tuong bam xong la may
                // tu goi: 113 tren may chua cap quyen goi thi chi mo ban quay so.
                // Toa do KHONG di SMS toi tong dai (ma ngan thoai chi), no di
                // toi so nguoi than da luu — noi dung phai khop voi loi ap lam.
                Text(
                    text = "Bấm một số: app " +
                        (if (hasCallPermission) "gọi thẳng tổng đài." else "mở bàn quay số — bấm Gọi để kết nối.") +
                        " Toạ độ được gửi SMS cho số người thân đã lưu. " + SosPlan.REDIRECTING_TO_RESCUE,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
