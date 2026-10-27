package com.example.service.map

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mot diem dinh vi da chuan hoa — bo loc nhay coc quyet dinh tren cac gia tri nay,
 * KHONG can doi tuong `android.location.Location`, neu khong don v test cu phai
 * dung Robolectric moi kiem duoc mot cong thuc toan.
 *
 * speedMetersPerSecond la van toc MAY BAO (Location.speed), khong phai van toc
 * gia dinh — may dung yen thi no ~ 0, do la chinh cai ta can de biet "nhay ca
 * cay" la that hay la loi.
 */
data class GuardedFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val speedMetersPerSecond: Float,
    val timestampMs: Long
)

/** Ket luan cua bo loc cho mot diem dua vao. */
enum class FixVerdict {
    /** Duoc cong bo lam vi tri hien tai (ghi Firestore + di chuyen tam ban do). */
    ACCEPT,

    /** Bo qua: sai so lon hon nguong tin, trong khi may dang giu mot diem chinh
     *  xac hon va con han — day la nguyen nhan chinh khi "mua vai met ma diem bay
     *  ca cay": mot phieu coarse/network chen vao giua cac phieu GPS tot. */
    SKIP_POOR_QUALITY,

    /** Bo qua: nhay xa hon mot do di chuat di nhanh hop ly, VA chua co it nhat
     *  [LocationJumpGuard.minConfirmationsForJump] phieu lien tiep dong thuan o
     *  cho moi. Xac nhan roi thi di chuyển that duoc cho phep. */
    SKIP_UNCONFIRMED_JUMP
}

/**
 * Bo loc nhay coc dinh vi (location outlier / jump filter).
 *
 * Van de nguoi dung bao (ban 69.x): "di chuyển co vai met ma no bay ca cay".
 * Dien thoai tra ve nhieu loai phieu xen ke nhau — GPS tot (±5 m), Coarse/network
 * te (±100..1000 m), va doi khi mot phieu GPS loi. Dich vu nen cua ban LUON lay
 * phieu cuoi ghi de vi tri, nen mot phieu coarse/loi duy nhat keo marker di gan
 * kilomet roi quay lai — "bay ca cay".
 *
 * Bo loc nay giu ba bat buoc, theo thu tu:
 *  1. PHIEU SAU TIN (accuracy > nguong) khong duoc de len mot phieu TIN DUOC con
 *     han. Do la cai "bay" pho bien nhat va loai no la du trong da so truong hop.
 *  2. Mot phieu TIN DUOC nhung NHAY XA hon khoang doi duoc cho phep (tong sai so
 *     hai phieu + quang duong di voi van toc may bao) thi phai CO NXIT TIEP TUC
 *     DONG THUAN quanh cho moi — mot phieu loi bi chan, di chuyen that (di bo,
 *     di xe) van duoc xac nhan sau vai giay.
 *  3. KHONG DUONG Dong Bang vi tri: neu diem dang giu da qua [maxHoldMs] thi
 *     chap nhan phieu moi du no o dau, de may that su di chuyen xa khong bi
 *     "dong" tai cho cu. Day la loi song con so that — thà đôi khi hiện một điểm
 *     cũ còn hơn khoá cứng vị trí mãi.
 *
 * Bo loc chi quyet dinh PHIEN NAO duoc cong bo; no khong doi duoc do chinh xac
 * that cua thiet bi, va khong bao gio tao ra toa do — moi dau vao deu la phieu
 * thiet bi thuc do.
 */
