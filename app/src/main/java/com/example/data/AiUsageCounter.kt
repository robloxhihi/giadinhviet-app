package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Dem so lan goi AI cua tung tai khoan — ca tren MAY lan tren FIRESTORE.
 *
 * Ly do co file nay: sach xep hang "thang nao chat nhieu nhat" trong app Quan
 * Tri Vien doc chinh truong `aiCallCount` tren document nguoi dung. Truoc no,
 * lich su chat chi nam trong SharedPreferences cua may — may chu khong biet ai
 * dung bao nhieu lan, nen moi con so dua ra deu la che.
 *
 * LOI NAY them mot chieu thu hai: `aiUsageDay` + `aiUsageToday` la so LUOT CUA
 * RIENG HOM NAY, thu do de app tu cho dung hoi khi vuot nguong 100, va de ba me
 * nhin thay "hom nay con hoi AI 63 lan" chu khong phai tong so ca doi.
 *
 * Tam thoi cua ba con so nguong (phai noi thang, dung de tho):
 *  - Ngong 100 lan do CHINH APP cat, vi cau hoi AI di thang tu may len Gemini/
 *    OpenRouter bang khoa nam trong APK — no khong di qua Firebase. Mot em biet
 *    do APK van goi duoc muon. Chan cung phai can proxy (task #45).
 *  - Con so tren Firestore thi firestore.rules GIU: vuot 100 la bi tu choi ghi,
 *    va trong cung mot ngay khong ai duoc lam no nho di — xoa du lieu app cung
 *    khong reset duoc luot hom nay.
 *
 * Mui giờ co dinh Asia/Ho_Chi_Minh: neu lay gio may, tat dong ho lai sau 12h dem
 * la duoc mot ngay moi de hoi.
 */
object AiUsageCounter {

    /** Tong so luot tu khi co bo dem — du dung cho sach xep hang cua admin. */
    const val TOTAL_FIELD = "aiCallCount"

    /** Nguoi dung vuot nguong nay thi thay can bao. */
    const val WARN_AT = 50

    /** Dat nguong nay la app khong goi AI nua. */
    const val DAILY_LIMIT = 100

    private const val PREFS = "ai_usage_counter"
    private const val DAY_FIELD = "aiUsageDay"
    private const val TODAY_FIELD = "aiUsageToday"
    private val ZONE: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    /** "2026-10-25" theo lich VN — dung lam khoa SharedPreferences va ghi len Firestore. */
    @JvmStatic
    fun todayKey(): String {
        val now = Calendar.getInstance(ZONE)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.time)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** So luot hom nay may nay biet. Chua đo duoc thi la 0, khong bao gio am. */
    @JvmStatic
    fun observeToday(context: Context): Int =
        prefs(context).getInt(todayKey(), 0).coerceAtLeast(0)

    @JvmStatic
    fun remainingToday(context: Context): Int =
        (DAILY_LIMIT - observeToday(context)).coerceAtLeast(0)

    /** Dung cho send(): da het suot trong ngay thi khong goi mang nua. */
    @JvmStatic
    fun isExhaustedToday(context: Context): Boolean = observeToday(context) >= DAILY_LIMIT

    /**
     * Ghi mot luot AI thanh cong. `merge` vi `update` fail khi document chua co
     * truong nay (tai khoan cu duoc tao truoc khi co bo dem), va vi `aiCallCount`
     * can `increment` chu khong the viet so tuyet doi.
     *
     * `aiUsageToday` ghi so TUYET DOI tu bo dem tren may — khong dung increment
     * o day vi increment khong biet "ngay moi": luc dong ho sang ngay thi no
     * cong tiep len cua hom qua. `aiUsageDay` ke ben duoi cho biet so do la cua
     * ngay nao, va chinh no la dieu kien de rules cho phep gia tri nho di.
     */
    @JvmStatic
    fun recordHit(context: Context) {
        val day = todayKey()
        val next = observeToday(context) + 1
        val edit = prefs(context).edit().putInt(day, next)
        // Xoa khoa cua nhung ngay cu: SharedPreferences nay chi can hom nay,
        // de lai thi no lon dan theo so ngay dung app.
        prefs(context).all.keys.filter { it != day }.forEach { edit.remove(it) }
        edit.apply()

        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) return
        runCatching {
            FirestoreDb.instance()
                .collection("users")
                .document(uid)
                .set(
                    mapOf(
                        TOTAL_FIELD to FieldValue.increment(1),
                        DAY_FIELD to day,
                        TODAY_FIELD to next
                    ),
                    SetOptions.merge()
                )
        }.onFailure {
            android.util.Log.w("AiUsageCounter", "khong ghi duoc dem AI cho $uid", it)
        }
    }

    /**
     * Lay so luot hom nay tu Firestore va CHI duoc no LAM TANG bo dem tren may.
     *
     * Goi mot luc khi man AI mo ra. Luc nay `observeToday` bang 0 sau khi xoa du
     * lieu app — neu khong doc lai thi em nho duoc nguong. Chieu nguoc lai (may
     * khac ghi it hon) phai bo qua: tai khoan co the dung tren hai may, va loi
     * mang cung khong duoc phep lam suy so lieu.
     */
    @JvmStatic
    fun syncFromFirestore(context: Context, onSettled: (Int) -> Unit = {}) {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) {
            onSettled(observeToday(context))
            return
        }
        runCatching {
            FirestoreDb.instance().collection("users").document(uid).get()
                .addOnCompleteListener { task ->
                    val remote = if (task.isSuccessful) {
                        val d = task.result
                        val day = d?.getString(DAY_FIELD)
                        val count = (d?.getLong(TODAY_FIELD) ?: 0L).toInt()
                        if (day == todayKey()) count else 0
                    } else 0
                    val local = observeToday(context)
                    val best = local.coerceAtLeast(remote.coerceAtLeast(0))
                    if (best != local && best > 0) {
                        prefs(context).edit().putInt(todayKey(), best).apply()
                    }
                    onSettled(best)
                }
        }.onFailure {
            android.util.Log.w("AiUsageCounter", "khong doc duoc dem AI", it)
            onSettled(observeToday(context))
        }
    }

    /** Dong thong ke kep theo cau hoi gui cho AI, de model biet da hoi bao nhieu. */
    @JvmStatic
    fun quotaNote(context: Context): String =
        "Lượt hỏi AI hôm nay: ${observeToday(context)}/$DAILY_LIMIT" +
            (if (isExhaustedToday(context)) " — đã hết suất" else "")
}
