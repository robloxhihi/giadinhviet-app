package com.example.service.map

import java.util.Locale

/**
 * Quy uoc "duoc phep noi chinhxac toi dau" cho mot diem dinh vi.
 *
 * Ly do co file nay: ban 26.0 do tren may gia lap in ra
 * `14.45722, 107.91763 ±1m` va ke map o z19 (~0,3 m/pixel) trong khi may chua
 * ghep noi voi bat ky ai. Dien thoai khong do duoc ±1 m, va so chu so thap phan
 * in ra nhieu hon sai so cua cam bien la mot loi khang dinh khong co that.
 * Moi con so dinh vi tren man hinh phai di qua ham duoi day.
 */
object FixPrecision {

    /** Duoi muc nay thi con so la cua nha cung cap, khong phai cua cam bien. */
    const val MIN_PLAUSIBLE_ACCURACY_M = 3f

    /** Nha cung cap khong bao sai so => khong duoc phep ve/ghi tot hon ±100 m. */
    const val UNKNOWN_ACCURACY_M = 100f

    /** Sai so dung de ve vong tron va de ghi chu — khong bao gio be hon 3 m. */
    fun honestAccuracyMeters(reported: Float): Float = when {
        reported <= 0f -> UNKNOWN_ACCURACY_M
        reported < MIN_PLAUSIBLE_ACCURACY_M -> MIN_PLAUSIBLE_ACCURACY_M
        else -> reported
    }

    /**
     * ±100 m thi toa do chi co y nghia den hang ~110 m, nen in 3 chu so thap
     * phan la du; in 5 chu so la tao cam giac chinh xac gia.
     */
    fun decimalsForAccuracy(meters: Float): Int = when {
        meters <= 1f -> 5
        meters <= 10f -> 4
        meters <= 100f -> 3
        meters <= 1000f -> 2
        else -> 1
    }

    /** Zoom ma mot pixel tren man hinh con mang noi dung voi sai so nay. */
    fun maxZoomForAccuracy(meters: Float): Double = when {
        meters <= 5f -> 19.0
        meters <= 15f -> 18.0
        meters <= 40f -> 17.0
        meters <= 100f -> 15.5
        meters <= 300f -> 14.0
        meters <= 1000f -> 12.5
        else -> 11.0
    }

    /** Toa do duoc bo cut theo dung sai so — dung de IN, khong dung de tinh. */
    fun formatCoordinates(latitude: Double, longitude: Double, meters: Float): String {
        val d = decimalsForAccuracy(meters)
        return String.format(Locale.US, "%.${d}f, %.${d}f", latitude, longitude)
    }

    /** Chuoi sai so in ngay canh toa do — luc nao cung la ±<sai so dung>, khong phai so tho. */
    fun accuracyShortLabel(reported: Float): String =
        "±${honestAccuracyMeters(reported).toInt()} m"

    /**
     * Loi khi con so tho bi huy (bang 0 hoac duoi nguong phan giai cua dien
     * thoai). Tra ve null khi so bao nhieu thi dung vay, de man hinh khoi dai
     * thuat khong can.
     */
    fun accuracyNote(reported: Float): String? = when {
        reported <= 0f ->
            "Máy không báo sai số — không được nói chính xác hơn ±${UNKNOWN_ACCURACY_M.toInt()} m."
        reported < MIN_PLAUSIBLE_ACCURACY_M ->
            "Nhà cung cấp báo ±${reported.toInt()} m — dưới ngưỡng phân giải của điện thoại," +
                    " mọi tính toán dùng ±${MIN_PLAUSIBLE_ACCURACY_M.toInt()} m."
        else -> null
    }
}
