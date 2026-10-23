package com.example

import com.example.service.ai.AiModel
import com.example.service.ai.AiModels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hồi quy cho chỗ hỏng người dùng phàn nàn ở bản 40.0: đã đưa khoá Orcarouter
 * vào app mà mọi lựa chọn trong bảng chọn đều trừ tiền.
 *
 * Gốc lỗi: nhầm nhà cung cấp. App gọi `openrouter.ai` trong khi key và hệ
 * thống là `orcarouter.ai`, nên key thật bị từ chối 401 và bảng model bị soạn
 * theo catalog của nhà cung cấp khác — catalog đó không có DeepSeek miễn phí,
 * sinh ra kết luận sai "không tồn tại bản `-free`". Đo lại trên
 * `GET https://www.orcarouter.ai/v1/models` ngày 2026-10-22 (209 model) rồi gọi
 * thật bằng key chủ tài khoản: `deepseek/deepseek-v4-flash-free` trả lời tiếng
 * Việt, pricing `0.0000000000` cho cả hai chiều.
 */
class AiModelCostRegressionTest {

    private val openRouter = AiModels.all.filter { it.provider == AiModel.Provider.OPENROUTER }

    @Test
    fun bangChonCoNhieuModelMienPhi() {
        val free = openRouter.filter { it.free }
        assertTrue("Bảng chọn Orcarouter không còn model miễn phí nào", free.isNotEmpty())
        assertTrue("Cần ít nhất bốn model miễn phí đã đo thật", free.size >= 4)
    }

    @Test
    fun mienPhiPhaiGhiRoKhongDong() {
        openRouter.filter { it.free }.forEach {
            assertTrue("`${it.label}` miễn phí mà note không ghi 0 đồng: ${it.note}",
                it.note.contains("0 đồng"))
        }
    }

    @Test
    fun traPhiPhaiGhiChuTraPhiVaKhongDuocNoiLaKhongDong() {
        openRouter.filter { !it.free }.forEach {
            assertTrue("`${it.label}` trả phí mà note không mở đầu TRẢ PHÍ: ${it.note}",
                it.note.startsWith("TRẢ PHÍ"))
            assertFalse("Note trả phí không được chứa chữ 0 đồng: ${it.note}",
                it.note.contains("0 đồng"))
        }
    }

    @Test
    fun tenMienPhiPhaiDungQuyUocCuaOrcarouter() {
        // Orcarouter dung hau to `-free` (khong phai `:free` nhu OpenRouter),
        // ngoai le ten dieu huong `orcarouter/free`. Bat ky ten nao khac hai dang
        // nay ma van bao mien phi la ten bia — se 404 hoac dem tien.
        openRouter.filter { it.free }.forEach {
            val hopLe = it.id.endsWith("-free") || it.id == "orcarouter/free"
            assertTrue("Ten mien phi khong dung quy uoc Orcarouter: ${it.id}", hopLe)
        }
    }

    @Test
    fun modelOrcarouterMacDinhPhaiMienPhi() {
        assertTrue("Model Orcarouter mặc định vẫn tính tiền: ${AiModels.defaultOpenRouter.id}",
            AiModels.defaultOpenRouter.free)
        assertTrue("isFree phải đồng ý với chính model mặc định đó",
            AiModels.isFree(AiModels.defaultOpenRouter))
    }

    @Test
    fun isFreeKhongDemNhamModelTraPhi() {
        // Hai id tra phi da bi xoai khoi bang chon. Chung khong duoc con song
        // duong nao trong app: doc `isFree` cua chung phai ra true vi `find`
        // roi ve model mien phi mac dinh, khong phai vi chung duoc thang tay.
        assertTrue("Id tra phi cu phai roi ve model mien phi, khong phai Gemini khong khoa",
            AiModels.isFree(AiModels.find("deepseek/deepseek-v4-flash")))
        assertTrue("Gemini phai duoc tinh la mien phi", AiModels.isFree(AiModels.find("gemini")))
        assertTrue("Id la phai roi ve model mien phi", AiModels.isFree(AiModels.find("khong-ton-tai")))
        assertTrue("Mac dinh khong duoc la Gemini (ban cai khong co khoa Gemini)",
            AiModels.default.provider == AiModel.Provider.OPENROUTER)
    }

    @Test
    fun tenModelPhaiKhopCatalogDaDo() {
        // Nam id nay la ban sao do truc tiep tu `GET /v1/models` cua
        // orcarouter.ai ngay 2026-10-22. Chan nguoi sua lai thanh ten bia.
        val ids = openRouter.map { it.id }.toSet()
        listOf(
            "deepseek/deepseek-v4-flash-free",
            "deepseek/deepseek-v4.1-flash-free",
            "z-ai/glm-5.3-flash-free",
            "orcarouter/free"
        ).forEach { assertTrue("Thiếu model `$it` trong bảng chọn", ids.contains(it)) }
        assertTrue("Danh sach Orcarouter phat sinh them ten chua do: $ids", ids.size <= 4)
    }

    /**
     * Hai loi phat hien khi cai 41.0 that tren BlueStacks (2026-10-22) — CI xanh
     * ma may van sai, nen hai thu nay phai nam duoi dang test hoi quy:
     *  - tieu de van dem "GLM 5.3 Flash (trả phí)" vi SharedPreferences con giu
     *    lua chon tra phi cua ban cu, lan truot model mien phi moi them;
     *  - app noi "đã lưu trên máy này" trong khi key thuc te di san trong ban cai.
     */
    @Test
    fun luaChonTraPhiCuKhongDuocChayTuDong() {
        val src = source("src/main/java/com/example/service/ai/AiModels.kt")
        assertTrue("Van doc truong lua chon cu nen model tra phi cu quay lai",
            !src.contains("getString(FIELD_SELECTED,"))
        assertTrue("Phai luu vao truong moi de bo lua chon tra phi cua ban cu",
            src.contains("FIELD_SELECTED_V2"))
        assertTrue("Mac dinh khi mo app phai la model mien phi",
            src.contains("getString(FIELD_SELECTED_V2, defaultOpenRouter.id)"))
        assertTrue("Gia tri rong cung phai roi ve model mien phi",
            src.contains("ifBlank { defaultOpenRouter.id }"))
    }

    private fun source(relative: String): String {
        val file = listOf(java.io.File(relative), java.io.File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }
}
