package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho chỗ hỏng phát hiện lúc kiểm bản 39.0 trên BlueStacks: một khi
 * khoá AI đã lưu hợp lệ thì thẻ nhập khoá biến mất vĩnh viễn, và
 * `AiKey.clear` / `AiKey.clearOpenRouter` không được bất kỳ chỗ nào gọi —
 * nghĩa là không có đường gỡ khoá sai khỏi máy.
 */
class AiKeyRegressionTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    @Test
    fun khoaDaLuuCoTheBoKhoiMay() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("Vẫn không có đường gỡ khoá OpenRouter", screen.contains("AiKey.clearOpenRouter(context)"))
        assertTrue("Vẫn không có đường gỡ khoá Gemini", screen.contains("AiKey.clear(context)"))
        assertTrue("Không có nút gỡ khoá để kiểm trên máy", screen.contains("testTag(\"ai_clear_key_btn\")"))
    }

    @Test
    fun khoaNhapDuocCheKhiGõ() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("Ô khoá không còn che ký tự", screen.contains("PasswordVisualTransformation()"))
        assertTrue("Phải có nút hiện/ẩn khoá", screen.contains("testTag(\"ai_key_field\")"))
    }
}
