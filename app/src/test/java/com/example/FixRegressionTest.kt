package com.example

import com.example.data.model.hasRealFix
import com.example.service.map.OfflineMapDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kiểm thử tĩnh (không cần thiết bị/emulator) cho các lỗi đã sửa trong vòng nâng cấp 2:
 *  - Manifest khai báo đủ quyền cho luồng runtime + quyền đặc biệt.
 *  - Danh sách 34 tỉnh thành cho bộ tải bản đồ offline.
 *  - Nguồn không còn mẫu "báo thành công giả" nguy hiểm.
 */
class FixRegressionTest {

    private fun projectFile(relative: String): File {
        // Unit test chạy với working dir = app/ (module dir)
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    @Test
    fun manifest_declaresCriticalPermissions() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val required = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.FOREGROUND_SERVICE_LOCATION",
            "android.permission.PACKAGE_USAGE_STATS"
        )
        required.forEach { perm ->
            assertTrue("Thiếu quyền trong manifest: $perm", manifest.contains(perm))
        }
    }

    @Test
    fun offlineMap_has34Provinces() {
        assertEquals(34, OfflineMapDownloader.provinces.size)
    }

    @Test
    fun smsSender_checksSimBeforeClaimingSuccess() {
        val src = projectFile("src/main/java/com/example/service/sos/FallbackSmsManager.kt").readText()
        assertTrue("Phải kiểm tra trạng thái SIM", src.contains("SIM_STATE_ABSENT"))
        assertTrue("Phải kiểm tra chế độ máy bay", src.contains("AIRPLANE_MODE_ON"))
        assertTrue("Phải chờ xác nhận từ hệ thống", src.contains("ACTION_SMS_SENT") || src.contains("SMS_SENT_ACTION"))
        assertTrue("Phải dùng sentIntent để biết kết quả thật", src.contains("sendMultipartTextMessage"))
    }

    @Test
    fun sosDispatcher_requiresRealDelivery() {
        val src = projectFile("src/main/java/com/example/service/sos/SmartSosDispatcher.kt").readText()
        assertTrue("Mesh chỉ thành công khi gửi đi thật", src.contains("sentCount > 0"))
        assertTrue("Phải có kiểm tra kết quả cloud thật", src.contains("CloudDelivery.SENT"))
    }

    @Test
    fun locationMap_noFakeStayDuration() {
        val src = projectFile("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt").readText()
        assertTrue("Không được mô phỏng thời gian ở (18 phút giả)", !src.contains("mutableStateOf(18)"))
        assertTrue("Phải vẽ tuyến đường di chuyển", src.contains("Polyline"))
    }

    /**
     * Nền tối phải GẦN ĐEN. Test cũ so chuỗi cứng "0xFF02030B" nên chỉ cần đổi
     * mã màu là đỏ, dù màu mới vẫn đạt yêu cầu. Ở đây kiểm tra tính chất: mọi
     * dải của SpaceNightPalette phải có kênh sáng nhất <= 0x12.
     */
    @Test
    fun darkTheme_isDarker() {
        val src = projectFile("src/main/java/com/example/ui/theme/PremiumTheme.kt").readText()
        val palette = src.substringAfter("val SpaceNightPalette")
        val gradient = palette.substringAfter("backgroundGradient = listOf(").take(400)
        val colors = Regex("0xFF([0-9A-Fa-f]{6})").findAll(gradient).map {
            val v = it.groupValues[1].toInt(16)
            Triple((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }.toList()
        assertTrue("Phải tìm thấy dải nền của SpaceNightPalette", colors.size >= 3)
        colors.forEach { (r, g, b) ->
            val brightest = maxOf(r, g, b)
            assertTrue("Màu nền tối $r,$g,$b không gần đen (trần 18)", brightest <= 0x12)
        }
    }

    /**
     * Toa do bịa (Ha Noi / Da Nang) tung bi dung lam "vi tri mac dinh", nen SOS
     * gui dung toa do cho luc hai nguy. Test tinh: khong file nguon nao duoc
     * chua cac hang so do nua (tru bo tai ban do offline, tam tai la cho duoc
     * phep).
     */
    @Test
    fun source_hasNoFabricatedCoordinates() {
        val root = projectFile("src/main/java/com/example")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "OfflineMapDownloader.kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("21.0285") || t.contains("105.8542") ||
                    t.contains("15.1205") || t.contains("108.7923")
            }
            .map { it.name }
            .toList()
        assertTrue("Con toa do bia ở: $offenders", offenders.isEmpty())
    }

    /** LocationData mac dinh phai la "chua do duoc GPS", khong phai mot dia chi that. */
    @Test
    fun locationData_defaultsToNoFix() {
        val blank = com.example.data.model.LocationData()
        assertTrue("Mac dinh 0/0 phai bi coi la khong co vi tri", !blank.hasRealFix)
        assertTrue("Khong duoc mo phong pin day", blank.batteryLevel == 0)
        val real = blank.copy(latitude = 10.7824, longitude = 106.6494)
        assertTrue("Toa do that phai duoc nhan", real.hasRealFix)
    }

    // ── Vong nang cap 3: duong luu noi quy chan app ────────────────────────

    /**
     * `usedMinutes` tung co mac dinh 45 trong khi khong duong nao ghi lai no,
     * nen moi man hinh deu bao "hom nay da dung 45 phut" — du hom do may chua
     * mo. Mac dinh phai la 0, va so phut hien thi phai lay tu danh sach app doc
     * duoc that (`todayUsedMinutes`).
     */
    @Test
    fun schedule_noFakeUsedMinutes() {
        assertEquals(
            "usedMinutes mac dinh khong duoc bịa số liệu",
            0,
            com.example.data.model.UsageSchedule().usedMinutes
        )
        val detailed = listOf(
            com.example.data.model.DetailedAppUsage("a", "A", 12, "Game"),
            com.example.data.model.DetailedAppUsage("b", "B", 30, "Học")
        )
        assertEquals(42, com.example.data.model.todayUsedMinutes(detailed, fallback = 7))
        // Danh sach rong (chua cap quyen Usage Access) thi moi dung gia tri cu.
        assertEquals(7, com.example.data.model.todayUsedMinutes(emptyList(), fallback = 7))
        assertEquals(0, com.example.data.model.todayUsedMinutes(emptyList()))
    }

    /**
     * Bo chan chay tren main thread cua he thong. `queryUsageStats()` la lenh
     * doc dien, goi truc tiep moi lan doi cua so lam giay khi cham tich app —
     * dung tinh chen so lieu va chi doc tren the nen.
     */
    @Test
    fun blockerService_readsUsageStatsOffMainThread() {
        val src = projectFile("src/main/java/com/example/service/blocker/AppBlockerService.kt").readText()
        assertTrue("Phải có worker riêng đọc UsageStats", src.contains("Executors.newSingleThreadExecutor"))
        assertTrue("Phải giữ giá trị trong bộ nhớ đệm", src.contains("usedMinutesCache"))
        assertTrue(
            "Không được dùng cửa sổ rolling 24h (tinh dan ca hom qua)",
            !src.contains("TimeUnit.DAYS.toMillis(1)")
        )
        assertTrue("Phải tính từ nửa đêm", src.contains("HOUR_OF_DAY, 0"))
    }

    /**
     * Han muc phut soan o Dashboard khong bao luu duoc (card do chi hien khi
     * CHUA ghep noi), nen no phaibien thanh noi dung + nut roi, khong con
     * thanh keo tu ghi may chủ.
     */
    @Test
    fun dashboard_dailyTargetIsNotEditedWhileUnpaired() {
        val src = projectFile("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue("Dashboard không được tự ghi hạn mức", !src.contains("onUpdateDailyTarget"))
        assertTrue("Phải chỉ đường tới nơi soạn được thật", src.contains("Đổi hạn mức trong Cài đặt"))
    }

    /** Cot "hom nay" tren bieu do phai theo thu tu mang (T2..CN), khong co dinh CN. */
    @Test
    fun screenTime_highlightsRealToday() {
        val src = projectFile("src/main/java/com/example/ui/screens/ScreenTimeScreen.kt").readText()
        assertTrue("Không được tô cứng cột Chủ nhật", !src.contains("day == \"CN\""))
        assertTrue("Phải suy ra thứ trong tuần từ Calendar", src.contains("Calendar.DAY_OF_WEEK"))
    }

    /**
     * `firestore.rules` chan `allow delete: if false` cho /users/{uid}, nen
     * nut "xóa tài khoản" khong the xoa gi. Noi such la noi tran; nay phai noi
     * dung viec xay ra (dang xuat) + huong dan thu tuc con lai.
     */
    @Test
    fun settings_accountActionDoesNotOverclaim() {
        val src = projectFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
        assertTrue(
            "Không được hứa xóa vĩnh viễn khi nút chỉ đăng xuất",
            !src.contains("xóa vĩnh viễn theo chính sách quyền riêng tư")
        )
        assertTrue("Phải nói rõ nút không xóa dữ liệu máy chủ", src.contains("KHÔNG xóa hồ sơ"))
    }

    /**
     * Lớp bản đồ chỉ nằm trong `remember`, nên mo lai man hinh la quay ve
     * "Đường phố". O nong thon, lop duong pho OSM trong gan nhu trang ran,
     * nguoi dung doc thanh "app bi doi do hoa". Phai luu vao SharedPreferences
     * va mac dinh mo bang lop ve tinh.
     */
    @Test
    fun mapLayer_choiceSurvivesReopeningTheScreen() {
        val src = projectFile(
            "src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt"
        ).readText()
        assertTrue("Phải đọc lớp đã lưu thay vì cứng số 0", src.contains("mapPrefs.getInt(\"layer2\""))
        assertTrue("Mặc định phải mở bằng lớp có ảnh thật + tên đường", src.contains("MapLayer.HYBRID.ordinal"))
        assertTrue("Không được khóa cứng nền đường phố khi tạo MapView", !src.contains("setTileSource(MapLayer.STREET.source)"))
    }

    /**
     * Do truc tiep hom 2026-10-21: tile.openstreetmap.org (MAPNIK) khong tra ve
     * HTTP nao tu mach VN, con Esri World Imagery tra 2521 byte giong het nhau
     * (o xam) tu z19. Nen phai ngung dung MAPNIK lam nen va ngung yeu cau Esri
     * z19+; nguoi dung thoang bao "map bi trang" chinh vi the.
     */
    @Test
    fun mapLayersUseOnlyTileHostsWithVerifiedRealImagery() {
        val src = projectFile(
            "src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt"
        ).readText()
        val enumBlock = src.substringAfter("private enum class MapLayer").substringBefore("private fun layerAt")
        assertTrue("Lop Duong pho khong duoc con la MAPNIK (host chet)", !enumBlock.contains("MAPNIK"))
        assertTrue("Phai co lop Google hybrid lam mac dinh", enumBlock.contains("MapTileSources.HYBRID"))
        assertTrue("Esri phai chep o z18 la muc cuoi co that", enumBlock.contains("ESRI_MAX_ZOOM"))
        assertTrue("The map khong duoc tu khai báo source rieng", !src.contains("XYTileSource("))
    }

    /**
     * Kho tile offline cua osmdroid tra theo TEN cua tile source. Neu tai vung
     * offline dung mot nen khac voi nen the dang hien thi thi tai xong van thay
     * trang, va do la loi "bao tai thanh cong nhung khong tai duoc" ban cu.
     */
    @Test
    fun offlineMapPackIsDownloadedForTheLayerUserActuallySees() {
        val dl = projectFile("src/main/java/com/example/service/map/OfflineMapDownloader.kt").readText()
        val shared = projectFile("src/main/java/com/example/service/map/MapTileSources.kt").readText()
        assertTrue("Tai offline khong duoc cung cap nen OSM da chet", !dl.contains("TileSourceFactory.MAPNIK"))
        assertTrue("Phai tai dung nen mac dinh cua the ban do", dl.contains("MapTileSources.HYBRID"))
        assertTrue("Zoom offline phai bi chep theo tran da do", dl.contains("suggestedMaxZoom(tileSource)"))
        assertTrue("Tran Esri da do la z18", shared.contains("val ESRI_MAX_ZOOM = 18"))
        assertTrue("Tran Google da do la z21", shared.contains("val GOOGLE_MAX_ZOOM = 21"))
    }

    // ── Trợ lý AI + dữ liệu dùng máy hằng ngày (bản 22.0) ─────────────────

    /**
     * Key that cua Gemini do nguoi dung cung cap. APK tai tu GitHub Release la
     * cong khai, nen mot chuoi trong file nguon cung la mot no loi dung thanh
     * than. Test quyet theo HINH DANG chuoi, khong theo mot chuoi cu the: chen
     * thang key vao day thi chinh file test da vi pham va lan do xanh khong
     * bao gio xay ra (dinh luu cua ban 22.0 chet vi chinh ly do nay).
     */
    @Test
    fun noGeminiKeyIsEverCommitted() {
        val repoRoot = findRepoRoot()
        val scanned = textFilesIn(repoRoot)
        val offenders = scanned.filter { looksLikeRealKey(readUtf8(it)) }
        assertTrue("File co duong nhu chua key that: ${offenders.map { it.name } }", offenders.isEmpty())
        assertTrue("Phan quet phai thay duoc nguon cua app", scanned.any { it.name == "GeminiClient.kt" })

        val example = File(repoRoot, ".env.example")
        assertTrue("Phai co .env.example de BuildConfig luoan sinh ra truong key", example.exists())
        assertTrue(".env.example phai chi la chuoi chiem cho", example.readText().contains("REPLACE_ME"))
        assertTrue(".env phai bi .gitignore", File(repoRoot, ".gitignore").readLines().map { it.trim() }.contains(".env"))
    }

    /** Build luon phai co truong BuildConfig, ke ca khi CI khong co repo secret. */
    @Test
    fun geminiKeyFallsBackToOnDeviceEntry() {
        val repoRoot = findRepoRoot()
        val key = projectFile("src/main/java/com/example/service/ai/AiKey.kt").readText()
        assertTrue("Phai lay key tu BuildConfig khi nguoi dung chua nhap", key.contains("BuildConfig.GEMINI_API_KEY"))
        assertTrue("Key nhap tay phai duoc uu tien", key.contains("entered(context).ifBlank"))
        assertTrue("Phai tu choi chuoi chiem cho cua .env.example", key.contains("REPLACE_ME"))
        assertTrue("Key luu trong SharedPreferences cua app, khong phai file cong khai",
            key.contains("getSharedPreferences(PREFS, Context.MODE_PRIVATE)"))

        val workflow = File(repoRoot, ".github/workflows/build-apk.yml").readText()
        assertTrue("CI phai lay key tu repo secret", workflow.contains("secrets.GEMINI_API_KEY"))
        assertTrue("CI phai ghi vao .env da bi ignore", workflow.contains("> .env"))
        // `$` phai thoat: trong chuoi Kotlin "$GEMINI_API_KEY" la bieu thuc
        // dai, se khong bien dich chu khong phai loi cua workflow.
        assertTrue("CI khong duoc in gia tri key ra log", !workflow.contains("echo \"\$GEMINI_API_KEY\""))
    }

    /** Key trong URL de bi luu vao log mang; API cua Google nhan header. */
    @Test
    fun geminiClientSendsKeyInHeaderAndSurvivesOverload() {
        val src = projectFile("src/main/java/com/example/service/ai/GeminiClient.kt").readText()
        assertTrue("Phai dung header x-goog-api-key", src.contains("x-goog-api-key"))
        assertTrue("Cam dua key vao query string", !src.contains("&key="))
        assertTrue("Phai co model phong khi model chinh 503", src.contains("gemini-3.1-flash-lite"))
        assertTrue("Model the he 3 an token nghi ngam, phai chan ngan sach", src.contains("\"thinkingBudget\" to 0"))
        assertTrue("Phai chay tren IO thread", src.contains("withContext(Dispatchers.IO)"))
    }

    /**
     * AI chi co gia tri khi no doc so lieu THAT. Bo dem phai cuoi theo epochDay
     * (dem don cua StudyProgressStore) va phai cat lich su, neu khong thi sau
     * mot thang prompt phung phit va so lieu cu ghi de so lieu moi.
     */
    @Test
    fun dailyUsageRecordRollsByDayAndStaysBounded() {
        val src = projectFile("src/main/java/com/example/data/ai/DailyUsageStore.kt").readText()
        assertTrue("Phai quy ve theo ngay", src.contains("timeInMillis / 86_400_000L"))
        assertTrue("Phai cat lich su qua cu", src.contains("MAX_HISTORY_DAYS"))
        assertTrue("Phai luu so lan bi chan that", src.contains("recordBlockedAttempt"))
        // Ban 24.0 doi bang dieu tra sang tieng Viet co dau, nen khoa kiem tra
        // la chuoi THAT ma nguoi dung nhin thay, khong con dang ASCII khong dau.
        assertTrue("Bang dieu tra phai gui kem han muc phut", src.contains("/ hạn mức "))
        assertTrue("Du lieu cua tre luu tren may, khong len Firestore",
            !src.contains("FirebaseFirestore") && !src.contains("repository"))
    }

    /** Bo chan la noi duy nhat biet chac chan mot lan ve Home da xay ra. */
    @Test
    fun blockerCountsRealBlocksForTheDailyReport() {
        val src = projectFile("src/main/java/com/example/service/blocker/AppBlockerService.kt").readText()
        val block = src.substringAfter("lastBlockedPackage = pkg")
        assertTrue("Phai dem dung luc chan that", block.contains("DailyUsageStore(applicationContext).recordBlockedAttempt(pkg)"))
    }

    /** Tro ly AI phai la man hinh that, co duong vao va co nguon so lieu. */
    @Test
    fun aiAssistantIsARealScreenWiredToRealData() {
        val shell = projectFile("src/main/java/com/example/ui/screens/MainAppScreen.kt").readText()
        assertTrue("Phai co overlay mo man hinh AI", shell.contains("\"ai\" -> AiChatScreen"))
        assertTrue("Bang dieu tra phai day len ViewModel",
            projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")
                .readText().contains("writeTodaySnapshot"))
        val dashboard = projectFile("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue("Tab Tong quan phai co nut mo chat AI", dashboard.contains("onOpenAiChat"))
        val chat = projectFile("src/main/java/com/example/ui/screens/AiChatScreen.kt").readText()
        assertTrue("Man hinh AI phai gui kem so lieu cua may", chat.contains("summaryForAi()"))
        assertTrue("Man hinh AI phai tu goi khi chua co key", chat.contains("AiKey.isUsable(key)"))
        assertTrue("Lich su chat phai luu tren may", chat.contains("ChatHistoryStore(context)"))
    }

    // ── Do dung cho test quet key ──────────────────────────────────────────

    private val keyFileExtensions = setOf("kt", "kts", "yml", "yaml", "json", "xml", "gradle", "properties", "example")

    /** Thu muc khong thuoc nguon commit — `.git`/`.gradle`/`build` chang day
     *  toan bo lan quet va co the chua file dang bi khoa. */
    private val skippedDirectories = setOf(".git", ".gradle", "build", "node_modules", ".idea", ".kotlin")

    /**
     * Goc repo khong phai luc nao cung la cha cua working dir: goi
     * `./gradlew :app:testDebugUnitTest` tu goc thi Gradle dat working dir cua
     * test la app/, nhung cach goi khac co the de no ngay tai goc. Di nguoc
     * len den khi thay file workflow la cach duy nhat dung cho ca hai.
     */
    private fun findRepoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, ".github/workflows/build-apk.yml").isFile) return dir
            dir = dir.parentFile
        }
        return error("Khong tim thay goc repo: di nguoc tu working dir khong thay .github/workflows/build-apk.yml")
    }

    private fun textFilesIn(root: File): List<File> {
        val found = ArrayList<File>()
        collectTextFiles(root, found)
        return found
    }

    private fun collectTextFiles(dir: File, out: MutableList<File>) {
        val entries = dir.listFiles() ?: return
        for (entry in entries) {
            when {
                entry.isDirectory -> if (entry.name !in skippedDirectories) collectTextFiles(entry, out)
                // `.env` la cho chua key cua MAY duoc phep, no da nam trong
                // .gitignore nen khong bao gio lên repo.
                entry.name == ".env" -> Unit
                entry.isFile && entry.extension in keyFileExtensions -> out.add(entry)
            }
        }
    }

    /** Doc bang UTF-8 thu cong: `readText()` nang exception neu gap byte ngoai
     *  UTF-8, va mot file nhiem mau trong repo se biem thanh "khong tim thay
     *  key" chu khong phai mot bao cao ro rang. */
    private fun readUtf8(file: File): String =
        String(file.readBytes(), Charsets.UTF_8)

    /** `AQ.` + 25 ky tu lien ke la dang khoa Google API moi; GEMINI_API_KEY=
     *  theo sau la chuoi dai khong phai cho cho cua `.env.example`. */
    private fun looksLikeRealKey(text: String): Boolean =
        Regex("""AQ\.[A-Za-z0-9_\-]{25,}""").containsMatchIn(text) ||
            Regex("""GEMINI_API_KEY\s*=\s*([A-Za-z0-9_.\-]{25,})""")
                .find(text)
                ?.groupValues?.get(1)
                ?.let { !it.contains("REPLACE_ME") } == true

    /**
     * Bieu do "SU DUNG TRONG TUẦN" va man Thấu hiểu thời gian tung ve cot 0
     * du may da dung 36 phut, vi `weeklyUsage` la hang so nam im trong
     * VesperaUiState va khong bao gio duoc ghi lai.
     */
    @Test
    fun weeklyChartReadsRealUsageStats() {
        val vm = projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt").readText()
        val helper = projectFile("src/main/java/com/example/service/TelemetryAndUsageHelper.kt").readText()
        assertTrue("ViewModel phai gan so that vao bieu do", vm.contains("weeklyUsage = snapshot.weekly"))
        assertTrue("Phai co ham tinh phut theo tung ngay", helper.contains("fun getWeeklyUsageMinutes()"))
        assertTrue("Phai truy van UsageStats tung ngay", helper.contains("minutesInWindow(um, from, to)"))
    }
}
