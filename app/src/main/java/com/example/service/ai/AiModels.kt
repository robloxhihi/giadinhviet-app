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
    val note: String
) {
    enum class Provider { GEMINI, OPENROUTER }
}

object AiModels {

    // Cung mot bo nho du dung cho khoa AI: model duoc chon khong phai bi mat
    // khi xoa khoa, nhung cung khong di chuyen giua cac may.
    private const val PREFS = "ai_config_v1"
    private const val FIELD_SELECTED = "selected_model"

    /** Model mac dinh phai la thu da duoc do bang key that som nhat. */
    val all: List<AiModel> = listOf(
        AiModel(
            id = "gemini",
            label = "Gemini",
            provider = AiModel.Provider.GEMINI,
            note = "Của Google — cần khoá AI Studio. Đã đo: trả lời tiếng Việt đúng khuôn."
        ),
        AiModel(
            id = "deepseek/deepseek-v4.1-flash-free",
            label = "DeepSeek V4.1",
            provider = AiModel.Provider.OPENROUTER,
            note = "Qua OpenRouter, gói miễn phí."
        ),
        AiModel(
            id = "deepseek/deepseek-v4-flash-free",
            label = "DeepSeek V4",
            provider = AiModel.Provider.OPENROUTER,
            note = "Qua OpenRouter, gói miễn phí."
        ),
        AiModel(
            id = "z-ai/glm-5.3-flash-free",
            label = "GLM 5.3",
            provider = AiModel.Provider.OPENROUTER,
            note = "Qua OpenRouter, gói miễn phí."
        )
    )

    val default: AiModel get() = all.first()

    fun find(id: String?): AiModel = all.firstOrNull { it.id == id } ?: default

    fun labelOf(id: String?): String = find(id).label

    /** Model da chon lan truoc, luu rieng tren may nay nhu khoa AI. */
    fun selectedId(context: Context): String =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(FIELD_SELECTED, default.id)
            .orEmpty()
            .ifBlank { default.id }

    fun saveSelected(context: Context, id: String) {
        val safe = find(id).id
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(FIELD_SELECTED, safe).apply()
    }

    /** Khoá đang có hiệu lực cho provider của model này. */
    fun keyFor(context: Context, model: AiModel): String = when (model.provider) {
        AiModel.Provider.GEMINI -> AiKey.effective(context)
        AiModel.Provider.OPENROUTER -> AiKey.effectiveOpenRouter(context)
    }

    fun keyLabel(model: AiModel): String = when (model.provider) {
        AiModel.Provider.GEMINI -> "khoá Gemini"
        AiModel.Provider.OPENROUTER -> "khoá OpenRouter"
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
        stats: String
    ): Result<String> = when (model.provider) {
        AiModel.Provider.GEMINI -> GeminiClient.chat(apiKey, history, question, stats)
        AiModel.Provider.OPENROUTER -> OpenRouterClient.chat(apiKey, model.id, history, question, stats)
    }
}
