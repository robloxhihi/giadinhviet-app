package com.example.service.sos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiem thu [SosPlan] — chay tren may tinh, khong can thiet bi, cung cach voi
 * `ConsentPolicyTest.kt`. Nam ca GitHub Actions chay.
 *
 * Nam dieu that bai tung dien ra o luu cuu nan va duoc gat chat o day:
 *   (1) banner cuu nan in ten thuong mai cu thay vi noi may dang lam gi;
 *   (2) nut "Goi qua Zalo" chi hien khi packageManager THAY duoc Zalo — muon
 *       thay duoc thi Manifest phai co `<queries>` cho `com.zing.zalo`, thieu
 *       la hanh lang "khong co Zalo" chay mai mai ma khong ai biet;
 *   (3) tong dai 113 dung `ACTION_CALL` khi chua co quyen CALL_PHONE →
 *       SecurityException, nguoi dung bam ma khong thấy goi di dau;
 *   (4) SMS soan san thieu mot trong ba thu: vi do, kinh do, dia diem;
 *   (5) loi doc toa do khi chua co GPS thi bịa "không xác định" thanh toa do
 *       0.0 — nguoi cuu nan chay den vịnh Guinea.
 */
class SosPlanTest {

    private val hanoi = SosPlan.Position(
        latitude = 21.01234,
        longitude = 105.81234,
        // 8 m theo FixPrecision duoc phep in 4 le; 12 m thi chi duoc 3 le.
        accuracyMeters = 8f,
        address = "Phố Lý Thường Kiệt, Hoàn Kiếm, Hà Nội",
        fixTimeMillis = 1_700_000_000_000L,
        senderName = "Bon"
    )

    /** May chua dinh vi duoc: sentinel 0/0 theo hop dong `LocationData.hasRealFix`. */
    private val noFix = SosPlan.Position(
        latitude = 0.0,
        longitude = 0.0,
        accuracyMeters = 0f,
        address = "",
        senderName = "Bon"
    )

    // ── (1) Chuoi trang thai, khong con ten thuong mai ───────────────────

    @Test
    fun bannerNoiDangChuyenHuongToiCuuHo() {
        assertEquals("Đang chuyển hướng đến cứu hộ", SosPlan.REDIRECTING_TO_RESCUE)
        assertFalse(
            "Van con ten thuong mai trong chuoi hien man",
            SosPlan.REDIRECTING_TO_RESCUE.contains("Vespera", ignoreCase = true)
        )
    }

    @Test
    fun moiChuoiInRaManHinhKhongChuCaiTenCu() {
        val all = listOf(
            SosPlan.REDIRECTING_TO_RESCUE, SosPlan.DIALER_OPENED, SosPlan.CALL_STARTED,
            SosPlan.NO_DIALER_APP, SosPlan.NO_SAVED_NUMBER, SosPlan.NO_CALL_CHANNEL,
            SosPlan.MISSING_NUMBER_FOR_SHARE, SosPlan.UNKNOWN_POSITION,
            SosPlan.channelLabel(SosPlan.CallChannel.ZALO),
            SosPlan.channelLabel(SosPlan.CallChannel.PLAIN_CALL),
            SosPlan.familySmsBody(hanoi), SosPlan.hotlineSmsBody("113", hanoi),
            SosPlan.speechText(hanoi), SosPlan.speechUnavailableMessage(false).orEmpty()
        )
        all.forEach { text ->
            assertFalse("Con cai ten cu trong chuoi: $text", text.contains("Vespera", ignoreCase = true))
        }
    }

    // ── (2) Hai truong hop: co Zalo / khong Zalo ──────────────────────────

    @Test
    fun coZaloThiHienThemNutGoiQuaZalo() {
        val options = SosPlan.callOptions(zaloInstalled = true, savedNumber = "0912345678")
        assertEquals("Co Zalo thi phai co 2 kenh goi", 2, options.size)
        assertEquals("Gọi qua Zalo", options.first().label)
        assertTrue("Kenh dau tien phai la Zalo", options.first().isZalo)
        assertTrue("Nut Zalo phai bam duoc vi da co so", options.first().enabled)
    }

