package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.PermissionManager
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.Premium
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Thẻ cảnh báo quyền — hiện ở đầu màn hình khi còn quyền bắt buộc chưa được cấp.
 * Bấm vào sẽ mở Trung tâm quyền.
 */
@Composable
fun PermissionCenterBanner(
    missingRequired: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (missingRequired <= 0) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(NeonAmber.copy(alpha = 0.16f))
            .border(1.dp, NeonAmber.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = NeonAmber, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Còn $missingRequired quyền chưa được cấp",
                color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            Text(
                "Bấm để hoàn tất quyền — GPS, SOS, thông báo và dữ liệu sử dụng.",
                color = TextSecondary, fontSize = 11.sp
            )
        }
        Text("Mở", color = NeonAmber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/**
 * Trung tâm quyền: liệt kê từng quyền với trạng thái THẬT và nút cấp quyền.
 * Mỗi lần mở lại (hoặc khi app resume) trạng thái được đọc lại từ Android.
 */
@Composable
fun PermissionCenterDialog(
    items: List<PermissionManager.PermissionItem>,
    onRequestRuntime: (Array<String>) -> Unit,
    onOpenSpecial: (PermissionManager.PermissionItem) -> Unit,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit
) {
    val granted = items.count { it.granted }
    val total = items.size

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Trung tâm quyền", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("$granted/$total quyền đã cấp", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else granted.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = NeonEmerald,
                    trackColor = Premium.glassFillTop
                )
                Spacer(modifier = Modifier.height(2.dp))
                items.forEach { item ->
                    PermissionRow(item, onRequestRuntime, onOpenSpecial)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Mẹo: sau khi cấp quyền, quay lại app — trạng thái sẽ tự cập nhật ngay.",
                    color = TextMuted, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onRecheck() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF05060A))
            ) { Text("Kiểm tra lại", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Đóng", color = TextSecondary) }
        }
    )
}

@Composable
private fun PermissionRow(
    item: PermissionManager.PermissionItem,
    onRequestRuntime: (Array<String>) -> Unit,
    onOpenSpecial: (PermissionManager.PermissionItem) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (item.granted) Premium.glassFillBottom else Premium.glassFillTop)
            .border(
                1.dp,
                if (item.granted) NeonEmerald.copy(alpha = 0.5f) else NeonAmber.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (item.granted) NeonEmerald.copy(alpha = 0.18f) else NeonAmber.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (item.granted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (item.granted) NeonEmerald else NeonAmber,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title + if (!item.required) "  (tùy chọn)" else "",
                color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp
            )
            Text(item.rationale, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (item.granted) {
            Text("Đã cấp", color = NeonEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        } else {
            Button(
                onClick = {
                    if (item.kind == PermissionManager.Kind.RUNTIME) {
                        val perms = PermissionManager.runtimePermissionsFor(item.id)
                        if (perms.isNotEmpty()) onRequestRuntime(perms) else onOpenSpecial(item)
                    } else {
                        onOpenSpecial(item)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF05060A)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) { Text("Cấp", fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        }
    }
}
