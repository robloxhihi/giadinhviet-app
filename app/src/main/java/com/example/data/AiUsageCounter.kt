package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

/**
 * Dem so lan goi AI cua tung tai khoan, ghi len `users/<uid>.aiCallCount`.
 *
 * Ly do co file nay: sach xep hang "thang nao chat nhieu nhat" trong app Quan
 * Tri Vien doc chinh truong nay. Truoc no, lich su chat chi nam trong
 * `getSharedPreferences` cua may dien thoai — may chu khong biet ai dung bao
 * nhieu lan, nen moi con so dua ra deu la che. Do ghi la "số liệu ghi từ máy"
 * tu thong bao, khong phai so do phien chu kiem.
 *
 * Ba dieu kien de mot lan dem duoc ghi:
 *  1. Da dang nhap (khong co uid thi bo, khong tao document vo nghi).
 *  2. Request AI that su thanh cong. Luot that bai / bi chan mang khong tinh.
 *  3. firestore.rules cho phep chu document ghi — `isOwner(uid) && notBanned()`,
 *     nen tai khoan da bi cam cung khong cong them duoc nua.
 *
 * That bai o day im lang co ly do: duong dem khong bao gio duoc phep lam hoac
 * che cau tra loi cua nguoi dung. Loi chi in ra log.
 */
object AiUsageCounter {

    private const val FIELD = "aiCallCount"

    /**
     * Goi sau khi mot luot AI ket thuc thanh cong. merge-set vi `update` fail
     * khi document chua co truong nay (tai khoan cu duoc tao truoc khi co dem).
     */
    @JvmStatic
    fun recordHit() {
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) return
        runCatching {
            FirestoreDb.instance()
                .collection("users")
                .document(uid)
                .set(mapOf(FIELD to FieldValue.increment(1)), SetOptions.merge())
        }.onFailure {
            android.util.Log.w("AiUsageCounter", "khong ghi duoc $FIELD cho $uid", it)
        }
    }
}
