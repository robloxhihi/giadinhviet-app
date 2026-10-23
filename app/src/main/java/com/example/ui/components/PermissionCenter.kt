package com.example.ui.components

import android.content.Context
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.PermissionManager
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.Premium
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

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

    // ── Luong "Cap tat ca quyen con thieu" ─────────────────────────────────
    val appContext = LocalContext.current
    val activityLifecycle = LocalLifecycleOwner.current.lifecycle
    var grantAllActive by remember { mutableStateOf(false) }
    var grantAllMessage by remember { mutableStateOf<String?>(null) }

    // Chay tung buoc: mot hop thoai runtime truoc, roi lan luot tung man hinh quyen
    // dac biet; moi luot quay ve, trang thai duoc doc LAI TU ANDROID qua
    // PermissionManager. Chi tiet trong runGrantAllMissing.
    LaunchedEffect(grantAllActive) {
        if (!grantAllActive) return@LaunchedEffect
        runGrantAllMissing(
            context = appContext,
            lifecycle = activityLifecycle,
            onRequestRuntime = onRequestRuntime,
            onOpenSpecial = onOpenSpecial,
            onRecheck = onRecheck,
            onProgress = { grantAllMessage = it },
            isActive = { grantAllActive }
        )
        grantAllActive = false
    }

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
                Spacer(modifier = Modifier.height(8.dp))
                // Nut "Cấp tất cả quyền còn thiếu": huong dan thu mot luong lien tuc.
                // Button chi MO man hinh he thong cho tung quyen dac biet — Android
                // khong cho phep bat cac quyen do bang code, va khong noi dung khac.
                Button(
                    onClick = { grantAllActive = !grantAllActive },
                    enabled = grantAllActive || items.any { !it.granted },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (grantAllActive) NeonAmber else NeonEmerald,
                        contentColor = Color(0xFF05060A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (grantAllActive) "Huỷ luồng cấp quyền" else "Cấp tất cả quyền còn thiếu",
                        fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
                grantAllMessage?.let { msg ->
                    Text(
                        msg,
                        color = NeonAmber, fontSize = 11.sp, lineHeight = 14.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
            if (!item.granted && item.permanentlyDenied) {
                // Noi that: hop thoai xin quyen se khong con hien ra nua.
                Text(
                    "Bạn đã chọn \"Không hỏi lại\" — hộp thoại hệ thống không còn hiện; chỉ bật lại được trong Cài đặt ứng dụng.",
                    color = NeonAmber, fontSize = 10.sp, lineHeight = 13.sp
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (item.granted) {
            Text("Đã cấp", color = NeonEmerald, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        } else {
            Button(
                onClick = {
                    if (item.kind == PermissionManager.Kind.RUNTIME && !item.permanentlyDenied) {
                        val perms = PermissionManager.runtimePermissionsFor(item.id)
                        if (perms.isNotEmpty()) onRequestRuntime(perms) else onOpenSpecial(item)
                    } else {
                        // SPECIAL hoac runtime bi "Don't ask again": MainActivity dieu
                        // huong dung loi (man hinh he thong / thong tin ung dung).
                        onOpenSpecial(item)
                    }
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color(0xFF05060A)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text(
                    if (item.permanentlyDenied) "Mở Cài đặt"
                    else if (item.kind == PermissionManager.Kind.SPECIAL) "Mở settings"
                    else "Cấp",
                    fontWeight = FontWeight.Bold, fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * "Cap tat ca quyen con thieu" — huoc nguoi dung qua nhung gi chua duoc cap trong
 * mot luong lien tuc: truoc het MOT hop thoai runtime cho toan bo quyen thieu, sau
 * do lan luot tung man hinh quyen dac biet (usage, overlay, battery, accessibility,
 * background location).
 *
 * That thuat quan trong:
 * - Moi luot quay ve app, trang thai duoc DOC LAI THAT tu Android (PermissionManager),
 *   khong bao gio tin co so luu tru.
 * - Moi muc chi xu ly TAI MAX MOT lan, moi buoc co gioi han thoi gian, va dung som
 *   neu nguoi dung tu choi lien tiep — khong the vong lap vo han.
 * - Voi quyen dac biet, nut chi MO man hinh he thong de nguoi dung tu bat; Android
 *   cam code tu bat cac quyen do.
 */
private suspend fun runGrantAllMissing(
    context: Context,
    lifecycle: Lifecycle,
    onRequestRuntime: (Array<String>) -> Unit,
    onOpenSpecial: (PermissionManager.PermissionItem) -> Unit,
    onRecheck: () -> Unit,
    onProgress: (String) -> Unit,
    isActive: () -> Boolean
) {
    // ── Buoc 1: toan bo quyen runtime con thieu trong MOT hop thoai he thong ──
    // Quyen da bi "Don't ask again" bi loai khoi lo nay vi se khong hien hop thoai;
    // chung di qua man hinh Thong tin ung dung (nut "Mở Cài đặt" tung dong).
    val missingRuntime = PermissionManager.missingRuntimePermissions(context)
        .filter { !PermissionManager.isPermanentlyDenied(it) }
        .toTypedArray()
    if (missingRuntime.isNotEmpty()) {
        onProgress("Đang mở hộp thoại quyền của hệ thống — hãy chọn Cho phép…")
        val settledBefore = PermissionManager.runtimeRequestSettledCount()
        onRequestRuntime(missingRuntime)
        // Cho den khi nguoi dung tra loi hop thoai (dem tang trong
        // PermissionManager.recordRequestOutcome), toi da 5 phut, co the Huy.
        var waited = 0
        while (PermissionManager.runtimeRequestSettledCount() == settledBefore && waited < 300_000) {
            delay(500); waited += 500
            if (!isActive()) return
        }
        onRecheck()
    }

    // ── Buoc 2: lan luot tung man hinh quyen dac biet, thu tu co dinh ──
    val order = listOf("usage", "overlay", "battery", "accessibility", "background_location")
    var declinedInRow = 0
    for (id in order) {
        if (!isActive()) return
        if (specialGrantedNow(context, id)) continue // bo qua muc da duoc cap
        if (id == "background_location" &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                !PermissionManager.hasForegroundLocation(context))
        ) {
            // Android 10+ chi cap quyen nen khi foreground location DA duoc chap
            // thuan; xin som hon bi tu choi tham — bo qua va ghi ro ly do.
            onProgress("Bỏ qua Vị trí nền: cần chấp thuận Vị trí (GPS) trước rồi bấm Cấp riêng mục này.")
            continue
        }
        val item = PermissionManager.snapshot(context).firstOrNull { it.id == id } ?: continue
        onProgress("Mở màn hình hệ thống cho “${item.title}” — hãy bật quyền ở đó rồi quay lại app.")
        onOpenSpecial(item)

        var waited = 0
        // Choi app bi che khi man hinh he thong mo ra; toi da 20 giay neu co bi che.
        while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && waited < 20_000) {
            delay(300); waited += 300
            if (!isActive()) return
        }
        // Choi nguoi dung quay ve tu man hinh he thong, toi da 3 phut cho tung muc.
        waited = 0
        while (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && waited < 180_000) {
            delay(300); waited += 300
            if (!isActive()) return
        }
        // Da quay ve: doc lai trang thai THAT tu Android, them 10 giay de he thong dong bo.
        waited = 0
        while (!specialGrantedNow(context, id) && waited < 10_000) {
            delay(500); waited += 500
            if (!isActive()) return
        }
        onRecheck()
        if (specialGrantedNow(context, id)) {
            declinedInRow = 0
        } else {
            declinedInRow++
            onProgress("Vẫn chưa bật: ${item.title} — bạn có thể bấm “Mở settings” riêng cho mục này sau.")
            if (declinedInRow >= 2) {
                onProgress("Dừng luồng: bạn đang từ chối liên tiếp. Mở lại Trung tâm quyền khi sẵn sàng.")
                return
            }
        }
    }
    onProgress("Hoàn tất — trạng thái từng quyền luôn được đọc lại trực tiếp từ Android.")
    delay(1_500)
}

/** Doc trang thai THAT cua tung quyen dac biet truc tiep tu he dieu hanh. */
private fun specialGrantedNow(context: Context, id: String): Boolean = when (id) {
    "usage" -> PermissionManager.hasUsageAccess(context)
    "overlay" -> PermissionManager.hasOverlay(context)
    "battery" -> PermissionManager.isIgnoringBatteryOptimizations(context)
    "accessibility" -> PermissionManager.hasAccessibilityEnabled(context)
    "background_location" -> PermissionManager.hasBackgroundLocation(context)
    else -> true
}
