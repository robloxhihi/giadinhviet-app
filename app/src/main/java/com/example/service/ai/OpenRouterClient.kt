package com.example.service.ai

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Goi OpenRouter (`https://openrouter.ai/api/v1/chat/completions`) bang
 * HttpURLConnection — cung kieu goi nhu `GeminiClient`, khong them dependency.
 *
 * Dung cho nhung model Google khong co: DeepSeek va GLM bang `*-free`
 * (khong ton tien). `model` lay nguyen chuoi cua OpenRouter, vi du
 * `deepseek/deepseek-v4-flash-free`.
 *
 * Key di bang `Authorization: Bearer`, khong nam trong URL (URL de bi ghi vao
 * log tai lieu mang). Payload dung dung `AiPrompt.system` nhu Gemini de moi
 * model tra loi theo cung một khuôn — nguoi dung switch model thi khong
 * chuyển sang một kiểu trình bày khác.
 */
object OpenRouterClient {

    private const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter(Map::class.java)

    suspend fun chat(
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        question: String,
        stats: String
    ): Result<String> {
        if (!AiKey.isUsable(apiKey)) {
            return Result.failure(
                GeminiClient.AiException(
                    "Chưa có khoá OpenRouter. Nhập khoá để gọi ${model}."
                )
            )
        }
        return withContext(Dispatchers.IO) {
            var lastError = "Không gọi được OpenRouter."
            for (attempt in 1..2) {
                when (val outcome = once(apiKey, model, history, question, stats)) {
                    is Call.Ok -> return@withContext Result.success(outcome.text)
                    is Call.Retryable -> {
                        lastError = outcome.message
                        if (attempt < 2) delay(1_500)
                    }
                    is Call.Fatal -> return@withContext Result.failure(GeminiClient.AiException(outcome.message))
                }
            }
            Result.failure(GeminiClient.AiException(lastError))
        }
    }

    private sealed class Call {
        data class Ok(val text: String) : Call()
        data class Retryable(val message: String) : Call()
        data class Fatal(val message: String) : Call()
    }

    private suspend fun once(
        apiKey: String,
        model: String,
        history: List<ChatTurn>,
        question: String,
        stats: String
    ): Call {
        val body = mapAdapter.toJson(buildBody(model, history, question, stats))
        return try {
            val conn = (java.net.URL(ENDPOINT).openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("User-Agent", "VesperaFamilySafe")
                // OpenRouter dung hai header nay de dem thong ke ung dung;
                // khong co thi van goi duoc, co thi khong bi tu choi.
                setRequestProperty("X-Title", "Gia Dinh Viet")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            conn.disconnect()
            handle(code, raw)
        } catch (e: java.io.IOException) {
            Call.Retryable("Không kết nối được tới OpenRouter (${e.javaClass.simpleName}). Kiểm tra Internet rồi thử lại.")
        } catch (e: Exception) {
            Call.Fatal("Không đọc được phản hồi AI: ${e.javaClass.simpleName}")
        }
    }

    private fun handle(code: Int, raw: String): Call {
        val json = runCatching { mapAdapter.fromJson(raw) as? Map<*, *> }.getOrNull()
        if (code in 200..299) {
            val choice = (json?.get("choices") as? List<*>)?.firstOrNull() as? Map<*, *>
            val message = choice?.get("message") as? Map<*, *>
            val text = (message?.get("content") as? String)?.trim().orEmpty()
            return when {
                text.isNotEmpty() -> Call.Ok(text)
                choice?.get("finish_reason") == "length" ->
                    Call.Retryable("Câu trả lời bị cắt vì quá dài. Hãy hỏi gọn hơn.")
                else -> Call.Retryable("Máy chủ AI trả về nội dung trống, thử lại được.")
            }
        }
        val error = json?.get("error") as? Map<*, *>
        val detail = (error?.get("message") as? String)?.take(220).orEmpty()
        val friendly = when (code) {
            401, 403 -> "Khoá OpenRouter không dùng được. Kiểm tra lại khoá (HTTP $code)."
            402 -> "Tài khoản OpenRouter hết hạn mức cho model này, hoặc model miễn phí đã hết suất trong ngày."
            404 -> "Model không tồn tại trên OpenRouter, hoặc khoá này không được dùng model đó."
            429 -> "Đã gọi hơi nhiều trong vài phút. Chờ một chút rồi gửi lại."
            else -> "Máy chủ AI trả lỗi HTTP $code."
        }
        return when (code) {
            408, 429, 500, 502, 503, 504 -> Call.Retryable(friendly)
            else -> Call.Fatal(listOf(friendly, detail).filter { it.isNotEmpty() }.joinToString(" "))
        }
    }

    /** Tach rieng de test doi chieu duoc payload ma khong goi mang. */
    fun buildBody(model: String, history: List<ChatTurn>, question: String, stats: String): Map<String, Any> {
        // `ChatTurn` luu theo ngon ngu cua Gemini ("model"); OpenRouter goi
        // tro ly la "assistant", nen phai doi ngay khi don — neu khong lich su
        // chat bi hieu thanh mot chuoi tin nhan cua cung mot phia.
        val messages = mutableListOf<Map<String, Any>>(
            mapOf("role" to "system", "content" to AiPrompt.system(stats))
        )
        history
            .filter { it.role == "user" || it.role == "model" }
            .forEach { messages.add(mapOf("role" to if (it.role == "model") "assistant" else "user", "content" to it.text)) }
        messages.add(mapOf("role" to "user", "content" to question))
        return mapOf(
            "model" to model,
            "messages" to messages,
            "temperature" to 0.7,
            "max_tokens" to 900,
            "stream" to false
        )
    }
}
