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
        assertTrue("Chat thường phải trả lời ngắn kiểu nhắn tin", p.contains("1-3 cau"))
        assertTrue("Phai cam lap lai khuon mau", p.contains("khong lap lai khuon mau"))
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
