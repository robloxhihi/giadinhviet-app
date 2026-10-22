package com.example.service.sos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.LocationData
import com.example.data.model.hasRealFix
import com.example.data.repository.VesperaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SmartSosDispatcher"

enum class SosChannelUsed {
    INTERNET_CLOUD,
    OFFLINE_MESH_P2P,
    FALLBACK_SMS
}

data class SosDispatchResult(
    val isSuccess: Boolean,
    val channel: SosChannelUsed,
    val detailMessage: String
)

/**
 * Điều phối viên cứu trợ thông minh (Smart Dispatcher):
 * Tự động chuyển đổi giữa 3 tầng cứu hộ:
 * Tầng 1: Internet Cloud (Firebase Firestore) khi có mạng.
 * Tầng 2: Offline Mesh P2P (Google Nearby Connections < 50m) khi mất mạng.
 * Tầng 3: Fallback SMS (SmsManager có kèm Google Maps GPS) khi ngoài 50m / cô lập.
 *
 * SỬA LỖI BÁO-THÀNH-CÔNG-GIẢ:
 *  - Chỉ báo thành công khi tin THỰC SỰ đi được (Cloud ghi xong / Mesh có ≥1 thiết bị
 *    đã kết nối và gửi đi / SMS được nhà mạng xác nhận RESULT_OK).
 *  - Không có SIM → kết quả trả về là thất bại kèm lý do, KHÔNG báo "đã gửi thành công".
 */
