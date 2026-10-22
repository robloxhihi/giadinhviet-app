package com.example.service.sos

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Nam bai toan cua man hinh cuu nan ma khong can may: chu in ra man hinh va
 * thu tu cua hanh dong.
 *
 * Test doc thang nguon Kotlin — cung cach voi `NoDemoDataRegressionTest.kt`, vi
 * Compose khong chay duoc tren may tinh ma van co the kiem chuoi va kiem thu tu
 * dong lenh. Chay bang GitHub Actions (repo nay khong co Gradle wrapper tren
 * may lam viec).
 *
 * Ly do tung sai: banner cuu nan in ten thuong mai cua mot san pham khac
 * ("Hệ thống Vespera đã tự động ghi nhận…") thay vi noi may dang lam gi; va
 * bam 113 thi may cho nha mang xac nhận tin SMS toi 20 giay ROI MO ban quay so
 * — tuc la ngay luc goi tong dai, nguoi dung nghe tieng reo trong khi van chua
 * thấy gì trên màn hình.
 */
class SosBrandingRegressionTest {

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    private fun readUtf8(file: File): String = String(file.readBytes(), Charsets.UTF_8)

    private val components get() = "src/main/java/com/example/ui/components/"
    private val sosService get() = "src/main/java/com/example/service/sos/"

    /**
     * (1) Ten thuong mai cu bi xoa khoi TOAN BO duong dan cuu nan: banner, bang
     * SOS, panel vi tri, loi doc, quyet dinh kenh. Ten `VesperaViewModel`,
     * `VesperaRepository` van giu — do la ten lop noi bo, khong ai nhin thay.
     */
    @Test
    fun sosUiFilesHaveNoLegacyBrand() {
        val files = listOf(
            components + "SosAlertBanner.kt",
            components + "DraggableFloatingSosButton.kt",
            components + "SosRescuePanel.kt",
            components + "SosVoice.kt",
            sosService + "SosPlan.kt",
            sosService + "SosRescueIntents.kt"
        )
        files.forEach { rel ->
            val src = readUtf8(projectFile(rel))
            assertTrue("$rel vẫn còn chữ thương hiệu cũ", !src.contains("vespera", ignoreCase = true))
        }
    }

    @Test
    fun bannerHienDangChuyenHuongDenCuuHo() {
        val plan = readUtf8(projectFile(sosService + "SosPlan.kt"))
        assertTrue(
            "Thiếu dòng trạng thái mà người dùng yêu cầu",
            plan.contains("\"Đang chuyển hướng đến cứu hộ\"")
        )
        val dialog = readUtf8(projectFile(components + "SosAlertBanner.kt"))
        assertTrue("Dialog SOS phai lay chuoi tu SosPlan", dialog.contains("SosPlan.REDIRECTING_TO_RESCUE"))
        assertTrue("Dong trang thai phai in ra man hinh", dialog.contains("text = statusMessage"))
        assertTrue("Dialog phai co vi tri cau cuu", dialog.contains("SosRescuePositionPanel("))
    }

    /**
     * (2) Nut "Gọi qua Zalo" sinh ra tu `SosPlan.callOptions`, khong phai tu mot
     * `if` viet tay trong composable — neu viet tay thi khong test duoc.
     */
    @Test
    fun bangSosVeNutTheoQuyetDinhCuaSosPlan() {
        val sheet = readUtf8(projectFile(components + "DraggableFloatingSosButton.kt"))
        assertTrue("Bảng SOS phải hỏi SosPlan thay vì tự đoán", sheet.contains("SosPlan.callOptions("))
        assertTrue("Phải có nút Zalo", sheet.contains("btn_call_via_zalo"))
        assertTrue("Phải có nút gọi điện thoại thường", sheet.contains("btn_call_saved_number"))
        assertTrue("Phải có nút soạn tin nhắn cứu nạn", sheet.contains("btn_open_family_sms"))
        assertTrue("Phải hiện vĩ độ / kinh độ / địa điểm", sheet.contains("SosRescuePositionPanel("))
        assertTrue("Mở bảng là phải xin vị trí mới", sheet.contains("onSheetOpened()"))

        val plan = readUtf8(projectFile(sosService + "SosPlan.kt"))
        assertTrue("Thiếu tên nút theo đúng yêu cầu: Gọi qua Zalo", plan.contains("\"Gọi qua Zalo\""))
        assertTrue("Thiếu tên nút theo đúng yêu cầu: Gọi điện thoại thường",
            plan.contains("\"Gọi điện thoại thường\""))
        assertTrue("Nhan toa do phai la tieng Viet co dau", plan.contains("\"Vĩ độ\""))
        assertTrue("Nhan kinh do phai la tieng Viet co dau", plan.contains("\"Kinh độ\""))
    }

    /**
     * (3) Android 11+ cat khoi `getPackageInfo` ung dung khong liet ke trong
     * `<queries>`. Thieu dong nay thi `isZaloInstalled` LUON false va nut Zalo
     * bien mat lang tren moi may — loi thuc te cua ban cu.
     */
    @Test
    fun manifestKhaiBaoZaloTrongQueries() {
        val manifest = readUtf8(projectFile("src/main/AndroidManifest.xml"))
        val queries = manifest.substringAfter("<queries>").substringBefore("</queries>")
        assertTrue(
            "<queries> phai co com.zing.zalo de packageManager nhin thay duoc",
            queries.contains("com.zing.zalo")
        )
        assertTrue("Phai co quyen CALL_PHONE de duoc goi thang khi nguoi dung chon",
            manifest.contains("android.permission.CALL_PHONE"))
    }

