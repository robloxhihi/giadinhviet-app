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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ExtraTimeDialog(
    onDismiss: () -> Unit,
    onSubmit: (minutes: Int, reason: String) -> Unit
) {
    var selectedMinutes by remember { mutableStateOf(30) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "⏱️ Xin thêm thời gian sử dụng",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Chọn số phút muốn xin thêm:",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 60).forEach { mins ->
                        val isSelected = selectedMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonAmber.copy(alpha = 0.25f) else SpaceCardElevated)
                                .border(1.dp, if (isSelected) NeonAmber else SpaceCardBorder, RoundedCornerShape(10.dp))
                                .clickable { selectedMinutes = mins }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+$mins ph",
                                color = if (isSelected) TextPrimary else TextMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Lý do (vd: Học online, làm bài tập)") },
                    modifier = Modifier.fillMaxWidth().testTag("extra_time_reason_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonAmber,
                        unfocusedBorderColor = SpaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedMinutes, reason) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("submit_extra_time_btn")
            ) {
                Text("Gửi yêu cầu", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DialogSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}

@Composable
fun PrivacyDialog(
    onDismiss: () -> Unit,
    onSubmit: (startDate: String, startHour: Int, startMinute: Int, endDate: String, endHour: Int, endMinute: Int, reason: String) -> Unit
) {
    val todayStr = remember {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
    }
    val tomorrowStr = remember {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(cal.time)
    }

    var startDate by remember { mutableStateOf(todayStr) }
    var endDate by remember { mutableStateOf(todayStr) }
    var startHour by remember { mutableStateOf(14) }
    var endHour by remember { mutableStateOf(17) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "🛡️ Thiết lập Khung giờ riêng tư",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Khi phụ huynh phê duyệt, vị trí GPS sẽ được ẩn an toàn trong khung giờ này. Kênh Chat và Cuộc gọi vẫn hoạt động bình thường!",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Ngày áp dụng
                Text(text = "Chọn ngày áp dụng:", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (startDate == todayStr && endDate == todayStr) NeonCyan.copy(alpha = 0.3f) else SpaceCardElevated)
                            .border(1.dp, if (startDate == todayStr && endDate == todayStr) NeonCyan else SpaceCardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                startDate = todayStr
                                endDate = todayStr
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Hôm nay ($todayStr)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (startDate == tomorrowStr) NeonCyan.copy(alpha = 0.3f) else SpaceCardElevated)
                            .border(1.dp, if (startDate == tomorrowStr) NeonCyan else SpaceCardBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                startDate = tomorrowStr
                                endDate = tomorrowStr
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "Ngày mai ($tomorrowStr)", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Giờ bắt đầu và Giờ kết thúc
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Từ (Giờ):", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(8, 12, 14, 18).forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (startHour == h) NeonCyan.copy(alpha = 0.35f) else SpaceCardElevated)
                                        .border(1.dp, if (startHour == h) NeonCyan else SpaceCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { startHour = h }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "$h:00", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Đến (Giờ):", style = MaterialTheme.typography.labelSmall, color = NeonCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(12, 16, 17, 21).forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (endHour == h) NeonCyan.copy(alpha = 0.35f) else SpaceCardElevated)
                                        .border(1.dp, if (endHour == h) NeonCyan else SpaceCardBorder, RoundedCornerShape(8.dp))
                                        .clickable { endHour = h }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "$h:00", color = TextPrimary, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Lý do (vd: Học thêm môn Toán / Nghỉ trưa)") },
                    modifier = Modifier.fillMaxWidth().testTag("privacy_reason_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = SpaceCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(startDate, startHour, 0, endDate, endHour, 0, reason)
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("submit_privacy_btn")
            ) {
                Text("Gửi cho Phụ huynh duyệt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", color = TextSecondary)
            }
        },
        containerColor = DialogSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary
    )
}
