package com.example.service.ai

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Mot luot noi trong doan chat. `role` dung theo ngon ngu cua Gemini:
 * "user" cho nguoi lon, "model" cho tra loi cua may.
 */
data class ChatTurn(val role: String, val text: String)

/**
 * Goi Gemini REST `v1beta ...:generateContent` bang HttpURLConnection.
 *
 * Vi sao khong dung firebase-ai SDK: thu vien do nam san trong repo nhung keo
 * theo ca toan bo Firebase pipeline va khong cho phep kiem soat timeout lan
 * retry — ma day lai la phan song con voi tre. Mot POST truc tiep de doc, de
 * test va khong them dependency nao.
 *
 * Da do voi key that hom 2026-10-21:
 *  - `gemini-2.5-flash` => 404 "no longer available to new users". Key moi
 *    chi dung duoc nhung model thuoc the he sau.
 *  - `gemini-3.6-flash`     => 200, ~3,5s, tra loi tieng Viet dung.
 *  - `gemini-3.1-flash-lite` => 200, ~1,6s — bo khi model chinh 503.
 *  - `gemini-3.8-flash` va `gemini-flash-latest` => 503 "high demand" ngay
 *    tren may phat trien, nen KHONG lay lam model chinh.
 *  - Model the he 3 nghi ngam (thinking) va an het ngan sach khi maxOutputTokens
 *    thap: 400 token => finishReason MAX_TOKENS, text rong. Vi vay dat
 *    thinkingBudget = 0 va to ra 900 token cho cau tra loi.
 */
object GeminiClient {

    private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

    /** Thu lan luot; tat ca deu that bai moi bao loi. */
    val MODELS = listOf("gemini-3.6-flash", "gemini-3.1-flash-lite")

    private val moshi = Moshi.Builder().build()
    private val mapAdapter = moshi.adapter(Map::class.java)

    /**
     * Gui lich su chat + cau hoi moi, nhan ve cau tra loi thoai mai.
     *
     * `systemInstruction` mang bang dieu tra ngay hom do, nen moi cau tra loi
     * deu dua tren so lieu that cua chinh may dang chay, khong phai loi khuyen
     * chung. `stats` rong thi van goi duoc (AI chi tra loi theo cau hoi).
     */
    suspend fun chat(
        apiKey: String,
        history: List<ChatTurn>,
        question: String,
        stats: String
    ): Result<String> {
        if (!AiKey.isUsable(apiKey)) {
            return Result.failure(AiException("Chưa có khoá Gemini. Nhập khoá để trợ lý trả lời bằng số liệu thật của máy."))
        }
        // HTTP tro thanh NEN nam trong ham nay, khong phai o man hinh: goi
        // truc tiep tu compose scope se chan main thread va dong bang phan
        // hoi khi may cham.
        return withContext(Dispatchers.IO) {
            var lastError = "Không gọi được Gemini."
            for (model in MODELS) {
                for (attempt in 1..2) {
                    when (val outcome = once(apiKey, model, history, question, stats)) {
                        is Call.Ok -> return@withContext Result.success(outcome.text)
                        is Call.Retryable -> {
                            lastError = outcome.message
                            if (attempt < 2) delay(1_500)
                        }
                        is Call.Fatal -> return@withContext Result.failure(AiException(outcome.message))
                    }
                }
            }
            Result.failure(AiException(lastError))
        }
    }

    private sealed class Call {
        data class Ok(val text: String) : Call()
        data class Retryable(val message: String) : Call()
        data class Fatal(val message: String) : Call()
    }

