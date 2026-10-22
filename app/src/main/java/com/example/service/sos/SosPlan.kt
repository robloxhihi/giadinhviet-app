package com.example.service.sos

import com.example.service.map.FixPrecision
import java.util.Locale

/**
 * Bo quyet dinh thuan Kotlin cua man hinh cuu nan (SOS).
 *
 * Tam goi rieng file nay vi nam cau hoi "bam nut SOS thi dieu gi xay ra" tra
 * loi duoc khong can may that, va chang cau nao trong so do duoc phep chi
 * ton tai trong mot composable (composable khong test duoc tren may tinh):
 *   (1) may co Zalo thi moi co "Goi qua Zalo", khong co thi chi con
 *       "Goi dien thoai thuong" voi so nguoi than da luu;
 *   (2) tong dai 113 (va 112/114/115) dung ACTION_CALL khi da co quyen
 *       CALL_PHONE, NGUOC LAI ACTION_DIAL — de khong bao gio SecurityException;
 *   (3) noi dung SMS soan san gui nguoi than (khong can quyen SEND_SMS);
 *   (4) cau doc bang Tieng Viet khi qua 2 giay.
 *
 * File nay tuyet doi KHONG cham android.* (khong `Intent`, khong `Uri`, khong
 * `Context`) — do vay unit test chay thang tren may tinh, cung cach voi
 * `ConsentPolicy.kt`. Xem `SosPlanTest.kt`.
 *
 * Mot hop thu khac cua file nay: chuoi hien tren man hinh. App nay tung in ten
 * thuong mai cua ben khac len banner cuu nan, tuc la ngay luc con can tin
 * nhat thi nguoi dung doc ten mot san pham khong ai biet. Moi chuoi o day la
 * trang thai hoac loi dan, khong phai ten nhieu.
 */
object SosPlan {

    // ── Chuoi bat buoc hien tren man hinh ────────────────────────────────

    /**
     * Dong chu duy nhat thay cho chuoi thuong mai cu o banner SOS: luc may dang
     * mo ban quay so / ket noi tong dai thi nguoi dung can biet may dang lam
     * viec gi, khong can biet app ten gi.
     */
    const val REDIRECTING_TO_RESCUE = "Đang chuyển hướng đến cứu hộ"

    /** Goi y nghia: bam la mo ban quay so, nguoi dung van phai bam nut Goi. */
    const val DIALER_OPENED = "Đã mở bàn quay số — bấm Gọi để kết nối."

    /** Goi thang khi quyen CALL_PHONE da duoc cap. */
    const val CALL_STARTED = "Đang bắt đầu cuộc gọi…"

    /** Khi may khong co ung dung nao xu ly tel:/sms: (may tre em, may gia dinh khong cai dung cu). */
    const val NO_DIALER_APP = "Máy này không có ứng dụng quay số để mở. Hãy gọi trực tiếp từ một điện thoại khác."

    /**
     * May khong co app quay so mac dinh (BlueStacks, ROM khong dien thoai): thoi
     * thay de mot app nhan tin dom linh ket `tel:`. Do tren BlueStacks
     * 2026-10-22: bam "Hotline 113" lai mo man dang nhap Zalo, vi Zalo dang ky
     * la noi xu ly lien ket dien thoai va may khong co dialer mac dinh.
     */
    fun noDialerHint(number: String): String =
        "Máy này không có ứng dụng quay số. Hãy tự gọi $number từ một điện thoại khác."

    /** Khi so nguoi than chua duoc luu — loi dan, khong phai loi he thong. */
    const val NO_SAVED_NUMBER = "Chưa lưu số người thân. Vào Cài đặt → Số khẩn cấp để lưu."

    /** Khi may khong co gi do tel:/sms: va cung khong co Zalo. */
    const val NO_CALL_CHANNEL = "Máy không mở được cuộc gọi. Hãy dùng một điện thoại khác để gọi cứu nạn."

    /** Nhuan khi thieu GPS — dung cho ca SMS lan loi doc. */
    const val UNKNOWN_POSITION = "chưa xác định được"

    /** Nhiem vu goi tuc tho: `Uri.parse("sms:...")` khong the co so trong. */
    const val MISSING_NUMBER_FOR_SHARE = "Chưa có số người thân nên không soạn được tin nhắn."