class LocationJumpGuard(
    /** Sai so toi da duoc coi la "tin duoc" — khop [TelemetryAndUsageHelper.MAX_ACCEPTED_ACCURACY_METERS]. */
    val maxTrustedAccuracyMeters: Float = 100f,
    /** Chan cho thanh phan van toc, de mot phieu mock bao toc cao khong mo dung
     *  cho phep "nhay" vo han. 60 m/s ~ 216 km/h — tren ca tach phap thuong. */
    val speedCapMetersPerSecond: Float = 60f,
    /** So phieu lien tiep phai dong thuan o cho moi truoc khi chap nhan mot buoc nhay xa. */
    val minConfirmationsForJump: Int = 2,
    /** Du de bo le hai phieu "dong thuan" o cung mot cho khi GPS nhieu nhieu
     *  nhiu — ket hop toi thieu 30 m. */
    private val minConsensusRadiusMeters: Float = 30f,
    /** Qua lau giu mot diem ma chua chap nhan cai moi thi nha khoa, chong dong
     *  vi tri khi di dong that su. */
    val maxHoldMs: Long = 5 * 60_000L
) {
    private var lastAccepted: GuardedFix? = null
    private var pending: GuardedFix? = null
    private var pendingCount: Int = 0

    /** Vi tri dang duoc giu (phieu ACCEPT gan nhat) — cho test va cho notification. */
    fun currentAnchor(): GuardedFix? = lastAccepted

    /** Xa bo trang thai (vd: khi gap phieu dau tien sau khoi dong lai). */
    fun reset() {
        lastAccepted = null
        pending = null
        pendingCount = 0
    }

    /**
     * Quyet dinh cho mot phieu moi. Goi theo thu tu thoi gian ma thiet bi dao phieu.
     * Chi tra ve ACCEPT khi phieu duoc cong bo; khi do trang thai ben trong cung
     * duoc cap nhat den phieu ay.
     */
    fun decide(candidate: GuardedFix): FixVerdict {
        val last = lastAccepted
            ?: run { accept(candidate); return FixVerdict.ACCEPT }

        val candidateIsTrusted = candidate.accuracyMeters <= maxTrustedAccuracyMeters
        val lastIsTrusted = last.accuracyMeters <= maxTrustedAccuracyMeters

        // (1) Phieu sau tin chep len phieu tin duoc con han => bo qua.
        val lastStillHolds = candidate.timestampMs - last.timestampMs < maxHoldMs
        if (!candidateIsTrusted && lastIsTrusted && lastStillHolds) {
            return FixVerdict.SKIP_POOR_QUALITY
        }

        val elapsedSec = ((candidate.timestampMs - last.timestampMs) / 1000f)
            .coerceIn(0f, MAX_ELAPSED_FOR_MOTION_SEC)
        val allowedMotion = speedMetersPerSecond(last, candidate) * elapsedSec
        val tolerance = (last.accuracyMeters + candidate.accuracyMeters + allowedMotion + STATIONARY_SLACK_METERS)
            .coerceAtLeast(1f)
        val jump = distanceMeters(last, candidate)

        if (jump <= tolerance) {
            accept(candidate)
            return FixVerdict.ACCEPT
        }

        // (2) Nhay qua xa => can dong thuan lien tiep.
        val p = pending
        val consensusRadius = consensusRadius(p, candidate)
        if (p != null && distanceMeters(p, candidate) <= consensusRadius) {
            pendingCount += 1
            pending = candidate
        } else {
            pending = candidate
            pendingCount = 1
        }

        // (3) Nha khoa khi diem dang giu da qua lau, du chua du phieu dong thuan.
        val lastIsStaleForHold = candidate.timestampMs - last.timestampMs >= maxHoldMs
        if (pendingCount >= minConfirmationsForJump || lastIsStaleForHold) {
            accept(candidate)
            return FixVerdict.ACCEPT
        }
        return FixVerdict.SKIP_UNCONFIRMED_JUMP
    }

    private fun accept(f: GuardedFix) {
        lastAccepted = f
        pending = null
        pendingCount = 0
    }

    /** Lay van toc cua phieu dang do, chan theo [speedCapMetersPerSecond]. */
    private fun speedMetersPerSecond(last: GuardedFix, candidate: GuardedFix): Float {
        val fromLast = if (last.speedMetersPerSecond > 0f) last.speedMetersPerSecond else 0f
        val fromCandidate = if (candidate.speedMetersPerSecond > 0f) candidate.speedMetersPerSecond else 0f
        return maxOf(fromLast, fromCandidate).coerceAtMost(speedCapMetersPerSecond)
    }

    /** Ban kinh cho phep hai phieu duoc coi la "cung mot noi moi". */
    private fun consensusRadius(p: GuardedFix?, candidate: GuardedFix): Float {
        val accs = listOfNotNull(p?.accuracyMeters, candidate.accuracyMeters, maxTrustedAccuracyMeters)
        return maxOf(accs.max(), minConsensusRadiusMeters)
    }

    companion object {
        /** Khoang lang tru tinh khi may dung yen (van toc 0), de hai phieu trung
         *  nhau gan nhu chac chan duoc chap nhan chu khong bi coi la "nhay coc". */
        private const val STATIONARY_SLACK_METERS = 5f

        /** Chan thoi gian dung de tinh quang duong di, de mot phieu cu (elapsed
         *  lon) khong mo rong dung sai vo han. */
        private const val MAX_ELAPSED_FOR_MOTION_SEC = 120f

        /** Hoan toa do ra met (Haversine), tam thoi dung ban kinh Trai Dat 6371 km. */
        fun distanceMeters(a: GuardedFix, b: GuardedFix): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.latitude - a.latitude)
            val dLon = Math.toRadians(b.longitude - a.longitude)
            val la1 = Math.toRadians(a.latitude)
            val la2 = Math.toRadians(b.latitude)
            val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(la1) * cos(la2) * sin(dLon / 2) * sin(dLon / 2)
            return 2 * r * asin(min(1.0, sqrt(h)))
        }
    }
}
