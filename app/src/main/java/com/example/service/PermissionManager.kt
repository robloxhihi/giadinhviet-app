package com.example.service

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Trung tâm quyền (Permission Center) — đọc TRẠNG THÁI THẬT của Android và
 * cung cấp cách xin từng quyền đúng thời điểm, kèm lý do (rationale).
 *
 * Sửa lỗi "đã cấp quyền mà app vẫn báo chưa cấp": mọi trạng thái ở đây được
 * đọc trực tiếp từ hệ điều hành mỗi lần [snapshot] được gọi (gọi lại khi app resume).
 */
object PermissionManager {

    enum class Kind { RUNTIME, SPECIAL }

    data class PermissionItem(
        val id: String,
        val title: String,
        val rationale: String,
        val granted: Boolean,
        val kind: Kind,
        val required: Boolean = true
    )

    // ── Runtime permissions cần cho toàn bộ chức năng ──────────────────────
    fun runtimePermissions(): List<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list += Manifest.permission.BLUETOOTH_SCAN
            list += Manifest.permission.BLUETOOTH_ADVERTISE
            list += Manifest.permission.BLUETOOTH_CONNECT
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list += Manifest.permission.POST_NOTIFICATIONS
            list += Manifest.permission.NEARBY_WIFI_DEVICES
        }
        return list
    }

    fun missingRuntimePermissions(context: Context): Array<String> =
        runtimePermissions().filter { !isGranted(context, it) }.toTypedArray()

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // ── Special access ─────────────────────────────────────────────────────
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true

    fun hasNotificationPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)
        } else true

    fun hasBackgroundLocation(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else true

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    // ── Settings intents ───────────────────────────────────────────────────
    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun overlayIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun notificationSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun backgroundLocationSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Suppress("BatteryLife")
    fun batteryOptimizationIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Ảnh chụp trạng thái quyền hiện tại, đọc trực tiếp từ Android.
     */
    fun snapshot(context: Context): List<PermissionItem> {
        val items = mutableListOf<PermissionItem>()

        val locFine = isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        items += PermissionItem(
            "location", "Vị trí (GPS)", "Định vị thời gian thực và gửi tọa độ khi khẩn cấp.",
            locFine, Kind.RUNTIME
        )
        items += PermissionItem(
            "background_location", "Vị trí khi chạy nền",
            "Cập nhật vị trí liên tục ngay cả khi đóng ứng dụng (giám sát an toàn 24/7).",
            hasBackgroundLocation(context), Kind.SPECIAL, required = false
        )
        items += PermissionItem(
            "sms", "Tin nhắn SMS",
            "Gửi SOS cứu nạn kèm tọa độ khi mất mạng/không có Internet.",
            isGranted(context, Manifest.permission.SEND_SMS), Kind.RUNTIME
        )
        items += PermissionItem(
            "phone", "Gọi điện khẩn cấp",
            "Gọi trực tiếp tới đường dây nóng 112/115/113/114 khi cần.",
            isGranted(context, Manifest.permission.CALL_PHONE), Kind.RUNTIME, required = false
        )
        items += PermissionItem(
            "notifications", "Thông báo",
            "Hiển thị cảnh báo SOS và trạng thái giám sát ở thanh thông báo.",
            hasNotificationPermission(context), Kind.RUNTIME
        )
        items += PermissionItem(
            "usage", "Truy cập dữ liệu sử dụng",
            "Đo thời gian dùng từng ứng dụng để thống kê & nhắc nhở cân bằng.",
            hasUsageAccess(context), Kind.SPECIAL
        )
        items += PermissionItem(
            "overlay", "Hiển thị trên ứng dụng khác",
            "Nút SOS nổi luôn hiện trên mọi màn hình để gọi cứu trợ tức thì.",
            hasOverlay(context), Kind.SPECIAL, required = false
        )
        items += PermissionItem(
            "camera", "Camera", "Gọi video gia đình và đính kèm ảnh trong chat.",
            isGranted(context, Manifest.permission.CAMERA), Kind.RUNTIME, required = false
        )
        items += PermissionItem(
            "microphone", "Micro", "Đàm thoại thoại/video và ghi âm tin nhắn thoại.",
            isGranted(context, Manifest.permission.RECORD_AUDIO), Kind.RUNTIME, required = false
        )
        items += PermissionItem(
            "battery", "Tối ưu pin (tắt)",
            "Cho phép dịch vụ giám sát chạy liên tục, không bị hệ thống tạm dừng.",
            isIgnoringBatteryOptimizations(context), Kind.SPECIAL, required = false
        )
        return items
    }

    fun allRequiredGranted(context: Context): Boolean =
        snapshot(context).filter { it.required }.all { it.granted }

    /** Các quyền runtime cụ thể ứng với một mục trong Trung tâm quyền. */
    fun runtimePermissionsFor(id: String): Array<String> = when (id) {
        "location" -> arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        "sms" -> arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        "phone" -> arrayOf(Manifest.permission.CALL_PHONE)
        "notifications" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        "camera" -> arrayOf(Manifest.permission.CAMERA)
        "microphone" -> arrayOf(Manifest.permission.RECORD_AUDIO)
        else -> emptyArray()
    }
}