    // ── Hang so he thong ─────────────────────────────────────────────────

    /** Goi 2 giay roi doc toa do — dung cho ca composable lan test. */
    const val SPEECH_DELAY_MS = 2_000L

    /**
     * Goi tuc tho cua Zalo. Nam ca o day de test quyet dinh "co hai kenh hay
     * mot kenh" ma khong can may.
     */
    const val ZALO_PACKAGE = "com.zing.zalo"

    /** Tong dai cong an nham bat theo yeu cau nguoi dung. */
    const val POLICE_HOTLINE = "113"

    /**
     * Ma hanh dong cua Android, viet tay thanh chuoi. Object nay khong duoc
     * cham `android.content.Intent` — tren may tinh khong Android thi moi hang
     * do tra ve null va test do gian.
     */
    const val ACTION_CALL = "android.intent.action.CALL"
    const val ACTION_DIAL = "android.intent.action.DIAL"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    /** Chuoi `Uri` cho tin nhan soan san — khong can quyen SEND_SMS. */
    const val EXTRA_BODY = "sms_body"

    // ── Nhan va kinh do: ten goi dang ly cho nguoi doc ───────────────────

    const val LATITUDE_LABEL = "Vĩ độ"
    const val LONGITUDE_LABEL = "Kinh độ"
    const val ADDRESS_LABEL = "Địa điểm"

    /** Vi tri do duoc (hoac khong) — dau vao cho moi chuoi o duoi. */
    data class Position(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float = 0f,
        val address: String = "",
        /** Mốc giờ của điểm định vị; 0 = khong ro. */
        val fixTimeMillis: Long = 0L,
        val senderName: String = ""
    ) {
        /** Cung bo kiem tra voi `LocationData.hasRealFix` — 0/0 la "chua do duoc". */
        val hasFix: Boolean
            get() = !(latitude == 0.0 && longitude == 0.0) &&
                !latitude.isNaN() && !longitude.isNaN() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
    }

    // ── (1) Kenh goi: co Zalo hay khong ──────────────────────────────────

    enum class CallChannel { ZALO, PLAIN_CALL, UNAVAILABLE }

    data class CallOption(
        val channel: CallChannel,
        /** Nhan nut — dung nguyen van chu nguoi dung muon nhin thay. */
        val label: String,
        val note: String,
        /** false = nut tat vi chua co so, bam khong duoc. */
        val enabled: Boolean
    ) {
        val isZalo: Boolean get() = channel == CallChannel.ZALO
    }

    /**
     * Hai truong hop ma nguoi dung yeu cau, quyet bang mot ham duy nhat:
     *  • May CAI Zalo → them nut "Gọi qua Zalo" (mo cuộc tro chuyện theo so).
     *  • May KHONG Zalo → chi con "Gọi điện thoại thường", goi thang so nguoi
     *    than da luu trong danh ba cua app.
     *
     * `zaloInstalled` den tu `packageManager.getPackageInfo(ZALO_PACKAGE)`, ma
     * Android 11+ chi thay duoc neu Manifest co `<queries>` tuong ung — thieu
     * cau do thi ham nay luc nao cung tra ve "khong co Zalo" va nut Zalo bien
     * mat vinh vien. Do la ly do `SosPlanTest` kim ca chuoi trong Manifest.
     */
    fun callOptions(zaloInstalled: Boolean, savedNumber: String): List<CallOption> {
        val number = savedNumber.trim()
        val hasNumber = number.isNotEmpty()
        return buildList {
            if (zaloInstalled) {
                add(
                    CallOption(
                        channel = CallChannel.ZALO,
                        label = "Gọi qua Zalo",
                        note = if (hasNumber)
                            "Mở cuộc trò chuyện Zalo với $number — bấm Nghe máy trong Zalo."
                        else MISSING_NUMBER_FOR_SHARE,
                        enabled = hasNumber
                    )
                )
            }
            add(
                CallOption(
                    channel = if (hasNumber) CallChannel.PLAIN_CALL else CallChannel.UNAVAILABLE,
                    label = "Gọi điện thoại thường",
                    note = if (hasNumber)
                        "Gọi số người thân đã lưu: $number"
                    else NO_SAVED_NUMBER,
                    enabled = hasNumber
                )
            )
        }
    }

