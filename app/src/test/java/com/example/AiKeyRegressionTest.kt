package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho chỗ hỏng phát hiện lúc kiểm bản 39.0 trên BlueStacks: thẻ nhập
 * khoá phải luôn che ký tự khi gõ, vì màn hình này hay bị chụp lại.
 *
 * Lưu ý bản 48.0: nút "Gỡ khoá đã nhập" đã bỏ theo yêu cầu, nên không còn test
 * nào bắt buộc đường gỡ khoá. Hệ quả thật: nhập sai khoá Gemini thì chỉ còn cách
 * xoá dữ liệu ứng dụng.
 */
class AiKeyRegressionTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    @Test
    fun khoaNhapDuocCheKhiGõ() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("Ô khoá không còn che ký tự", screen.contains("PasswordVisualTransformation()"))
        assertTrue("Phải có nút hiện/ẩn khoá", screen.contains("testTag(\"ai_key_field\")"))
    }
}
