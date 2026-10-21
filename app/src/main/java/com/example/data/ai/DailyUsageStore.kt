package com.example.data.ai

import android.content.Context
import com.example.data.model.AppCategoryUsage
import com.example.data.model.DetailedAppUsage
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.Calendar

/**
 * Mot ngay ding lieu su dung may cua tre, luu trên MAY (SharedPreferences),
 * khong lenFirestore — du lieu cua tre khong di dau ca.
 *
 * Luoi nay la dau vao cua tro ly AI: nguoi lon hoi "hom nay the nao?" thi
 * thang may phai tra loi dua tren so THAT, khong phai cau noi chung. Vi vay
 * moi lan mo app lai co bang dieu tra cua chinh ngay hom do va 7 ngay truoc.
 *
 * `blockedAttempts` dem lan Bo Chan THUC SU chan (khong phai danh sach app bi
 * chan — danh sach do nam trong UsageSchedule). Dem o day viet bo
 * AppBlockerService, luc do moi biet chac chan rang mot lan ve Home da xay ra.
 */
data class DailyUsageRecord(
    val day: Long,
    val label: String,
    val usedMinutes: Int,
    val quotaMinutes: Int,
    val blockedAttempts: Int,
    val distinctBlockedApps: Int,
    val topApps: String,
    val categories: String,
    val locationPings: Int,
    val emotion: String
)

class DailyUsageStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val listType = Types.newParameterizedType(List::class.java, DailyUsageRecord::class.java)
    private val adapter = moshi.adapter<List<DailyUsageRecord>>(listType)

    /**
     * Goi bo AppBlockerService moi lan chan that. Ghi tren IO thread cua
     * AccessibilityService; SharedPreferences.apply() da khong chan main.
     */
    fun recordBlockedAttempt(packageName: String) {
        val day = epochDay()
        val attempts = prefs.getInt("$KEY_BLOCKED$day", 0) + 1
        val packages = readPackages(day).toMutableSet().apply { add(packageName) }
        prefs.edit()
            .putInt("$KEY_BLOCKED$day", attempts)
            .putString("$KEY_PKGS$day", packages.joinToString("|"))
            .apply()
    }

    /** Gom so lieu hom nay thanh mot ban ghi, de ghi de ban ghi cu cung ngay. */
    fun writeTodaySnapshot(
        usedMinutes: Int,
        quotaMinutes: Int,
        categories: List<AppCategoryUsage>,
        detailed: List<DetailedAppUsage>,
        locationPings: Int,
        emotion: String
    ) {
        val day = epochDay()
        val distinct = readPackages(day)
        val record = DailyUsageRecord(
            day = day,
            label = labelOf(day),
            usedMinutes = usedMinutes,
            quotaMinutes = quotaMinutes,
            blockedAttempts = prefs.getInt("$KEY_BLOCKED$day", 0),
            distinctBlockedApps = distinct.size,
            topApps = detailed
                .sortedByDescending { it.minutesUsed }
                .take(6)
                .filter { it.minutesUsed > 0 }
                .joinToString(", ") { "${it.appName} ${it.minutesUsed}ph" }
                .ifEmpty { "chua ghi nhan app nao" },
            categories = categories
                .sortedByDescending { it.minutes }
                .take(5)
                .joinToString(", ") { "${it.categoryName} ${it.percentage}%" }
                .ifEmpty { "chua co so phep (thieu quyen truy cap su dung)" },
            locationPings = locationPings,
            emotion = emotion
        )
        val kept = readAll().filter { it.day != day && it.day >= day - MAX_HISTORY_DAYS } + record
        prefs.edit().putString(KEY_RECORDS, adapter.toJson(kept.sortedBy { it.day })).apply()
    }

    /** Ban ghi hom nay, hoac null neu chura mo app lan dau sau khi cai dat. */
    fun today(): DailyUsageRecord? = readAll().lastOrNull { it.day == epochDay() }

    /** Nguay gan nhat, sap xep moi nhat truoc — du de AI thay duong loi. */
    fun recent(count: Int): List<DailyUsageRecord> =
        readAll().sortedByDescending { it.day }.take(count)

    /**
     * Bang dieu tra day du gui cho AI. Chuoi nay la CHUNG duy nhat giua nut
     * "Phan tich hom nay" va moi hoi thoai tu do, de AI khao bat ky cau hoi
     * nao cung dua tren so THAT cua cung mot ngay.
     */
    fun summaryForAi(): String {
        val t = today() ?: return "Chưa có số liệu hôm nay (app chưa kịp đọc thống kê sử dụng)."
        val history = recent(8).filter { it.day != t.day }.take(7).reversed()
        val sb = StringBuilder()
        sb.append("HÔM NAY (").append(t.label).append("):\n")
        sb.append("- Phut dung may: ").append(t.usedMinutes).append(" / han muc ").append(t.quotaMinutes).append("\n")
        sb.append("- So lan bi bo chan chan app: ").append(t.blockedAttempts)
            .append(" (").append(t.distinctBlockedApps).append(" ung dung khac nhau)\n")
        sb.append("- Ung dung dung nhieu nhat: ").append(t.topApps).append("\n")
        sb.append("- Theo nhom: ").append(t.categories).append("\n")
        sb.append("- Diem vi tri ghi nhan trong ngay: ").append(t.locationPings).append("\n")
        if (t.emotion.isNotBlank()) sb.append("- Cam xuc con ghi nhan: ").append(t.emotion).append("\n")
        if (history.isNotEmpty()) {
            sb.append("\n7 NGAY TRUOC (moi nhat ben phai):\n")
            history.forEach { r ->
                sb.append("- ").append(r.label)
                    .append(": ").append(r.usedMinutes).append("ph/").append(r.quotaMinutes)
                    .append(", chan ").append(r.blockedAttempts).append(" lan")
                    .append("\n")
            }
            val avg = history.sumOf { it.usedMinutes } / history.size
            sb.append("Trung binh 7 ngay truoc: ").append(avg).append(" phut/ngay.\n")
        }
        return sb.toString().trim()
    }

    private fun readAll(): List<DailyUsageRecord> =
        prefs.getString(KEY_RECORDS, null)?.let { runCatching { adapter.fromJson(it) }.getOrNull() } ?: emptyList()

    private fun readPackages(day: Long): Set<String> =
        prefs.getString("$KEY_PKGS$day", null)
            ?.split('|')
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

    private fun epochDay(): Long = Calendar.getInstance().timeInMillis / 86_400_000L

    private fun labelOf(day: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = day * 86_400_000L }
        return "%02d/%02d".format(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1)
    }

    companion object {
        private const val PREFS = "ai_daily_usage_v1"
        private const val KEY_RECORDS = "records"
        private const val KEY_BLOCKED = "blocked_"
        private const val KEY_PKGS = "blocked_pkgs_"
        private const val MAX_HISTORY_DAYS = 30L
    }
}
