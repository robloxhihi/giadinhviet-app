package com.example

import com.example.service.ai.AiPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hồi quy cho phàn nàn đo được trên máy ngày 2026-10-22: trợ lý "cứ lải nhải mãi"
 * theo một khuôn báo cáo, vì `AiPrompt` ép MỌI tin nhắn — kể cả một câu chào —
 * phải trả lời đủ ba khối "Nguyên nhân / Giải pháp / Lời khuyên".
 *
 * Khuôn đó chỉ đúng khi người dùng YÊU CẦU phân tích. Hai chế độ phải tách nhau,
 * và cả hai vẫn giữ nghiêm cấm đoan không bịa số liệu.
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

    /**
     * Lô 2026-10-23: người dùng phàn nàn ngay trên máy "nó gò bó quá", và cả ba
     * chỗ gò đều nằm trong prompt chat — trần "1-3 câu", cấm danh sách, và lệnh
     * "chỉ dựa trên số liệu" khiến model từ chối luôn câu hỏi thường.
     */
    @Test
    fun chuyenThoaiThuongDuocPhepNoiNhieuVaHoiChung() {
        val p = AiPrompt.system(stats, report = false)
        assertFalse("Vẫn còn trần 1-3 câu nên model không được giải thích đủ",
            p.contains("1-3 cau"))
        assertFalse("Vẫn cấm danh sách đánh số trong lúc chat", p.contains("khong danh sach danh so"))
        assertFalse("Lệnh 'chỉ dựa trên số liệu' đặt chung cho mọi câu hỏi",
            p.contains("chỉ dựa trên số liệu được cung cấp"))
        assertTrue("Phải nói rõ được phép trả lời câu hỏi ngoài số liệu",
            p.contains("cứ trả lời bình thường"))
        assertTrue("Phải cho phép độ dài theo câu hỏi", p.contains("Tra loi tu nhien het muc"))
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

    @Test
    fun caHaiCheDoKhongDuocPhepBiaSoLieu() {
        listOf(false, true).forEach { mode ->
            val p = AiPrompt.system(stats, report = mode)
            assertTrue("Che do report=$mode thiếu lệnh cấm bịa", p.contains("không bịa"))
            assertTrue("Che do report=$mode thiếu ràng buộc số liệu thật",
                p.contains("SỐ LIỆU THIẾT BỊ"))
            assertTrue("Che do report=$mode phải chứa số liệu được truyền lên",
                p.contains("220 phút"))
        }
    }

    @Test
    fun khongCoSoLieuPhaiNoiRongThayViImLang() {
        val p = AiPrompt.system("", report = false)
        assertTrue("Thiếu số liệu phải nói rõ là chưa có", p.contains("Chưa có số liệu thiết bị"))
        assertFalse("Không được tưởng tượng ra khối báo cáo khi chat thường",
            p.contains("**Nguyên nhân:**"))
    }
}
