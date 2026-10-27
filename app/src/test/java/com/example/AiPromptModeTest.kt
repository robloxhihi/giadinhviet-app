package com.example

import com.example.service.ai.AiPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho ba lần người dùng phàn nàn về cùng một chỗ — giọng của trợ lý.
 *
 * 2026-10-22: trợ lý "cứ lải nhải mãi" theo khuôn báo cáo, vì `AiPrompt` ép MỌI
 * tin nhắn (kể cả một câu chào) phải trả lời đủ ba khối "Nguyên nhân / Giải pháp
 * / Lời khuyên". Khuôn đó chỉ đúng khi người dùng YÊU CẦU phân tích.
 *
 * 2026-10-23: "cho ai trả lời vô tư như bình thường đi, chứ nó gò bó quá" — vẫn
 * còn trần độ dài và lệnh "chỉ dựa trên số liệu được cung cấp" khiến model từ
 * chối cả câu hỏi thường.
 *
 * 2026-10-26: "ai trả lời tự do như ai bình thường, không phải đọc j hết, quá
 * hạn chế và chỉ đọc dữ liệu, phân tích khi yêu cầu". Hai việc phải đúng:
 *  1) prompt không còn câu nào giới hạn phạm vi được trả lời;
 *  2) số liệu thiết bị chỉ đi lên model khi người dùng hỏi số liệu hoặc bấm nút
 *     phân tích — không phải mặc định mọi lượt.
 * Cấm đoan không bịa số liệu và các ranh giới an toàn vẫn nguyên vẹn.
 */
class AiPromptModeTest {

    private val stats = "Hôm nay: 220 phút, nhiều nhất TikTok."

    @Test
    fun chuyenThoaiThuongKhongBiEpKhuonBaoCao() {
        val p = AiPrompt.system(stats, report = false)
        assertFalse("Chat thường vẫn bị ép khối Nguyên nhân", p.contains("**Nguyên nhân:**"))
        assertFalse("Chat thường vẫn bị ép khối Giải pháp đánh số", p.contains("**Giải pháp:**"))
        assertTrue("Chat thường phải được nói rõ là KHÔNG phải báo cáo",
            p.contains("KHONG phai yeu cau phan tich"))
        assertTrue("Phai cam lap lai khuon mau", p.contains("khong lap lai khuon mau"))
    }

    @Test
    fun chuyenThoaiThuongDuocPhepNoiTuDoNhuTroLyBinhThuong() {
        val p = AiPrompt.system(stats, report = false)
        assertFalse("Vẫn còn trần 1-3 câu nên model không được giải thích đủ",
            p.contains("1-3 cau"))
        assertFalse("Vẫn cấm danh sách đánh số trong lúc chat", p.contains("khong danh sach danh so"))
        assertFalse("Lệnh 'chỉ dựa trên số liệu' đặt chung cho mọi câu hỏi",
            p.contains("chỉ dựa trên số liệu được cung cấp"))
        assertFalse("Giọng 'quy tắc tuyệt đối' cũ vẫn thu hẹp phạm vi trả lời",
            p.contains("Quy tắc về số liệu (tuyệt đối)"))
        assertFalse("Câu 'ngoài số liệu đó thì...' vẫn hàm ý phần còn lại bị hạn chế",
            p.contains("Ngoài số liệu đó thì"))
        assertTrue("Phải nói rõ đây là trợ lý bình thường, được trả lời mọi câu hỏi",
            p.contains("trợ lý AI bình thường"))
        assertTrue("Phải cho phép độ dài theo câu hỏi", p.contains("tu do theo do dai ma cau hoi can"))
    }

    /** Cấm bịa số liệu vẫn nguyên vẹn sau khi nới giọng điệu. */
    @Test
    fun noiGiongDieuKhongDuocNongCamDoBia() {
        val p = AiPrompt.system(stats, report = false)
        assertTrue("Mất ràng buộc nguồn số liệu", p.contains("chỉ được lấy từ phần SỐ LIỆU"))
        assertTrue("Mất lệnh cấm bịa", p.contains("không bịa"))
        assertTrue("Mất quy tắc an toàn khám bệnh", p.contains("không chẩn đoán bệnh"))
        assertTrue("Mất quy tắc không theo dõi bí mật", p.contains("không gợi ý theo"))
    }

    @Test
    fun khiXinPhanTichPhaiRaKhuonDayDu() {
        val p = AiPrompt.system(stats, report = true)
        listOf("**Nguyên nhân:**", "**Giải pháp:**", "**Lời khuyên cho cha mẹ:**").forEach {
            assertTrue("Báo cáo thiếu khối $it", p.contains(it))
        }
    }

