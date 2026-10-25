package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho lô đổi luồng ghép nối (bản 59.0 và 60.0), viết sau khi đọc lại
 * toàn bộ đường đi của một phép ghép chứ không đoán từ màn hình.
 *
 * Năm điều xác minh được trong code, mỗi điều tương ứng với một test dưới
 * đây:
 *
 * 1. **Chỉ có một đường ghép thật.** Phụ huynh gõ mã ở thẻ "Ghép nối thiết bị
 *    học sinh" -> `pairDirectly` ghi `pairings/pair_<parentId>_<childId>` ->
 *    máy con tự nhận qua `watchPairingArrival`, mỗi 20 giây một lần.
 * 2. **Nhánh "bên kia phải duyệt" là code chết, không phải tính năng.**
 *    `createPairingRequest` không có chỗ nào gọi, nên không một dòng nào được
 *    ghi vào `pairing_requests`; khối "YÊU CẦU GHÉP NỐI" với nút Đồng ý/Từ chối
 *    ở máy con vì thế không bao giờ hiện. Giữ lại là giữ một lời hứa app không
 *    thể thực hiện — và về nguyên tắc, con không nên phải duyệt việc mình bị
 *    theo dõi (quyết định tương tự đã áp cho nội quy từ bản 40.0, xem
 *    ConsentPolicy).
 * 3. **`verificationPin` là số bịa.** App từng sinh PIN và toast "PIN: 1234"
 *    mà không một dòng code nào đem đối chiếu.
 * 4. **Hình dạng mã hay bị nói sai.** Mã thật là `HS-` + 8 ký tự, còn ô nhập
 *    cũ ghi "vd: HS-XXXXX" (5 chữ X), khiến phụ huynh nhìn máy con và tưởng mã
 *    lỗi.
 * 5. **(bản 60.0) Chưa ghép nối thì màn hình chỉ được nói về việc ghép nối.**
 *    Trước đó máy phụ huynh chưa ghép vẫn bày thêm bốn thẻ "chờ kết nối", máy
 *    con bày thêm thẻ GPS/PIN, và ba tab Vị trí / Giờ dùng / Chat mở ra màn
 *    trống vì chưa có ai ở đầu kia.
 *
 * Test quét tệp nguồn theo kiểu FirestoreKeyRegressionTest: so literal và hằng
 * số, không dựng Compose.
 */
class PairingFlowRegressionTest {

    private val mainRoot: File = listOf(
        File("app/src/main/java/com/example"),
        File("src/main/java/com/example")
    ).firstOrNull { it.isDirectory }
        ?: error("Không tìm thấy thư mục nguồn")

    private fun read(rel: String): String {
        val direct = File(mainRoot, rel)
        assertTrue("Không tìm thấy tệp nguồn: $rel", direct.exists())
        return direct.readText()
    }

    /**
     * Bo qua comment. Ly do va ten `pairing_requests` duoc nhac lai rat nhieu
     * lan trong phan giai thich; doc ca comment thi test xanh ngay khi code
     * chep quay lai.
     */
    private fun stripComments(src: String): String =
        src.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .let { Regex("/\\*[\\s\\S]*?\\*/").replace(it, " ") }

