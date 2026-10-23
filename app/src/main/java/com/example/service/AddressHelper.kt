package com.example.service

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale

/**
 * Helper biến toạ độ thành địa chỉ tiếng Việt đọc hiểu được.
 *
 * Nguyên tắc trung thực (bắt buộc vì app dùng cho SOS và phụ huynh giám sát con):
 *  • Không bao giờ trả về một "địa chỉ" trông như thật khi reverse-geocode thất bại.
 *  • Không có fix (vĩ độ/kinh độ = 0.0 theo hợp đồng `LocationData.hasRealFix`) → [NO_FIX_ADDRESS].
 *  • Geocoder bị chặn/timeout → trả toạ độ CÓ NHÃN "chưa phân giải được địa chỉ".
 */
object AddressHelper {

    /**
     * Chuỗi chiếm chỗ THÀNH THẬT. Trước đây là "Đang cập nhật vị trí thực tế..." —
     * nghe như một địa chỉ đang về; đổi lại thành câu nói rõ "chưa có địa chỉ".
     * Lưu ý cho phiên chính: [com.example.ui.components.GoogleMapsStudentLocationCard]
     * dòng ~131 dùng hằng số này làm nhãn trong lúc chờ geocode — nếu muốn phân biệt
     * "đang tải" và "không có địa chỉ" thì chuyển sang [LOADING_ADDRESS] ở đó.
     */
    const val DEFAULT_ADDRESS = "Chưa xác định được địa chỉ"

    /** Thiết bị chưa định vị được GPS — phân biệt với "có toạ độ nhưng geocoder fail". */
    const val NO_FIX_ADDRESS = "Chưa định vị được — thiết bị chưa có GPS"

    /** Chỉ dùng cho trạng thái "đang truy vấn", không được ghi vào Firestore. */
    const val LOADING_ADDRESS = "Đang xác định vị trí…"

    /** Geocoder chặn thread; quá hạn này thì bỏ, trả nhãn trung thực. */
    private const val GEOCODER_TIMEOUT_MS = 4_000L

    private const val TAG = "AddressHelper"

    /** Cùng bộ kiểm tra với `LocationData.hasRealFix` trong Models.kt. */
    fun isPlausibleCoordinates(latitude: Double, longitude: Double): Boolean =
        !(latitude == 0.0 && longitude == 0.0) &&
            !latitude.isNaN() && !longitude.isNaN() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0

    suspend fun getReadableAddress(context: Context, latitude: Double, longitude: Double): String {
        if (!isPlausibleCoordinates(latitude, longitude)) {
            return NO_FIX_ADDRESS
        }

        val resolved = try {
            withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
                withContext(Dispatchers.IO) { reverseGeocode(context.applicationContext, latitude, longitude) }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Reverse geocoding IOException: ${e.message}")
            null
        } catch (e: Exception) {
            // Geocoder còn ném IllegalArgumentException/ServiceDiedException trên một số máy.
            Log.w(TAG, "Reverse geocoding exception: ${e.message}")
            null
        }

        if (!resolved.isNullOrBlank()) return resolved

        // Không bịa địa chỉ: chỉ trả toạ độ thật và nói thẳng là chưa dịch được.
        // Khong co sai so o day thi phai tinh la xau nhat (±100 m) — in 4 le la
        // khang dinh ~11 m ma chuyen den khong he do duoc.
        return "Tọa độ GPS (${com.example.service.map.FixPrecision.formatCoordinates(
            latitude, longitude, com.example.service.map.FixPrecision.UNKNOWN_ACCURACY_M
        )}) — chưa phân giải được địa chỉ"
    }

    /**
     * Bản block của `Geocoder.getFromLocation(DDI)`. `isPresent()` và bản synchronous này bị
     * đánh dấu deprecated từ API 33, nhưng bản async (GeocodeListener) CHỈ có từ API 33
     * trong khi minSdk = 24 → buộc phải giữ và suppressing cảnh báo.
     */
    @Suppress("DEPRECATION")
    /**
     * Nhãn đường mà Google trả về cho một điểm chưa được bản đồ hoá.
     *
     * Đây KHÔNG phải lỗi định vị: toạ độ vẫn đúng, chỉ cái tên thì không ai đặt.
     * Nhưng in ra "Đường Chưa Đặt Tên, Huyện X, Tỉnh Y" trong một card có viền
     * xanh trông y như một địa chỉ đã xác minh — phụ huynh đọc rồi tin con đang ở
     * một con đường thật. Vì vậy card phải hỏi hàm này và nói rõ ràng.
     *
     * So sánh không phân biệt hoa thường và bỏ dấu gạch nối/_space_ để bắt được
     * cả "Unnamed Road", "UNNAMED-ROAD", "đường chưa đặt tên".
     */
    fun isUnmappedRoadLabel(address: String): Boolean {
        val norm = address.lowercase(Locale.US)
            .replace('-', ' ').replace('_', ' ')
            .let { Regex("\\s+").replace(it, " ") }
            .trim()
        if (norm.isEmpty()) return false
        if (norm == DEFAULT_ADDRESS.lowercase(Locale.US)) return true
        return UNMAPPED_ROAD_MARKERS.any { marker -> norm.contains(marker) }
    }

    private val UNMAPPED_ROAD_MARKERS = listOf(
        "chưa đặt tên", "không đặt tên", "chưa có tên", "không có tên",
        "unnamed road", "unnamed street", "no name", "noname", "đường không tên"
    )

    private fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        return try {
            val geocoder = Geocoder(context, Locale("vi", "VN"))
            if (!Geocoder.isPresent()) return null

            val addresses: List<Address>? = geocoder.getFromLocation(latitude, longitude, 1)
            val addr = addresses?.firstOrNull() ?: return null

            val feature = addr.featureName
            val thoroughfare = addr.thoroughfare
            val subAdmin = addr.subAdminArea // Huyện/Thị xã
            val admin = addr.adminArea // Tỉnh/Thành phố

            val parts = mutableListOf<String>()
            if (!thoroughfare.isNullOrBlank()) {
                parts.add(thoroughfare)
            } else if (!feature.isNullOrBlank() && feature != thoroughfare) {
                parts.add(feature)
            }
            if (!subAdmin.isNullOrBlank()) parts.add(subAdmin)
            if (!admin.isNullOrBlank()) parts.add(admin)

            parts.joinToString(", ").ifBlank { null }
        } catch (e: IOException) {
            Log.w(TAG, "Geocoder IOException: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Geocoder exception: ${e.message}")
            null
        }
    }
}
