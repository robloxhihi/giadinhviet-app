package com.example

import com.example.ui.screens.AiMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertTrue("bong bong chat phai render qua AiAnswerBody", manHinh.contains("AiAnswerBody("))
        assertFalse("khong duoc quay lai AiBubbleText da xoa", manHinh.contains("AiBubbleText("))
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

    /**
     * Van that ghi lai tu BlueStacks ban 26.0. Model khong dam dong nhu hen,
     * nen neu khong tach theo dau `:` thi nguoi dung thay mot dam chu cung
     * mau trong bong 320dp — "noi dung duoc ma bo cuc khong muon doc".
     */
    private val realAnswer = """
        Dựa trên số liệu hôm nay, có thể thấy con chưa bị vượt hạn mức.

        **Nguyên nhân:**
        - Bộ chặn chưa được kích hoạt với ứng dụng nào.
        - Chưa có dữ liệu về nhóm Học tập.

        **Giải pháp:**
        1. **Điều chỉnh hạn mức:** Giữ nguyên hạn mức 120 phút.
        2. Kiểm tra ứng dụng: Mở ứng dụng Gia Đình Việt trên máy con.
        3. Thống nhất lịch dùng máy: Cùng con nhìn lại hạn mức 120 phút/ngày.
        4. Theo dõi đối chiếu: Quan sát thêm số liệu 2-3 ngày tới.

        **Lời khuyên cho cha mẹ:** Nên khen ngợi khi con tuân thủ.
    """.trimIndent()

    @Test
    fun numberedAnswerBecomesReadableBlocks() {
        val blocks = AiMarkdown.blocks(realAnswer)
        val steps = blocks.filter { it.kind == AiMarkdown.Kind.STEP }
        assertEquals("bon buoc phai bon khoai rieng", 4, steps.size)
        assertEquals(listOf("1", "2", "3", "4"), steps.map { it.marker })
        assertEquals(
            "nhan phai tach khoi noi dung, ca khi model khong dam",
            listOf("Điều chỉnh hạn mức", "Kiểm tra ứng dụng", "Thống nhất lịch dùng máy", "Theo dõi đối chiếu"),
            steps.map { it.label }
        )
        assertTrue(
            "noi dung khong duoc con dem theo nhan",
            steps.all { it.body.isNotEmpty() && !it.body.contains(it.label) }
        )
        assertEquals(
            "gach dau dong cung phai la khoai rieng",
            2,
            blocks.count { it.kind == AiMarkdown.Kind.BULLET }
        )
        assertTrue(
            "nhan don doc lap (Nguyên nhân:) van duoc nhan ra",
            blocks.any { it.label == "Nguyên nhân" && it.body.isEmpty() }
        )
    }

    /** Doan van thuong khong duoc bi cat doi o dau `:` — chi danh sach moi split. */
    @Test
    fun plainProseIsNotSplitOnItsColon() {
        val only = AiMarkdown.blocks("Lưu ý: hôm nay con học muộn nên dùng máy trễ.")
        assertEquals(1, only.size)
        assertEquals("", only[0].label)
        assertEquals("Lưu ý: hôm nay con học muộn nên dùng máy trễ.", only[0].body)
    }

    /**
     * Do tren BlueStacks ban 26.0: the dinh vi ghi "14.45722, 107.91763 ±1m"
     * va ke map z19 trong khi chua ghep noi voi may nao. Dien thoai khong do
     * duoc ±1 m; so thap phan in ra khong duoc mong hon sai so cua no.
     */
    @Test
    fun gpsPrecisionIsCappedByReportedAccuracy() {
        assertEquals("±1m khong phai do dien thoai", 3f, com.example.service.map.FixPrecision.honestAccuracyMeters(1f))
        assertEquals("khong ro sai so thi dung ±100m", 100f, com.example.service.map.FixPrecision.honestAccuracyMeters(0f))
        assertEquals("sai so that thi giu nguyen", 42f, com.example.service.map.FixPrecision.honestAccuracyMeters(42f))

        assertEquals(5, com.example.service.map.FixPrecision.decimalsForAccuracy(1f))
        assertEquals(3, com.example.service.map.FixPrecision.decimalsForAccuracy(100f))
        assertEquals(2, com.example.service.map.FixPrecision.decimalsForAccuracy(1000f))
        assertEquals(
            "±100m thi khong duoc in 5 chu so thap phan",
            "14.457, 107.918",
            com.example.service.map.FixPrecision.formatCoordinates(14.45722, 107.91763, 100f)
        )

        assertTrue(
            "sai so ±100m khong duoc phep ngi z19",
            com.example.service.map.FixPrecision.maxZoomForAccuracy(100f) <= 16.0
        )
        assertTrue(
            "zoom phai giam dan khi sai so tang",
            com.example.service.map.FixPrecision.maxZoomForAccuracy(5f) >
                com.example.service.map.FixPrecision.maxZoomForAccuracy(500f)
        )

        assertEquals(
            "in thi ±<sai so dung>, khong in so tho",
            "±3 m",
            com.example.service.map.FixPrecision.accuracyShortLabel(1f)
        )
        assertEquals("±42 m", com.example.service.map.FixPrecision.accuracyShortLabel(42f))
        assertEquals("±100 m", com.example.service.map.FixPrecision.accuracyShortLabel(0f))
        assertNull("sai so that thi khong can thong bao", com.example.service.map.FixPrecision.accuracyNote(42f))
        val note = com.example.service.map.FixPrecision.accuracyNote(1f) ?: ""
        assertTrue("phai noi vi sao con so bi doi", note.contains("dưới ngưỡng"))
        assertTrue("phai noi ro da dung ±3m", note.contains("±3 m"))
        assertTrue("may khong bao thi khong duoc noi hon ±100m",
            (com.example.service.map.FixPrecision.accuracyNote(0f) ?: "").contains("±100 m"))
    }

    /** Map card phai an theo FixPrecision, khong in toa do/ sai so tho nua. */
    @Test
    fun mapCardStopsQuotingRawSensorPrecision() {
        val card = source("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt")
        assertTrue("toa do phai qua FixPrecision", card.contains("FixPrecision.formatCoordinates("))
        assertTrue("sai so phai qua FixPrecision", card.contains("FixPrecision.accuracyShortLabel("))
        assertTrue("vong tron dung sai so trung thuc", card.contains("honestAcc.toDouble()"))
        assertTrue("maxZoom phai bi chan theo sai so", card.contains("coerceAtMost(zoomCeiling)"))
        assertFalse(
            "khong duoc con day `±<so tho>m` ra man hinh",
            card.contains("±${'$'}{accuracyMeters.toInt()}m")
        )
        assertFalse(
            "khong duoc in dinh toa do 5 chu so co dinh",
            card.contains("\"%.5f, %.5f\"")
        )
    }

    /** cau lenh he phai noi ro khuon trinh bay, neu khong model lai tra 1 khoai. */
    @Test
    fun aiIsToldToAnswerInShortLabelledLines() {
        val client = source("src/main/java/com/example/service/ai/GeminiClient.kt")
        assertTrue("phai yeu cau 3 khoi co nhan", client.contains("**Nguyên nhân:**"))
        assertTrue("moi y mot dong", client.contains("Mỗi ý nằm trọn trong MỘT dòng"))
        assertTrue("cam emoji vi bong chat khong co cho", client.contains("không emoji"))
    }

    /**
     * Ca app khong duoc tu in toa do 4-5 chu so thap phan nua — moi chu so phai
     * di qua FixPrecision. Truong hop ngoai le duy nhat la URL `maps.google.com`
     * trong SMS cuu nan, noi con so tho giup luc luong va ± da ghi ranh manh ben canh.
     */
    @Test
    fun noRawCoordinateFormatSurvivesInTheApp() {
        val roots = listOf(File("src/main/java"), File("app/src/main/java"))
        val dir = roots.firstOrNull { it.exists() } ?: error("Khong tim thay src/main/java")
        val rawFormat = Regex("\"%\\.[45]f, ?%\\.[45]f\"")
        val offenders = dir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "FixPrecision.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    if (rawFormat.containsMatchIn(line)) "${file.name}:${i + 1} ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue("con noi do gia chinh xac: $offenders", offenders.isEmpty())
    }

    /**
     * `timestamp` cua mot diem dung la ping CUOI CUNG, `duration` la thoi gian da
     * o do duoc cong don luc ghi. Lay rieng (now - timestamp) lam "da o day" sinh
     * ra dong "Tu 21:33 den 23:14 • Da o day: 2p 13s" — hai so mau thuan nhau
     * ngay tren man hinh (ban 28.0 do tren BlueStacks).
     */
    @Test
    fun historyStayMatchesTheClockRangeItShows() {
        val screen = source("src/main/java/com/example/ui/screens/LocationScreen.kt")
        assertTrue("phai cong don thoi gian luu tru", screen.contains("it.duration.coerceAtLeast(0L)"))
        assertTrue("diem dung da ket thi dung bang o ping cuoi", screen.contains("if (ongoing) currentEpochMillis else entry.timestamp"))
        assertFalse(
            "khong duoc lay (now - timestamp) khong cong duration",
            screen.contains("(endTimeMillis - entry.timestamp) / 1000L")
        )
    }

    /**
     * Man hinh "Hanh trinh & Vung an toan" mo tu tab Vi tri tung ve 4 diem bieu
     * dien khi may chua co diem nao, va tu xung la "hanh trinh cua con" du chua
     * ghep noi — dung cai nguoi dung phan nan nhat: so lieu khong that.
     */
    @Test
    fun routeTraceOnlyDrawsRealFixes() {
        val screen = source("src/main/java/com/example/ui/screens/LocationHistoryScreen.kt")
        assertFalse(
            "khong duoc boi diem gia khi thieu du lieu",
            screen.contains("Offset(w * 0.14f, h * 0.68f)")
        )
        assertTrue("phai noi ro vi sao chua co duong", screen.contains("Chưa đủ điểm để vẽ tuyến đường"))
        assertTrue("diem phai lay theo toa do that", screen.contains("filter { it.latitude != 0.0 || it.longitude != 0.0 }"))
    }

    /** Chua ghep noi thi tuyet doi khong duoc ghi "cua con". */
    @Test
    fun historyScreenNamesTheOwnerItActuallyShows() {
        val screen = source("src/main/java/com/example/ui/screens/LocationHistoryScreen.kt")
        assertTrue(
            "tieu de phai doi theo trang thai ghep noi",
            screen.contains("if (isPaired) \"Hành trình của \$partnerName\" else \"Hành trình của máy này\"")
        )
        assertFalse("khong duoc cung kieu hoa mot tieu de", screen.contains("title = \"Hành trình của con\""))
    }

    /**
     * `pairings/<id>/location_history` dung chung cho ca hai may, nen moi man
     * hinh doc no phai loc theo userId — neu khong phu huynh thay ca ping cua
     * minh trong hanh trinh cua con.
     */
    @Test
    fun historyIsScopedToTheTrackedDevice() {
        for (path in listOf(
            "src/main/java/com/example/ui/screens/LocationHistoryScreen.kt",
            "src/main/java/com/example/ui/screens/LocationScreen.kt"
        )) {
            val screen = source(path)
            assertTrue("$path phai loc theo chu nhan", screen.contains("it.userId == id") || screen.contains("it.userId == partnerLoc.userId"))
        }
    }
}