    /**
     * 2026-10-26: trước đây khối số liệu LUON LUON được gắn vào, nên model lúc
     * nào cũng có cớ đọc lại cả bảng thống ke. Từ đây khối đó chỉ xuất hiện khi
     * người gọi thực sự truyền số liệu lên.
     */
    @Test
    fun soLieuChiXuatHienKhiDuocTruyenLen() {
        val khongSoLieu = AiPrompt.system("", report = false)
        assertFalse("Không có số liệu mà vẫn gán cả khối SỐ LIỆU THIẾT BỊ",
            khongSoLieu.contains("SỐ LIỆU THIẾT BỊ"))
        assertFalse("Không được đổ dòng 'chưa có số liệu' vào mọi câu chat",
            khongSoLieu.contains("Chưa có số liệu"))
        val baoCao = AiPrompt.system(stats, report = true)
        assertTrue("Báo cáo bắt buộc phải kèm số liệu thật", baoCao.contains("SỐ LIỆU THIẾT BỊ"))
        assertTrue("Báo cáo phải chứa con số app đo được", baoCao.contains("220 phút"))
    }

    /** Bộ lọc quyết định khi nào được đọc số liệu: hỏi số liệu = có, hỏi khôn = không. */
    @Test
    fun phanBietHoiSoLieuVaHoiKhuyen() {
        listOf(
            "Hôm nay con dùng máy bao nhiêu phút?",
            "Phân tích giúp tôi tuần này",
            "Vị trí của con đang ở đâu?",
            "Số liệu thống kê hôm qua thế nào?"
        ).forEach {
            assertTrue("Câu này cần số liệu mà wantsDeviceData báo false: $it",
                AiPrompt.wantsDeviceData(it))
        }
        listOf(
            "Xin chào",
            "Viết giúp tôi tin nhắn nhắc con ăn cơm",
            "Tôi nên nói gì với con về thời gian dùng máy?",
            "Con hay cáu gắt thì nên xử lý sao?"
        ).forEach {
            assertFalse("Câu này không cần số liệu mà vẫn bị kéo dữ liệu lên: $it",
                AiPrompt.wantsDeviceData(it))
        }
    }

    /** Luật nằm ở prompt thì màn hình phải thật sự tuân theo. */
    @Test
    fun manHinhAIchiTruyenSoLieuKhiCan() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("Màn hình AI phải hỏi wantsDeviceData trước khi lấy số liệu",
            screen.contains("AiPrompt.wantsDeviceData("))
        assertTrue("Không hỏi số liệu thì phải truyền chuỗi rỗng",
            screen.contains("stats = if (withData)"))
        assertTrue("Số liệu gửi lên phải đi qua ham gan nhan may",
            screen.contains("statsForAi(usage.summaryForAi(), childMinutesToday, viewerIsChild)"))
        assertTrue("Nút 'Phân tích hôm nay' vẫn phải được cấp số liệu",
            screen.contains("report = index < 2"))
    }

    /**
     * 2026-10-27, ban 69.2 do tren BlueStacks: tro ly bao "hom nay con dung may
     * 237 phut" trong khi the DUNG cua may con ghi 279 phut. Khong phai model bia:
     * no doc `summaryForAi()` — so do CHINH may phu huynh dang chay app — roi
     * gán cho con vi prompt goi khoi do la "so lieu may cua con". Tu 69.3 moi
     * khoi so lieu phai de ten may, va man hinh chinh (the DANG DUNG) doc tu
     * telemetry may con. Test nay giu ca hai ben: nhan co that, va chu khong con
     * quang cao rang so lieu di theo MOI cau hoi.
     */
    @Test
    fun khoiSoLieuPhaiDeTenMayVaKhongQuangCaoSai() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        val dashboard = source("src/main/java/com/example/ui/screens/DashboardScreen.kt")
        val host = source("src/main/java/com/example/ui/screens/MainAppScreen.kt")

        assertTrue("Phai nhan ro khoi may cua con", screen.contains("MÁY CỦA CON"))
        assertTrue("Phai nhan ro khoi may dang chay app", screen.contains("MÁY NÀY TỰ ĐO"))
        assertTrue("May con chua gui thi phai noi thang la chua co",
            screen.contains("app chưa nhận được số liệu hôm nay từ máy con"))
        assertTrue("So lieu may con gui len phai duoc truyen vao man hinh AI",
            host.contains("childMinutesToday = uiState.partnerLocation?.totalUsedMinutes"))

        listOf("gửi kèm mỗi câu hỏi", "Bảng số liệu gửi kèm mỗi câu hỏi").forEach { cu ->
            assertFalse("Chu cu van noi so lieu di theo moi cau hoi: $cu", screen.contains(cu))
        }
        assertFalse("Subtitle cu van goi AI la cong cu doc so lieu",
            screen.contains("hỏi về số liệu hôm nay của máy"))
        assertFalse("The Tong quan van quang cao sai",
            dashboard.contains("gửi kèm mỗi câu hỏi"))

        val p = AiPrompt.system(stats, report = false)
        assertTrue("Prompt phai cam lay so may nay gan cho may kia",
            p.contains("không lấy số của máy này gán cho con"))
        assertTrue("Prompt phai biet co hai khoi so lieu khac nhau",
            p.contains("MÁY CỦA CON") && p.contains("MÁY NÀY TỰ ĐO"))
    }

    private fun source(relative: String): String {
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }
}
