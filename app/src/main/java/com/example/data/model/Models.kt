package com.example.data.model

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
    val historyChangedAt: Long = 0L
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

data class UsageSchedule(
    val id: String = "",
    val pairingId: String = "",
    val title: String = "Lịch ngày thường",
    val dailyTargetMinutes: Int = 120,
    /**
     * So phut da dung hom nay. MAC DINH LA 0 — day KHONG phai noi dung de
     * tu hien: `usedMinutes` chua bao gio duoc ghi lai sau khi tao tai lieu,
     * nen gia tri duoi mac dinh cu (45) se treo mai o moi man hinh va bi
     * hien nhu "hom nay con da dung 45 phut". Gio thi ScreenTimeScreen /
     * StudentMainScreen lay so phut THAT tu `detailedAppUsage`, con truong
     * nay chi la du lieu du phong khi may chua cap quyen Usage Access.
     */
    val usedMinutes: Int = 0,
    val allowedStartHour: Int = 7,
    val allowedStartMinute: Int = 0,
    val allowedEndHour: Int = 21,
    val allowedEndMinute: Int = 30,
    val daysOfWeek: String = "T2, T3, T4, T5, T6, T7, CN",
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
    val isActive: Boolean = true,
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
    val proposedAt: Long = 0L,
    val respondedAt: Long = 0L
)

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
    val minutesRequested: Int = 30,
    val reason: String = "",
    val status: RequestStatus = RequestStatus.PENDING,
    val respondedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class ScreenTimeAgreement(
    val id: String = "",
    val pairingId: String = "",
    val requestedMinutes: Int = 120,
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
    val startHour: Int = 14,
    val startMinute: Int = 0,
    val endHour: Int = 16,
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
    val placeName: String = "Điểm dừng chân",
    val speed: Float = 0.0f,
    val batteryLevel: Int = 0,
    val isCharging: Boolean = false,
    val duration: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val dateStr: String = "",
    val timeStr: String = "",
    val year: Int = 2026,
    val month: Int = 10,
    val day: Int = 12,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0
)

