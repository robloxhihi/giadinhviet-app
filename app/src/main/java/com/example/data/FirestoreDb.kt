package com.example.data

import com.google.firebase.firestore.FirebaseFirestore

/**
 * Project Firebase cua app KHONG co database `(default)`. Database duoc tao voi
 * ten dinh danh rieng, va `FirebaseFirestore.getInstance()` khong tham so tro
 * thang `(default)` — mot database khong ton tai.
 *
 * That bai kieu nay rat im lang: snapshot listener khong bao gio phat ra du lieu
 * va cung khong bao gio ném loi ngay o goi `getInstance()`, nen bo chan doc
 * `users/<uid>` roi `pairings/<id>/schedules` mai mai khong ap dung duoc lich nao.
 * Vi vay TOAN BO app phai lay instance tu day, khong goi truc tiep.
 */
object FirestoreDb {

    const val DATABASE_ID = "ai-studio-android-vespera-f0b8eaec-ca07-4549-bb58-a45233ead3c3"

    @JvmStatic
    fun instance(): FirebaseFirestore = FirebaseFirestore.getInstance(DATABASE_ID)
}