    @Test
    fun khongZaloThiChiConGoiDienThoaiThuong() {
        val options = SosPlan.callOptions(zaloInstalled = false, savedNumber = "0912345678")
        assertEquals("Khong co Zalo thi khong duoc mo nut Zalo", 1, options.size)
        assertEquals("Gọi điện thoại thường", options.single().label)
        assertFalse(options.single().isZalo)
        assertTrue("So nguoi than da luu thi nut Goi phai dung duoc", options.single().enabled)
    }

    /** So cua nguoi than chinh la "danh ba" ma app nay luu — van de trong note. */
    @Test
    fun goiThuongNoiRangGoiSoDaLuu() {
        val plain = SosPlan.callOptions(zaloInstalled = false, savedNumber = "0912345678")
            .single()
        assertTrue("Nut goi phai noi ro goi so nao: ${plain.note}", plain.note.contains("0912345678"))
    }

    @Test
    fun chuaLuuSoThiNutBiTatVaDanDiCaiDat() {
        val options = SosPlan.callOptions(zaloInstalled = true, savedNumber = "  ")
        options.forEach { option ->
            assertFalse("Chua co so ma van mo nut goi la ham rang", option.enabled)
        }
        assertTrue(
            "Phai dan nguoi dung toi cho luu so",
            options.any { it.note.contains("Cài đặt") }
        )
    }

    // ── (3) Tong dai 113: khong bao gio xin goi thang khi thieu quyen ────

    @Test
    fun thieuQuyenCallPhoneFallsBackToDia() {
        assertEquals(SosPlan.ACTION_DIAL, SosPlan.callActionId(hasCallPermission = false))
        assertEquals("android.intent.action.DIAL", SosPlan.callActionId(false))
        assertFalse(SosPlan.dialsDirectly(false))
        assertTrue("Phai noi cho nguoi dung rang may dang mo ban quay so",
            SosPlan.callStatusMessage(false).contains("bấm Gọi"))
    }

    @Test
    fun coQuyenMoiDuocGoiThang() {
        assertEquals(SosPlan.ACTION_CALL, SosPlan.callActionId(hasCallPermission = true))
        assertEquals("android.intent.action.CALL", SosPlan.callActionId(true))
        assertTrue(SosPlan.dialsDirectly(true))
    }

    @Test
    fun chuoiDuongDanGoChiGiUChuSo() {
        assertEquals("tel:0912345678", SosPlan.dialUri("0912 345 678"))
        assertEquals("tel:+8491234567", SosPlan.dialUri("+84 912 345 67"))
        assertEquals("sms:0912345678", SosPlan.smsUri("0912-345-678"))
    }

    @Test
    fun tongDaiCongAnLaMuoiBa() {
        assertEquals("113", SosPlan.POLICE_HOTLINE)
        assertEquals(2_000L, SosPlan.SPEECH_DELAY_MS)
    }

    // ── (4) SMS soan san: du bo ba vi do / kinh do / dia diem ────────────

    @Test
    fun smsGiaDinhLuonDuBaY() {
        val body = SosPlan.familySmsBody(hanoi)
        assertTrue("Thieu nhan vi do: $body", body.contains("Vĩ độ"))
        assertTrue("Thieu nhan kinh do: $body", body.contains("Kinh độ"))
        assertTrue("Thieu dia diem: $body", body.contains("Địa điểm"))
        assertTrue("Thieu ten duong: $body", body.contains("Phố Lý Thường Kiệt"))
        assertTrue("Phai co so do toa do: $body", body.contains("21.0123"))
        assertTrue("Phai co link ban do: $body", body.contains("maps.google.com"))
        assertTrue("Phai noi ro la cuu nan: $body", body.contains("CỨU NẠN"))
    }