    /** Toan bo ma Kotlin cua app, da cat comment, kem duong dan tuong doi. */
    private fun allMainSources(): List<Pair<String, String>> =
        mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(mainRoot).invariantSeparatorsPath to stripComments(it.readText()) }
            .toList()

    /** Than cua `fun <header>(...)` — dem ngoac hoa thi, khong lan sang ham ke tiep. */
    private fun funBody(src: String, header: String): String {
        val start = src.indexOf(header)
        assertTrue("Thiếu $header — ai đó đã đổi tên hàm mà không sửa test", start >= 0)
        val open = src.indexOf('{', start)
        assertTrue("$header không có thân hàm", open >= 0)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(start, i + 1)
                }
            }
        }
        error("Thân của $header không đóng ngoặc")
    }

    /** Mo rong `X-Y` trong nh ky tu thanh day so; ky tu don giu nguyen. */
    private fun expandCharClass(body: String): Set<Char> {
        val out = mutableSetOf<Char>()
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (i + 2 < body.length && body[i + 1] == '-' && body[i + 2] != ']') {
                for (x in c..body[i + 2]) out.add(x)
                i += 3
            } else {
                out.add(c)
                i++
            }
        }
        return out
    }

    /**
     * Cat dung doan nam giua `header` (tinh ca dau `{` mo cuoi cua no) va dau
     * `}` dong nguyen block do — dem ngoac hoa nen khong lan sang phan ke tiep.
     * Dung de hoi "bên trong nhanh CHUA ghep noi co nhung the nao", vi do la
     * cau hoi ve vung nguon, khong phai ve chuoi toan cuc.
     */
    private fun blockOf(src: String, header: String): String {
        val start = src.indexOf(header)
        assertTrue("Thiếu `$header` — cấu trúc màn hình đã đổi mà không sửa test", start >= 0)
        val open = src.indexOf('{', start)
        assertTrue("$header không có thân block", open >= 0)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return src.substring(open, i + 1)
                }
            }
        }
        error("Block của $header không đóng ngoặc")
    }

    private fun countOf(regex: Regex, text: String): Int = regex.findAll(text).count()

    // ── 1. Bên con chỉ hiện mã ────────────────────────────────────────────

    @Test
    fun mayHocSinhKhongCoONhapMa() {
        val student = stripComments(read("ui/screens/StudentMainScreen.kt"))
        assertFalse("Máy học sinh phải chỉ HIỆN mã. Có OutlinedTextField nghĩa là đường " +
                "nhập mã đã quay lại phía con",
            student.contains("OutlinedTextField(")
        )
        // Chu "Đồng ý" duoc phep xuat hien trong cau giai thich ("không cần bấm
        // Đồng ý"), nhung khong duoc phep la NUT BAM.
        assertFalse("Máy con không còn nút Đồng ý", student.contains("Text(\"Đồng ý\")"))
        assertFalse("Máy con không còn nút Từ chối", student.contains("Text(\"Từ chối\")"))
        assertFalse("Máy con không còn hàng đợi yêu cầu ghép nối", student.contains("YÊU CẦU GHÉP NỐI"))
        assertTrue(
            "Phải nói rõ máy con tự nhận liên kết trong ~20 giây, không bắt mở lại app",
            student.contains("20 giây")
        )
        assertTrue(
            "Vẫn phải lấy mã từ hồ sơ, không có mã giả",
            student.contains("userProfile?.studentCode.orEmpty()")
        )
        assertTrue("Thẻ mã phải có testTag cho uiautomator", student.contains("testTag = \"student_code_card\""))
    }

    @Test
    fun caAppChiConMotONhapMaCuaPhuHuanh() {
        // Chi mot o nhap ma trong toan bo app, va no nam o man Tong quan cua
        // phu huynh (DashboardScreen). Cac o nhap dang nhap/ten o SignInScreen
        // khong tinh vi chung khong phai duong ghep noi.
        val dashboard = stripComments(read("ui/screens/DashboardScreen.kt"))
        assertEquals(
            "Màn Tổng quan phải còn đúng một ô nhập mã",
            1,
            Regex("OutlinedTextField\\(").findAll(dashboard).count()
        )
        assertTrue("Ô nhập phải gọi onPairDirectly", dashboard.contains("onPairDirectly(typedCode)"))
        assertTrue("Ô nhập phải có testTag", dashboard.contains("testTag(\"student_code_input\")"))
        val screensWithCodeField = allMainSources()
            .filter { (_, src) -> src.contains("student_code_input") }
            .map { (path, _) -> path }
            .toSet()
        assertEquals(
            "Ô nhập mã ghép nối phải chỉ tồn tại ở màn Tổng quan của phụ huynh",
            setOf("ui/screens/DashboardScreen.kt"),
            screensWithCodeField
        )
        val pairingCallers = allMainSources()
            .filter { (path, _) -> path.startsWith("ui/screens/") }
            .filter { (_, src) -> src.lowercase().contains("pairdirectly") }
            .map { (path, _) -> path }
            .toSet()
        assertEquals(
            "Ghép nối chỉ được gọi từ thẻ của phụ huynh và cầu nối MainAppScreen",
            setOf("ui/screens/DashboardScreen.kt", "ui/screens/MainAppScreen.kt"),
            pairingCallers
        )
    }

    // ── 2. Nhánh "phải bấm Đồng ý" bị cắt tận gốc ─────────────────────────

    @Test
    fun khongConMotDongNaoNhoToiYeuCauGhepNoi() {
        val banned = listOf(
            "pairing_requests",
            "PairingRequest",
            "pendingRequests",
            "createPairingRequest",
            "acceptPairingRequest",
            "rejectPairingRequest",
            "observePendingRequestsForChild",
            "onAcceptPairing",
            "onRejectPairing"
        )
        val hits = allMainSources().flatMap { (name, src) ->
            banned.filter { src.contains(it) }.map { "$name -> $it" }
        }
        assertTrue("Nhánh xin-phép-ghép-nối còn ở: " + hits.joinToString(), hits.isEmpty())
    }

    @Test
    fun khongConSinhVaHienSoPinKhongAiDoiChieu() {
        val banned = listOf(
            "getOrGenerateVerificationPin",
            "regenerateVerificationPin",
            "regenerateDeviceStudentCode",
            "regenerateStudentCode",
            "PIN:"
        )
        val hits = allMainSources().flatMap { (name, src) ->
            banned.filter { src.contains(it) }.map { "$name -> $it" }
        }
        assertTrue("Số PIN không luồng nào đối chiếu vẫn còn ở: " + hits.joinToString(), hits.isEmpty())
        // Truong `verificationPin` DUOC GIU lai trong UserProfile: document cua
        // tai khoan cu van mang khoa do, xoa khai bao se in "No setter/field for
        // verificationPin" ra logcat — dung loai canh bao ban 58.0 da mat cong
        // doi de sach. Ke cu the la xoa, chi la khong ai dung den no.
        assertTrue(
            "UserProfile phải còn khai báo verificationPin (chỉ để đọc tài liệu cũ)",
            read("data/model/Models.kt").contains("val verificationPin: String = \"\"")
        )
    }

    // ── 3. Chặn trên máy trước khi tốn một lượt đọc Firestore ─────────────

    @Test
    fun ghepNoiChanNguoiSaiVaiTroRoiMoiChanMaSai() {
        val vm = stripComments(read("ui/viewmodel/VesperaViewModel.kt"))
        val body = funBody(vm, "fun pairDirectly(studentCode: String)")
        assertTrue("Chưa đăng nhập thì phải im lặng từ chối", body.contains("_uiState.value.userProfile ?: return"))
        assertTrue("Máy đang vai trò Học sinh không được xin ghép", body.contains("parent.role == UserRole.CHILD"))
        assertTrue("pairDirectly phải kiểm tra hình dạng mã qua repository", body.contains("repository.isPlausibleStudentCode(cleanCode)"))
        assertTrue(
            "Chặn vai trò phải đứng TRƯỚC vòng gọi máy chủ",
            body.indexOf("parent.role == UserRole.CHILD") < body.indexOf("repository.pairDirectly(cleanCode, parent)")
        )
        assertTrue(
            "Chặn mã sai phải đứng TRƯỚC vòng gọi máy chủ",
            body.indexOf("repository.isPlausibleStudentCode") < body.indexOf("repository.pairDirectly(cleanCode, parent)")
        )
        assertTrue("Phải có đường nói cho người dùng biết mã sai dạng", body.contains("studentCodeExample"))
    }

    @Test
    fun phuHuongKhongGhiHoCuaCon() {
        // Bay cu: ghi "ho ho" ho so cua ben kia thi rules tu choi
        // PERMISSION_DENIED va hai ben lech nhau (may me "Đã kết nối", may con
        // "chưa ghép nối"). Phep ghep chi duoc ghi: document ghep noi + ho so
        // cua chinh may dang bam.
        val repo = stripComments(read("data/repository/VesperaRepository.kt"))
        val body = funBody(repo, "suspend fun pairDirectly(")
        assertTrue("Phải ghi hồ sơ của CHÍNH máy phụ huynh", body.contains("db.collection(\"users\").document(parentProfile.id)"))
        assertFalse("Không được ghi vào hồ sơ của con", body.contains("document(student.id)"))
        // Ma chi doc duoc neu tai khoan mang vai tro HOC SINH: phu huynh khong
        // the lay ma cua mot phu huynh khac ghep vao.
        assertTrue(
            "findStudentByCode phải lọc theo vai trò CHILD",
            funBody(repo, "suspend fun findStudentByCode(").contains("whereEqualTo(\"role\", UserRole.CHILD.name)")
        )
    }

    @Test
    fun chuThichKhongConHuaHenSaiVeMayCon() {
        val vm = stripComments(read("ui/viewmodel/VesperaViewModel.kt"))
        val body = funBody(vm, "fun pairDirectly(studentCode: String)")
        assertFalse(
            "Câu cũ bảo con chỉ nhận được ở lần mở app kế tiếp — sai: app đang mở " +
                "thì khoảng 20 giây là liên kết về máy",
            body.contains("ngay lần mở app tiếp theo")
        )
        assertTrue("Phải nói đúng: mở app thì ~20 giây, tắt thì lần mở sau", body.contains("đang mở app"))
    }

    // ── 4. Hình dạng mã: một nguồn duy nhất ───────────────────────────────

    @Test
    fun chuoiHuongDanVaThuocDoDinhDangKhongTheLacNhau() {
        val repo = stripComments(read("data/repository/VesperaRepository.kt"))
        val alphabet = Regex("""STUDENT_CODE_ALPHABET = "([^"]+)"""")
            .find(repo)?.groupValues?.get(1)
            ?: error("Không thấy STUDENT_CODE_ALPHABET — chỗ sinh mã đã đổi tên")
        val length = Regex("""STUDENT_CODE_LENGTH = (\d+)""")
            .find(repo)?.groupValues?.get(1)?.toInt()
            ?: error("Không thấy STUDENT_CODE_LENGTH")
        assertTrue(
            "Bộ ký tự phải đủ dài để sinh mã " + length + " ký tự, hiện có " + alphabet.length,
            alphabet.length >= length
        )

        // Gợi ý UI viet bang `Regex("^HS-[...]{n}$")` trong DashboardScreen.
        val dashboard = stripComments(read("ui/screens/DashboardScreen.kt"))
        val ui = Regex("""Regex\("\^HS-\[([^\]]+)\]\{(\d+)\}""").find(dashboard)
            ?: error("Ô nhập của phụ huynh không còn kiểm tra hình dạng mã ngay trên máy")
        assertEquals(
            "Độ dài trong gợi ý UI phải bằng độ dài mã thật ($length)",
            length,
            ui.groupValues[2].toInt()
        )
        assertEquals(
            "Bộ ký tự UI liệt kê phải đúng bằng bảng ký tự sinh mã",
            alphabet.toSet(),
            expandCharClass(ui.groupValues[1])
        )
        assertTrue(
            "Repository phải là nơi quyết định, qua studentCodePattern",
            repo.contains("val studentCodePattern: Regex") &&
                repo.contains("studentCodePattern.matches(raw.trim().uppercase())")
        )
        // Chu giai o nhap khong duoc noi do dai khac 8.
        assertTrue(
            "Nhãn ô nhập phải ví dụ đúng hình dạng mã",
            dashboard.contains("HS-" + "X".repeat(length))
        )
    }

    // ── 5. Chưa ghép nối thì màn hình CHỈ còn việc ghép nối (bản 60.0) ─────

    @Test
    fun chuaGhepNoiManTongQuanChiConTheGhepNoi() {
        // Truoc ban 60.0, nhanh "CHUA ghep noi" cua phu huynh nhem them bon the
        // phu — "Trung tam giam sat hoc sinh", "Thiet lap muc tieu gio dung cho
        // con", "An toan khan cap & cuu ho ngoai tuyen", "Trang thai thiet bi
        // phu huynh". Ca bon chi to "chua ghep noi" va day di noi khac, trong
        // khi nguoi dung luc do chi can MOT thu: o nhap ma cua con.
        val dashboard = stripComments(read("ui/screens/DashboardScreen.kt"))
        val unpaired = blockOf(dashboard, "if (!isPaired) {")
        assertEquals(
            "Nhanh chua ghep noi phai chi con dung mot the cho moi vai tro (2 = the cua con + the cua ba me)",
            2,
            countOf(Regex("BentoCard\\("), unpaired)
        )
        listOf(
            "Trung tâm giám sát học sinh",
            "Thiết lập mục tiêu giờ dùng cho con",
            "An toàn khẩn cấp",
            "Trạng thái thiết bị phụ huynh",
            "ĐANG CHỜ KẾT NỐI"
        ).forEach {
            assertFalse("Thẻ filler \"$it\" đã quay lại màn chưa ghép nối", unpaired.contains(it))
        }
        assertTrue("Phải còn thẻ nhập mã của phụ huynh", unpaired.contains("parent_enter_code_card"))
        assertTrue("Phải còn ô nhập mã", unpaired.contains("student_code_input"))
        // Tro ly AI doc so lieu may nay, nen no chi co nghia khi da nam trong
        // mot gia dinh that: no phai nam ngoai nhanh chua ghep noi.
        assertFalse(
            "Trợ lý AI không được hiện khi chưa ghép nối",
            unpaired.contains("open_ai_chat_button")
        )
        assertTrue(
            "Thẻ AI phải bị khoá sau isPaired",
            dashboard.contains("if (!isChild && isPaired) {")
        )
    }

    @Test
    fun chuaGhepNoiMayConChiConTheMa() {
        val student = stripComments(read("ui/screens/StudentMainScreen.kt"))
        // Mốc là testTag của thẻ MA (comment bị stripComments cắt đi nên không
        // được dùng "// Student Code Card" làm mốc). Từ thẻ mã tới nhanh
        // `if (isPaired) {` khong duoc con the phu nao: bo them BentoCard o day
        // la them chu "chua ghep noi" mà app không có dữ liệu để nói.
        val card = student.indexOf("student_code_card")
        val paired = student.indexOf("if (isPaired) {")
        assertTrue("Thẻ mã học sinh đã đổi chỗ", card in 0 until paired)
        assertEquals(
            "Giữa thẻ mã và nhanh ghép-nối-xong không được còn thẻ phụ nào",
            0,
            countOf(Regex("BentoCard\\("), student.substring(card, paired))
        )
        assertFalse(
            "Máy con chưa ghép nối không còn thẻ \"TRẠNG THÁI THIẾT BỊ\" (GPS/PIN của chính nó)",
            student.contains("TRẠNG THÁI THIẾT BỊ")
        )
    }

    @Test
    fun baTabCanNguoiBenKiaPhaiChanKhiChuaGhepNoi() {
        val shell = stripComments(read("ui/screens/MainAppScreen.kt"))
        assertEquals(
            "Đúng ba tab (Vị trí, Giờ dùng, Chat) phải bị khoá tới khi ghép nối",
            3,
            countOf(Regex("OnlyWhenPaired\\(isPaired, isChild\\) \\{"), shell)
        )
        // Goi truc tiep ma khong qua OnlyWhenPaired la mo lai mot man trong.
        Regex("""\b[123] -> (LocationScreen|ScreenTimeScreen|ChatScreen)\(""").find(shell)?.let {
            assertTrue("Tab ${it.groupValues[1]} gọi thẳng ${it.groupValues[2]} khi chưa ghép nối", false)
        }
        assertTrue("Phải còn lời nói thẳng cho người dùng", shell.contains("chưa ghép nối"))
        assertTrue("Tab Cài đặt phải mở suốt (để còn đường đăng xuất)", shell.contains("4 -> SettingsScreen("))
        assertTrue("Placeholder phải có testTag cho uiautomator", shell.contains("testTag(\"unpaired_placeholder\")"))
    }

    /**
     * (bản 61.0) Tab Cài đặt cũng phải theo cùng một luật: chưa có ai ở đầu kia
     * thì không có gì để cấu hình. Trước đây màn này bày bảy thẻ ngay cả khi
     * chưa ghép nối — quyền hệ thống, nội quy chặn app, bảng màu, hồ sơ, quản lý
     * ghép nối, cài đặt SOS — trong đó sáu thẻ quản lý một quan hệ chưa tồn tại.
     */
    @Test
    fun chuaGhepNoiManCaiDatChiConDuongRaKhoiMay() {
        val settings = stripComments(read("ui/screens/SettingsScreen.kt"))
        // blockOf dung indexOf nên mốc là `if (isPaired) {` dau tien trong tep —
        // dung la nhanh chan ca man, khong phai `if (isPaired) {` nho ben trong
        // the quan ly ghep noi.
        val gate = blockOf(settings, "if (isPaired) {")
        val afterGate = settings.substring(settings.indexOf(gate) + gate.length)

        assertEquals(
            "Ngoài khối if (isPaired) chỉ được còn đúng một thẻ",
            1,
            countOf(Regex("BentoCard\\("), afterGate)
        )
        assertTrue("Thẻ còn lại phải có nút đăng xuất", afterGate.contains("sign_out_btn"))
        // TestTag `delete_account_btn` từ bản 55.0 gắn cho nút CHỈ đăng xuất (rules
        // khi đó chặn mọi lệnh xóa). Bản 66.0 mở quyền tự xóa cho chính chủ nên
        // cùng tag đó giờ là nút "Xóa tài khoản và dữ liệu trên máy chủ".
        assertTrue("Thẻ còn lại phải có nút xóa tài khoản", afterGate.contains("delete_account_btn"))

        listOf(
            "grant_permissions_button",
            "NỘI QUY ỨNG DỤNG",
            "GIAO DIỆN & CHẾ ĐỘ SÁNG TỐI",
            "QUẢN LÝ GHÉP NỐI THIẾT BỊ",
            "unpair_btn",
            "CỨU HỘ KHẨN CẤP"
        ).forEach {
            assertTrue("\"$it\" phải nằm sau song chắn isPaired", gate.contains(it))
        }

        // Phu de khi chua ghep noi phai ke lai dung nhung the bi an. "Quyen he
        // thong" khong duoc nam trong danh sach "se tro lai": dong nhac quyen o
        // dau man van con nguyen khi chua ghep noi, hen tra no la hen hai duong.
        assertTrue(
            "Phụ đề khi chưa ghép nối phải nói thật là chỉ còn đường đăng xuất",
            settings.contains("chưa ghép nối — vào đây chỉ để đăng xuất")
        )
        assertFalse(
            "Phụ đề không được hứa trả lại quyền hệ thống",
            settings.substringAfter("chưa ghép nối — vào đây chỉ để đăng xuất")
                .substringBefore("style = MaterialTheme")
                .contains("Quyền hệ thống")
        )

        // Hop thu "Ngung dung tren may nay" tung dan nguoi doc toi nut "Huy lien
        // ket" "o tren", ma nut do nam trong the vua bi an khi chua ghep noi: loi
        // dan vao mot cho khong ton tai. Chi ai doc man hinh moi phat hien, nen
        // no phai bi test chan bang chuoi cu the.
        assertFalse(
            "Hộp thoại ngừng dùng không được trỏ tới nút “Hủy liên kết ở trên” nữa",
            settings.contains("Hủy liên kết” ở trên")
        )
        assertTrue(
            "Hộp thoại phải có câu riêng cho máy chưa ghép nối",
            settings.contains("chưa ghép nối nên không còn liên kết nào để gỡ")
        )
    }
}