class SmartSosDispatcher(
    private val context: Context,
    private val repository: VesperaRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    val networkMonitor = NetworkMonitor(context)
    val meshManager = OfflineMeshManager(context)
    val smsManager = FallbackSmsManager(context)

    private val prefs = context.getSharedPreferences("vespera_sos_schedule_prefs", Context.MODE_PRIVATE)

    private val _currentActiveChannel = MutableStateFlow(SosChannelUsed.INTERNET_CLOUD)
    val currentActiveChannel: StateFlow<SosChannelUsed> = _currentActiveChannel.asStateFlow()

    private val _isOfflineAutoScanning = MutableStateFlow(false)
    val isOfflineAutoScanning: StateFlow<Boolean> = _isOfflineAutoScanning.asStateFlow()

    init {
        observeNetworkChanges()
    }

    private fun observeNetworkChanges() {
        scope.launch {
            networkMonitor.isOnline.collectLatest { isOnline ->
                if (isOnline) {
                    Log.d(TAG, "Đã có mạng: Ưu tiên Internet Cloud, tắt bộ quét ngoại tuyến để tiết kiệm pin.")
                    _currentActiveChannel.value = SosChannelUsed.INTERNET_CLOUD
                    _isOfflineAutoScanning.value = false
                    meshManager.stopAll()
                } else {
                    Log.d(TAG, "Mất mạng: Tự động kích hoạt quét ngoại tuyến P2P Mesh (<50m)...")
                    _currentActiveChannel.value = SosChannelUsed.OFFLINE_MESH_P2P
                    _isOfflineAutoScanning.value = true
                    meshManager.startDiscovery()
                }
            }
        }
    }

    fun isSosAllowedAtCurrentTime(): Boolean = true

    fun setSosAllowedSchedule(isRestricted: Boolean, startHour: Int, endHour: Int) {
        prefs.edit()
            .putBoolean("sos_schedule_restricted", isRestricted)
            .putInt("sos_allowed_start_hour", startHour)
            .putInt("sos_allowed_end_hour", endHour)
            .apply()
    }

    fun getSosScheduleConfig(): Triple<Boolean, Int, Int> {
        val restricted = prefs.getBoolean("sos_schedule_restricted", false)
        val startH = prefs.getInt("sos_allowed_start_hour", 6)
        val endH = prefs.getInt("sos_allowed_end_hour", 22)
        return Triple(restricted, startH, endH)
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Chẩn đoán kênh — dùng cho UI hiển thị trạng thái thật
    // ─────────────────────────────────────────────────────────────────────

    /** true nếu thiết bị hiện có thể gửi SMS (có SIM, không máy bay, đã cấp quyền). */
    fun canSendSms(): Boolean = smsPreflightReason() == null

    /** Lý do KHÔNG gửi được SMS (null nếu gửi được). */
    fun smsPreflightReason(): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) return "Chưa cấp quyền gửi SMS"

        val airplane = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
        } catch (_: Exception) { false }
        if (airplane) return "Đang bật chế độ máy bay"

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return null
        try { if (!tm.isSmsCapable) return "Thiết bị không hỗ trợ SMS" } catch (_: Exception) {}
        return when (tm.simState) {
            TelephonyManager.SIM_STATE_READY, TelephonyManager.SIM_STATE_UNKNOWN -> null
            TelephonyManager.SIM_STATE_ABSENT -> "Không có thẻ SIM"
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM đang bị khóa (PIN/PUK)"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "SIM bị khóa mạng"
            TelephonyManager.SIM_STATE_NOT_READY -> "SIM chưa sẵn sàng"
            else -> "Thẻ SIM không khả dụng"
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    //  Gửi SOS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Gửi tín hiệu SOS tới Người thân, tự động chọn kênh tối ưu và BÁO ĐÚNG kết quả.
     */
    suspend fun sendSosToFamily(
        location: LocationData,
        senderName: String,
        customMessage: String? = null
    ): SosDispatchResult {
        val isOnline = networkMonitor.isOnline.value
        val hasFix = location.hasRealFix
        val gpsDesc = gpsDescription(location)
        // KHÔNG có fix thật → document SOS vẫn phải gửi (tín hiệu khẩn cấp quan trọng hơn
        // tọa độ), nhưng trường mô tả trong payload (`currentSafeZone`, VesperaRepository
        // .sendSosAlert ghi nguyên object LocationData xuống sos_alerts + locations) phải nói
        // thẳng là KHÔNG xác định được vị trí — phụ huynh không còn thấy marker ở vịnh
        // Guinea (0/0) kèm chú thích "vị trí GPS".
        val payload = if (hasFix) location else location.copy(currentSafeZone = gpsDesc)

        if (isOnline) {
            val cloud = repository.sendSosAlert(payload)
            if (cloud.delivery == VesperaRepository.CloudDelivery.SENT) {
                _currentActiveChannel.value = SosChannelUsed.INTERNET_CLOUD
                return SosDispatchResult(
                    isSuccess = true,
                    channel = SosChannelUsed.INTERNET_CLOUD,
                    // Báo KHỚP đúng những gì đã ghi xuống Firestore.
                    detailMessage = if (hasFix)
                        "Đã gửi SOS qua Internet tới gia đình, kèm vị trí GPS " +
                                com.example.service.map.FixPrecision.formatCoordinates(
                                    location.latitude, location.longitude,
                                    com.example.service.map.FixPrecision.honestAccuracyMeters(location.accuracy)
                                ) + "."
                    else
                        "Đã gửi SOS khẩn cấp qua Internet tới gia đình nhưng $gpsDesc — " +
                                "document lưu tọa độ 0.0, 0.0, KHÔNG có vị trí để đi cứu."
                )
            }
            // Cloud không ghi được → thử ngay các kênh ngoại tuyến để không "im lặng thất bại".
            Log.w(TAG, "Cloud chưa gửi được (${cloud.detail}), chuyển sang kênh ngoại tuyến…")
            val offline = attemptOfflineDispatch(payload, senderName, customMessage)
            return if (offline.isSuccess) {
                offline.copy(
                    detailMessage = offline.detailMessage + " (Kênh Internet tạm thời gián đoạn: ${cloud.detail})"
                )
            } else {
                offline
            }
        }
        return attemptOfflineDispatch(payload, senderName, customMessage)
    }

    /**
     * Chuỗi mô tả vị trí TRUNG THỰC, dùng chung cho cả 3 tầng. Không bao giờ bịa tọa độ.
     */
    private fun gpsDescription(location: LocationData): String =
        if (location.hasRealFix)
            "Tọa độ GPS: " + com.example.service.map.FixPrecision.formatCoordinates(
                location.latitude, location.longitude,
                com.example.service.map.FixPrecision.honestAccuracyMeters(location.accuracy)
            )
        else
            "KHÔNG XÁC ĐỊNH ĐƯỢC VỊ TRÍ GPS lúc " +
                    SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date())

    private suspend fun attemptOfflineDispatch(
        location: LocationData,
        senderName: String,
        customMessage: String?
    ): SosDispatchResult {
        val connectedPeers = meshManager.connectedPeers.value
        val hasFix = location.hasRealFix
        // KHÔNG bao giờ phát tọa độ bịa: không có fix thật → ghi rõ "không xác định được vị trí".
        val locText = gpsDescription(location)

        // Tầng 2: Mesh P2P — CHỈ thành công khi có thiết bị ĐÃ kết nối và gửi đi thực sự.
        if (connectedPeers.isNotEmpty()) {
            val sosText = "CẦN TRỢ GIÚP! $locText. Pin: ${location.batteryLevel}%. ${customMessage.orEmpty()}"
            val sentCount = meshManager.broadcastSosBeacon(sosText, senderName)
            if (sentCount > 0) {
                _currentActiveChannel.value = SosChannelUsed.OFFLINE_MESH_P2P
                return SosDispatchResult(
                    isSuccess = true,
                    channel = SosChannelUsed.OFFLINE_MESH_P2P,
                    detailMessage = if (hasFix)
                        "Đã phát tín hiệu SOS kèm tọa độ GPS qua mạng ngoại tuyến Mesh tới $sentCount thiết bị trong bán kính 50m."
                    else
                        "Đã phát tín hiệu SOS qua Mesh tới $sentCount thiết bị (KHÔNG kèm vị trí — chưa có GPS)."
                )
            }
            Log.w(TAG, "Mesh có peer nhưng gửi đi 0 thiết bị — chuyển sang SMS.")
        }

        // Tầng 3: Fallback SMS
        val discovered = meshManager.discoveredPeers.value
        val smsResult = smsManager.sendEmergencySmsToFamily(
            latitude = location.latitude,
            longitude = location.longitude,
            senderName = senderName,
            customMessage = customMessage,
            accuracyMeters = if (hasFix) location.accuracy else 0f,
            fixTimeMillis = if (hasFix) location.updatedAt else 0L
        )
        _currentActiveChannel.value = SosChannelUsed.FALLBACK_SMS

        val meshNote = if (discovered.isNotEmpty() && connectedPeers.isEmpty())
            " Có ${discovered.size} thiết bị lân cận nhưng chưa kết nối được." else ""
        // Báo ĐÚNG nội dung đã gửi: chỉ nói "kèm tọa độ" khi SMS thực chứa tọa độ thật.
        val locNote = if (smsResult.locationIncluded)
            "kèm tọa độ Google Maps" else "không kèm vị trí GPS (tin nhắn ghi rõ KHÔNG XÁC ĐỊNH ĐƯỢC VỊ TRÍ)"

        return when (smsResult.status) {
            SmsStatus.SENT -> SosDispatchResult(
                isSuccess = true,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "Không có Internet. Đã gửi SMS cứu nạn $locNote tới ${smsResult.targetPhone} (nhà mạng xác nhận).$meshNote"
            )
            SmsStatus.QUEUED -> SosDispatchResult(
                isSuccess = true,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "Đã đưa SMS cứu nạn $locNote tới ${smsResult.targetPhone} vào hàng đợi hệ thống, đang chờ nhà mạng xác nhận.$meshNote"
            )
            else -> SosDispatchResult(
                isSuccess = false,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "KHÔNG gửi được SOS: ${smsResult.errorMessage ?: "không rõ nguyên nhân"}$meshNote"
            )
        }
    }

    /**
     * Bam tong dai cuu ho (112, 115, 114, 113): MO CUOC GOI, con toa do thi
     * gui SMS toi SO NGUOI THAN DA LUU — khong bao gio gui SMS toi chinh
     * tong dai.
     *
     * 112/113/114/115 la ma ngan chi phuc vu THOAI. Ban cu dung o day tung
     * gui SMS toa do loi goi: tin nhan do khong den tay ai cua tong dai,
     * `dispatchSms` lai cho nha mang xac nhan toi 20 giay, nen nguoi dung bam
     * 113 bi ket trong mot man hinh "dang gui SMS" trong khi dang can noi.
     * Toa do chi co gia tri khi no den tay nguoi that — so nguoi than trong
     * Cai dat.
     *
     * THU TU VAN LA GOI TRUOC, SMS SAU: cuu nan thi cuoc goi phai di truong.
     */
    suspend fun sendSosToRescueForce(
        hotline: String,
        location: LocationData,
        senderName: String,
        /**
         * false khi ben goi DA tu mo ban quay so truoc do (ViewModel mo ngay
         * tuc thi khi bam 113) — dung mo lan hai, kha nua la chen len cuoc goi
         * dang ket noi.
         */
        startCall: Boolean = true
    ): SosDispatchResult {
        val startedRealCall = if (startCall) smsManager.makeDirectEmergencyCall(hotline) else false
        val result = smsManager.sendEmergencySmsToFamily(
            latitude = location.latitude,
            longitude = location.longitude,
            senderName = senderName,
            accuracyMeters = if (location.hasRealFix) location.accuracy else 0f,
            fixTimeMillis = if (location.hasRealFix) location.updatedAt else 0L
        )

        val callNote = if (startedRealCall)
            "${SosPlan.REDIRECTING_TO_RESCUE} — đang gọi trực tiếp $hotline…"
        else
            "${SosPlan.REDIRECTING_TO_RESCUE} — đã mở bàn phím gọi $hotline (bấm gọi để kết nối)."
        // Báo đúng nội dung đã gửi: chi noi "kèm tọa độ" khi SMS that co toa do hop le.
        val locNote = if (result.locationIncluded) "tọa độ cứu nạn" else "SMS cứu nạn KHÔNG kèm vị trí GPS (ghi rõ không xác định được vị trí)"
        // SMS di den so nguoi than, khong phai tong dai — noi ro de khong ai
        // tuong tong dai da nhan duoc toa do.
        val smsTargetNote = if (result.isSuccess || result.status == SmsStatus.QUEUED)
            "đã gửi $locNote tới số người thân đã lưu"
        else
            "KHÔNG gửi được SMS tới số người thân: ${result.errorMessage ?: "lỗi không rõ"}"

        return when {
            result.status == SmsStatus.SENT -> SosDispatchResult(
                isSuccess = true,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "$callNote — $smsTargetNote."
            )
            result.status == SmsStatus.QUEUED -> SosDispatchResult(
                isSuccess = true,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "$callNote — $smsTargetNote, đang chờ nhà mạng xác nhận."
            )
            // Cuoc goi van duoc mo nen tong van de isSuccess = true; chi la
            // khong duoc noi gian toa do da den noi dan.
            startedRealCall -> SosDispatchResult(
                isSuccess = true,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "$callNote — $smsTargetNote."
            )
            else -> SosDispatchResult(
                isSuccess = false,
                channel = SosChannelUsed.FALLBACK_SMS,
                detailMessage = "Chưa mở được cuộc gọi $hotline và $smsTargetNote."
            )
        }
    }

    fun release() {
        networkMonitor.stopMonitoring()
        meshManager.stopAll()
    }
}
