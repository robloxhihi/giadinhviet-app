package com.example.service

import android.app.AppOpsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.AppCategoryUsage
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.coroutines.resume

private const val TAG = "TelemetryHelper"

/**
 * Nguồn gốc của một điểm định vị. Hợp đồng bắt buộc: toạ độ KHÔNG bao giờ đi kèm
 * một mình mà thiếu nhãn này — xem `LocationData` + `hasRealFix` trong Models.kt
 * (vĩ độ/kinh độ = 0.0 nghĩa là "chưa có fix", không phải một tọa độ thật).
 */
enum class LocationFixSource {
    /** Không lấy được vị trí thật — [LocationFix.location] = null. */
    NO_FIX,

    /** Điểm tươi vừa đo được (tuổi <= [TelemetryAndUsageHelper.MAX_STALE_FIX_AGE_MS]). */
    LIVE_FIX,

    /** Điểm cached (`lastLocation`) còn tươi — bắt buộc UI ghi rõ "đây là vị trí CŨ". */
    STALE_CACHED_FIX
}

/**
 * Kết quả định vị trung thực: hoặc toạ độ thật kèm sai số/tuổi/nguồn, hoặc null.
 * Không có đường nào trả về toạ độ mẫu.
 */
data class LocationFix(
    val location: Location?,
    val source: LocationFixSource,
    val missingFinePermission: Boolean = false,
    val coarseOnlyPermission: Boolean = false,
    val rejectedMockLocation: Boolean = false,
    val accuracyTooLow: Boolean = false,
    val unknownAccuracy: Boolean = false,
    val ageMs: Long = 0L
) {
    /** Có toạ độ thật để ghi xuống LocationData hay không. */
    val hasRealCoordinates: Boolean get() = location != null

    /** Lý do trần trụi khi không định vị được — dùng cho notification/UI, null khi OK. */
    val failureReason: String?
        get() = when {
            location != null -> null
            missingFinePermission -> "Chưa có quyền định vị"
            rejectedMockLocation -> "Vị trí giả lập (mock) đã bị từ chối"
            accuracyTooLow -> if (coarseOnlyPermission) {
                "Chỉ có quyền COARSE nên sai số > ${TelemetryAndUsageHelper.MAX_ACCEPTED_ACCURACY_METERS.toInt()} m"
            } else {
                "Sai số định vị quá lớn (>${TelemetryAndUsageHelper.MAX_ACCEPTED_ACCURACY_METERS.toInt()} m)"
            }
            else -> "Thiết bị chưa định vị được GPS"
        }
}