    private suspend fun once(apiKey: String, model: String, history: List<ChatTurn>, question: String, stats: String): Call {
        val body = mapAdapter.toJson(buildBody(history, question, stats))
        return try {
            val conn = (java.net.URL("$BASE$model:generateContent").openConnection() as java.net.HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                // Key di trong HEADER, khong nam trong URL: URL de bi luu
                // vao log cua he thong, va Android ghi log tai lieu mang.
                setRequestProperty("x-goog-api-key", apiKey)
                setRequestProperty("User-Agent", "VesperaFamilySafe")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val raw = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            conn.disconnect()
            handle(code, raw)
        } catch (e: java.io.IOException) {
            Call.Retryable("Không kết nối được tới máy chủ AI (${e.javaClass.simpleName}). Kiểm tra Internet rồi thử lại.")
        } catch (e: Exception) {
            Call.Fatal("Không đọc được phản hồi AI: ${e.javaClass.simpleName}")
        }
    }

    /**
     * Doc phan hoi bang Map thay vi data class: Moshi + reflection can lop co
     * the truy cap duoc, ma cac model mang cua API nay chi dung tam 3 truong.
     * Cac truong khac cua Gemini (citationMetadata, safetyRatings...) bo qua
     * mot cach im lang, khong gay loi.
     */
    private fun handle(code: Int, raw: String): Call {
        val json = runCatching { mapAdapter.fromJson(raw) as? Map<*, *> }.getOrNull()
        if (code in 200..299) {
            val candidate = (json?.get("candidates") as? List<*>)?.firstOrNull() as? Map<*, *>
            val parts = (candidate?.get("content") as? Map<*, *>)?.get("parts") as? List<*>
            val text = parts.orEmpty()
                .mapNotNull { it as? Map<*, *> }
                .filter { it["thought"] != true }
                .mapNotNull { (it["text"] as? String)?.takeIf { t -> t.isNotBlank() } }
                .joinToString("\n")
                .trim()
            return when {
                text.isNotEmpty() -> Call.Ok(text)
                candidate?.get("finishReason") == "MAX_TOKENS" ->
                    Call.Retryable("Câu trả lời bị cắt vì quá dài. Hãy hỏi gọn hơn.")
                else -> Call.Retryable("Máy chủ AI trả về nội dung trống, thử lại được.")
            }
        }
        val error = json?.get("error") as? Map<*, *>
        val detail = (error?.get("message") as? String)?.take(220).orEmpty()
        val status = (error?.get("status") as? String).orEmpty()
        val friendly = when (code) {
            400, 403 -> "Khoá Gemini không dùng được với API này. Kiểm tra lại khoá (lỗi ${status.ifBlank { code.toString() }})."
            404 -> "Model AI tạm thời không có trên khoá này."
            429 -> "Đã gọi hơi nhiều trong vài phút. Chờ một chút rồi gửi lại."
            503 -> "Máy chủ AI đang quá tải, sẽ tự thử lại với model khác."
            else -> "Máy chủ AI trả lỗi HTTP $code."
        }
        return when (code) {
            408, 429, 500, 502, 503, 504 -> Call.Retryable(friendly)
            else -> Call.Fatal(listOf(friendly, detail).filter { it.isNotEmpty() }.joinToString(" "))
        }
    }

    /**
     * Than request tach rieng khong nam sau lop HTTP: don vi test doi chieu
     * duoc payload (role, systemInstruction, ngan sach token) ma khong can
     * goi mang.
     */
    fun buildBody(history: List<ChatTurn>, question: String, stats: String): Map<String, Any> {
        val contents = history
            .filter { it.role == "user" || it.role == "model" }
            .map { mapOf("role" to it.role, "parts" to listOf(mapOf("text" to it.text))) }
            .toMutableList()
        contents.add(mapOf("role" to "user", "parts" to listOf(mapOf("text" to question))))
        return mapOf(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to systemPrompt(stats)))),
            "contents" to contents,
            "generationConfig" to mapOf(
                "temperature" to 0.7,
                "maxOutputTokens" to 900,
                // Model the he 3 nghi truoc khi noi. Khong chan ngan sach thi
                // phan lon token bi an va nguoi dung thay khung troi.
                "thinkingConfig" to mapOf("thinkingBudget" to 0)
            )
        )
    }

    private fun systemPrompt(stats: String): String = buildString {
        append(
            "Bạn là trợ lý gia đình trong ứng dụng Gia Đình Việt, nói tiếng Việt tự nhiên, " +
                "ngắn gọn, tôn trọng. Người hỏi là cha mẹ hoặc người giám hộ.\n" +
                "Nhiệm vụ: đọc số liệu sử dụng thiết bị, chỉ ra nguyên nhân có khả năng nhất, " +
                "rồi đưa GIẢI PHÁP cụ thể được đánh số (3-5 bước, làm được trong tuần này) " +
                "và LỜI KHUYÊN ngắn cho cha mẹ khi nói chuyện với con.\n" +
                "Quy tắc: chỉ dựa trên số liệu được cung cấp; số liệu thiếu thì nói rõ là chưa có, " +
                "không bịa. Không chẩn đoán bệnh, không kết luận con hư hay sai. " +
                "Không gợi ý theo dõi bí mật hay đọc lén tin nhắn của con. " +
                "Trả lời dưới 200 từ, dùng gạch đầu dòng, không dùng markdown heading.\n"
        )
        if (stats.isNotBlank()) {
            append("SỐ LIỆU THIẾT BỊ (app đo trực tiếp, không phải ước lượng):\n").append(stats)
        } else {
            append("Chưa có số liệu thiết bị cho hôm nay.")
        }
    }

    class AiException(message: String) : Exception(message)
}
