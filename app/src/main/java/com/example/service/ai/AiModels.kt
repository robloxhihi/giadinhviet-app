package com.example.service.ai

import android.content.Context

/**
 * Model nguoi dung co the chon trong tro ly gia dinh.
 *
 * `id` la ten model nguyen van ma Orcarouter can (doi chieu bang
 * `GET /v1/models`). Gemini da bi xoa khoi bo chon o ban 69.0: `id = "gemini"`
 * chi la chuoi dieu huong cho `GeminiClient`, ma khoa GEMINI_API_KEY chua tung
 * ton tai trong repo — mot lua chon luc nao cung bao "chưa có khoá" thi khong
 * phai la mot lua chon, va nguoi dung yeu cau xoa thang no.
 */
data class AiModel(
    val id: String,
    val label: String,
    val provider: Provider,
    val note: String,
    /**
     * True khi do THAT bang key cua chu tai khoan cho thay khong tieu ton tien.
     * De cờ tường minh thay vi doan tu ten: `orcarouter/free` mien phi nhung
     * khong mang hau to `-free`, con cach doan hau to se dem nham no vao nhom
     * tra phi.
     */
    val free: Boolean = false
) {
    /**
     * OPENROUTER la ten MA HOAT DONG cua Orcarouter (object `OpenRouterClient`),
     * giu nguyen de khong pha ba noi test mo filepath. NHA CUNG CAP THAT la
     * orcarouter.ai — xem ghi chu trong `OpenRouterClient`.
     */
    enum class Provider { OPENROUTER }
}

object AiModels {

    // Cung mot bo nho du dung cho khoa AI: model duoc chon khong phai bi mat
    // khi xoa khoa, nhung cung khong di chuyen giua cac may.
    private const val PREFS = "ai_config_v1"
    // Ban 41.0 them model mien phi, nhung kiem tra tren may that (BlueStacks,
    // 2026-10-22) phat hien: mo app len van dem "GLM 5.3 Flash (tra phi)" o
    // tieu de, vi `FIELD_SELECTED` con giu lua chon tra phi cua ban cu. Doi
    // ten truong de lua chon cu bi bo qua, va mac dinh cua may moi la model
    // MIEN PHI — khoa Orcarouter da nhung san trong ban cai nen goi duoc ngay.
    private const val FIELD_SELECTED_V2 = "selected_model_v2"

