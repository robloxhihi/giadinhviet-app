package com.example.service.sos

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.service.map.FixPrecision
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

private const val TAG = "FallbackSmsManager"
private const val PREFS_NAME = "vespera_emergency_prefs"
private const val KEY_EMERGENCY_PHONE = "emergency_phone_number"
private const val KEY_RESCUE_HOTLINE = "rescue_hotline_number"
private const val SMS_SENT_ACTION = "com.example.vespera.SMS_SENT"

/**
 * Kết quả gửi SMS — phản ánh ĐÚNG sự thật, không bao giờ báo thành công giả.
 *
 *  • [SmsStatus.SENT]        — hệ thống viễn thông đã nhận và đẩy tin đi (RESULT_OK).
 *  • [SmsStatus.QUEUED]      — đã đưa vào hàng đợi của hệ điều hành (chưa xác nhận).
 *  • [SmsStatus.FAILED]      — gửi thất bại (kèm [errorMessage] nêu rõ lý do).
 *  • [SmsStatus.NO_SIM]      — không có thẻ SIM / SIM bị khóa / không hỗ trợ SMS.
 *  • [SmsStatus.NO_PERMISSION] — ứng dụng chưa được cấp quyền SEND_SMS.
 *  • [SmsStatus.AIRPLANE]    — đang bật chế độ máy bay.
 */
enum class SmsStatus { SENT, QUEUED, FAILED, NO_SIM, NO_PERMISSION, AIRPLANE }

data class SmsSendResult(
    val isSuccess: Boolean,
    val status: SmsStatus,
    val targetPhone: String = "",
    val errorMessage: String? = null,
    /** true chỉ khi tin nhắn THỰC SỰ chứa tọa độ GPS hợp lệ (không phải "không xác định được vị trí"). */
    val locationIncluded: Boolean = false
)

/**
 * Quản lý gửi tin nhắn khẩn cấp qua SMS dự phòng khi mất kết nối Internet
 * và ngoài vùng phủ sóng Mesh (>50m, hoàn toàn cô lập).
 *
 * ĐIỂM QUAN TRỌNG (sửa lỗi báo-thành-công-giả):
 *  - Kiểm tra trước khi gửi: quyền SEND_SMS, trạng thái SIM, chế độ máy bay,
 *    khả năng SMS của thiết bị. Không có SIM → KHÔNG báo thành công.
 *  - Sau khi đưa tin vào hệ thống, lắng nghe broadcast ACTION_SMS_SENT để biết
 *    kết quả THỰC (RESULT_OK / lỗi radio) thay vì luôn trả về true.
 */
class FallbackSmsManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getEmergencyPhoneNumber(): String = prefs.getString(KEY_EMERGENCY_PHONE, "")?.trim().orEmpty()

    fun saveEmergencyPhoneNumber(phoneNumber: String) {
        prefs.edit().putString(KEY_EMERGENCY_PHONE, phoneNumber.trim()).apply()
    }

    val emergencyHotlines = listOf(
        HotlineInfo("112", "Cứu nạn, Cứu hộ Quốc gia (Tìm kiếm cứu nạn đa năng)", "🆘"),
        HotlineInfo("115", "Cấp cứu Y tế (Khẩn cấp y khoa, tai nạn)", "🚑"),
        HotlineInfo("114", "Cứu hỏa, Phòng cháy & Sự cố sập đổ", "🚒"),
        HotlineInfo("113", "Công an / An ninh trật tự khẩn cấp", "👮")
    )

    data class HotlineInfo(val number: String, val label: String, val icon: String)

    // ─────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Cùng bộ kiểm tra với `LocationData.hasRealFix` (Models.kt): 0/0 hoặc ngoài
     * khoảng hợp lệ = CHƯA có vị trí thật → tuyệt đối không bịa tọa độ trong SMS.
     */
    private fun hasPlausibleFix(latitude: Double, longitude: Double): Boolean =
        !(latitude == 0.0 && longitude == 0.0) &&
                latitude >= -90.0 && latitude <= 90.0 &&
                longitude >= -180.0 && longitude <= 180.0

    private fun fmtTime(millis: Long): String =
        SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(millis))

    private fun noFixLabel(): String = "KHÔNG XÁC ĐỊNH ĐƯỢC VỊ TRÍ GPS lúc ${fmtTime(System.currentTimeMillis())}"

    /**
     * Số của chính máy gửi để cứu hộ gọi lại. `getLine1Number()` cần quyền
     * READ_PHONE_STATE (app KHÔNG khai trong Manifest) nên phần lớn máy trả về
     * null — khi đó phải nói thẳng "không đọc được", cấm để trống khiến người
     * nhận tưởng tin nhắn thiếu dữ liệu.
     */
    private fun senderPhoneOrUnknown(): String = try {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager)?.line1Number
            ?.takeIf { it.isNotBlank() } ?: "không đọc được số (chưa cấp quyền đọc SIM)"
    } catch (_: Exception) { "không đọc được số (chưa cấp quyền đọc SIM)" }

    /**
     * Dòng vị trí cho SMS cứu nạn: hoặc link maps thật kèm ±accuracy và mốc thời
     * gian của điểm GPS, hoặc nhãn "không xác định được vị trí" — không bao giờ
     * in tọa độ mặc định.
     */
    private fun locationBlock(
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float,
        fixTimeMillis: Long
    ): String = buildString {
        if (!hasPlausibleFix(latitude, longitude)) {
            append(noFixLabel())
        } else {
            // Link giu nguyen toa do tho (nguoi nhan may cuu nan can diem chinh xac
            // nhat co the), con dong "Sai số"/"Tọa độ" doc duoi thi phai theo
            // FixPrecision: dien thoai khong phan biet duoc 1 m thi khong duoc noi 5 le.
            append("Vị trí GPS: https://maps.google.com/?q=%.6f,%.6f".format(latitude, longitude))
            append("\nSai số: ${FixPrecision.accuracyShortLabel(accuracyMeters)}")
            FixPrecision.accuracyNote(accuracyMeters)?.let { append("\nLưu ý: $it") }
            if (fixTimeMillis > 0L) append("\nVị trí lấy lúc ${fmtTime(fixTimeMillis)}")
            append("\nTọa độ: ${FixPrecision.formatCoordinates(
                latitude,
                longitude,
                FixPrecision.honestAccuracyMeters(accuracyMeters)
            )}")
        }
    }

    suspend fun sendEmergencySmsToFamily(
        latitude: Double,
        longitude: Double,
        senderName: String,
        customMessage: String? = null,
        accuracyMeters: Float = 0f,
        fixTimeMillis: Long = 0L
    ): SmsSendResult {
        val targetPhone = getEmergencyPhoneNumber()
        if (targetPhone.isBlank()) {
            return SmsSendResult(
                isSuccess = false,
                status = SmsStatus.FAILED,
                errorMessage = "Chưa cài đặt số điện thoại người thân khẩn cấp trong Cài đặt."
            )
        }

        val hasFix = hasPlausibleFix(latitude, longitude)
        val smsBody = buildString {
            append("[GIA ĐÌNH VIỆT - BÁO ĐỘNG SOS KHẨN CẤP]\n")
            append("Người gửi: ${senderName.ifEmpty { "Thành viên gia đình" }}\n")
            append("Số điện thoại người gửi: ${senderPhoneOrUnknown()}\n")
            append("Tình trạng: CẦN TRỢ GIÚP GẤP!\n")
            if (!customMessage.isNullOrBlank()) append("Nội dung: $customMessage\n")
            append(locationBlock(latitude, longitude, accuracyMeters, fixTimeMillis))
        }
        return dispatchSms(targetPhone, smsBody, locationIncluded = hasFix)
    }

    suspend fun sendChatViaSms(senderName: String, messageText: String): SmsSendResult {
        val targetPhone = getEmergencyPhoneNumber()
        if (targetPhone.isBlank()) {
            return SmsSendResult(
                isSuccess = false,
                status = SmsStatus.FAILED,
                errorMessage = "Chưa cài đặt số điện thoại người thân khẩn cấp để gửi SMS chat."
            )
        }
        // Nhan tin nhan trong SMS den. Chuoi cu dung ten thuong mai cua ben
        // khac, hien thang ra man hinh khoa cua nguoi nhan. May cu van con gui
        // nhan chuoi cu, nen dau nhan o `SmsBridgeReceiver` giu ca hai.
        return dispatchSms(targetPhone, "[GĐ VIỆT CHAT] $senderName: $messageText")
    }

    /**
     * Gọi khẩn cấp: `ACTION_CALL` khi quyen CALL_PHONE DA duoc cap that luc bam,
     * nguoc lai `ACTION_DIAL`.
     *
     * Ban cu bat moi exception roi tra ve false: quyen bi thu hoi giua luc doc
     * va luc bam thi `SecurityException` bi an, khong mo ban quay so thay the,
     * nguoi dung bam tong dai 113 va KHONG THAY GI XAY RA. Toan bo quyet dinh
     * + rut lui nam trong [SosRescueIntents.call].
     *
     * @return true khi cuoc goi duoc bat dau truc tiep (khong phai mo ban quay so).
     */
    fun makeDirectEmergencyCall(hotline: String): Boolean {
        val canCall = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        return SosRescueIntents.call(context, hotline, canCall).let { outcome ->
            Log.d(TAG, "Goi $hotline: ok=${outcome.ok}, fallback=${outcome.usedFallback}, ${outcome.note}")
            outcome.ok && !outcome.usedFallback && canCall
        }
    }

    /**
     * Mo o SOAN TIN nhan cuu nan (khong phai gui lang). `ACTION_SENDTO` +
     * `sms:<so>` + thuc the "sms_body": dung cu tin nhan cua may se mo voi noi
     * dung da dien san, KHONG can quyen SEND_SMS.
     */
    fun openFamilySmsDraft(latitude: Double, longitude: Double, senderName: String): SosIntentOutcome {
        val target = getEmergencyPhoneNumber()
        val body = buildString {
            append("[CỨU NẠN KHẨN CẤP] ${senderName.ifBlank { "Người thân trong nhà" }} cần giúp đỡ.\n")
            append(locationBlock(latitude, longitude, 0f, 0L))
        }
        return SosRescueIntents.composeFamilySms(context, target, body)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Core
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra trước khi gửi. Trả về [SmsSendResult] lỗi nếu KHÔNG thể gửi,
     * hoặc null nếu mọi điều kiện đã sẵn sàng.
     */
    private fun preflightFailure(): SmsSendResult? {
        // 1) Quyền gửi SMS
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return SmsSendResult(
                isSuccess = false,
                status = SmsStatus.NO_PERMISSION,
                errorMessage = "Ứng dụng chưa được cấp quyền 'Gửi tin nhắn SMS' trong hệ thống."
            )
        }

        // 2) Chế độ máy bay
        val airplane = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        if (airplane) {
            return SmsSendResult(
                isSuccess = false,
                status = SmsStatus.AIRPLANE,
                errorMessage = "Thiết bị đang bật chế độ máy bay — không thể gửi SMS."
            )
        }

        // 3) Thẻ SIM & khả năng SMS
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm != null) {
            try {
                if (!tm.isSmsCapable) {
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "Thiết bị này không hỗ trợ gửi SMS (không có modem viễn thông)."
                    )
                }
            } catch (_: Exception) { /* một số ROM không hỗ trợ */ }

            when (tm.simState) {
                TelephonyManager.SIM_STATE_ABSENT ->
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "Không tìm thấy thẻ SIM trên thiết bị — không thể gửi SMS cứu nạn."
                    )
                TelephonyManager.SIM_STATE_PIN_REQUIRED,
                TelephonyManager.SIM_STATE_PUK_REQUIRED ->
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "Thẻ SIM đang bị khóa (cần nhập mã PIN/PUK) — không thể gửi SMS."
                    )
                TelephonyManager.SIM_STATE_NETWORK_LOCKED ->
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "Thẻ SIM đang bị khóa mạng — không thể gửi SMS."
                    )
                TelephonyManager.SIM_STATE_CARD_IO_ERROR,
                TelephonyManager.SIM_STATE_CARD_RESTRICTED ->
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "Thẻ SIM bị lỗi truy cập — không thể gửi SMS."
                    )
                TelephonyManager.SIM_STATE_NOT_READY ->
                    return SmsSendResult(
                        isSuccess = false,
                        status = SmsStatus.NO_SIM,
                        errorMessage = "SIM chưa sẵn sàng (đang khởi tạo) — vui lòng thử lại sau vài giây."
                    )
                else -> Unit // READY / UNKNOWN → cho phép thử
            }
        }
        return null
    }

    private suspend fun dispatchSms(
        destinationAddress: String,
        text: String,
        locationIncluded: Boolean = false
    ): SmsSendResult {
        preflightFailure()?.let { return it }

        val smsManager: SmsManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (e: Exception) {
            null
        } ?: return SmsSendResult(
            isSuccess = false,
            status = SmsStatus.FAILED,
            errorMessage = "Không truy cập được dịch vụ SMS của hệ thống."
        )

        return try {
            val parts = smsManager.divideMessage(text)
            val outcomes = CompletableDeferred<SmsSendResult>()

            val sentReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    if (intent?.action != SMS_SENT_ACTION) return
                    val code = resultCode
                    if (code == android.app.Activity.RESULT_OK) {
                        outcomes.complete(
                            SmsSendResult(true, SmsStatus.SENT, destinationAddress, locationIncluded = locationIncluded)
                        )
                    } else {
                        outcomes.complete(
                            SmsSendResult(
                                isSuccess = false,
                                status = SmsStatus.FAILED,
                                targetPhone = destinationAddress,
                                errorMessage = "Nhà mạng từ chối gửi SMS (mã lỗi $code). Kiểm tra số dư/sóng di động."
                            )
                        )
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                sentReceiver,
                IntentFilter(SMS_SENT_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            try {
                val requestCode = AtomicInteger(4100 + (System.currentTimeMillis() % 1000).toInt())
                val sentIntents = ArrayList<android.app.PendingIntent>(parts.size)
                for (i in parts.indices) {
                    val intent = Intent(SMS_SENT_ACTION).setPackage(context.packageName)
                    sentIntents.add(
                        android.app.PendingIntent.getBroadcast(
                            context,
                            requestCode.incrementAndGet(),
                            intent,
                            android.app.PendingIntent.FLAG_IMMUTABLE or
                                    android.app.PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    )
                }

                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    sentIntents,
                    null
                )
                Log.d(TAG, "Đã chuyển ${parts.size} phần SMS tới hệ thống cho $destinationAddress — chờ xác nhận…")

                // Chờ xác nhận thực từ hệ điều hành (tối đa 20 giây)
                val result = withTimeoutOrNull(20_000L) { outcomes.await() }
                result ?: SmsSendResult(
                    isSuccess = true,
                    status = SmsStatus.QUEUED,
                    targetPhone = destinationAddress,
                    errorMessage = "Tin nhắn đã được đưa vào hàng đợi của hệ thống nhưng chưa nhận được xác nhận từ nhà mạng.",
                    locationIncluded = locationIncluded
                )
            } finally {
                try { context.unregisterReceiver(sentReceiver) } catch (_: Exception) {}
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Lỗi bảo mật khi gửi SMS", e)
            SmsSendResult(
                isSuccess = false,
                status = SmsStatus.NO_PERMISSION,
                errorMessage = "Ứng dụng chưa được cấp quyền 'Gửi tin nhắn SMS' trong hệ thống."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi gửi SMS: ${e.message}", e)
            SmsSendResult(
                isSuccess = false,
                status = SmsStatus.FAILED,
                errorMessage = "Không thể gửi SMS: ${e.localizedMessage ?: "lỗi sóng điện thoại"}"
            )
        }
    }
}