    /** Ten kenh de in ra nhat ky / loi bao cao. */
    fun channelLabel(channel: CallChannel): String = when (channel) {
        CallChannel.ZALO -> "Zalo"
        CallChannel.PLAIN_CALL -> "Điện thoại thường"
        CallChannel.UNAVAILABLE -> "Chưa có số để gọi"
    }

    // ── (2) Goi tong dai: ACTION_CALL hay ACTION_DIAL ────────────────────

    /**
     * Co quyen CALL_PHONE moi duoc dung `ACTION_CALL` (bam la ket noi thang).
     * Khong co quyen thi bat buoc `ACTION_DIAL` — mo ban quay so, khong can
     * quyen nao ca. Dao nguoc lai se SecurityException va luu cuu nan do.
     */
    fun callActionId(hasCallPermission: Boolean): String =
        if (hasCallPermission) ACTION_CALL else ACTION_DIAL

    /** true khi duoc phep goi thang, khong phai mo ban quay so. */
    fun dialsDirectly(hasCallPermission: Boolean): Boolean = hasCallPermission

    /** Chuoi trang thai in ngay duoi nut — dung mot trong hai nguoi dung doc duoc. */
    fun callStatusMessage(hasCallPermission: Boolean): String =
        if (hasCallPermission) CALL_STARTED else DIALER_OPENED

    /** `tel:` + so, viet tay vi object nay khong duoc cham `Uri.parse`. */
    fun dialUri(number: String): String = "tel:" + number.filter { it.isDigit() || it == '+' }

    // ── (3) SMS soan san cho nguoi than ───────────────────────────────────

    /** `smsto:`/`sms:` + so — `Uri.parse` o tang Android dung lai nguyen chuoi nay. */
    fun smsUri(number: String): String = "sms:" + number.filter { it.isDigit() || it == '+' }

    /**
     * Noi dung tin nhan so SAN SAN de nguoi dung chi bam Gui. Buoc phai co ca
     * hai nhan toa do va dia diem: nguoi nhan la nguoi lon toi, ho khong co
     * thoi gian doan chu.
     */
    fun familySmsBody(position: Position): String {
        val name = position.senderName.ifBlank { "Người thân trong nhà" }
        return buildString {
            append("[CỨU NẠN KHẨN CẤP] $name cần được giúp đỡ ngay.\n")
            append("$LATITUDE_LABEL: ${plainLatitude(position)}\n")
            append("$LONGITUDE_LABEL: ${plainLongitude(position)}\n")
            append("$ADDRESS_LABEL: ${readableAddress(position)}\n")
            if (position.hasFix) {
                append("Bản đồ: https://maps.google.com/?q=%.6f,%.6f".format(position.latitude, position.longitude))
                append("\nSai số: ${FixPrecision.accuracyShortLabel(position.accuracyMeters)}")
            }
            append("\nHãy gọi lại số này hoặc đến ngay vị trí trên.")
        }
    }

    // ── (4) Doc bang Tieng Viet sau 2 giay ────────────────────────────────

    /**
     * Cau doc toa do. So doc tung chu so ("hai một phẩy không một hai ba") chu
     * khong doc ca con so: nguoi nghe ghi lai toa do thi chu so ro hon "hai
     * phay khong muoi hai" nhieu, va mot so thap phan dai bi TTS doc thanh
     * "hai phẩy mươi hai nghìn ba trăm bốn mươi lăm" la vo dung.
     */
    fun speechText(position: Position): String = buildString {
        append("Vị trí cứu nạn. ")
        append("$LATITUDE_LABEL ${spokenNumber(position.latitude, position.hasFix)}. ")
        append("$LONGITUDE_LABEL ${spokenNumber(position.longitude, position.hasFix)}. ")
        append("$ADDRESS_LABEL ${spokenAddress(position)}.")
        if (position.hasFix) {
            append(" ${spokenAccuracy(position.accuracyMeters)}.")
        }
        append(" Hãy gọi lại số điện thoại này.")
    }