    /** Model mac dinh phai la thu da duoc do bang key that som nhat. */
    // Nha cung cap la Orcarouter (orcarouter.ai), KHONG phai OpenRouter. Ban
    // dau ghi nham ten `:free` cua OpenRouter va loai bo cac id duoi `-free`
    // vi tuong chung khong ton tai — do la ly do moi luong chon trong app dem
    // tien. Do lai tren `GET https://www.orcarouter.ai/v1/models` ngay
    // 2026-10-22: Orcarouter dung hau to `-free`, va goi that bang key cua chu
    // tai khoan thi `deepseek/deepseek-v4-flash-free` tra loi tieng Viet, 0 dong.
    val all: List<AiModel> = listOf(
        // NHA CUNG CAP LA ORCAROUTER (orcarouter.ai), KHONG PHAI OpenRouter.
        // Do tren chinh `GET https://www.orcarouter.ai/v1/models` ngay
        // 2026-10-22 (209 model) va goi that bang key cua chu tai khoan:
        // mien phi dung hau to `-free` (khong phai `:free` nhu OpenRouter),
        // pricing tra ve `0.0000000000` cho ca luot hoi luot dap.
        //
        // CAC MODEL `-free` DEU LA MODEL SUY LY: chung phat `reasoning_content`
        // rui moi den `content`. Doi `max_tokens` qua nho thi `content` rong
        // hoan toan trong khi van HTTP 200 — do la chat trang xoan ma nguoi
        // dung khong hieu tai sao. `OpenRouterClient` dat max_tokens du lon
        // va bao khi noi dung trong.
        // NHAN (label) KHONG con chu "miễn phí"/"Free" tu 69.0 — nguoi dung yeu
        // cau ngay 2026-10-26: "bỏ chữ free đằng sau mấy cái ai hiển thị trong
        // app". Chi sua chu hien ra: `id` van nguyen van nhu do tren
        // `GET /v1/models` vi no la hop dong voi may chu (doi id la 404), con
        // `free` va `note` giu nguyen de khong ai doc bang model nay ma tuong
        // chung tra tien — gia van ghi "0 đồng" o note, chi bo cach dem tien
        // vao ten goi.
        AiModel(
            id = "deepseek/deepseek-v4-flash-free",
            label = "DeepSeek V4 Flash",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: trả lời tiếng Việt, 2,5 giây.",
            free = true
        ),
        AiModel(
            id = "deepseek/deepseek-v4.1-flash-free",
            label = "DeepSeek V4.1 Flash",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: bản mới hơn, chậm hơn một chút.",
            free = true
        ),
        AiModel(
            id = "z-ai/glm-5.3-flash-free",
            label = "GLM 5.3 Flash",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: viết dài nhất trong nhóm này.",
            free = true
        ),
        AiModel(
            id = "orcarouter/free",
            label = "Orcarouter (tự chọn)",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Hệ thống tự picking model đang nhàn nhất.",
            free = true
        ),
        // Chi con model MIEN PHI. Hai model tra phi (deepseek/deepseek-v4-flash
        // va z-ai/glm-5.3-flash) da go theo yeu cau ngay 2026-10-23 — giu lai la
        // bang model dem tien cua nguoi dung trong khi bon thu mien phi da do
        // chay that.
        //
        // 69.0: model "gemini" cung bi xoa theo yeu cau cua nguoi dung. Tai
        // may nao tung chon Gemini thi `selectedId` tra ve chuoi cu, `find()`
        // khong thay nen roi ve model Orcarouter mien phi — khong co vo, cung
        // khong co goi mot provider da bi cat.
    )

    /** Model Orcarouter mac dinh: bao luu la thu da duoc do MIEN PHI, khong phai thu tra phi. */
    val defaultOpenRouter: AiModel
        get() = all.first { it.provider == AiModel.Provider.OPENROUTER && it.free }

    /**
     * Mac dinh cua app. Tu 69.0 day cung la model DUY NHAT co the chay: Gemini
     * tung dung dau bang nhung khong co khoa trong ban cai (repo thieu secret
     * `GEMINI_API_KEY`), nen roi ve no la chat im lang.
     */
    val default: AiModel get() = defaultOpenRouter

    /** True khi model nay khong tieu ton tien cua user. */
    fun isFree(model: AiModel): Boolean = model.free

    fun find(id: String?): AiModel = all.firstOrNull { it.id == id } ?: default

    fun labelOf(id: String?): String = find(id).label

    /** Model da chon lan truoc, luu rieng tren may nay nhu khoa AI. */
    fun selectedId(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(FIELD_SELECTED_V2, defaultOpenRouter.id)
            .orEmpty()
            .ifBlank { defaultOpenRouter.id }

    fun saveSelected(context: Context, id: String) {
        val safe = find(id).id
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(FIELD_SELECTED_V2, safe).apply()
    }

    /** Khoá đang có hiệu lực cho model này — từ 69.0 chi con mot loai khoa. */
    fun keyFor(context: Context, model: AiModel): String = when (model.provider) {
        AiModel.Provider.OPENROUTER -> AiKey.effectiveOpenRouter(context)
    }

    fun keyLabel(model: AiModel): String = when (model.provider) {
        AiModel.Provider.OPENROUTER -> "khoá Orcarouter"
    }

    /**
     * Mot lan goi, mot model duoc chon — khong thu lan luot nhieu model nhu
     * `GeminiClient.chat` tung lam: nguoi dung chon DeepSeek ma nhan ve noi dung
     * cua GLM thi khong con la model ho chon nua.
     */
    suspend fun chat(
        model: AiModel,
        apiKey: String,
        history: List<ChatTurn>,
        question: String,
        stats: String,
        report: Boolean = false
    ): Result<String> = when (model.provider) {
        AiModel.Provider.OPENROUTER -> OpenRouterClient.chat(apiKey, model.id, history, question, stats, report)
    }
}
