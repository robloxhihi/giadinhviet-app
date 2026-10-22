package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test tinh cho nguyen tac "khong duoc co du lieu mau o bat ky noi nao": moi
 * so lieu, nhan, duong biu va trang thai tren man hinh phai den tu GPS that,
 * UsageStats, Firestore hoac gia tri chinh app nay luu tren may. Chua co thi
 * phai in ra "chua co".
 *
 * Test doc thang nguon Kotlin (khong can thiet bi) vi moi thu trong lop nay la
 * mot loi tung xay ra va da duoc sua; chung quen de may sau dung tay xoa di.
 * comment duoc loai tru khi quet chuoi cam, vi comment da sua thi con ghi ten
 * du lieu cu de giai thich (vi du "Trường học" trong chu giai cua
 * LocationHistoryScreen).
 */
class NoDemoDataRegressionTest {

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    private fun readUtf8(file: File): String = String(file.readBytes(), Charsets.UTF_8)

    private val mainRoot get() = projectFile("src/main/java/com/example")

    private val mainSources: List<File>
        get() = mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * Cat `// ...` va `/* ... */` nhung KHONG cat ben trong chuoi: duong dan
     * tile mang `https://` nen mot ham cat tho (tim "//" bang tay) se an luon
     * ca dong ma chuoi "glitch.me" nam trong do, va test cam se xanh gian.
     */
    private fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        var inStr = false
        var inChar = false
        var inLine = false
        var inBlk = false
        while (i < src.length) {
            val c = src[i]
            val next = if (i + 1 < src.length) src[i + 1] else ' '
            when {
                inLine -> {
                    if (c == '\n') { inLine = false; out.append(c) }
                    i++
                }
                inBlk -> {
                    if (c == '*' && next == '/') { inBlk = false; i += 2 } else i++
                }
                inStr -> {
                    out.append(c)
                    if (c == '\\') { if (next != '\n') { out.append(next); i += 2 } else i++ }
                    else { if (c == '"') inStr = false; i++ }
                }
                inChar -> {
                    out.append(c)
                    if (c == '\\') { out.append(next); i += 2 }
                    else { if (c == '\'') inChar = false; i++ }
                }
                c == '/' && next == '/' -> { inLine = true; i += 2 }
                c == '/' && next == '*' -> { inBlk = true; i += 2 }
                c == '"' -> { inStr = true; out.append(c); i++ }
                c == '\'' -> { inChar = true; out.append(c); i++ }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** Noi dung dau tien ma nguon SACH comment chua trong toan bo `src/main`. */
    private fun firstOffender(forbidden: List<String>): List<String> =
        mainSources
            .map { it.name to stripComments(readUtf8(it)) }
            .filter { (_, code) -> forbidden.any { word -> code.contains(word) } }
            .map { it.first }
            .toList()

    // ── 1. Firestore: phai chi danh database CO THAT ───────────────────────

    /**
     * Du an Firebase nay KHONG co database `(default)`. Goi
     * `FirebaseFirestore.getInstance()` khong tham so tro toi `(default)` va
     * that bai LANG: listener khong bao gio chay, khong nem exception o cho goi,
     * nen bo chan app va den tin nhan khong bao gio nhan duc du lieu may chu.
     */
    @Test
    fun firestoreNeverTargetsTheMissingDefaultDatabase() {
        val noArg = firstOffender(listOf("FirebaseFirestore.getInstance()"))
        assertTrue("Nơi còn gọi getInstance() không tham số: $noArg", noArg.isEmpty())

        val gate = stripComments(readUtf8(projectFile("src/main/java/com/example/data/FirestoreDb.kt")))
        assertTrue("FirestoreDb phải trỏ đúng database có thật", gate.contains("getInstance(DATABASE_ID)"))
        assertTrue(
            "Id database phai nam o DUNG MOT noi",
            mainSources.count { readUtf8(it).contains("ai-studio-android-vespera-f0b8eaec") } == 1
        )
        assertTrue(
            "Không được còn bản sao id trong res/values",
            !File(projectFile("src/main"), "res/values/firebase_applet_config.xml").exists()
        )
    }

    // ── 2. Vùng an toàn: khong con danh sach biem ──────────────────────────

    /**
     * The "Vùng an toàn" tung ve ba vung co dinh (Trường học 300 m, Nhà riêng
     * 250 m, Nhà bà ngoại 400 m) kem cong tat/slider va nut "Thêm vùng an toàn
     * mới" — tat ca chi ton tai trong bo nho, khong luu, khong bao cao, nhu
     * vay la du lieu mau trong ung dung dinh bao "ko demo".
     */
    @Test
    fun safeZonesAreNotAPropsList() {
        val src = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/LocationHistoryScreen.kt")))
        listOf("safeZones", "safeZonesAreSample", "Trường học", "Nhà bà ngoại", "Vùng mới", "Thêm vùng an toàn mới")
            .forEach { word ->
                assertTrue("Vẫn còn dấu vết vùng an toàn mẫu: $word", !src.contains(word))
            }
        assertTrue("Phải nói thẳng là máy này chưa có vùng nào", src.contains("Chưa có vùng nào trên máy này"))
    }

    // ── 3. "Dùng hôm nay" lay tu UsageStats, khong tu hang so cu ───────────

    /**
     * `UsageSchedule.usedMinutes` khong ai ghi lai sau khi tao tai lieu, lay no
     * lam gia tri bo sung la in mot con so co dinh nhu so lieu hom nay.
     */
    @Test
    fun todayUsageNeverReadsTheFrozenScheduleField() {
        listOf("ScreenTimeScreen", "StudentMainScreen", "InsightsScreen").forEach { name ->
            val src = stripComments(
                readUtf8(projectFile("src/main/java/com/example/ui/screens/$name.kt"))
            )
            assertTrue("$name phải tính từ danh sách app đọc được", src.contains("todayUsedMinutes(uiState.detailedAppUsage)"))
            assertTrue("$name không được rơi lại vào usedMinutes có sẵn", !src.contains("sched?.usedMinutes"))
        }
    }

    // ── 4. Khong con demo host ben ngoai ──────────────────────────────────

    /**
     * SocketManager mac dinh ket noi `vespera-chat.glitch.me` — may chu demo
     * cua ben ba, trai voi loi hen "du lieu o lai trong nha".
     */
    @Test
    fun noThirdPartyDemoRelay() {
        val offenders = firstOffender(listOf("glitch.me", "SocketManager"))
        assertTrue("Vẫn còn nguồn nối tới host demo: $offenders", offenders.isEmpty())
        val gradle = readUtf8(projectFile("build.gradle.kts"))
        assertTrue("Không còn phụ thuộc socket.io client", !gradle.contains("socket.io"))
    }

    // ── 5. Che do khach/demo bi xoa het ───────────────────────────────────

    @Test
    fun guestDemoModeIsGone() {
        val offenders = firstOffender(
            listOf(
                "isGuestDemoMode", "enterGuestDemoMode", "switchDemoRole", "exitDemoMode",
                "Demo:", "(Demo)", "demo_msg_", "demo_extra_", "demo_privacy_"
            )
        )
        assertTrue("Vẫn còn nhánh demo: $offenders", offenders.isEmpty())
        listOf("ui/screens/SignInScreen.kt", "MainActivity.kt").forEach { rel ->
            val src = readUtf8(projectFile("src/main/java/com/example/${rel}"))
            assertTrue("$rel không còn tham số onEnterDemo", !src.contains("onEnterDemo"))
        }
    }

    // ── 6. Chạm tay phai di qua may chu ───────────────────────────────────

    /**
     * Nut "Zenly Bump" tung chi rung MAY DANG BAM va tang bo dem trong bo nho
     * trong khi nhan ghi "Rung chuông". Nay phai ghi `lastBumpAt` vao
     * `pairings/{id}` va ben kia rung khi listener keo ve.
     */
    @Test
    fun bumpGoesThroughThePairedDocument() {
        val repo = stripComments(readUtf8(projectFile("src/main/java/com/example/data/repository/VesperaRepository.kt")))
        assertTrue("Repository phải ghi bump", repo.contains("suspend fun sendBump"))
        assertTrue("Phải ghi đúng trường lastBumpAt", repo.contains("\"lastBumpAt\" to atMillis"))

        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        assertTrue("ViewModel phải gọi bản ghi", vm.contains("repository.sendBump("))
        assertTrue("Phải rung khi nhận snapshot mới", vm.contains("applyBumpFromServer(pairing)"))
        assertTrue("Không được còn bộ đếm đếm tại chỗ", !vm.contains("lastBumpCount"))
        assertTrue("Không rung khi chưa ghép nối", vm.contains("Chưa ghép nối với máy nào nên không có ai để chạm tay"))

        val screens = listOf("LocationScreen", "DashboardScreen").joinToString(" ") {
            stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/$it.kt")))
        }
        assertTrue("Nhãn phải lấy thời gian thật", screens.contains("bumpStateLabel("))
    }

    // ── 7. Khong tao tai khoan ao ─────────────────────────────────────────

    /**
     * Nhanh catch cua dang nhap Google tung ty sinh uid `user_<ngau nhien>`,
     * email `phuhuynh@giadinhviet.vn` roi danh dau `isAuthenticated = true`,
     * tao ra mot ho so khong ai so duoc ma van ghi len `users/`.
     */
    @Test
    fun signInNeverInventsAnAccount() {
        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        assertTrue("Không được bịa uid dự phòng", !vm.contains("val fallbackUid"))
        assertTrue("Không được bịa id 'user_local'", !vm.contains("\"user_local\""))
        assertTrue("Hồ sơ tại chỗ phải lấy uid thật hoặc để trống", vm.contains("id = auth.currentUser?.uid.orEmpty()"))

        val repo = stripComments(readUtf8(projectFile("src/main/java/com/example/data/repository/VesperaRepository.kt")))
        assertTrue("Phải từ chối ghi khi không có uid", repo.contains("if (uid.isEmpty())"))
        assertTrue("Không còn hàm sinh id khách", !repo.contains("fun requireUserId"))
    }

    // ── 8. "Lời khuyên AI" dinh khong duoc con ────────────────────────────

    /**
     * `updateSmartAdvice` tra mot doan van co dinh ("Buổi chiều nay tiến độ học
     * tập ổn định") theo ngon cam xuc duoc chon — khong doc GPS, khong doc
     * UsageStats, khong goi Gemini: do la noi dung bịa, va khong man hinh nao
     * dung den no.
     */
    @Test
    fun cannedAdvisorTextIsDeleted() {
        val offenders = firstOffender(listOf("smartAdvice", "Cố vấn Vespera", "updateSmartAdvice"))
        assertTrue("Vẫn còn lời khuyên bịa sẵn: $offenders", offenders.isEmpty())
    }

    // ── 9. Ban do phuc vu nhu Google Maps ─────────────────────────────────

    /**
     * Dieu khong (zoom, dinh vi, lop, toan man hinh) phai nam NGAY TREN map
     * theo bo cuc Google Maps; nut lop mo bang chon THEO TEN chu khong phai
     * bam vong; va "toan man hinh" phai dung het chieu cao man hinh that.
     */
    @Test
    fun mapControlsFloatOnTheMapLikeGoogleMaps() {
        val src = stripComments(
            readUtf8(projectFile("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt"))
        )
        assertTrue("Cụm zoom phải nổi trên map", src.contains(".align(Alignment.CenterEnd)"))
        assertTrue("Nút định vị phải nổi trên map", src.contains(".align(Alignment.BottomEnd)"))
        assertTrue("Chip lớp phải nổi trên map", src.contains(".align(Alignment.BottomStart)"))
        assertTrue("Phải có bảng chọn lớp theo tên", src.contains("testTag(\"map_layer_picker\")"))
        assertTrue("Bảng chọn lớp phải liệt kê từng lớp", src.contains("MapLayer.entries.forEachIndexed"))
        assertTrue("Toàn màn hình phải theo chiều cao máy", src.contains("LocalConfiguration.current.screenHeightDp"))
        assertTrue("Không còn chiều cao 560.dp cố định", !src.contains("560.dp"))
        assertTrue("Nút lớp không còn là nút xoay vòng dưới map", !src.contains("MapControlButton(Icons.Default.Layers"))
        assertTrue("Đổi lớp phải cập nhật trần zoom của lớp mới", src.contains("applyLayer"))
        // Tran scale bar phai day len de chip lop khong de len no.
        assertTrue("Scale bar phải né chip lớp", src.contains("setScaleBarOffset(20, 96)"))
    }

    // ── 10. Hạn mức phút: chưa đặt là chưa đặt ─────────────────────────────

    /**
     * `?: 120` bien mot gia dinh CHUA DAT han muc nao thanh "dùng 40/120 phút",
     * va con day 120 vao prompt cua Gemini nhu the do la quy dinh cua nha. Nguoi
     * dung nhin thay mot con so ma khong ai tung nhap.
     */
    @Test
    fun dailyQuotaIsNeverInvented() {
        val offenders = firstOffender(listOf("?: 120"))
        assertTrue("Vẫn còn hạn mức bịa 120 phút: $offenders", offenders.isEmpty())

        val ai = stripComments(readUtf8(projectFile("src/main/java/com/example/data/ai/DailyUsageStore.kt")))
        assertTrue("Prompt AI phải nói rõ là chưa đặt hạn mức", ai.contains("chưa đặt hạn mức"))
    }

    // ── 11. Mã học sinh phải là mã do máy chủ cấp ──────────────────────────

    /**
     * The hien `HS-CONNECT` khi ho so trong nuoc co studentCode rong khien nguoi
     * dung tuong do la ma that cua con minh va di nhap ma ay ben may phu huynh.
     */
    @Test
    fun studentCodeIsNeverHardcoded() {
        val offenders = firstOffender(listOf("HS-CONNECT"))
        assertTrue("Vẫn còn mã học sinh bịa sẵn: $offenders", offenders.isEmpty())

        listOf("DashboardScreen", "StudentMainScreen").forEach { name ->
            val src = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/$name.kt")))
            assertTrue("$name phải lấy mã từ hồ sơ và chấp nhận rỗng", src.contains("userProfile?.studentCode.orEmpty()"))
            assertTrue("$name phải nói thẳng khi chưa có mã", src.contains("chưa có mã"))
        }
    }

    // ── 12. Dia diem va moc thoi gian khong duoc bea ───────────────────────

    /**
     * `placeName = "Điểm dừng chân"` khien mot tai lieu thieu ten van hien ra
     * nhu mot dia diem da dich duoc; `year = 2026, month = 10, day = 12` dong
     * bang ngay cua lok demo vao moi tai lieu duoc tao tren may khac.
     */
    @Test
    fun placeNamesAndDatesComeFromTheFix() {
        val offenders = firstOffender(listOf("Điểm dừng chân"))
        assertTrue("Vẫn còn tên địa điểm bịa sẵn: $offenders", offenders.isEmpty())

        val models = stripComments(readUtf8(projectFile("src/main/java/com/example/data/model/Models.kt")))
        assertTrue("Địa điểm mặc định phải để trống", models.contains("val placeName: String = \"\""))
        listOf("val year: Int = 0", "val month: Int = 0", "val day: Int = 0").forEach {
            assertTrue("Models còn ngày tháng đông cứng: $it", models.contains(it))
        }

        val history = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/LocationHistoryScreen.kt")))
        assertTrue("Dòng lịch sử phải nói khi chỉ có tọa độ", history.contains("Không rõ địa điểm"))
        val places = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/LocationScreen.kt")))
        assertTrue("Dòng vị trí phải nói khi chưa dịch được tên", places.contains("Không rõ địa điểm"))
    }

    // ── 13. Khung giờ ngoài giờ: cau hinh that, duyet that ─────────────────

    /**
     * Bon truong `allowedStartHour/Minute`, `allowedEndHour/Minute` ton tai trong
     * Firestore va BlockPolicy nhung KHONG man hinh nao ghi duoc vao — mot lop
     * chan chay ma khong ai dat duoc. Nay chung di qua cung mot phieu duyet nhu
     * danh sach app, nen ben kia van nhin thay va dong y duoc.
     */
    @Test
    fun outsideHoursWindowIsEditableAndConsented() {
        val models = stripComments(readUtf8(projectFile("src/main/java/com/example/data/model/Models.kt")))
        listOf(
            "fun UsageSchedule.windowCode()",
            "fun parseWindowCode",
            "fun windowCodeFrom",
            "fun UsageSchedule.withWindowCode",
            "fun windowDisplay",
            "val pendingAllowedWindow: String"
        ).forEach { assertTrue("Models thiếu: $it", models.contains(it)) }
        assertTrue("Không còn hasTimeWindow() bỏ trống", !models.contains("hasTimeWindow"))

        val consent = stripComments(readUtf8(projectFile("src/main/java/com/example/service/blocker/ConsentPolicy.kt")))
        assertTrue("Phiếu đề xuất phải mang theo khung giờ", consent.contains("pendingAllowedWindow = windowCode"))
        assertTrue("Duyệt phải áp đúng khung giờ của phiếu", consent.contains("kept.withWindowCode(base.pendingAllowedWindow)"))
        assertTrue("So sánh bản phải gồm cả khung giờ", consent.contains("s.windowCode() == windowCode"))

        val card = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/components/BlockRulesCard.kt")))
        listOf("WindowEditor", "TimeEndpointRow", "windowSummary", "draftWindowCode").forEach {
            assertTrue("Bảng chặn thiếu: $it", card.contains(it))
        }
        assertTrue("Phải hiển thị khung giờ đang chờ duyệt", card.contains("schedule?.pendingAllowedWindow"))

        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        assertTrue("ViewModel phải truyền khung giờ xuống đề xuất", vm.contains("windowCode = windowCode ?: base.windowCode()"))
    }

    // ── 14. Trang thai va avatar khong duoc do truoc ──────────────────────

    /**
     * Cham "online" luc nao cung mau ngoc luc va avatar luc nao cung chu "H"
     * ("Học sinh") — ca hai la trang thai khong do may chu ma ra.
     */
    @Test
    fun presenceAndAvatarDoNotClaim() {
        val src = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/StudentMainScreen.kt")))
        assertTrue("Chấm hiện diện phải đọc trạng thái thật", src.contains("uiState.isOnline"))
        assertTrue("Chưa biết thì avatar để trống, không viết chữ H", !src.contains("\"H\""))
        assertTrue("Avatar phải lấy chữ cái đầu của tên thật", src.contains("displayName?.trim()?.firstOrNull()"))
    }

    // ── 15. Mau nhan nhanh khong kheng dinh vi tri ────────────────────────

    /**
     * "Con đã về đến nhà an toàn!" va "Con đang ở trường học" la nhung cai
     * ten ma app khong kiem chung duoc: bam nut la gui mot loi noi co the sai.
     */
    @Test
    fun quickPresetsDoNotAssertLocations() {
        val chat = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/ChatScreen.kt")))
        listOf("Con đã về đến nhà an toàn!", "Con đang ở trường học").forEach {
            assertTrue("Mẫu chat vẫn khẳng định vị trí không kiểm chứng được: $it", !chat.contains(it))
        }
    }

    // ── 16. Card truong biet va store khong dung den ──────────────────────

    /**
     * BentoRadarCard ve mot "radar" bang so lieu ngau nhien,
     * LiveBackgroundTelemetryCard ghi "Đồng bộ liên tục" trong khi khong co
     * dong bo nao, con StudyProgressStore khong man hinh nao goi den.
     */
    @Test
    fun deadShowcaseCardsAreDeleted() {
        val bento = readUtf8(projectFile("src/main/java/com/example/ui/components/BentoCards.kt"))
        listOf("BentoRadarCard", "MetricChip", "LiveBackgroundTelemetryCard", "Đồng bộ liên tục").forEach {
            assertTrue("BentoCards vẫn còn $it", !bento.contains(it))
        }
        assertTrue("Store không màn hình nào dùng phải bị xoá", !File(mainRoot, "data/study/StudyProgressStore.kt").exists())
    }

    // ── 17. Xich cua bieu do va mac dinh phieu khong duoc boi len ─────────

    /**
     * Bon thu tung lam bang so lieu "that" ma khong phai: dinh 160 phut tu vong
     * cho bieu do ca tuan (khong gia dinh nao dat, khong sensor nao do), san 5%
     * cho nhom 0 phut (ve gach mau nhu co dung), "0 km/h" khi chua co diem dinh
     * vi nao, va cac mac dinh 30/120 phut + 14:00-16:00 trong Models — mot phieu
     * thieu du lieu doc thanh con so va khung gio nguoi ta khong tung nhap.
     */
    @Test
    fun chartScalesAndRequestDefaultsAreNotInvented() {
        val screen = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/ScreenTimeScreen.kt")))
        assertTrue("Biểu đồ tuần vẫn dùng trần 160 phút bịa sẵn", !screen.contains("160f"))
        assertTrue("Trần biểu đồ phải lấy từ chính dữ liệu tuần",
            screen.contains("uiState.weeklyUsage.maxOfOrNull { it.second }"))
        assertTrue("Nhóm 0 phút phải vẽ thanh rỗng, không tô 5%",
            screen.contains("if (minutes <= 0) 0f else percentage.coerceIn(0f, 1f)"))
        assertTrue("Chỉ hiện nhóm có phút thật", screen.contains("usageCategories.filter { it.minutes > 0 }"))
        assertTrue("Không có phút nào phải nói thẳng", screen.contains("chưa ghi nhận phút dùng nào"))

        val history = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/LocationHistoryScreen.kt")))
        assertTrue("Tốc độ max phải để trống khi chưa có điểm định vị",
            history.contains("if (maxSpeed > 0f) \"${'$'}{maxSpeed.toInt()} km/h\" else \"—\""))

        val models = stripComments(readUtf8(projectFile("src/main/java/com/example/data/model/Models.kt")))
        listOf(
            "val minutesRequested: Int = 0",
            "val requestedMinutes: Int = 0",
            "val startHour: Int = -1",
            "val endHour: Int = -1"
        ).forEach { assertTrue("Models còn giá trị bịa sẵn, thiếu: $it", models.contains(it)) }
        listOf("val minutesRequested: Int = 30", "val requestedMinutes: Int = 120", "val startHour: Int = 14")
            .forEach { assertTrue("Models vẫn còn $it", !models.contains(it)) }

        val dashboard = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/DashboardScreen.kt")))
        assertTrue("Phiếu không có khung giờ phải nói rõ", dashboard.contains("chưa nêu khung giờ cụ thể"))

        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        assertTrue("Duyệt phiếu không có số phút phải từ chối đổi hạn mức",
            vm.contains("Phiếu thỏa thuận không có số phút nên chưa đổi hạn mức."))

        val consent = stripComments(readUtf8(projectFile("src/main/java/com/example/service/blocker/ConsentPolicy.kt")))
        assertTrue("Nhãn vai trò phải trung tính, không đoán giới tính người dùng",
            consent.contains("UserRole.PARENT -> \"Phụ huynh\""))
        val gendered = firstOffender(listOf("\"Mẹ\""))
        assertTrue("Vẫn còn nơi gọi người dùng là \"Mẹ\": $gendered", gendered.isEmpty())
    }

    // ── 18. Bao cao nen tang: chua doc duoc la chua doc duoc ──────────────

    /**
     * `batteryPct = else 85` va "Ứng dụng: 12ph" trong thong bao dient ra da
     * duoc ghi len Firestore va hien trong tin nhan SOS — nguoi doc tuong pin
     * that cua may con. Nay `-1`/chuoi rong phai chay thang ra chuoi "chưa đọc
     * được", va danh thiem song chi sang mau khi du lieu duoi 5 phut.
     */
    @Test
    fun telemetryFallbacksSayUnknown() {
        val helper = stripComments(readUtf8(projectFile("src/main/java/com/example/service/TelemetryAndUsageHelper.kt")))
        assertTrue("Pin phải về -1 khi đọc không được",
            helper.contains("if (level >= 0 && scale > 0) (level * 100 / scale) else -1"))
        assertTrue("Không còn phần trăm bịa 85", !helper.contains("else 85"))

        val telemetry = stripComments(readUtf8(projectFile("src/main/java/com/example/service/VesperaTelemetryService.kt")))
        assertTrue("Thông báo phải nói khi chưa đọc được pin", telemetry.contains("chưa đọc được"))
        assertTrue("Ứng dụng nổi bật phải lấy từ danh sách thật hoặc để trống",
            telemetry.contains(".firstOrNull()?.appName.orEmpty()"))

        val bento = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/components/BentoCards.kt")))
        assertTrue("Đèn danh thiếp phải phân biệt dữ liệu live", bento.contains("isLive: Boolean"))
        assertTrue("Pin chưa đọc được hiển thị là Pin ?", bento.contains("\"Pin ?\""))

        val screen = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/ScreenTimeScreen.kt")))
        assertTrue("Danh thiếp phải nói rõ dữ liệu còn mới", screen.contains("isLive = partnerLoc.updatedAt > 0L"))
    }

    // ── 19. Ket luan cua mang/GPS khong duoc bi xoa khi dang xuat ───────────

    /**
     * `NetworkMonitor` chi phat mot lan khi gia tri THAY DOI. tren BlueStacks
     * mang da "Online" tu truoc luc app mo, con lan goi dau tien cua
     * `addAuthStateListener` (Firebase 24.x) lai den TREM cac collector trong
     * `init` — nen mot `VesperaUiState()` tron ven o nhanh dang xuat xoa mat
     * ket luan mang VINH VIEN: khung chat bao "Chưa kiểm tra được kết nối mạng"
     * tren may van co mang, khong bao gio tu hoi lai. Reset phai copy.
     */
    @Test
    fun logoutResetKeepsMonitorVerdicts() {
        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        assertTrue("Phải giữ kết luận mạng khi đăng xuất", vm.contains("isOnline = keep.isOnline"))
        assertTrue("Phải giữ kết luận VPN khi đăng xuất", vm.contains("isVpnActive = keep.isVpnActive"))
        assertTrue("Phải giữ kết luận dịch vụ định vị",
            vm.contains("isLocationServicesEnabled = keep.isLocationServicesEnabled"))
        assertTrue("Phải giữ các quyền đã đọc", vm.contains("permissionSnapshot = keep.permissionSnapshot"))
        assertTrue("Không còn dựng UiState trắng ở nhánh đăng xuất",
            !vm.contains("VesperaUiState(isAuthenticated = false)"))
    }

    /**
     * O nhap key AI la noi dan chuoi mo duoc tien. Hien ro key thi khong kiem
     * tra duoc nhung bi quay man hinh/doi mat ke ben thu duoc; mac dinh phai
     * che va co nut bo che khi nguoi dung muon xem lai.
     */
    @Test
    fun aiKeyFieldMasksTheKeyByDefault() {
        val chat = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/screens/AiChatScreen.kt")))
        assertTrue("Khoá AI phải nhập dạng che", chat.contains("PasswordVisualTransformation()"))
        assertTrue("Phải có nút hiện/ẩn khoá", chat.contains("var keyVisible by remember { mutableStateOf(false) }"))
    }

    /**
     * O dang nhap hoi TEN tai khoan, nguoi dung hay dan ca email da dang ky.
     * Noi thuong ra `me@giadinhviet.vn@giadinhviet.vn` va Firebase bao "The
     * email address is badly formatted" — loi do khong he noi sai o dau. Hai
     * duong dang nhap/dang ky phai qua cung mot ham gop duoi bi.
     */
    @Test
    fun usernameEmailSuffixIsNeverDoubled() {
        val vm = stripComments(readUtf8(projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")))
        val bad = Regex.escape("val email = \"\$username@giadinhviet.vn\"")
        val joined = Regex(bad).findAll(vm).count()
        assertTrue("Còn chỗ tự nối đuôi email mà không kiểm tra: $joined", joined == 0)
        val shared = Regex("val email = emailForUsername\\(username\\)").findAll(vm).count()
        assertTrue("Cả đăng nhập lẫn đăng ký phải dùng chung một hàm ghép", shared == 2)
        assertTrue("Phải gỡ đuôi thừa trước khi nối", vm.contains("name.endsWith(EMAIL_SUFFIX, ignoreCase = true)"))
    }
}
