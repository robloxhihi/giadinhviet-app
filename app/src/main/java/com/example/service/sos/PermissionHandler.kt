package com.example.service.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat

/**
 * Quản lý và kiểm tra quyền runtime (Vị trí chính xác, Bluetooth quét lân cận, Gửi SMS, Thông báo)
 * một cách thanh thoát, minh bạch và an toàn theo chuẩn Google Play.
 */
object PermissionHandler {

    /**
     * Danh sách tất cả các quyền cần thiết cho hệ thống Cứu nạn & Giám sát an toàn
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )

        // Android 12+ (API 31+) yêu cầu quyền Bluetooth chuyên biệt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        // Android 13+ (API 33+) yêu cầu thông báo & thiết bị wifi gần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        return permissions
    }

    /**
     * Kiểm tra xem một quyền cụ thể đã được cấp hay chưa
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Kiểm tra các quyền cốt lõi cho SOS (Vị trí & SMS)
     */
    fun hasCoreSosPermissions(context: Context): Boolean {
        val hasLoc = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val hasSms = hasPermission(context, Manifest.permission.SEND_SMS)
        return hasLoc && hasSms
    }

    /**
     * Lọc ra những quyền chưa được cấp để xin cấp quyền một cách tối giản
     */
    fun getMissingPermissions(context: Context): Array<String> {
        return getRequiredPermissions().filter { !hasPermission(context, it) }.toTypedArray()
    }
}

/**
 * Composable Helper launcher để khởi tạo và kích hoạt luồng xin quyền dễ dàng
 */
@Composable
fun rememberSmartPermissionLauncher(
    onPermissionsResult: (allGranted: Boolean, deniedPermissions: List<String>) -> Unit
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys.toList()
        val allGranted = denied.isEmpty()
        onPermissionsResult(allGranted, denied)
    }
}
