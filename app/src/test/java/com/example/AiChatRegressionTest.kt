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
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
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

    /**
     * Loc BlueStacks ban 25.0: cung man "Giờ dùng", the "Đã dùng hôm nay" doi
     * 88 phut trong khi cot T4 cua bieu do bao 53. Nguyen nhan la bucket ngay
     * cua UsageStatsManager chong len bucket "dang chay" — noi thi max moi
     * package, noi thi cong don tung dong.
     */
    @Test
    fun allMinuteCountersShareOneAggregation() {
        val helper = source("src/main/java/com/example/service/TelemetryAndUsageHelper.kt")
        assertTrue(
            "chi mot lan truy van USAGE_STATS roi moi cho dung lai",
            helper.split("UsageStatsManager.INTERVAL_DAILY,").size - 1 == 1
        )
        assertTrue("phai gop dong trung package", helper.contains("private fun usageRowsPerPackage"))
        assertEquals(
            "ca 3 cho dem phut cung phai qua usageRowsPerPackage",
            4,
            helper.split("usageRowsPerPackage(").size - 1
        )
    }

    /** Nut Goi mo ca khi chua ghep noi: may tu bao "dang do chuong" goi mot nguoi khong ton tai. */
    @Test
    fun callButtonsRequireRealPairing() {
        val chat = source("src/main/java/com/example/ui/screens/ChatScreen.kt")
        assertTrue("phai kiem tra ghep noi that", chat.contains("val canCall = uiState.activePairing != null"))
        assertFalse("khong duoc tu che roomId", chat.contains("room_\${uiState.userProfile?.id}"))
        assertFalse(
            "nhat kiem khong duoc doan Firestore dang chay khi chi co mang",
            chat.contains("\"Đang hoạt động • Cloud Firestore\"")
        )
    }

    /** Man hinh gọi phải tự nói hết giả sau 30 giây, không đổ chuông vô hạn. */
    @Test
    fun callScreenStopsPretendingToRing() {
        val call = source("src/main/java/com/example/ui/call/CallActivity.kt")
        assertTrue("phai hen gio cho phia goi", call.contains("postDelayed(noAnswerRunnable"))
        assertTrue("phai huy hen khi man hinh dong", call.contains("removeCallbacks(noAnswerRunnable"))
        assertTrue("phai noi ly do khong noi duoc", call.contains("Không có máy nào trả lời"))
    }

    /**
     * `AppCategoryUsage.percentage` la ti le 0..1. Loc BlueStacks ban 25.0:
     * bang so lieu ghi "Khac 1.0%" cho nhom dung 100% thoi gian, va man "Thấu
     * hiểu thời gian" in "1%" kem muyen dai 1% — cung mot con so doc theo hai
     * quy uoc khac nhau.
     */
    @Test
    fun categoryRatioIsScaledBeforeBeingCalledPercent() {
        val insights = source("src/main/java/com/example/ui/screens/InsightsScreen.kt")
        val store = source("src/main/java/com/example/data/ai/DailyUsageStore.kt")
        assertFalse("khong duoc in ti le 0..1 roi keu %", insights.contains("category.percentage.toInt()}%"))
        assertTrue("phai nhan 100 truoc khi in", insights.contains("(category.percentage * 100f).roundToInt()"))
        assertTrue("muyen phai di thang ti le 0..1", insights.contains("progress = category.percentage"))
        assertTrue("bang so lieu cho AI cung phai nhan 100", store.contains("(it.percentage * 100f).roundToInt()"))
    }
}
