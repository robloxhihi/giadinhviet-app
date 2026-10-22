package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonRose

@Composable
fun SosAlertBanner(
    message: String,
    onCallSos: (() -> Unit)? = null
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NeonRose.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = NeonRose,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        if (onCallSos != null) {
            Spacer(modifier = Modifier.height(10.dp))
            androidx.compose.material3.Button(
                onClick = onCallSos,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = NeonRose,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📞 GỌI WEBRTC KHẨN CẤP NGAY", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SosEmergencyWarningDialog(
    message: String,
    channel: com.example.service.sos.SosChannelUsed,
    /** Trang thai may dang lam gi — mac dinh la dong chu nguoi dung yeu cau. */
    statusMessage: String = com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE,
    /** Vi tri hien tren man hinh va doc ra sau 2 giay. null = chua do duoc GPS. */
    position: com.example.service.sos.SosPlan.Position? = null,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = NeonRose,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "🚨 CẢNH BÁO SOS KHẨN CẤP",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = NeonRose,
                    fontWeight = FontWeight.Black
                )
            }
        },
        text = {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = statusMessage,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("sos_status_strip")
                )
                // Vi tri cau cuu in THAT MAN va doc ra sau 2 giay: nguoi dung
                // khong kip nhin thi con kip nghe.
                position?.let { spot ->
                    Spacer(modifier = Modifier.height(8.dp))
                    SosRescuePositionPanel(spot)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = message,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = com.example.ui.theme.TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                val channelName = when(channel) {
                    com.example.service.sos.SosChannelUsed.INTERNET_CLOUD -> "Internet Cloud (Firebase)"
                    com.example.service.sos.SosChannelUsed.OFFLINE_MESH_P2P -> "Mạng ngoại tuyến Mesh P2P (<50m)"
                    com.example.service.sos.SosChannelUsed.FALLBACK_SMS -> "Tin nhắn khẩn cấp SMS kèm GPS"
                }
                Text(
                    text = "Kênh truyền đang hoạt động: $channelName",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = com.example.ui.theme.NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = NeonRose,
                    contentColor = Color.White
                )
            ) {
                Text("Đã hiểu / Tắt cảnh báo", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = com.example.ui.theme.DialogSurface,
        titleContentColor = com.example.ui.theme.TextPrimary,
        textContentColor = com.example.ui.theme.TextSecondary
    )
}