    /**
     * Do chinh xac noi bang loi nguoi thuong, khong doc "muoi hai phay khong
     * nam mét": doc tung chu so sau dau phap la de nham lan. Nguoi nghe chi can
     * biet den gan du tim hay chua.
     */
    private fun spokenAccuracy(meters: Float): String {
        val honest = FixPrecision.honestAccuracyMeters(meters)
        return when {
            honest <= 10f -> "Điểm này chính xác tới khoảng ${honest.toInt()} mét"
            honest <= 100f -> "Điểm này sai số vài chục mét"
            honest <= 1000f -> "Điểm này sai số vài trăm mét"
            else -> "Điểm này chỉ đúng tới hàng cây số, hãy ưu tiên địa chỉ"
        }
    }

    /** Dong toa do hien tren man hinh — co nhan ro rang de nguoi doc biet doc gi. */
    fun positionLines(position: Position): List<Pair<String, String>> = listOf(
        LATITUDE_LABEL to plainLatitude(position),
        LONGITUDE_LABEL to plainLongitude(position),
        ADDRESS_LABEL to readableAddress(position)
    )

    /** Vĩ độ, cat theo dung sai so do may bao — khong in gia chinh xac gia. */
    fun plainLatitude(position: Position): String =
        if (!position.hasFix) UNKNOWN_POSITION
        else decimal(position.latitude, decimals(position))

    fun plainLongitude(position: Position): String =
        if (!position.hasFix) UNKNOWN_POSITION
        else decimal(position.longitude, decimals(position))

    /**
     * Dia diem: AddressHelper tra ve mot chuoi that hoac mot nhan "chưa...".
     * Rong thi noi thang "chưa xác định được", de dong khong bi trong nhu da
     * dich duoc ten duong.
     */
    fun readableAddress(position: Position): String =
        position.address.trim().ifBlank { UNKNOWN_POSITION }

    private fun decimals(position: Position): Int =
        FixPrecision.decimalsForAccuracy(
            if (position.accuracyMeters > 0f) position.accuracyMeters else FixPrecision.UNKNOWN_ACCURACY_M
        )

    private fun decimal(value: Double, places: Int): String =
        String.format(Locale.US, "%.${places}f", value)

    /** "21,0123" -> "hai một phẩy không một hai ba". */
    private fun spokenNumber(value: Double, hasFix: Boolean): String {
        if (!hasFix) return UNKNOWN_POSITION
        val rounded = String.format(
            Locale.US, "%.4f", value
        ).trimEnd('0').trimEnd('.', ',').ifBlank { "0" }
        val words = mutableListOf<String>()
        var first = true
        for (ch in rounded) {
            if (!first) words += if (ch == '.' || ch == ',') "phẩy" else ""
            first = false
            if (ch.isDigit()) words += vietnameseDigit(ch)
            else if (ch == '-') words += "âm"
        }
        return words.filter { it.isNotBlank() }.joinToString(" ")
    }

    private fun vietnameseDigit(ch: Char): String = when (ch) {
        '0' -> "không"
        '1' -> "một"
        '2' -> "hai"
        '3' -> "ba"
        '4' -> "bốn"
        '5' -> "năm"
        '6' -> "sáu"
        '7' -> "bảy"
        '8' -> "tám"
        '9' -> "chín"
        else -> ""
    }

    /** Dia chi doc duoc: bo dau gạch va dau cham chu trong URL vi TTS doc no thanh tieng Anh. */
    private fun spokenAddress(position: Position): String {
        val raw = readableAddress(position)
        if (raw == UNKNOWN_POSITION) return "chưa xác định được"
        return raw.replace(",", "; ").replace("/", " ").trim()
    }

    /**
     * Loi nhuan khi may khong co giong doc Tieng Viet. Nam o day chu khong
     * nam trong tang Android de test kiem duoc rang loi luon duoc noi thang,
     * khong im lang: nguoi dung dung cu ghi am ra.
     */
    fun speechUnavailableMessage(vietnameseAvailable: Boolean): String? =
        if (vietnameseAvailable) null
        else "Máy này chưa có giọng đọc tiếng Việt nên không tự đọc toạ độ được — " +
            "toa độ vẫn hiện trên màn hình, hãy đọc cho người cứu nạn."
}
