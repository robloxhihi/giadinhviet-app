package com.example.service.ai

import android.content.Context

/**
 * Model nguoi dung co the chon trong tro ly gia dinh.
 *
 * `id` cua Gemini la chuoi dieu huong (`gemini`), khong phai ten model that:
 * `GeminiClient` tu do chon model va tu thay model phong khi 503, nen khong
 * trung phat ten o day. Voi OpenRouter thi `id` la ten model nguyen van ma
 * OpenRouter can.
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
    enum class Provider { GEMINI, OPENROUTER }
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
        AiModel(
            id = "gemini",
            label = "Gemini",
            provider = AiModel.Provider.GEMINI,
            note = "Của Google — cần khoá AI Studio. Đã đo: trả lời tiếng Việt đúng khuôn.",
            free = true
        ),
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
        AiModel(
            id = "deepseek/deepseek-v4-flash-free",
            label = "DeepSeek V4 Flash (miễn phí)",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: trả lời tiếng Việt, 2,5 giây.",
            free = true
        ),
        AiModel(
            id = "deepseek/deepseek-v4.1-flash-free",
            label = "DeepSeek V4.1 Flash (miễn phí)",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: bản mới hơn, chậm hơn một chút.",
            free = true
        ),
        AiModel(
            id = "z-ai/glm-5.3-flash-free",
            label = "GLM 5.3 Flash (miễn phí)",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Đã đo thật: viết dài nhất trong nhóm miễn phí.",
            free = true
        ),
        AiModel(
            id = "orcarouter/free",
            label = "Orcarouter Free (tự chọn)",
            provider = AiModel.Provider.OPENROUTER,
            note = "Orcarouter · 0 đồng. Hệ thống tự picking model miễn phí đang nhàn nhất.",
            free = true
        ),
        // Hai model tra phi giu lai cho ai can chat luong cao, nhung ghi ro
        // TRAO PHI va lay gia tu chinh `pricing` cua orcarouter (ty My phan
        // trieu token), khong phai gia tu mot nha cung cap khac.
        AiModel(
            id = "deepseek/deepseek-v4-flash",
            label = "DeepSeek V4 Flash (trả phí)",
            provider = AiModel.Provider.OPENROUTER,
            note = "TRẢ PHÍ Orcarouter · 0,22 US$ mỗi triệu token hỏi, 0,66 mỗi triệu token trả lời."
        ),
        AiModel(
            id = "z-ai/glm-5.3-flash",
            label = "GLM 5.3 Flash (trả phí)",
            provider = AiModel.Provider.OPENROUTER,
            note = "TRẢ PHÍ Orcarouter · 0,15 US$ mỗi triệu token hỏi, 0,50 mỗi triệu token trả lời."
        )
    )

    val default: AiModel get() = all.first()

    /** Model Orcarouter mac dinh: bao luu la thu da DO MIEN PHI, khong phai thu tra phi. */
    val defaultOpenRouter: AiModel
        get() = all.first { it.provider == AiModel.Provider.OPENROUTER && it.free }

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

    /** Khoá đang có hiệu lực cho provider của model này. */
    fun keyFor(context: Context, model: AiModel): String = when (model.provider) {
        AiModel.Provider.GEMINI -> AiKey.effective(context)
        AiModel.Provider.OPENROUTER -> AiKey.effectiveOpenRouter(context)
    }

    fun keyLabel(model: AiModel): String = when (model.provider) {
        AiModel.Provider.GEMINI -> "khoá Gemini"
        AiModel.Provider.OPENROUTER -> "khoá Orcarouter"
    }

    /**
     * Mot lan goi, mot model duoc chon — khong thu lan luot nhieu model nhu
     * `GeminiClient.chat` lam: nguoi dung chon DeepSeek ma nhan ve noi dung
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
        AiModel.Provider.GEMINI -> GeminiClient.chat(apiKey, history, question, stats, report)
        AiModel.Provider.OPENROUTER -> OpenRouterClient.chat(apiKey, model.id, history, question, stats, report)
    }
}
