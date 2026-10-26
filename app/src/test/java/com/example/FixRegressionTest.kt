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
     * Han muc phut soan o Dashboard khong bao luu duoc (the do chi hien khi
     * CHUA ghep noi, ma noi quy thi chua co cho de ghi). Ban 59.0 doi no thanh
     * noi dung + nut roi; ban 60.0 cat han the do vi man CHUA ghep noi khong
     * duoc noi bat cu thu gi khac viec ghep noi. Test giu ca hai cach quay
     * lai: tu ghi len may chu, va mo lai mot the chi de day nguoi dung di choi.
     */
    @Test
    fun dashboard_dailyTargetIsNotEditedWhileUnpaired() {
        val src = projectFile("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue("Dashboard không được tự ghi hạn mức", !src.contains("onUpdateDailyTarget"))
        assertTrue(
            "Màn chưa ghép nối không còn thẻ hạn mức lẫn nút điều hướng tới nó",
            !src.contains("Đổi hạn mức trong Cài đặt") &&
                !src.contains("Thiết lập mục tiêu giờ dùng cho con")
        )
        // Noi quy that van phai soan duoc o mot cho co ton tai: the luat trong
        // tab Cai dat (BlockRulesCard), noi co nut gui va nhat ky thay doi.
        assertTrue(
            "Cài đặt phải còn nơi soạn nội quy thật",
            projectFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
                .contains("BlockRulesCard")
        )
    }

    /** Cot "hom nay" tren bieu do phai theo thu tu mang (T2..CN), khong co dinh CN. */
    @Test
    fun screenTime_highlightsRealToday() {
        val src = projectFile("src/main/java/com/example/ui/screens/ScreenTimeScreen.kt").readText()
        assertTrue("Không được tô cứng cột Chủ nhật", !src.contains("day == \"CN\""))
        assertTrue("Phải suy ra thứ trong tuần từ Calendar", src.contains("Calendar.DAY_OF_WEEK"))
    }

    /**
     * (đổi chiều ở bản 66.0) Từ bản 55.0 `firestore.rules` chặn
     * `allow delete: if false`, nên nút "xóa tài khoản" chỉ đăng xuất và test
     * bắt màn hình phải tự nhận là "KHÔNG xóa hồ sơ". Bản 66.0 mở quyền tự xóa
     * cho CHÍNH CHỦ nên câu đó giờ mới là câu sai — giữ lại là đánh lừa người
     * dùng theo chiều ngược.
     *
     * Cái vẫn ngoài tầm một client: tài khoản trong Firebase Authentication
     * (email + mật khẩu) — client không có Admin SDK. Vì vậy màn hình phải nói
     * rõ "đăng nhập của bạn vẫn còn", và nút bấm phải hứa đúng phạm vi nó làm
     * được ("Xóa dữ liệu trên máy chủ", không phải "xóa tài khoản Google").
     */
    @Test
    fun settings_accountActionDoesNotOverclaim() {
        val src = projectFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
        assertTrue(
            "Không được hứa xóa tài khoản Google — chỉ xóa được dữ liệu trong app",
            !Regex("x[óo]a\\s+(hẳn|vĩnh viễn|toàn bộ|luôn)?\\s*tài khoản Google", RegexOption.IGNORE_CASE)
                .containsMatchIn(src)
        )
        assertTrue(
            "Lời hứa cũ (nút không xóa gì trên máy chủ) đã sai từ bản 66.0",
            !src.contains("KHÔNG xóa hồ sơ")
        )
        assertTrue("Phải nói thẳng đăng nhập Google vẫn còn sau khi xóa", src.contains("ĐĂNG NHẬP CỦA BẠN VẪN CÒN"))
        assertTrue("Phải nói vì sao không xóa được đăng nhập", src.contains("Firebase Authentication"))
        assertTrue(
            "Nút xác nhận phải hứa đúng phạm vi: dữ liệu trên máy chủ",
            src.contains("Xóa dữ liệu trên máy chủ")
        )
        // Quy tắc cũ vẫn còn giá trị: đừng hứa hẹn "chính sách quyền riêng tư"
        // mà app không có.
        assertTrue(
            "Không được hứa xóa vĩnh viễn theo chính sách quyền riêng tư",
            !src.contains("xóa vĩnh viễn theo chính sách quyền riêng tư")
        )
    }

    /**
     * Nut "Xóa tài khoản" phai la mot chuong trinh don THAT, va thu tu cua no bi
     * rules rang buoc: `accountIsBeingDeleted` chi cho don vi tri / vet di chuyen
     * / phieu SOS / nhat ky noi quy khi `users/<uid>` KHONG CON. Dao thu tu thi
     * toan bo cac lenh sau bi 403, va nguoi dung nhan duoc bien nhan "da xoa"
     * trong khi GPS cua ho van nam tren may chu.
     */
    @Test
    fun xoaTaiKhoanDonDuLieuTheoDungThuTu() {
        val repo = projectFile("src/main/java/com/example/data/repository/VesperaRepository.kt").readText()
        val wipe = repo.substringAfter("suspend fun deleteAccount(")
        assertTrue("Phải có lệnh xóa hồ sơ", wipe.contains("db.collection(\"users\").document(userId).delete()"))
        assertTrue(
            "Hồ sơ phải bị xóa TRƯỚC các dữ liệu khác (rules: accountIsBeingDeleted)",
            wipe.indexOf("collection(\"users\")") < wipe.indexOf("inventory.locationDocs")
        )
        assertTrue("Phải đếm số mục xóa được để in biên nhận", repo.contains("data class WipeOutcome"))
        assertTrue(
            "Không được xóa ghép nối trước subcollection: isPairMember cần document đó còn nguyên",
            wipe.indexOf("inventory.pairingChildDocs") < wipe.indexOf("inventory.pairingDocs")
        )
        val settings = projectFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
        assertTrue("Màn Cài đặt phải gọi thẳng lệnh xóa", settings.contains("onClick = onDeleteAccount"))
        val shell = projectFile("src/main/java/com/example/ui/screens/MainAppScreen.kt").readText()
        assertTrue("ViewModel phải được nối vào nút xóa", shell.contains("viewModel.deleteAccount()"))
        assertTrue("Phải đếm trước khi mở hộp thoại", shell.contains("viewModel.previewAccountDeletion()"))
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
        assertTrue("Lop OSM chi tiet phai dung nen Esri World_Street_Map", enumBlock.contains("MapTileSources.ESRI_STREET,"))
        assertTrue("Moi tran zoom phai den tu hang so DA DO trong MapTileSources, khong nhap so cam",
            enumBlock.contains("MapTileSources.GOOGLE_MAX_ZOOM") &&
                enumBlock.contains("MapTileSources.ESRI_STREET_MAX_ZOOM"))
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

    /**
     * Khoa OpenRouter la cua nguoi dung, khong phai cua repo: cung luat voi
     * Gemini. BuildConfig chi la nguon du phong, key nhap tren may duoc uu
     * tien, va khong co dong echo nao in gia tri ra log CI.
     */
    @Test
    fun openRouterKeyUsesSameNoCommitRuleAsGemini() {
        val key = projectFile("src/main/java/com/example/service/ai/AiKey.kt").readText()
        assertTrue("Phai lay key OpenRouter tu BuildConfig", key.contains("BuildConfig.OPENROUTER_API_KEY"))
        assertTrue("Key OpenRouter nhap tay phai duoc uu tien",
            key.contains("enteredOpenRouter(context).ifBlank"))
        assertTrue("Key OpenRouter luu cung SharedPreferences voi key AI khac nhan",
            key.contains("openrouter_key_manual"))
        assertTrue("Khong duoc dung chung ham luu cua Gemini (deo key sai loai)",
            key.contains("saveOpenRouter(context: Context, key: String)"))

        val repoRoot = findRepoRoot()
        val example = File(repoRoot, ".env.example").readText()
        assertTrue(".env.example phai co cho cho OpenRouter de BuildConfig sinh truong",
            example.contains("OPENROUTER_API_KEY=REPLACE_ME"))

        val workflow = File(repoRoot, ".github/workflows/build-apk.yml").readText()
        assertTrue("CI phai lay key OpenRouter tu repo secret",
            workflow.contains("secrets.OPENROUTER_API_KEY"))
        assertTrue("CI khong duoc in gia tri key OpenRouter ra log",
            !workflow.contains("echo \"\$OPENROUTER_API_KEY\""))

        val gradle = projectFile("build.gradle.kts").readText()
        assertTrue("Key OpenRouter phai di qua secrets plugin, khong hardcode",
            gradle.contains("propertiesFileName = \".env\""))
    }

    /**
     * Nguoi dung chon model thi phai nhan duoc dung model do: khong duoc
     * lat nguoc sang Gemini khi OpenRouter 503 (ban 35.0 chi co mot model nen
     * chuyen nay chua dat len), va payload phai dung cung `AiPrompt` de moi
     * model tra loi theo dung mot khuon trinh bay.
     */
    @Test
    fun modelPickerSendsToTheModelUserChose() {
        val models = projectFile("src/main/java/com/example/service/ai/AiModels.kt").readText()
        listOf(
            "deepseek/deepseek-v4-flash-free",
            "deepseek/deepseek-v4.1-flash-free",
            "z-ai/glm-5.3-flash-free"
        ).forEach { id ->
            assertTrue("Bo chon model phai co $id", models.contains("\"$id\""))
        }
        // Orcarouter dung hau to `-free` cho ban khong tien (KHONG phai `:free`
        // nhu OpenRouter). Truoc day phat biểu ngươc lại — rằng `-free` la ten
        // bịa — va chinh phat biểu đó là lý do danh muc model trong app chi toan
        // model dem tien. Do lai tren `GET /v1/models` cua orcarouter.ai.
        assertTrue("Phai co model duoi `-free` cua Orcarouter", models.contains("-free\""))
        assertTrue("Gemini van phai o trong bo chon", models.contains("AiModel.Provider.GEMINI"))
        assertTrue("Chon model phai luu lai tren may", models.contains("FIELD_SELECTED"))
        assertTrue("Goi dung model duoc chon, khong thu lan luot",
            models.contains("OpenRouterClient.chat(apiKey, model.id"))

        val chat = projectFile("src/main/java/com/example/service/ai/OpenRouterClient.kt").readText()
        assertTrue("Phai goi dung host Orcarouter, khong phai OpenRouter",
            chat.contains("https://www.orcarouter.ai/v1/chat/completions"))
        assertTrue("Cam goi nham host OpenRouter", !chat.contains("https://openrouter.ai/"))
        assertTrue("Key Orcarouter phai di bang header Bearer",
            chat.contains("setRequestProperty(\"Authorization\", \"Bearer \$apiKey\")"))
        assertTrue("Cam dua key vao URL", !chat.contains("orcarouter.ai/v1/chat/completions?"))
        // Model `-free` cua Orcarouter la model suy ly: chung an token vao
        // `reasoning_content` truoc khi tra `content`. max_tokens nho thi API
        // van 200 ma `content` rong = chat trang xoan.
        assertTrue("max_tokens phai du lon cho model suy ly",
            Regex("\"max_tokens\" to (\\d{4,})").containsMatchIn(chat))
        assertTrue("Phai phao khi noi dung trong",
            chat.contains("trả về nội dung trống") || chat.contains("bị cắt vì quá dài"))
        assertTrue("Phai chay tren IO thread", chat.contains("withContext(Dispatchers.IO)"))
        assertTrue("Cung mot khuon trinh bay cho moi model", chat.contains("AiPrompt.system(stats, report)"))
        assertTrue("Lich su Gemini phai doi sang ngon ngu OpenRouter",
            chat.contains("\"assistant\" else \"user\"") || chat.contains("to \"assistant\""))

        val screen = projectFile("src/main/java/com/example/ui/screens/AiChatScreen.kt").readText()
        assertTrue("Man hinh phai ve hang chon model", screen.contains("AiModels.all.forEach"))
        assertTrue("Man hinh phai goi dung model da chon", screen.contains("AiModels.chat("))
        assertTrue("Khuon nhap key phai doi theo provider cua model",
            screen.contains("AiModel.Provider.OPENROUTER"))
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

    /**
     * `AQ.` + 25 ky tu lien ke la dang khoa Google API moi. Moi dong
     * `..._API_KEY=<25+ ky tu>` deu bi soi, ke ca `OPENROUTER_API_KEY`: bat
     * theo TEN truong thi khong the dinh mot dang key moi, con bat theo chuoi
     * dau (`AIza`, `sk-or`) thi them mot loai la them mot loi de lu.
     * Chuoi chua `REPLACE_ME` la cho cho cua `.env.example`, khong phai key.
     */
    private fun looksLikeRealKey(text: String): Boolean {
        if (Regex("""AQ\.[A-Za-z0-9_\-]{25,}""").containsMatchIn(text)) return true
        return Regex("""[A-Z0-9_]*API_KEY\s*=\s*"?([A-Za-z0-9_.\-]{25,})""")
            .findAll(text)
            .map { it.groupValues[1] }
            .any { !it.contains("REPLACE_ME") }
    }

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

    /**
     * tren BlueStacks 1920x1080/240dpi: hang soan tin cua Chat nam 95..139 dp
     * tu day man hinh, qua cau SOS noi (padding 104 dp) nam 104..154 dp — hai
     * choong de len nhau. Cham nut "Gui" la mo bang cuu nan va tin nhan khong
     * bao gio di. Tab Chat phai nhac qua cau len cao hon hang soan tin.
     */
    @Test
    fun sosOrbDoesNotCoverTheChatComposer() {
        val shell = projectFile("src/main/java/com/example/ui/screens/MainAppScreen.kt").readText()
        assertTrue(
            "Tab Chat (3) phai dat qua cau SOS cao hon o soan tin nhan",
            shell.contains("bottom = if (selectedTab == 3) 152.dp else 104.dp")
        )
    }

    /**
     * (lỗi thật bắt được khi test 64.0 trên máy giả lập) Máy phụ huynh đặt luật
     * cho con xong bị chính bộ chặn của mình đá về Home với dòng "Hôm nay đã dùng
     * hết thời gian cho phép." AppBlockerService đọc nguyên `pairings/{id}/schedules`
     * mà không hỏi máy này đang ở VAI NAO của cặp ghép nối, trong khi mọi nội quy
     * trong app đều được viết theo giả định "áp lên máy con".
     *
     * Test giữ ba thứ một lúc:
     *  - cổng role phải đứng TRƯỚC cả nhánh "chưa ghép nối" (nếu đứng sau thì
     *    máy phụ huynh chưa ghép nối vẫn chạy hết vòng listener),
     *  - phải đọc cột `role` trần, không deserialize qua `toObject()` — vì
     *    Models.kt đặt mặc định `role = PARENT`, nên hồ sơ CHILD đời trước thiếu
     *    trường sẽ biến thành PARENT và bộ chặn tắt trên máy của con (fail-open),
     *  - tắt phải là tắt thật: gỡ cả hai listener và xóa cả lịch lẫn phiên học.
     */
    @Test
    fun blockerDoesNotPunishTheParentWhoSetTheRules() {
        val src = projectFile("src/main/java/com/example/service/blocker/AppBlockerService.kt").readText()
        assertTrue("Phải import UserRole để so vai", src.contains("import com.example.data.model.UserRole"))

        val listen = src.substringAfter("private fun listenSchedule()")
        assertTrue("Phải đọc vai tro bang cot phang", listen.contains("getString(\"role\")"))
        assertTrue(
            "Phải so voi UserRole.PARENT.name (cot luu ten enum, khong phai so chuoi tay)",
            listen.contains("role == UserRole.PARENT.name")
        )
        val gateAt = listen.indexOf("role == UserRole.PARENT.name")
        val pairingAt = listen.indexOf("pairingId.isEmpty()")
        assertTrue("Cổng role phải đứng trước nhánh chưa ghép nối", gateAt in 0 until pairingAt)
        assertTrue(
            "So bang ten enum, khong so string tay (đổi tên enum là đổi cả dữ liệu cũ)",
            !listen.contains("role == \"PARENT\"")
        )

        val parentBranch = listen.substringAfter("role == UserRole.PARENT.name")
            .substringBefore("return@addSnapshotListener")
        listOf(
            "scheduleReg?.remove()", "studyReg?.remove()",
            "schedule = null", "study = null",
            // Phai xa `watchedPairingId = null`, khong phai chi ngat listener: neu
            // gia tri cu con giu thi lan ghep noi sau se bi `pairingId != watchedPairingId`
            // chan, may khong bao gio doc lai duoc noi quy moi.
            "watchedPairingId = null"
        ).forEach {
            assertTrue("Nhanh PHU HUYNH phai co '$it'", parentBranch.contains(it))
        }
    }
}
