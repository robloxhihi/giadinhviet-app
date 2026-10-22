package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import com.example.MainActivity
import com.example.R
import com.example.data.model.LocationData
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.hasRealFix
import com.example.data.repository.VesperaRepository
import com.example.service.map.FixPrecision
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VesperaTelemetryService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var telemetryHelper: TelemetryAndUsageHelper
    private lateinit var repository: VesperaRepository
    private var locationCallback: LocationCallback? = null
    private var periodicUsageJob: Job? = null
    private var cadenceJob: Job? = null
    private var screenStateReceiver: BroadcastReceiver? = null
    private var lastHistoryLoggedLocation: Location? = null
    private var lastHistoryLoggedTime: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Điểm định vị gần nhất KÈM nguồn gốc/tuổi/sai số — không bao giờ là tọa độ bịa. */
    private var lastFix: LocationFix? = null

    /**
     * Cờ foreground/background. `null` = dịch vụ tự dò ([isAppUiVisible]);
     * giá trị cụ thể do [ACTION_FOREGROUND] / [ACTION_BACKGROUND] áp đặt từ lớp UI.
     */
    private var foregroundHint: Boolean? = null
    private var cadenceIsForeground: Boolean? = null

    companion object {
        const val CHANNEL_ID = "vespera_telemetry_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "ACTION_START_TELEMETRY"
        const val ACTION_STOP = "ACTION_STOP_TELEMETRY"

        /**
         * Nhịp GPS phụ thuộc app đang hiển thị hay chạy nền.
         *
         * Repo CHƯA có `androidx.lifecycle:lifecycle-process` (đã kiểm tra
         * gradle/libs.versions.toml + app/build.gradle.kts) nên KHÔNG dùng
         * ProcessLifecycleOwner. Cách dò, theo thứ tự ưu tiên:
         *  1. [ACTION_FOREGROUND] / [ACTION_BACKGROUND] do Activity/ViewModel gửi tường minh.
         *  2. [PowerManager.isInteractive] + receiver SCREEN_ON/OFF đã đăng ký trong service.
         *
         * Không còn dùng `ActivityManager.getRunningAppProcesses()`: từ Android 10 lời gọi đó
         * chỉ trả về tiến trình của CHÍNH app và importance của tiến trình chứa service không
         * phản ánh Activity đang resumed → luôn ra `false`, kẹt nhịp 45 s dù đang mở app.
         */
        const val ACTION_FOREGROUND = "ACTION_TELEMETRY_FOREGROUND"
        const val ACTION_BACKGROUND = "ACTION_TELEMETRY_BACKGROUND"

        /** Foreground: ~10 s/điểm, nhanh nhất 5 s — map không còn chậm nửa phút. */
        const val INTERVAL_FOREGROUND_MS = 10_000L
        const val FASTEST_FOREGROUND_MS = 5_000L

        /** Chạy nền: 45 s (trong khoảng 30–60 s) để giữ pin. */
        const val INTERVAL_BACKGROUND_MS = 45_000L
        const val FASTEST_BACKGROUND_MS = 30_000L

        /** Vòng lặp đồng bộ pin/ứng dụng + vị trí đã lưu. */
        const val PERIODIC_SYNC_INTERVAL_MS = 45_000L

        private const val CADENCE_CHECK_INTERVAL_MS = 5_000L
        private const val TAG = "VesperaService"

        /**
         * Tín hiệu "dịch vụ đã dừng CÓ CHỦ ĐÍCH vì thiếu quyền vị trí" — UI/ViewModel đọc
         * được mà không cần đổi API public (start/stop/action giữ nguyên).
         */
        const val PREFS_TELEMETRY_STATE = "vespera_telemetry_state"
        const val KEY_MISSING_PERMISSION_REASON = "missing_location_permission_reason"
        const val KEY_LAST_STOP_MS = "last_intentional_stop_ms"

        var isServiceRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, VesperaTelemetryService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VesperaTelemetryService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        /**
         * Báo cho dịch vụ biết app đang hiển thị ([isFront] = true) hay đã xuống nền,
         * để chuyển nhịp GPS 10 s / 45 s ngay lập tức thay vì chờ vòng dò 5 s.
         * Không bắt buộc: dịch vụ tự dò được.
         */
        fun notifyForegroundState(context: Context, isFront: Boolean) {
            if (!isServiceRunning) return
            val intent = Intent(context, VesperaTelemetryService::class.java).apply {
                action = if (isFront) ACTION_FOREGROUND else ACTION_BACKGROUND
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Không gửi được nhịp foreground/background: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        telemetryHelper = TelemetryAndUsageHelper(applicationContext)
        repository = VesperaRepository(applicationContext)
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationUpdates()
                periodicUsageJob?.cancel()
                cadenceJob?.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isServiceRunning = false
                return START_NOT_STICKY
            }

            ACTION_FOREGROUND, ACTION_BACKGROUND -> {
                foregroundHint = intent?.action == ACTION_FOREGROUND
                if (!isServiceRunning) {
                    if (!startForegroundWithNotification()) return START_NOT_STICKY
                    isServiceRunning = true
                    startPeriodicUsageAndTelemetrySync()
                    startCadenceWatchdog()
                }
                serviceScope.launch { applyCadence(force = true) }
                return START_STICKY
            }

            else -> {
                if (!startForegroundWithNotification()) return START_NOT_STICKY
                isServiceRunning = true
                serviceScope.launch { applyCadence(force = true) }
                startPeriodicUsageAndTelemetrySync()
                startCadenceWatchdog()
                return START_STICKY
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gia Đình Việt - Giám sát vị trí & Gia đình",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Chạy ngầm định vị GPS và kiểm tra thời gian sử dụng ứng dụng liên tục"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sosIntent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_SOS"
            putExtra("TRIGGER_SOS", true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sosPendingIntent = PendingIntent.getActivity(
            this,
            1,
            sosIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gia Đình Việt • Giám sát & SOS 24/7")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_vespera_logo)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_vespera_logo, "🚨 BẤM SOS KHẨN CẤP", sosPendingIntent)
            .build()
    }

    /**
     * Thứ tự BẮT BUỘC: kiểm tra quyền vị trí TRƯỚC rồi mới startForeground(..., LOCATION).
     * Android 14+ (repo đặt targetSdk 36) ném SecurityException nếu request
     * FOREGROUND_SERVICE_TYPE_LOCATION khi chưa có ACCESS_FINE/COARSE_LOCATION.
     *
     *  • Có FINE        → foreground GPS chính xác, notification thường.
     *  • Chỉ có COARSE  → VẪN chạy service (TYPE_LOCATION hợp lệ vì đã có quyền vị trí),
     *                      notification ghi rõ "cần cấp quyền Vị trí chính xác".
     *  • Không quyền nào → dừng CÓ CHỦ ĐÍCH + để lại tín hiệu nhìn thấy được
     *                      (notification + Toast + key trong SharedPreferences), không im lặng.
     *
     * @return `true` nếu service đã vào foreground hợp lệ.
     */
    private fun startForegroundWithNotification(): Boolean {
        val hasAnyLocation = telemetryHelper.hasLocationPermission()
        val hasFine = telemetryHelper.hasFineLocationPermission()
        if (!hasAnyLocation) return stopForMissingLocationPermission(null)

        val contentText = if (hasFine) {
            "Đang đồng bộ vị trí GPS và thời gian sử dụng ứng dụng..."
        } else {
            "⚠️ Chỉ định vị được XẤP XỈ — cần cấp quyền Vị trí CHÍNH XÁC"
        }
        val notification = buildNotification(contentText)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            // Service chạy được nghĩa là quyền vị trí đã hợp lệ → xóa tín hiệu lỗi cũ.
            getSharedPreferences(PREFS_TELEMETRY_STATE, Context.MODE_PRIVATE).edit()
                .remove(KEY_MISSING_PERMISSION_REASON)
                .apply()
            true
        } catch (e: SecurityException) {
            // Quyền bị thu hồi đúng lúc, hoặc thiếu FOREGROUND_SERVICE_LOCATION (manifest:10).
            stopForMissingLocationPermission(e)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể chạy foreground service: ${e.message}", e)
            isServiceRunning = false
            stopSelf()
            false
        }
    }

    /**
     * Dừng có chủ đích kèm 3 tín hiệu nhìn thấy được. Vẫn thử `startForeground()`
     * (không chỉ định type) trước khi stopSelf() để không dính lỗi
     * "startForegroundService did not call startForeground" trên Android 8+.
     */
    private fun stopForMissingLocationPermission(cause: Throwable?): Boolean {
        val reason = telemetryHelper.missingPermissionReason()
            ?: "Chưa cấp quyền Vị trí — ứng dụng không định vị được"
        Log.e(TAG, "Dừng telemetry service: $reason${cause?.let { " | ${it.message}" } ?: ""}", cause)
        isServiceRunning = false

        try {
            startForeground(NOTIFICATION_ID, buildNotification("⚠️ $reason"))
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            // Không vào được foreground → phát notification thường (best-effort).
            try {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, buildNotification("⚠️ $reason"))
            } catch (e2: Exception) {
                Log.w(TAG, "Không phát được notification: ${e2.message}")
            }
        }

        getSharedPreferences(PREFS_TELEMETRY_STATE, Context.MODE_PRIVATE).edit()
            .putString(KEY_MISSING_PERMISSION_REASON, reason)
            .putLong(KEY_LAST_STOP_MS, System.currentTimeMillis())
            .apply()

        mainHandler.post {
            try {
                Toast.makeText(this, "Gia Đình Việt: $reason", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.w(TAG, "Không hiển thị được Toast: ${e.message}")
            }
        }
        stopSelf()
        return false
    }

    private fun updateNotificationText(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    /**
     * App đang hiển thị hay chạy nền?
     *  1. [foregroundHint] — cờ tường minh do lớp UI gửi qua [ACTION_FOREGROUND] /
     *     [ACTION_BACKGROUND]; đáng tin nhất, có hiệu lực ngay (không chờ vòng dò 5 s).
     *  2. [PowerManager.isInteractive] — màn hình tắt ⇒ chắc chắn nền (khỏi tốn GPS
     *     khi máy trong túi); màn hình đang bật ⇒ coi như có UI để map cập nhật ~10 s.
     *     Receiver SCREEN_ON/OFF đã đăng ký ở [registerScreenStateReceiver] ép tính lại
     *     ngay khi bật/tắt màn hình.
     *
     * Đã BỎ `ActivityManager.getRunningAppProcesses()` + IMPORTANCE_FOREGROUND: từ
     * Android 10 lời gọi đó chỉ thấy tiến trình của chính mình và importance của
     * tiến trình chạy service không phải FOREGROUND → luôn trả `false`, khiến nhịp GPS
     * kẹt 45 s dù người dùng đang mở app. Repo không có `lifecycle-process`
     * (đã kiểm tra gradle/libs.versions.toml + app/build.gradle.kts) nên không dùng
     * ProcessLifecycleOwner, cũng không tự thêm dependency.
     */
    private fun isAppUiVisible(): Boolean = foregroundHint ?: isScreenInteractive()

    private fun isScreenInteractive(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /**
     * Đăng ký / đăng ký lại FusedLocation theo nhịp foreground (~10 s) hoặc nền (~45 s).
     *
     *  • [Priority.PRIORITY_HIGH_ACCURACY] chỉ khi có ACCESS_FINE_LOCATION.
     *  • Chỉ có COARSE → hạ xuống PRIORITY_BALANCED_POWER_ACCURACY và báo rõ trên notification,
     *    vì GPS chính xác (HIGH_ACCURACY) bắt buộc quyền ACCESS_FINE_LOCATION.
     *  • Không có quyền nào → KHÔNG gọi FusedLocation, bỏ đăng ký cũ, nói thẳng lý do
     *    (tuyệt đối không lặng lẽ đưa tọa độ cũ thay thế).
     */
    private fun applyCadence(force: Boolean) {
        val priority = telemetryHelper.preferredLocationPriorityOrNull()
        if (priority == null) {
            // Đã ở trạng thái "thiếu quyền" (không callback, cadence = null) → khỏi
            // cập nhật notification mỗi 5 s, chỉ làm khi có ép buộc hoặc khi quyền thay đổi.
            if (!force && locationCallback == null && cadenceIsForeground == null) return
            stopLocationUpdates()
            cadenceIsForeground = null
            lastFix = LocationFix(null, LocationFixSource.NO_FIX, missingFinePermission = true)
            updateNotificationText("⚠️ ${telemetryHelper.missingPermissionReason() ?: "Chưa cấp quyền Vị trí"}")
            return
        }

        val foreground = isAppUiVisible()
        if (!force && locationCallback != null && foreground == cadenceIsForeground) return
        cadenceIsForeground = foreground

        // Hủy đăng ký cũ trước khi đăng ký lại — tránh 2 callback cùng đẩy tọa độ.
        stopLocationUpdates()

        val interval = if (foreground) INTERVAL_FOREGROUND_MS else INTERVAL_BACKGROUND_MS
        val fastest = if (foreground) FASTEST_FOREGROUND_MS else FASTEST_BACKGROUND_MS
        val locationRequest = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(fastest)
            .setMinUpdateDistanceMeters(if (foreground) 0f else 5f)
            // Lấy cả điểm sai số lớn rồi tự đánh dấu (> 100 m) thay vì chờ GPS fix vô hạn.
            .setWaitForAccurateLocation(false)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val locations = result.locations
                val realFix = locations.lastOrNull { !telemetryHelper.isMockLocation(it) }
                if (realFix == null) {
                    if (locations.isNotEmpty()) {
                        lastFix = LocationFix(null, LocationFixSource.NO_FIX, rejectedMockLocation = true)
                        updateNotificationText("⚠️ Vị trí giả lập (mock) đã bị từ chối — đang chờ GPS thật")
                    }
                    return
                }
                onLocationDelivered(realFix)
            }
        }

        try {
            locationCallback?.let { cb ->
                fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    cb,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e("VesperaService", "Location permission missing", e)
            stopLocationUpdates()
            updateNotificationText(
                "⚠️ ${telemetryHelper.missingPermissionReason() ?: "Quyền vị trí vừa bị thu hồi"}"
            )
        }
    }

    /** Điểm vừa được FusedLocation đẩy lên — đã chắc chắn không phải mock. */
    private fun onLocationDelivered(location: Location) {
        if (!telemetryHelper.isPlausibleLocation(location)) {
            Log.w(TAG, "Bỏ qua điểm GPS bất hợp lý: ${location.latitude}, ${location.longitude}")
            return
        }
        if (telemetryHelper.isAccuracyTooLow(location)) {
            Log.w(
                TAG,
                "Điểm GPS sai số ${location.accuracy} m > ${TelemetryAndUsageHelper.MAX_ACCEPTED_ACCURACY_METERS} m" +
                    " — vẫn dùng nhưng đánh dấu rõ là định vị kém"
            )
        }
        lastFix = LocationFix(
            location = location,
            source = LocationFixSource.LIVE_FIX,
            coarseOnlyPermission = !telemetryHelper.hasFineLocationPermission(),
            accuracyTooLow = telemetryHelper.isAccuracyTooLow(location),
            unknownAccuracy = telemetryHelper.hasUnknownAccuracy(location),
            ageMs = telemetryHelper.locationAgeMs(location)
        )
        syncTelemetryWithLocation(location)
    }

    private fun startCadenceWatchdog() {
        cadenceJob?.cancel()
        cadenceJob = serviceScope.launch {
            while (isActive) {
                delay(CADENCE_CHECK_INTERVAL_MS)
                try {
                    applyCadence(force = false)
                } catch (e: Exception) {
                    Log.w(TAG, "Đổi nhịp GPS thất bại", e)
                }
            }
        }
    }

    /** Màn hình bật/tắt là tín hiệu nền tức thời — khỏi chờ vòng dò 5 s. */
    private fun registerScreenStateReceiver() {
        if (screenStateReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF ->
                        serviceScope.launch { applyCadence(force = true) }
                }
            }
        }
        screenStateReceiver = receiver
        try {
            ContextCompat.registerReceiver(
                this,
                receiver,
                IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) },
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w(TAG, "Không đăng ký được SCREEN_ON/OFF: ${e.message}")
            screenStateReceiver = null
        }
    }

    /**
     * Periodic background loop running every [PERIODIC_SYNC_INTERVAL_MS]:
     * Reads usage stats (which app, how long) and updates notification and Firestore!
     */
    private fun startPeriodicUsageAndTelemetrySync() {
        periodicUsageJob?.cancel()
        periodicUsageJob = serviceScope.launch {
            while (isActive) {
                try {
                    val location = lastFix?.location
                    if (location != null) {
                        // Điểm đang có (kể cả đã cũ) vẫn được đồng bộ — safeZone + updatedAt
                        // nói rõ nó cũ tới mức nào. Không bao giờ bịa tọa độ để thay.
                        syncTelemetryWithLocation(location)
                    } else {
                        telemetryHelper.fetchLocationFix { fetched ->
                            lastFix = fetched
                            val fresh = fetched.location
                            if (fresh != null) {
                                syncTelemetryWithLocation(fresh)
                            } else {
                                val reason = fetched.failureReason ?: "Chưa định vị được GPS"
                                Log.w(TAG, "Không có vị trí thật: $reason")
                                updateNotificationText("⚠️ $reason")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("VesperaService", "Periodic background tick failed", e)
                }
                delay(PERIODIC_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun syncTelemetryWithLocation(location: Location) {
        // Chua dang nhap thi van ghi du lieu THAT cua may nay duoi id thiet bi
        // on dinh (luu trong may), khong phai toa do mau.
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            ?: guestDeviceId()
        val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName.orEmpty()
            .ifEmpty { "Thiết bị này" }
        val (batteryLevel, isCharging) = telemetryHelper.getBatteryInfo()

        // Trạng thái THẬT của điểm định vị — dùng để đánh dấu, không phải để giấu.
        val fixAge = telemetryHelper.locationAgeMs(location)
        val isStaleFix = fixAge > TelemetryAndUsageHelper.MAX_STALE_FIX_AGE_MS
        val poorAccuracy = telemetryHelper.isAccuracyTooLow(location)
        val unknownAccuracy = telemetryHelper.hasUnknownAccuracy(location)

        // Tiết kiệm pin thông minh & tự động kích hoạt khi di chuyển > 5m
        val distanceFromLast = lastHistoryLoggedLocation?.distanceTo(location) ?: 999f
        val isMoving = location.speed > 0.8f || distanceFromLast >= 5f
        val now = System.currentTimeMillis()
        // updatedAt = mốc của CHÍNH điểm định vị (không phải giờ của lần ghi).
        val fixTime = telemetryHelper.fixTimeMillis(location)
        val timeSinceLastLog = now - lastHistoryLoggedTime

        serviceScope.launch {
            try {
                // Fetch real app usage from UsageStatsManager
                val detailedApps = telemetryHelper.getDetailedAppUsageList()
                val topApp = detailedApps.firstOrNull()?.appName.orEmpty()
                val topAppMins = detailedApps.firstOrNull()?.minutesUsed ?: 0
                val totalUsedMins = detailedApps.sumOf { it.minutesUsed }.coerceAtLeast(topAppMins)

                // Detect Safe Zone based on coordinates / speed or real address
                val realAddr = AddressHelper.getReadableAddress(applicationContext, location.latitude, location.longitude)
                val accuracyNote = when {
                    unknownAccuracy -> "sai số không rõ"
                    poorAccuracy -> "±${FixPrecision.honestAccuracyMeters(location.accuracy).toInt()} m"
                    else -> null
                }
                val baseZone = when {
                    location.speed > 8f -> "Đang di chuyển (${(location.speed * 3.6f).toInt()} km/h)"
                    realAddr.isNotBlank() -> realAddr
                    else -> "Vị trí GPS (${FixPrecision.formatCoordinates(
                        location.latitude,
                        location.longitude,
                        FixPrecision.honestAccuracyMeters(location.accuracy)
                    )})"
                }
                // Định vị kém / vị trí cũ phải hiện ngay trong chuỗi an toàn, phụ huynh đọc là hiểu.
                val safeZone = buildString {
                    if (accuracyNote != null) append("⚠️ Định vị kém ($accuracyNote) — ")
                    if (isStaleFix) append("🕒 Vị trí cũ ${formatFixAge(fixAge)} trước — ")
                    append(baseZone)
                }

                // Update Foreground Notification so user sees GPS & app tracking live in notification bar
                // Notification cũng phải nói đúng: số lẻ lấy theo sai số thật, không
                // in "±1m" cua may gia lap hay mock.
                val honestAcc = FixPrecision.honestAccuracyMeters(location.accuracy)
                // Pin -1 = he thong khong bao: trong notification phai la "chua
                // doc duoc", khong phai "0%" (ngua nguoi doc thanh pin sat gui).
                val batteryText = if (batteryLevel >= 0) "$batteryLevel%" else "chưa đọc được"
                // Chua doc duoc ung dung nao thi khong in ": 0 ph" — khong ai
                // biet hom nay may dung bao nhieu, do la trang thai, khong phai
                // con so 0 phut.
                val topAppText = if (topApp.isBlank()) "chưa đọc được ứng dụng"
                else "$topApp: $topAppMins ph"
                val notifStatus = String.format(
                    Locale.US,
                    "📍 GPS: %s (±%.0fm) | 📱 %s | ⚡ Pin: %s%s%s",
                    FixPrecision.formatCoordinates(
                        location.latitude,
                        location.longitude,
                        honestAcc
                    ),
                    honestAcc,
                    topAppText,
                    batteryText,
                    if (isCharging) " (Sạc)" else "",
                    if (isStaleFix) " | 🕒 cũ ${formatFixAge(fixAge)}" else ""
                )
                updateNotificationText(notifStatus)

                // Push to Firestore LocationData
                val locData = LocationData(
                    userId = currentUserId,
                    userName = currentUserName,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    speed = location.speed,
                    batteryLevel = batteryLevel,
                    isCharging = isCharging,
                    isPrivacyActive = false,
                    currentSafeZone = safeZone,
                    topActiveApp = topApp,
                    topActiveAppMinutes = topAppMins,
                    totalUsedMinutes = totalUsedMins,
                    updatedAt = fixTime
                )
                // Lưới chặn cuối: không ghi tọa độ rỗng/bịa lên Firestore.
                if (!locData.hasRealFix) {
                    Log.w(TAG, "Bỏ qua updateLocation: tọa độ không hợp lệ (${locData.latitude}, ${locData.longitude})")
                    return@launch
                }
                repository.updateLocation(locData)

                // TIẾT KIỆM PIN & GHI LỊCH SỬ DI CHUYỂN:
                val prefs = getSharedPreferences("vespera_prefs", Context.MODE_PRIVATE)
                val historyEnabled = prefs.getBoolean("history_enabled", true)
                // Chỉ ghi vết di chuyển khi điểm còn TƯƠI: ghi lại điểm cũ sẽ tạo vệt "đi" giả.
                if (historyEnabled && !isStaleFix && (isMoving || timeSinceLastLog >= 15 * 60 * 1000L)) {
                    val profile = repository.getUserProfile(currentUserId)
                    val pairingId = profile?.activePairingId.orEmpty()

                    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = fixTime }
                    val year = calendar.get(java.util.Calendar.YEAR)
                    val month = calendar.get(java.util.Calendar.MONTH) + 1
                    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
                    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                    val minute = calendar.get(java.util.Calendar.MINUTE)
                    val second = calendar.get(java.util.Calendar.SECOND)

                    val dateStr = String.format(Locale.getDefault(), "%02d/%02d/%04d", day, month, year)
                    val timeStr = String.format(Locale.getDefault(), "%02d:%02d:%02d", hour, minute, second)

                    val historyEntry = LocationHistoryEntry(
                        id = "${currentUserId}_${fixTime}",
                        pairingId = pairingId,
                        userId = currentUserId,
                        userName = currentUserName,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        placeName = safeZone,
                        speed = location.speed,
                        batteryLevel = batteryLevel,
                        isCharging = isCharging,
                        timestamp = fixTime,
                        dateStr = dateStr,
                        timeStr = timeStr,
                        year = year,
                        month = month,
                        day = day,
                        hour = hour,
                        minute = minute,
                        second = second
                    )
                    repository.saveLocationHistory(historyEntry)
                    lastHistoryLoggedLocation = location
                    lastHistoryLoggedTime = now
                    Log.d("VesperaService", "Đã ghi lịch sử di chuyển: $safeZone lúc $timeStr ngày $dateStr")
                }
            } catch (e: Exception) {
                Log.w("VesperaService", "Background location & usage sync failed", e)
            }
        }
    }

    /** Tuổi điểm định vị dạng dễ đọc cho notification / currentSafeZone. */
    private fun formatFixAge(ageMs: Long): String = when {
        ageMs == Long.MAX_VALUE -> "không rõ thời gian"
        ageMs < 60_000L -> "${ageMs / 1000L} giây"
        ageMs < 3_600_000L -> "${ageMs / 60_000L} phút"
        else -> "${ageMs / 3_600_000L} giờ"
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.w(TAG, "removeLocationUpdates lỗi: ${e.message}")
            }
        }
        locationCallback = null
    }

    /** Mã định danh thiết bị ổn định cho chế độ khách (chưa đăng nhập). */
    private fun guestDeviceId(): String {
        val prefs = getSharedPreferences("vespera_device_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("guest_device_uid", "") ?: ""
        if (id.isBlank()) {
            id = "guest_device_${System.currentTimeMillis()}"
            prefs.edit().putString("guest_device_uid", id).apply()
        }
        return id
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        periodicUsageJob?.cancel()
        cadenceJob?.cancel()
        screenStateReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenStateReceiver = null
        lastFix = null
        foregroundHint = null
        cadenceIsForeground = null
        serviceScope.cancel()
        isServiceRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
