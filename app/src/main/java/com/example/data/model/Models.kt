package com.example.data.model

import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

enum class UserRole {
    PARENT,
    CHILD
}

enum class PairingStatus {
    UNPAIRED,
    PAIRING_PENDING,
    PAIRED,
    UNPAIRING
}

enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class EmotionTag(val label: String, val emoji: String) {
    HAPPY("Vui vẻ", "😊"),
    NORMAL("Bình thường", "😐"),
    STUDY_STRESS("Áp lực học tập", "📚"),
    NEED_SUPPORT("Cần hỗ trợ", "🆘")
}

data class UserProfile(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String = "",
    val role: UserRole = UserRole.PARENT,
    val studentCode: String = "",
    val verificationPin: String = "",
    val pairedWith: String = "",
    val activePairingId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Pairing(
    val id: String = "",
    val parentId: String = "",
    val childId: String = "",
    val studentCode: String = "",
    val parentName: String = "",
    val childName: String = "",
    val status: PairingStatus = PairingStatus.UNPAIRED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Co dang ghi diem di chuyen khong. Truoc day co quan nay nam trong
     * SharedPreferences cua may, nghia la hoc sinh tat duoc ma me khong he
     * biet. Chuyen sang document `pairings/{id}` de ca hai ben cung thay.
     */
    val locationHistoryEnabled: Boolean = true,
    /** Ai la nguoi thay doi gan nhat, va thay doi luc nao. */
    val historyChangedByRole: UserRole? = null,
    val historyChangedByName: String = "",
    val historyChangedAt: Long = 0L,
    /**
     * "Chạm tay" Zenly Bump. Truoc day nut nay CHI rung may dang bam va tang
     * mot bo dem trong bo nho — may ben kia khong nhan duoc gi ca, trong khi
     * nhan ghi "Rung chuông". Nay ghi gio that vao document ghep noi de ben
     * kia nghe duoc listener va rung that; 0 = chua ai chạm.
     */
    val lastBumpAt: Long = 0L,
    val lastBumpByRole: UserRole? = null,
    val lastBumpByName: String = ""
)

data class PairingRequest(
    val id: String = "",
    val studentCode: String = "",
    val parentId: String = "",
    val parentName: String = "",
    val parentEmail: String = "",
    val childId: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)

data class LocationData(
    val userId: String = "",
    val pairingId: String = "",
    val userName: String = "",
    // 0.0 = CHUA CO vi tri. Truoc day mac dinh toa do Ha Noi, nen moi lan
    // thieu GPS la may tu "dinh vi" o mot noi khong ai dung den.
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0.0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val isPrivacyActive: Boolean = false,
    val currentSafeZone: String = "",
    val topActiveApp: String = "",
    val topActiveAppMinutes: Int = 0,
    val totalUsedMinutes: Int = 0,
    val isSos: Boolean = false,
    val updatedAt: Long = 0L
)

/**
 * Vi tri nay den tu cam bien that khong. 0/0 la "chua do duoc GPS", con toa do
 * ngoai khoang hop le cua trai dat thi coi nhu du lieu hong.
 */
val LocationData.hasRealFix: Boolean
    get() = !(latitude == 0.0 && longitude == 0.0) &&
            latitude >= -90.0 && latitude <= 90.0 &&
            longitude >= -180.0 && longitude <= 180.0

/**
 * Bo noi quy gia dinh. TOAN BO mac dinh o day mang nghia "CHUA cau hinh",
 * khong phai mot gia tri do duoc:
 *  - `title` rong -> UI in "Chưa đặt tên lịch";
 *  - `dailyTargetMinutes = 0` -> chua han muc phut, BlockPolicy khong chan
 *    theo phut (xem `BlockPolicy.blockReason`);
 *  - khung gio `null` -> chua cau hinh gio giac, BlockPolicy bo qua luat ngoai
 *    gio. Truoc day no la 7:00-21:30 nen mot gia dinh chua bao gio chon gi
 *    van bi hien "khung gio cho phep 07:00 - 21:30" nhu do chinh ho da dat;
 *  - `daysOfWeek` rong -> ap dung moi ngay (BlockPolicy da hieu the);
 *  - `isActive = false` + `isReminderOnly = true` -> may hoc sinh khong chan
 *    gi ca cho den khi co nguoi bat that su.
 */
data class UsageSchedule(
    val id: String = "",
    val pairingId: String = "",
    val title: String = "",
    val dailyTargetMinutes: Int = 0,
    /**
     * So phut da dung hom nay. MAC DINH LA 0 — day KHONG phai noi dung de
     * tu hien: `usedMinutes` chua bao gio duoc ghi lai sau khi tao tai lieu,
     * nen gia tri duoi mac dinh cu (45) se treo mai o moi man hinh va bi
     * hien nhu "hom nay con da dung 45 phut". Gio thi ScreenTimeScreen /
     * StudentMainScreen lay so phut THAT tu `detailedAppUsage`, con truong
     * nay chi la du lieu du phong khi may chua cap quyen Usage Access.
     */
    val usedMinutes: Int = 0,
    val allowedStartHour: Int? = null,
    val allowedStartMinute: Int? = null,
    val allowedEndHour: Int? = null,
    val allowedEndMinute: Int? = null,
    val daysOfWeek: String = "",
    val isReminderOnly: Boolean = true,
    /**
     * Danh sach goi tuc ung dung bi chan hoan toan khi [isReminderOnly] = false.
     * Vi du: "com.zhiliaoapp.musically" (TikTok).
     */
    val blockedPackages: List<String> = emptyList(),
    /**
     * Nhung ung dung luon duoc phep chay, ke ca khi ngoai gio hoac het han ngay.
     */
    val allowedApps: List<String> = emptyList(),
    /** Bat len thi may hoc sinh moi chan; mac dinh = chua bat. */
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Phieu dong thuan cua noi quy gia dinh.
     *
     * Cac truong o tren (blockedPackages / isReminderOnly / dailyTargetMinutes)
     * luon la ** luat dang ap dung that ** — may cua hoc sinh chan theo dung
     * danh sach nay, khong can biet changeStatus.
     *
     * Con `changeStatus == PENDING` co nghia mot phia dang xin DOI va ben kia
     * chua tra loi. Phan "xin doi" nam trong cac truong pending* ben duoi,
     * tach khoi phan dang chay de:
     * - de xuat khong lam mat hieu luc cua luat cu (khong co khoang trong
     *   "may khong chan gi ca" khi cho duyet);
     * - neu bi tu choi thi luat cu van nguyen, khong phai hoan tro.
     */
    val changeStatus: RequestStatus = RequestStatus.APPROVED,
    /** Ben giu de xuat (Parent hoac Child) — dung chan ben do tu duyet minh. */
    val proposedByRole: UserRole? = null,
    val pendingBlockedPackages: List<String> = emptyList(),
    val pendingAllowedApps: List<String> = emptyList(),
    /** null = khong doi che do "chi nhac nho". */
    val pendingIsReminderOnly: Boolean? = null,
    /** null = khong doi han muc phut/ngay. */
    val pendingDailyTargetMinutes: Int? = null,
    /**
     * Khung gio ben phieu de xuat xin ap dung, dung chung dang với
     * [windowCode]: `""` = ben nay xin XOA khung gio (may khong chan theo nua),
     * con lai la `"HH:mm-HH:mm"`.
     *
     * Dung chuoi rong chu khong dung `null` vi khi serialize POJO, Firebase
     * Firestore bo qua truong null: mot phieu "xin xoa khung gio" se bi may doc
     * thanh "phieu nay khong noi gi den khung gio". Phieu luon mang TOAN BO
     * noi quy (app chan + che do + mien tran + han muc + khung gio) nen khong
     * co truong hop "chi doi mot phan".
     */
    val pendingAllowedWindow: String = "",
    val proposedAt: Long = 0L,
    val respondedAt: Long = 0L
)

/**
 * Dang khung gio dang chay thanh mot chuoi de so sanh va gui trong phieu.
 * `""` = chua dat: khong phai "cho phep ca ngay" ma la "KHONG co luat ngoai
 * gio" — UI phai in "Chưa đặt khung giờ", con BlockPolicy thi bo qua lop nay.
 */
fun UsageSchedule.windowCode(): String {
    val s = allowedStartHour ?: return ""
    val e = allowedEndHour ?: return ""
    return windowCodeFrom(s * 60 + (allowedStartMinute ?: 0), e * 60 + (allowedEndMinute ?: 0))
}

/** "07:00-21:30" -> (420, 1290) so phut tu 00:00; null neu chuoi khong hop le. */
fun parseWindowCode(code: String): Pair<Int, Int>? {
    val halves = code.split('-')
    if (halves.size != 2) return null
    val a = halves[0].trim().split(':').map { it.toIntOrNull() ?: return null }
    val b = halves[1].trim().split(':').map { it.toIntOrNull() ?: return null }
    if (a.size != 2 || b.size != 2) return null
    if (a[0] !in 0..23 || b[0] !in 0..23 || a[1] !in 0..59 || b[1] !in 0..59) return null
    return (a[0] * 60 + a[1]) to (b[0] * 60 + b[1])
}

fun windowCodeFrom(startMinutes: Int, endMinutes: Int): String =
    "%02d:%02d-%02d:%02d".format(
        startMinutes / 60 % 24, startMinutes % 60,
        endMinutes / 60 % 24, endMinutes % 60
    )

/**
 * Ap mot khung gio (hay xoa no, neu [code] rong hoac khong parse duoc) vao
 * noi quy. Chi dung khi ap dung phieu — doc tu Firestore ve thi khong go.
 */
fun UsageSchedule.withWindowCode(code: String): UsageSchedule {
    val w = parseWindowCode(code)
    return if (w == null) {
        copy(
            allowedStartHour = null, allowedStartMinute = null,
            allowedEndHour = null, allowedEndMinute = null
        )
    } else {
        copy(
            allowedStartHour = w.first / 60, allowedStartMinute = w.first % 60,
            allowedEndHour = w.second / 60, allowedEndMinute = w.second % 60
        )
    }
}

/** Chuoi hien thi cho nguoi dung: "07:00 → 21:30", hoac null khi chua dat. */
fun windowDisplay(code: String): String? =
    parseWindowCode(code)?.let { windowCodeFrom(it.first, it.second).replace("-", " → ") }

/**
 * Mot dong trong nhat ky noi quy: ai doi gi, khi nao, ket ra sao.
 *
 * Cap nhat trong Firestore la bi (chi them + doc), xem `firestore.rules`
 * — day la phan "minh bach" cua co che dong thuan: ca hai ben deu doc
 * duoc qua khuyu thay doi, khong the xoa im vet.
 */
data class RuleAuditEntry(
    val id: String = "",
    val pairingId: String = "",
    val actorRole: UserRole? = null,
    val actorName: String = "",
    /** "de_xuat", "dong_y", "tu_choi", "rut", "ap_dung", "lich_su". */
    val action: String = "",
    val detail: String = "",
    val status: RequestStatus? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ExtraTimeRequest(
    val id: String = "",
    val pairingId: String = "",
    val childId: String = "",
    val childName: String = "",
    // Mac dinh = 0 chu khong phai 30: mot document thieu truong phai hiện ra
    // la "chưa có con số", khong phai mot yêu cầu 30 phut do app tu che.
    val minutesRequested: Int = 0,
    val reason: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val respondedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ScreenTimeAgreement(
    val id: String = "",
    val pairingId: String = "",
    val requestedMinutes: Int = 0,
    val proposerRole: UserRole = UserRole.PARENT,
    val status: RequestStatus = RequestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
)

data class PrivacyRequest(
    val id: String = "",
    val pairingId: String = "",
    val childId: String = "",
    val childName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    // -1 = khong co khung gio. 14/16 tung khiến một phiếu thiếu dữ liệu đọc
    // thành "từ 14:00 đến 16:00", tức app bịa ra khung giờ người ta không xin.
    val startHour: Int = -1,
    val startMinute: Int = 0,
    val endHour: Int = -1,
    val endMinute: Int = 0,
    val reason: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val respondedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class FamilyMessage(
    val id: String = "",
    val pairingId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: UserRole = UserRole.PARENT,
    val text: String = "",
    val emotion: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val isStarred: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AppCategoryUsage(
    val categoryName: String,
    val minutes: Int,
    val iconName: String,
    val percentage: Float
)

data class DetailedAppUsage(
    val packageName: String,
    val appName: String,
    val minutesUsed: Int,
    val category: String,
    val lastTimeUsed: Long = System.currentTimeMillis()
)

/**
 * So phut da dung hom nay TREN MAY NAY, cong tu danh sach app doc duoc bang
 * quyen Usage Access. Neu danh sach rong (chua cap quyen / chua doc xong) thi
 * dung `fallback` — thuong la `UsageSchedule.usedMinutes`, hien mac dinh 0.
 *
 * Dung chung cho moi man hinh de "Du dung hom nay" o Screen Time, o Student
 * va o Dashboard khong con la ba con so khac nhau cung mot thoi diem.
 */
fun todayUsedMinutes(detailed: List<DetailedAppUsage>, fallback: Int = 0): Int =
    detailed.sumOf { it.minutesUsed }.let { if (it > 0) it else fallback }

data class LocationHistoryEntry(
    val id: String = "",
    val pairingId: String = "",
    val userId: String = "",
    val userName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    /**
     * Rong = may do duoc toa do nhung KHONG dich duoc thanh ten cho (geocoder
     * chua chay, chua co quyen, hoac o ngoai vi do). Truoc day mac dinh la
     * "Điểm dừng chân" nen mot tai lieu thieu ten van hiên ra nhu mot dia diem
     * that da duoc dat ten.
     */
    val placeName: String = "",
    val speed: Float = 0.0f,
    val batteryLevel: Int = 0,
    // Kotlin sinh getter `isCharging()`, Firestore cat chu "is" va doi doc
    // "charging" trong khi ben ghi luon ghi khoa "isCharging" -> moi doc mot
    // tai lieu luu bao "No setter/field for isCharging" va co pin sac bi rung
    // khi hien thi lich su. Ten khoa phai khai bao ro.
    @get:PropertyName("isCharging") val isCharging: Boolean = false,
    val duration: Long = 0,
    @get:ServerTimestamp val serverTimestamp: Date? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val dateStr: String = "",
    val timeStr: String = "",
    // 0 = tai lieu khong ghi duoc ngay/thang. Vi phan hien thi sap theo
    // `timestamp` (FieldValues that tu GPS), cac truong nay chi la ban in thuan
    // tien; bo mac dinh 2026/10/12 de khong co moc thoi gian ao nao.
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
)

