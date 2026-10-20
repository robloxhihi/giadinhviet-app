package com.example.service.blocker

import com.example.data.model.UsageSchedule
import java.util.Calendar

/**
 * Quyet dinh: tai thoi diem nay, ung dung [packageName] co bi chan tren may
 * cua hoc sinh hay khong.
 *
 * Tam ly thiet ke: mac dinh AN TOAN. Chi chan khi that su ro rang, va
 * khong bao gio chan nhung thu khien nguoi dung mat duong thoat.
 */
object BlockPolicy {

    /** Nhung goi tuc khong bao gio bi chan. */
    private val NEVER_BLOCKED: Set<String> = setOf(
        "com.android.settings",          // de tat duoc chan
        "com.samsung.android.settings",
        "com.google.android.settings.intelligence",
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "oppo.launcher",
        "com.vivo.launcher",
        "com.android.dialer",            // goi dien
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.emergency",
        "com.android.phone",
        "com.android.incallui",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.chrome",            // de tim duoc huong dan go
        "com.android.vending",           // CH Play: de go duoc app nay
        "com.google.android.permissioncontroller",
        "com.google.android.gms",        // dang nhap Google + Credential Manager
        "com.google.android.gsf",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard"
        // Khong can liet ke app cua minh o day: isSelf() da lo.
        // Luu y: applicationId that la "com.aistudio.vesperafamily.vspxql",
        // khac voi namespace "com.example", nen khong duoc hard-code ten goi tuc.
    )

    /** Dung ten goi tuc ung dung cua minh de khong tu chan minh. */
    fun isSelf(pkg: String?, ownPackage: String): Boolean = pkg == ownPackage

    /**
     * Boy nhung thu khong duoc phep chan, ke ca khi chung xuat hien trong
     * `blockedPackages` do nguoi dung chon nham:
     * - man hinh chinh (launcher): chan launcher = bi day ve Home lien tuc, may rung loan
     * - ban phim (inputmethod): chan ban phim = khong go duoc chu nao
     * - dich vu Google: chan la khong dang nhap duoc vao app
     * - CH Play: chan la khong go duc app de mo khoa
     */
    fun isNeverBlocked(pkg: String): Boolean =
        NEVER_BLOCKED.any { pkg.equals(it, ignoreCase = true) } ||
            pkg.startsWith("com.android.") ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains(".launcher", ignoreCase = true) ||
            pkg.endsWith("android.launcher") ||
            pkg == "com.google.android.gms" ||
            pkg.startsWith("com.google.android.gsf")

    /**
     * Tra ve ly do ngan bang tieng Viet neu ung dung bi chan,
     * hoac null neu duoc phep chay.
     */
    fun blockReason(
        pkg: String?,
        schedule: UsageSchedule?,
        ownPackage: String,
        usedMinutesToday: Int,
        now: Calendar = Calendar.getInstance()
    ): String? {
        if (pkg.isNullOrBlank()) return null
        if (isSelf(pkg, ownPackage)) return null
        if (isNeverBlocked(pkg)) return null

        // Chua bat che do chan thi coi nhu nhac nho.
        if (schedule == null || !schedule.isActive || schedule.isReminderOnly) return null

        if (!isDayScheduled(schedule, now)) return null

        if (schedule.blockedPackages.any { it.equals(pkg, ignoreCase = true) }) {
            return "Ứng dụng này bị chặn theo lịch của gia đình."
        }

        // Het khung gio cho phep -> chan moi thu khong thuoc danh sách cho phép.
        if (!isWithinAllowedWindow(schedule, now)) {
            if (schedule.allowedApps.none { it.equals(pkg, ignoreCase = true) }) {
                return "Đã quá giờ dùng điện thoại theo lịch của gia đình."
            }
        }

        // Vuot han so phut ca ngay -> chan, trir danh sách cho phép.
        if (usedMinutesToday >= schedule.dailyTargetMinutes) {
            if (schedule.allowedApps.none { it.equals(pkg, ignoreCase = true) }) {
                return "Hôm nay đã dùng hết thời gian cho phép."
            }
        }

        return null
    }

    /** Lich co ap dung cho ngay hom nay khong. */
    private fun isDayScheduled(s: UsageSchedule, now: Calendar): Boolean {
        if (s.daysOfWeek.isBlank()) return true
        val label = when (now.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "T2"
            Calendar.TUESDAY -> "T3"
            Calendar.WEDNESDAY -> "T4"
            Calendar.THURSDAY -> "T5"
            Calendar.FRIDAY -> "T6"
            Calendar.SATURDAY -> "T7"
            else -> "CN"
        }
        return s.daysOfWeek.split(',').any { it.trim().equals(label, ignoreCase = true) }
    }

    /** Co đang nằm trong khung giờ được dùng máy không. */
    private fun isWithinAllowedWindow(s: UsageSchedule, now: Calendar): Boolean {
        val minutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = s.allowedStartHour * 60 + s.allowedStartMinute
        val end = s.allowedEndHour * 60 + s.allowedEndMinute

        // end < start: khung gio dem (vi du 21:30 -> 06:00)
        return if (end >= start) minutes in start..end
        else minutes >= start || minutes <= end
    }
}
