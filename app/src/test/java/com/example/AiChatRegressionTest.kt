package com.example

import com.example.ui.screens.AiMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho 4 loc ghi nhan duoc tren BlueStacks ban 22.0 ngay 2026-10-21:
 *  - `**...**` cua Gemini hien nguyen van tren bong bong chat.
 *  - Bang so lieu ASCII khong dau va chuoi "bo chan chan app" kho hieu.
 *  - "Ung dung dung nhieu nhat" lap ten cung mot ung dung (26ph + 10ph).
 *  - Firestore bao "No setter/field for isCharging" moi lan doc tai lieu.
 */
class AiChatRegressionTest {

    private fun source(relative: String): String {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
    }

    @Test
    fun boldBecomesStyledSegment() {
        val parsed = AiMarkdown.segments("**Đánh giá:** con dùng ít")
        assertEquals(2, parsed.size)
        assertEquals("Đánh giá:", parsed[0].text)
        assertTrue("doan dau phai in dam", parsed[0].bold)
        assertEquals(" con dùng ít", parsed[1].text)
        assertFalse("doan sau khong in dam", parsed[1].bold)
    }

    @Test
    fun unbalancedMarkersAreKeptAsPlainText() {
        assertEquals("a ** b", AiMarkdown.plain("a ** b"))
        assertEquals("", AiMarkdown.plain(""))
        assertEquals("hết", AiMarkdown.plain("hết"))
    }

    @Test
    fun bulletsAndHeadingsAreNormalized() {
        assertEquals("• ăn tối", AiMarkdown.normalize("- ăn tối"))
        assertEquals("• ăn tối", AiMarkdown.normalize("* ăn tối"))
        assertEquals("  • ăn tối", AiMarkdown.normalize("  - ăn tối"))
        assertEquals("Giải pháp", AiMarkdown.normalize("### Giải pháp"))
        assertEquals("*italic* không phải bullet", AiMarkdown.normalize("*italic* không phải bullet"))
    }

    @Test
    fun realGeminiAnswerRendersClean() {
        val thucTe = "**Lời khuyên khi nói chuyện:**\n- Cha mẹ có thể nhẹ nhàng hỏi con"
        val sach = AiMarkdown.plain(thucTe)
        assertFalse("van con dau **", sach.contains("**"))
        assertTrue(sach.startsWith("Lời khuyên khi nói chuyện:"))
        assertTrue(sach.contains("• Cha mẹ có thể nhẹ nhàng hỏi con"))
    }

    @Test
    fun chatScreenUsesMarkdownRendererNotRawText() {
        val manHinh = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("bong bong chat phai goi AiMarkdown", manHinh.contains("AiMarkdown.segments"))
        assertTrue("bong bong chat phai render qua AiBubbleText", manHinh.contains("AiBubbleText("))
    }

    @Test
    fun dailySummaryHasDiacriticsAndNoBrokenPhrase() {
        val store = source("src/main/java/com/example/data/ai/DailyUsageStore.kt")
        assertFalse("con chuoi ASCII khong dau", store.contains("bo chan chan app"))
        assertFalse(store.contains("Ung dung dung nhieu nhat"))
        assertTrue(store.contains("Số lần bộ chặn giữ lại"))
        assertTrue(store.contains("Phút dùng máy"))
        assertTrue(store.contains("Trung bình 7 ngày trước"))
    }

    @Test
    fun topAppsMergeDuplicateNames() {
        val store = source("src/main/java/com/example/data/ai/DailyUsageStore.kt")
        assertTrue("phai gong theo ten ung dung", store.contains("groupBy { it.appName"))
        assertTrue("phai cong so phut cua cac dong trung ten", store.contains("sumOf { it.minutesUsed }"))
    }

    @Test
    fun locationHistoryModelDeclaresFirestoreKeys() {
        val models = source("src/main/java/com/example/data/model/Models.kt")
        assertTrue("isCharging phai gan ten khoa tuyet doi", models.contains("@get:PropertyName(\"isCharging\")"))
        assertTrue("serverTimestamp phai co truong doc lai", models.contains("@get:ServerTimestamp"))
    }
}