    @Test
    fun smsTongDaiCungDuBaY() {
        val body = SosPlan.hotlineSmsBody("113", hanoi)
        listOf("Vĩ độ", "Kinh độ", "Địa điểm").forEach {
            assertTrue("SMS tong dai thieu $it: $body", body.contains(it))
        }
        assertTrue("SMS tong dai phai de ten tong dai: $body", body.contains("113"))
    }

    /**
     * Trong luu cuu nan, mot con so gia con tai hai hon mot con so trong:
     * 0/0 bi doi thanh "chưa xác định được" de nguoi ta di tim bang cach khac.
     */
    @Test
    fun khongCoGPSKhongDuocBeiToaDo() {
        val body = SosPlan.familySmsBody(noFix)
        assertTrue("Phai noi thang la chua dinh vi duoc: $body", body.contains("chưa xác định được"))
        assertFalse("Van in 0.0 ra SMS: $body", Regex("Vĩ độ: 0\\.").containsMatchIn(body))
        assertFalse("Van in link ban do 0,0: $body", body.contains("q=0.000000"))
        assertFalse("Khong co fix thi khong duoc noi sai so: $body", body.contains("±"))
    }

    @Test
    fun viTriInManHinhCoDungNhanVaGiaTri() {
        val lines = SosPlan.positionLines(hanoi).toMap()
        assertEquals("Vĩ độ", lines.keys.toList()[0])
        assertEquals("21.0123", lines["Vĩ độ"])
        assertEquals("105.8123", lines["Kinh độ"])
        assertEquals("Phố Lý Thường Kiệt, Hoàn Kiếm, Hà Nội", lines["Địa điểm"])
    }

    @Test
    fun diaChiTrongThiNoiChuaXacDinhDuoc() {
        val lines = SosPlan.positionLines(hanoi.copy(address = "  ")).toMap()
        assertEquals("chưa xác định được", lines["Địa điểm"])
    }

    // ── (5) Loi doc sau 2 giay ────────────────────────────────────────────

    @Test
    fun loiDocCoCaToaDoLanDiaDiem() {
        val text = SosPlan.speechText(hanoi)
        assertTrue("Thieu mo dau vi tri cuu nan: $text", text.startsWith("Vị trí cứu nạn"))
        assertTrue("Thieu nhan vi do: $text", text.contains("Vĩ độ"))
        assertTrue("Thieu nhan kinh do: $text", text.contains("Kinh độ"))
        assertTrue("Thieu dia diem: $text", text.contains("Địa điểm"))
        assertTrue("Phai doc duoc ten duong: $text", text.contains("Lý Thường Kiệt"))
        // Toa do doc tung chu so de nguoi nghe ghi lai duoc.
        assertTrue("Vi do phai doc tung chu so: $text", text.contains("hai một"))
        assertTrue("Phay khong duoc doc thanh 'cham': $text", text.contains("phẩy"))
        assertFalse("Loi doc chua ten thuong mai cu: $text", text.contains("Vespera", true))
    }

    @Test
    fun loiDocKhongCoGPSVanNoiDungMotY() {
        val text = SosPlan.speechText(noFix)
        assertTrue("Phai noi thang la chua xac dinh: $text", text.contains("chưa xác định được"))
        listOf("Vĩ độ", "Kinh độ", "Địa điểm").forEach {
            assertTrue("Loi doc thieu nhan $it: $text", text.contains(it))
        }
        assertFalse("Khong duoc doc 'không không phẩy' nhu toa do that: $text",
            text.contains("Vĩ độ không"))
    }

    @Test
    fun mayKhongCoGiongVietPhaiNoiRanh() {
        assertNull("May co giong thi khong co loi nhuan", SosPlan.speechUnavailableMessage(true))
        val warn = SosPlan.speechUnavailableMessage(false)
        assertNotNull("May khong co giong Viet thi phai bao", warn)
        assertTrue("Phai noi ro toa do van hien tren man hinh: $warn", warn!!.contains("màn hình"))
    }
}
