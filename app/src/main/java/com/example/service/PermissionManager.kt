package com.example.service

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
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
        val required: Boolean = true,
        /**
         * RUNTIME + chua duoc cap + nguoi dung da chon "Don't ask again" o hop thoai
         * he thong. requestPermissions khi do tra loi NGAY, khong hien hop thoai, nen
         * loi duyet duy con lai la man hinh Thong tin ung dung
         * (ACTION_APPLICATION_DETAILS_SETTINGS).
         */
        val permanentlyDenied: Boolean = false
    )

    /** Ten lop day du cua AccessibilityService chan ung dung (khop AndroidManifest). */
    private const val ACCESSIBILITY_SERVICE_CLASS = "com.example.service.blocker.AppBlockerService"

    // ── Theo doi "tu choi vinh vien" ───────────────────────────────────────
    // Only touched from the main thread (launcher callbacks and snapshot readers).
    private val permanentlyDeniedPermissions = mutableSetOf<String>()
    private var requestSettledCounter = 0

    /**
     * MainActivity goi SAU moi khi nguoi dung tra loi hop thoai quyen runtime.
     * [showRationale] phai la Activity::shouldShowRequestPermissionRationale — chi
     * Android moi biet nguoi dung chon "Tu choi" hay "Khong hoi nua".
     * Tu choi mot lan (con rationale) = van xin lai duoc; tu choi ma khong con
     * rationale = "Don't ask again" -> danh dau vinh vien. Quyen duoc cap thi xoa dau.
     */
    fun recordRequestOutcome(results: Map<String, Boolean>, showRationale: (String) -> Boolean) {
        requestSettledCounter++
        results.forEach { (permission, granted) ->
            if (granted || showRationale(permission)) {
                permanentlyDeniedPermissions.remove(permission)
            } else {
                permanentlyDeniedPermissions.add(permission)
            }
        }
    }

    fun isPermanentlyDenied(permission: String): Boolean =
        permanentlyDeniedPermissions.contains(permission)

    /**
     * Bo dem "hop thoai quyen da duoc tra loi" — luong "Cap tat ca" dung no de biet
     * nguoi dung da xong hop thoai he thong ma khong can doan thoi gian.
     */
    fun runtimeRequestSettledCount(): Int = requestSettledCounter

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

    /** Vi tri foreground (FINE hoac COARSE) — dieu kien tien quyet cua quyen nen tren Android 10+. */
    fun hasForegroundLocation(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

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

    /**
     * Trang thai THAT cua dich vu tro nang (AccessibilityService) chan ung dung.
     * Doc truc tiep tu Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES — danh sach
     * cac dich vu duoc bat, phan cach nhau bang dau ':' — va so sanh voi chinh
     * ComponentName cua app. KHONG tin bat ky co so duoc luu tru nao.
     */
    fun hasAccessibilityEnabled(context: Context): Boolean {
        val enabled = try {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        } catch (e: Exception) {
            null
        } ?: return false
        // unflattenFromString tu chuan hoa dang viet tat ".TenLoai" ma mot so OEM luu.
        val pkg = context.packageName
        return enabled.split(':').any { entry ->
            val parsed = ComponentName.unflattenFromString(entry) ?: return@any false
            parsed.packageName == pkg &&
                parsed.className.equals(ACCESSIBILITY_SERVICE_CLASS, ignoreCase = true)
        }
    }

    /**
     * Dịch vụ định vị CỦA MÁY có đang bật không — khác hẳn quyền của app.
     *
     * Cấp đủ quyền mà máy tắt GPS thì không fix nào tồn tại; trước đây màn
     * Vị trí chỉ in "Chưa dò được GPS" nên người dùng không biết phải bật gì.
     */
    @Suppress("DEPRECATION")
    fun isLocationServicesEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE)
            as? android.location.LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            lm.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        }
    }

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

    /**
     * Man hinh Thong tin ung dung — loi duy nhat con lai khi quyen runtime bi
     * "Don't ask again" (requestPermissions khi do khong hien hop thoai nao).
     */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * AccessibilityService: Android cam code tu bat — nut chi MO man hinh Tro nang
     * de nguoi dung tu bat; trang thai duoc doc lai that tu Settings.Secure.
     */
    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Màn hình bật/tắt dịch vụ định vị của máy (không phải quyền của app). */
    @Suppress("DEPRECATION")
    fun locationServicesIntent(): Intent =
        Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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

        val locFine = hasForegroundLocation(context)
        items += PermissionItem(
            "location", "Vị trí (GPS)", "Định vị thời gian thực và gửi tọa độ khi khẩn cấp.",
            locFine, Kind.RUNTIME
        )
        items += PermissionItem(
            "background_location", "Vị trí khi chạy nền",
            "BẮT BUỘC để định vị chạy ngầm: không bật quyền này thì Android dừng dịch vụ " +
                "ngay khi đóng app, bản đồ ngừng cập nhật vị trí của con. " +
                "Android 10 trở lên chỉ cấp được qua hộp thoại hệ thống và sau khi Vị trí (GPS) đã được chấp thuận — bấm nút để mở hộp thoại xin quyền nền.",
            hasBackgroundLocation(context), Kind.SPECIAL, required = true
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
            "accessibility", "Trợ năng (chặn ứng dụng)",
            "Quyền quan trọng nhất để chặn ứng dụng theo lịch hoạt động. " +
                "Android cấm ứng dụng tự bật quyền này — bấm nút chỉ mở màn hình Trợ năng để bạn tự bật Gia Đình Việt.",
            hasAccessibilityEnabled(context), Kind.SPECIAL
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
            "BẮT BUỘC để chạy ngầm ổn định: nếu để Android tối ưu pin, hệ thống sẽ " +
                "ngắt dịch vụ định vị/ chặn ứng dụng khi máy nghỉ (Doze) — bản đồ ngừng cập nhật " +
                "và nội quy không còn áp dụng. Bấm nút rồi chọn \"Không tối ưu hóa\".",
            isIgnoringBatteryOptimizations(context), Kind.SPECIAL, required = true
        )
        // Danh dau "tu choi vinh vien" (Don't ask again) cho cac quyen runtime chua cap.
        // Trang thai granted luon doc truc tiep tu Android; co so nay chi bo sung loi duyet,
        // va duoc xoa khi quyen duoc cap o bat ky dau.
        return items.map { item ->
            if (item.kind == Kind.RUNTIME && !item.granted &&
                runtimePermissionsFor(item.id).any { isPermanentlyDenied(it) }
            ) item.copy(permanentlyDenied = true) else item
        }
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