class TelemetryAndUsageHelper(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    /**
     * Task của FusedLocation chỉ giao kết quả về MainLooper, nên toàn bộ lời gọi
     * định vị suspend được phóng lên Main — tránh "Task is not on the main looper".
     */
    private val locationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Check if user granted Usage Access permission
     */
    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Intent to open Usage Access Settings screen in Android
     */
    fun getUsageAccessSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Fetch real usage stats for today and categorize apps
     */
    fun getTodayUsageCategories(): List<AppCategoryUsage> {
        if (!hasUsageStatsPermission()) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val stats = usageRowsPerPackage(usageStatsManager, todayStartMillis(), System.currentTimeMillis())

        var socialMinutes = 0
        var studyMinutes = 0
        var gameMinutes = 0
        var otherMinutes = 0

        for (item in stats) {
            val totalTimeForegroundMs = item.totalTimeInForeground
            if (totalTimeForegroundMs < 60_000) continue // Skip under 1 min

            val mins = (totalTimeForegroundMs / (1000 * 60)).toInt()
            val pkg = item.packageName.lowercase()

            when {
                pkg.contains("zalo") || pkg.contains("facebook") || pkg.contains("tiktok") ||
                pkg.contains("youtube") || pkg.contains("instagram") || pkg.contains("twitter") ||
                pkg.contains("threads") || pkg.contains("messenger") -> {
                    socialMinutes += mins
                }
                pkg.contains("zoom") || pkg.contains("teams") || pkg.contains("classroom") ||
                pkg.contains("duolingo") || pkg.contains("quizlet") || pkg.contains("khan") ||
                pkg.contains("study") || pkg.contains("sach") || pkg.contains("toan") -> {
                    studyMinutes += mins
                }
                pkg.contains("game") || pkg.contains("roblox") || pkg.contains("freefire") ||
                pkg.contains("garena") || pkg.contains("minecraft") || pkg.contains("pubg") ||
                pkg.contains("genshin") || pkg.contains("honor") -> {
                    gameMinutes += mins
                }
                else -> {
                    otherMinutes += mins
                }
            }
        }

        val total = (socialMinutes + studyMinutes + gameMinutes + otherMinutes).coerceAtLeast(1)

        return listOf(
            AppCategoryUsage("Mạng xã hội", socialMinutes, "social", socialMinutes.toFloat() / total),
            AppCategoryUsage("Học tập", studyMinutes, "study", studyMinutes.toFloat() / total),
            AppCategoryUsage("Trò chơi", gameMinutes, "game", gameMinutes.toFloat() / total),
            AppCategoryUsage("Khác", otherMinutes, "other", otherMinutes.toFloat() / total)
        )
    }

    /**
     * Query detailed per-app usage list (app name, minutes used, category)
     */
    fun getDetailedAppUsageList(): List<com.example.data.model.DetailedAppUsage> {
        if (!hasUsageStatsPermission()) {
            return emptyList()
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val stats = usageRowsPerPackage(usageStatsManager, todayStartMillis(), System.currentTimeMillis())

        val pm = context.packageManager
        val list = mutableListOf<com.example.data.model.DetailedAppUsage>()

        for (item in stats) {
            val totalMs = item.totalTimeInForeground
            if (totalMs < 60_000) continue // Skip under 1 minute

            val mins = (totalMs / (1000 * 60)).toInt()
            val pkg = item.packageName
            val pkgLower = pkg.lowercase()

            val appLabel = try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

            val category = when {
                pkgLower.contains("zalo") || pkgLower.contains("facebook") || pkgLower.contains("tiktok") ||
                pkgLower.contains("youtube") || pkgLower.contains("instagram") || pkgLower.contains("twitter") ||
                pkgLower.contains("threads") || pkgLower.contains("messenger") -> "Mạng xã hội"

                pkgLower.contains("zoom") || pkgLower.contains("teams") || pkgLower.contains("classroom") ||
                pkgLower.contains("duolingo") || pkgLower.contains("quizlet") || pkgLower.contains("khan") ||
                pkgLower.contains("study") || pkgLower.contains("sach") || pkgLower.contains("toan") -> "Học tập"

                pkgLower.contains("game") || pkgLower.contains("roblox") || pkgLower.contains("freefire") ||
                pkgLower.contains("garena") || pkgLower.contains("minecraft") || pkgLower.contains("pubg") ||
                pkgLower.contains("genshin") || pkgLower.contains("honor") -> "Trò chơi"

                else -> "Tiện ích & Khác"
            }

            list.add(
                com.example.data.model.DetailedAppUsage(
                    packageName = pkg,
                    appName = appLabel,
                    minutesUsed = mins,
                    category = category,
                    lastTimeUsed = item.lastTimeUsed
                )
            )
        }

        return list.sortedByDescending { it.minutesUsed }
    }

    /**
     * So phut THAT tung ngay trong tuan (T2 -> CN), lay truc tiep tu
     * UsageStatsManager.
     *
     * Truoc day `weeklyUsage` la hang so 0 nam im trong VesperaUiState, nen
     * bieu do "SU DUNG TRONG TUẦN" va man "Thấu hiểu thời gian" ve cot 0 du
     * may da dung 36 phut (loc BlueStacks 2026-10-21). Day la nguon du lieu
     * that cho hai cho do.
     */
    fun getWeeklyUsageMinutes(): List<Pair<String, Int>> {
        val labels = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
        val um = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return labels.map { it to 0 }
        if (!hasUsageStatsPermission()) return labels.map { it to 0 }

        val midnightToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Calendar.DAY_OF_WEEK: CN=1, T2=2 ... T7=7 -> quy ve thu tu T2..CN.
        val dow = midnightToday.get(Calendar.DAY_OF_WEEK)
        val todayIndex = if (dow == Calendar.SUNDAY) 6 else dow - Calendar.MONDAY

        return labels.mapIndexed { index, label ->
            if (index > todayIndex) {
                label to 0 // ngay chua toi trong tuan
            } else {
                val from = (midnightToday.clone() as Calendar)
                    .apply { add(Calendar.DAY_OF_YEAR, index - todayIndex) }.timeInMillis
                val to = if (index == todayIndex) {
                    System.currentTimeMillis()
                } else {
                    (midnightToday.clone() as Calendar)
                        .apply { add(Calendar.DAY_OF_YEAR, index - todayIndex + 1) }.timeInMillis
                }
                label to minutesInWindow(um, from, to)
            }
        }
    }

    /** Nửa đêm hôm nay — mốc chung cho mọi con số "hom nay" trong app. */
    private fun todayStartMillis(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Dung mot dong DUY NHAT cho moi package trong [from, to).
     *
     * `queryUsageStats(INTERVAL_DAILY)` tra ve bucket ngay CHONG LEN bucket
     * "dang chay" cua cung mot app; cong don moi dong la tinh hai lan. Loc
     * that tren BlueStacks 2026-10-21: the "Da dung hom nay" doi 88 phut trong
     * khi bieu do tuan cung ngay bao 53 — hai con số cung mot thuc te vi chung
     * di qua hai phep tinh khac nhau. Gio ca hai nguon deu goi qua ham nay.
     */
    private fun usageRowsPerPackage(um: UsageStatsManager, from: Long, to: Long): List<UsageStats> {
        val stats = runCatching {
            um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, from, to)
        }.getOrNull() ?: return emptyList()
        val bestByPackage = HashMap<String, UsageStats>()
        for (s in stats) {
            if (s.lastTimeUsed !in from until to) continue
            val kept = bestByPackage[s.packageName]
            if (kept == null || s.totalTimeInForeground > kept.totalTimeInForeground) {
                bestByPackage[s.packageName] = s
            }
        }
        return bestByPackage.values.toList()
    }

    /**
     * So phut dung may trong [from, to). Cat le tung app va bo app duoi 1 phut
     * — DUNG phep tinh cua `getDetailedAppUsageList()` de the "Da dung hom
     * nay" va cot T4 tren bieu do khong bao hai so khac nhau cung mot ngay.
     */
    private fun minutesInWindow(um: UsageStatsManager, from: Long, to: Long): Int =
        usageRowsPerPackage(um, from, to)
            .filter { it.totalTimeInForeground >= 60_000L }
            .sumOf { (it.totalTimeInForeground / 60_000L).toInt() }

    /**
     * Get current battery percentage & charging status.
     *
     * `-1` = may KHONG BAO pin nao. Truoc day o day tra `85` khi doc khong
     * duoc, va con so bia den that đi khắp nơi: notification, Firestore
     * `batteryLevel`, tin nhan SOS — may phu huynh thay "Pin 85%" cua mot cai
     * may khong he do ra pin. That bai thi phai noi that bai.
     */
    fun getBatteryInfo(): Pair<Int, Boolean> {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging: Boolean = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(batteryPct, isCharging)
    }

    /**
     * Check if location permission is granted (FINE **hoặc** COARSE).
     * Giữ nguyên chữ ký — MainActivity.kt:115, VesperaViewModel.kt:1439 vẫn gọi.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /** CHỈ khi có FINE mới được phép dùng [Priority.PRIORITY_HIGH_ACCURACY]. */
    fun hasFineLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Mức ưu tiên định vị được PHÉP gọi với quyền hiện có.
     * `null` = chưa có quyền vị trí nào → cấm gọi FusedLocation (chống "tọa độ bịa").
     */
    fun preferredLocationPriorityOrNull(): Int? = when {
        hasFineLocationPermission() -> Priority.PRIORITY_HIGH_ACCURACY
        hasLocationPermission() -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        else -> null
    }

    /** Lý do thiếu quyền, nói thẳng với người dùng; `null` khi đã đủ GPS chính xác. */
    fun missingPermissionReason(): String? = when {
        hasFineLocationPermission() -> null
        hasLocationPermission() ->
            "Chỉ mới có quyền Vị trí xấp xỉ (COARSE) — cần quyền GPS CHÍNH XÁC để định vị đúng chỗ"
        else -> "Chưa cấp quyền Vị trí — ứng dụng không định vị được"
    }

    /**
     * Điểm giả lập (mock provider) — bắt buộc loại vì app dùng cho SOS và phụ huynh giám sát con.
     * minSdk = 24: [Location.isMock] chỉ tồn tại từ API 31 nên phải phân nhánh theo
     * [Build.VERSION.SDK_INT]; [Location.isFromMockProvider] bị bỏ tên ở API 31.
     */
    fun isMockLocation(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

    /** Toạ độ có hợp lý không — loại (0,0) "chưa có fix" và mọi giá trị ngoài Trái Đất. */
    fun isPlausibleLocation(location: Location?): Boolean {
        if (location == null) return false
        val lat = location.latitude
        val lng = location.longitude
        if (lat.isNaN() || lng.isNaN()) return false
        if (lat == 0.0 && lng == 0.0) return false
        return lat in -90.0..90.0 && lng in -180.0..180.0
    }

    /** Sai số > 100 m: KHÔNG được coi là "vị trí hiện tại" nếu không gắn cảnh báo. */
    fun isAccuracyTooLow(location: Location): Boolean =
        location.accuracy > MAX_ACCEPTED_ACCURACY_METERS

    /** Nhà cung cấp không báo sai số (accuracy <= 0) → không được tin là điểm chính xác. */
    fun hasUnknownAccuracy(location: Location): Boolean = location.accuracy <= 0f

    /**
     * Tuổi điểm định vị theo đồng hồ monotonic [SystemClock.elapsedRealtime]
     * (chống người dùng/ứng dụng đổi giờ máy). Trả [Long.MAX_VALUE] khi không có mốc nào.
     */
    fun locationAgeMs(location: Location): Long {
        val elapsedNanos = location.elapsedRealtimeNanos
        if (elapsedNanos > 0L) {
            return (SystemClock.elapsedRealtime() - elapsedNanos / 1_000_000L).coerceAtLeast(0L)
        }
        val wall = location.time
        return if (wall > 0L) (System.currentTimeMillis() - wall).coerceAtLeast(0L) else Long.MAX_VALUE
    }

    /**
     * Mốc giờ máy của CHÍNH điểm định vị — dùng cho `LocationData.updatedAt` (yêu cầu 5),
     * không bao giờ sao chép giá trị cũ. `time` bất hợp lý (âm / quá xa trong tương lai,
     * điển hình của mock provider) bị thay bằng giờ hiện tại của lần nhận điểm.
     */
    fun fixTimeMillis(location: Location): Long {
        val now = System.currentTimeMillis()
        val reported = location.time
        return if (reported in 1L..(now + MAX_CLOCK_FORWARD_SLACK_MS)) reported else now
    }

    /**
     * API CŨ — giữ nguyên chữ ký vì VesperaViewModel.kt:1465 vẫn gọi theo cách này.
     *
     * Chỉ trả toạ độ khi: đã có quyền, không phải điểm mock, toạ độ hợp lý, là điểm TƯƠI
     * và sai số <= [MAX_ACCEPTED_ACCURACY_METERS]. Mọi trường hợp khác trả `null`.
     * Callback này KHÔNG có chỗ để đánh dấu "điểm cũ / sai số lớn", nên thà trả null còn
     * hơn trả một tọa độ gây nhầm lẫn. Code mới phải dùng [fetchLocationFix].
     */
    fun fetchCurrentLocation(onLocation: (Location?) -> Unit) {
        fetchLocationFix { fix ->
            val usable = fix.location
                ?.takeIf { fix.source == LocationFixSource.LIVE_FIX && !fix.accuracyTooLow }
            onLocation(usable)
        }
    }

    /**
     * Lấy vị trí hiện tại với hợp đồng trung thực (khuyến nghị cho code mới).
     *
     *  1. Thiếu quyền vị trí → [LocationFix.missingFinePermission] + `location = null`,
     *     KHÔNG gọi FusedLocation, KHÔNG lặng lẽ trả điểm cũ.
     *  2. `getCurrentLocation(PRIORITY_HIGH_ACCURACY, token)` với timeout [FRESH_FIX_TIMEOUT_MS];
     *     `CancellationTokenSource` luôn bị huỷ khi coroutine bị huỷ / hết timeout.
     *     (play-services-location 21.3.0 CHỈ có overload nhận `CancellationToken`, không có
     *     overload `CancellationSignal` — đã kiểm chứng trong lớp jar của bản này.)
     *  3. Điểm mock và toạ độ bất hợp lý bị loại. Chỉ fallback `lastLocation` khi còn tươi
     *     (< [MAX_STALE_FIX_AGE_MS]) và luôn gắn nhãn [LocationFixSource.STALE_CACHED_FIX].
     *  4. Không có gì thật → [LocationFixSource.NO_FIX] với `location = null`.
     *
     * Callback chạy trên Main thread.
     */
    fun fetchLocationFix(
        timeoutMs: Long = FRESH_FIX_TIMEOUT_MS,
        maxCachedAgeMs: Long = MAX_STALE_FIX_AGE_MS,
        onFix: (LocationFix) -> Unit
    ) {
        val priority = preferredLocationPriorityOrNull()
        if (priority == null) {
            Log.w(TAG, missingPermissionReason().orEmpty())
            locationScope.launch {
                onFix(LocationFix(null, LocationFixSource.NO_FIX, missingFinePermission = true))
            }
            return
        }

        locationScope.launch {
            val coarseOnly = !hasFineLocationPermission()
            val live = requestFreshLocation(priority, timeoutMs)
            val liveOk = live?.takeIf { isPlausibleLocation(it) && !isMockLocation(it) }
            if (liveOk != null) {
                onFix(buildFix(liveOk, LocationFixSource.LIVE_FIX).copy(coarseOnlyPermission = coarseOnly))
                return@launch
            }
            val sawMock = live != null && isMockLocation(live)

            val cached = readCachedLocation()?.takeIf { isPlausibleLocation(it) && !isMockLocation(it) }
            val cachedAge = cached?.let { locationAgeMs(it) } ?: Long.MAX_VALUE
            if (cached != null && cachedAge <= maxCachedAgeMs) {
                // Điểm cached còn tươi: trả kèm nhãn STALE_CACHED_FIX + tuổi thật.
                onFix(
                    buildFix(cached, LocationFixSource.STALE_CACHED_FIX)
                        .copy(rejectedMockLocation = sawMock, coarseOnlyPermission = coarseOnly)
                )
            } else {
                onFix(
                    LocationFix(
                        location = null,
                        source = LocationFixSource.NO_FIX,
                        coarseOnlyPermission = coarseOnly,
                        rejectedMockLocation = sawMock,
                        accuracyTooLow = cached != null && isAccuracyTooLow(cached),
                        ageMs = if (cached != null && cachedAge != Long.MAX_VALUE) cachedAge else 0L
                    )
                )
            }
        }
    }

    private fun buildFix(location: Location, source: LocationFixSource): LocationFix =
        LocationFix(
            location = location,
            source = source,
            accuracyTooLow = isAccuracyTooLow(location),
            unknownAccuracy = hasUnknownAccuracy(location),
            ageMs = locationAgeMs(location)
        )

    private suspend fun requestFreshLocation(priority: Int, timeoutMs: Long): Location? {
        val cts = CancellationTokenSource()
        return try {
            withContext(Dispatchers.Main) {
                withTimeoutOrNull(timeoutMs) {
                    suspendCancellableCoroutine<Location?> { cont ->
                        try {
                            fusedLocationClient
                                .getCurrentLocation(priority, cts.token)
                                .addOnSuccessListener { loc ->
                                    if (cont.isActive) cont.resume(loc)
                                }
                                .addOnFailureListener { e ->
                                    Log.w(TAG, "getCurrentLocation thất bại: ${e.message}")
                                    if (cont.isActive) cont.resume(null)
                                }
                        } catch (se: SecurityException) {
                            // Quyền bị thu hồi đúng lúc đang gọi.
                            Log.w(TAG, "SecurityException khi gọi getCurrentLocation($priority)")
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                }
            }
        } finally {
            // Huỷ request GPS ngay khi hết timeout hoặc coroutine bị huỷ.
            cts.cancel()
        }
    }

    private suspend fun readCachedLocation(): Location? =
        try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine<Location?> { cont ->
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "lastLocation thất bại: ${e.message}")
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }
        } catch (se: SecurityException) {
            Log.w(TAG, "SecurityException khi đọc lastLocation")
            null
        }

    companion object {
        /** Sai số tối đa chấp nhận được cho "vị trí hiện tại" (SOS + giám sát trẻ em). */
        const val MAX_ACCEPTED_ACCURACY_METERS = 100f

        /** Điểm cached già hơn mức này không bao giờ được trả về. */
        const val MAX_STALE_FIX_AGE_MS = 60_000L

        /** Timeout một lần gọi getCurrentLocation. */
        const val FRESH_FIX_TIMEOUT_MS = 12_000L

        /** Dung sai đồng hồ khi tin `location.time`. */
        private const val MAX_CLOCK_FORWARD_SLACK_MS = 5_000L
    }
}
