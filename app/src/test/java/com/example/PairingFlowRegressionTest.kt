package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho lô đổi luồng ghép nối (bản 59.0), viết sau khi đọc lại toàn bộ
 * đường đi của một phép ghép chứ không đoán từ màn hình.
 *
 * Bốn điều xác minh được trong code cũ, mỗi điều tương ứng với một test dưới
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
}