    /**
     * (4) Thu tu cuu 113: MO BAN QUAY SO TRUOC, toa do di SMS SAU — va SMS do
     * phai den SO NGUOI THAN, khong den tong dai. 112/113/114/115 la ma ngan
     * thoai: SMS toa do gui den do khong den tay ai, lai con cho xac nhan toi
     * 20 giay nen nguoi dung bam 113 bi ket giua man hinh.
     */
    @Test
    fun goiTongDaiXTruocRoiToaDoDiSoNguoiThan() {
        val dispatcher = readUtf8(projectFile(sosService + "SmartSosDispatcher.kt"))
        val body = dispatcher.substringAfter("suspend fun sendSosToRescueForce")
        val callAt = body.indexOf("makeDirectEmergencyCall")
        val smsAt = body.indexOf("sendEmergencySmsToFamily")
        assertTrue("Không còn mở cuộc gọi trong sendSosToRescueForce", callAt >= 0)
        assertTrue("Không còn gửi SMS toạ độ cho người thân", smsAt >= 0)
        assertTrue("Phai mo cuoc goi TRUOC khi gui SMS toa do", callAt < smsAt)
        assertTrue(
            "Khong duoc gui SMS toi chinh tong dai (toa do phai ve so nguoi than)",
            !body.contains("sendEmergencySmsToRescue")
        )
        assertTrue(
            "Phai co duong bat tuy khi ben goi da mo ban quay so truoc do",
            dispatcher.contains("startCall: Boolean")
        )
    }

    /**
     * (5) CALL_PHONE bi thu hoi giua luc thi `ACTION_CALL` nem
     * SecurityException. Ban cu bat roi an luon -> nguoi dung bam ma khong thay
     * gi. Nguoc lai phai rut ve ACTION_DIAL.
     */
    @Test
    fun goiThatThatBaiThiRutVeBanQuaySo() {
        val intents = readUtf8(projectFile(sosService + "SosRescueIntents.kt"))
        val callBody = intents.substringAfter("fun call(").substringBefore("private fun dialerNote")
        val callAt = callBody.indexOf("Intent(Intent.ACTION_CALL")
        val dialAt = callBody.indexOf("Intent(Intent.ACTION_DIAL", callAt)
        assertTrue("Thieu nhanh ACTION_CALL khi co quyen", callAt >= 0)
        assertTrue("Goi that bai thi phai thu lai ACTION_DIAL", dialAt > callAt)
        assertTrue("Phai bat SecurityException rieng", intents.contains("catch (e: SecurityException)"))
        assertTrue("Phai bat ActivityNotFoundException rieng",
            intents.contains("catch (e: ActivityNotFoundException)"))
        // Chi MOT cho duoc goi thang he thong, de khong con lenh nao nam ngoai
        // vong bao ve.
        val raw = Regex("""context\.startActivity\(""").findAll(intents).count()
        assertTrue("startActivity phải nằm trong duy nhất một launcher, thấy $raw", raw == 1)
    }

    @Test
    fun smsDoSoanSanKhongCanQuyenGuiSms() {
        val intents = readUtf8(projectFile(sosService + "SosRescueIntents.kt"))
        assertTrue("Phai dung ACTION_SENDTO (khong can quyen SEND_SMS)",
            intents.contains("SosPlan.ACTION_SENDTO"))
        assertTrue("Phai dien san noi dung vao o soan tin",
            intents.contains("putExtra(SosPlan.EXTRA_BODY, body)"))
        assertTrue("Duong dan phai dang sms:<so>",
            readUtf8(projectFile(sosService + "SosPlan.kt")).contains("fun smsUri(number: String)"))
    }

    /**
     * (6) Loi doc: Tieng Viet, tre 2 giay, va TAT khi roi man hinh. Mot may
     * cuu nan keu lien tuc sau khi da dong bang SOS la thiet bi bi hong.
     */
    @Test
    fun ttsDocTiengVietVaTatKhiRoiManHinh() {
        val voice = readUtf8(projectFile(components + "SosVoice.kt"))
        assertTrue("Phai dung TextToSpeech that", voice.contains("import android.speech.tts.TextToSpeech"))
        assertTrue("Phai dat ngon ngu tieng Viet", voice.contains("Locale(\"vi\")"))
        assertTrue("Phai cho 2 giay", voice.contains("SosPlan.SPEECH_DELAY_MS"))
        assertTrue("onDispose phai dung doc", voice.contains("voice.stop()"))
        assertTrue("onDispose phai dong engine", voice.contains("voice.shutdown()"))
        assertTrue("May khong co giong Viet phai duoc bao ro", voice.contains("speechUnavailableMessage"))
        val plan = readUtf8(projectFile(sosService + "SosPlan.kt"))
        assertTrue("Delay doc phai la 2 giay", plan.contains("const val SPEECH_DELAY_MS = 2_000L"))
    }
}
