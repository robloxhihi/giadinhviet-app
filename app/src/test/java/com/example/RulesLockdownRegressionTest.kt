package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test cho lo "khoa 5 khe ho" (ban 65.0, 2026-10-25).
 *
 * `firestore.rules` trong repo KHONG duoc CI deploy — file nay chi la bang
 * chung, con rules chay that tren production la thu nguoi dung bam "Publish"
 * trong Firebase Console. Vi vay day la lop phong duy nhat: no giu de khong ai
 * quay lai duoc `allow read, write: if isAuthenticated()`, va giu cho nhung
 * cho THAT SU can mo (phieu "gio trong quyen rieng" phu huynh duyet, query
 * `location_history` cua chinh may) van mo dung mot cho du — khong mo hon.
 *
 * Sau dieu uoc duot nhat cung bi bat bang loi, vi moi cai tung la mot loi thiet
 * ke da phai tra bang nuoc mat trong du an nay:
 *  1. Co DUY NHAT mot file `firestore.rules` (CI v64 do vi ban cu o `app/`).
 *  2. Quan he ghep noi phai duoc doi chieu bang HAI UID tren document ghep noi,
 *     khong tin truong `pairingId` do client tu ghi len.
 *  3. Cong thuc id ghep noi trong Kotlin va trong rules khop nhau.
 *  4. Lenh Cam phai cat duong du lieu dinh vi, khong chi cat ho so.
 *  5. Ham phai duoc khai BAO TRUOC noi dung no (bai hoc commit 5cc4367).
 */
class RulesLockdownRegressionTest {

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile.canonicalFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "firestore.rules").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Không tìm thấy thư mục gốc dự án (phải có settings.gradle.kts + firestore.rules)")
    }

    private fun repoFile(name: String): String =
        String(File(repoRoot(), name).readBytes(), Charsets.UTF_8)

    /**
     * Cat comment truoc khi noi "ma rules KHONG con chuoi X". Mot dong giai
     * thich ve cai loi cu ("truoc day: allow read, write: if isAuthenticated()")
     * du lam assert do that bai vi ly do sai. Phai cat theo DONG — ban Regex
     * `//[^\n]*` an ca `https://` ben trong chuoi.
     */
    private fun stripComments(source: String): String = source
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    private val rules: String by lazy { stripComments(repoFile("firestore.rules")) }

    /**
     * Lay than cua mot block o NGON.
     *
     * `match /location_history/{historyId}` xuat hien HAI lan: ban 6 dong nam
     * DUOI `pairings/{id}` (da khat bang isPairMember tu lau) va ban o ngon
     * (moi khoa ban 65.0). Tim chuoi tran lang se ra ban duoi pairings va do
     * fail vi ly do sai. Neo bang 4 dau cach dau dong → chi block o ngon.
     *
     * `substringAfter` mặc định trả về CẢ TỆP khi không thấy marker, tức test
     * sẽ đạt mà không đo gì cả (tìm thấy thứ muốn tìm ở bất kỳ đâu). Vì vậy
     * mỗi đoạn phải được kết luận là có thật trước khi cắt.
     */
    private fun segment(startMarker: String, endMarker: String = "\n    }"): String {
        val i = rules.indexOf(startMarker)
        assertTrue("firestore.rules thiếu đoạn bắt đầu bằng '$startMarker'", i >= 0)
        val rest = rules.substring(i + startMarker.length)
        val j = rest.indexOf(endMarker)
        assertTrue("Đoạn '$startMarker' không đóng bằng '$endMarker'", j >= 0)
        return rest.substring(0, j)
    }

    private fun block(path: String): String = segment("\n    match $path {")

    // ── 1. Chi mot file rules ────────────────────────────────────────────────

    /**
     * `app/firestore.rules` va `app/applet/firestore.rules` la hai ban 58 dong
     * bi quen tu ban 55.0: chung mo toan bo quyen ghi cho /users (ke ca `delete`).
     * Ai do deploy tu thu muc `app/` la toan bo nhung khoa o day bay mau, va
     * test doc rules se do sai file.
     */
    @Test
    fun chiCoMotFileRulesDuyNhatTrongRepo() {
        val copies = repoRoot().walkTopDown()
            .maxDepth(3)
            .filter { it.name == "firestore.rules" }
            .filter { f ->
                val p = f.invariantSeparatorsPath
                !p.contains("/build/") && !p.contains("/.git/") && !p.contains("/.gradle/")
            }
            .toList()
        val goc = repoRoot()
        assertTrue(
            "Phải còn ĐÚNG một file firestore.rules, nằm ở gốc repo. Bản sao ở " +
                "thư mục con là bản cũ và mở toang rules: " +
                copies.joinToString { it.path },
            copies.size == 1 &&
                copies.first().invariantSeparatorsPath ==
                File(goc, "firestore.rules").invariantSeparatorsPath
        )
    }

    // ── 2. Bon khe ho o ngon da khat ─────────────────────────────────────────

    @Test
    fun khongConDuongMoToanBoChoDuLieuOngon() {
        // Chuoi ky nan cua ca bon block cu: mo cho moi tai khoan da dang nhap.
        assertTrue(
            "Không được còn dòng 'allow read, write: if isAuthenticated();' — đó " +
                "là chữ ký của các block mở toang",
            !rules.contains("allow read, write: if isAuthenticated();")
        )

        val loc = block("/locations/{userId}")
        assertTrue("locations: chủ máy phải đọc được doc của mình",
            loc.contains("request.auth.uid == userId"))
        assertTrue("locations: người ghép nối thật mới đọc được",
            loc.contains("isPairedWith(userId)"))

        val hist = block("/location_history/{historyId}")
        assertTrue("location_history ngọn: chỉ vết của CHÍNH máy này (khớp " +
            "whereEqualTo(\"userId\", uid) mà app gửi lên)",
            hist.contains("resource.data.userId == request.auth.uid") &&
                hist.contains("request.resource.data.userId == request.auth.uid"))

        val sos = block("/sos_alerts/{alertId}")
        assertTrue("Phiếu SOS: chỉ máy lập phiếu được ghi",
            sos.contains("request.resource.data.userId == request.auth.uid"))
        assertTrue("Phiếu SOS: cha/me ghép nối phải đọc được, không thì nút SOS vô nghĩa",
            sos.contains("isPairedWith(resource.data.userId)"))

        assertTrue("pairings: đọc phải là thành viên (hoặc quản trị)",
            block("/pairings/{pairingId}").contains("allow read: if isPairMember(pairingId) || isAdmin();"))
        assertTrue("pairings: tạo phải LÀ một trong hai người",
            rules.contains("request.resource.data.parentId == request.auth.uid"))

        assertTrue("pairing_requests đã vô sinh từ 59.0 thì phải deny hết",
            block("/pairing_requests/{requestId}").contains("allow read, write: if isAdmin();"))
    }

    @Test
    fun callsKhongConLaHopThuWebrtcCuaAiCungMo() {
        val calls = block("/calls/{callId}")
        // May goi dat roomId = activePairing.id → id document chinh la id ghep noi.
        assertTrue("calls: phải là một ben của ghép nối mang id đó",
            calls.contains("isPairMember(callId)"))
        assertTrue("calls: exists() phải đi trước get() để id lạ không làm lỗi điều kiện",
            calls.contains("exists(/databases/$(database)/documents/pairings/$(callId))"))
        assertTrue("calls: nhánh 'room_<uid>' nội bộ khi chưa ghép nối vẫn chạy được",
            calls.contains("callId == 'room_' + request.auth.uid"))
    }

    // ── 3. Khong tin chu document duoc hoi ───────────────────────────────────

    /**
     * `resource.data.pairingId` do client tu ghi, nen no chi duoc dung nhu MOT
     * CANDI: pairOfTwo phai ton tai + PAIRED + {parentId, childId} khop dung
     * {a, b}. Neu bo mot trong ba dieu kien thi "gian pairingId = nha ke ben"
     * lai mo duoc du lieu cua ho.
     */
    @Test
    fun doiChieuHaiUidTrenDocumentGhepNoiChuKhongTinPairingId() {
        val fn = segment("function pairOfTwo(pairingId, a, b) {")
        listOf(
            "pairingId is string" to "phải từ chối pairingId thiếu/không phải chuỗi",
            "exists(/databases/$(database)/documents/pairings/$(pairingId))" to "phải kiểm tra ghép nối có thật",
            "data.status == 'PAIRED'" to "phải đòi status PAIRED đúng như UI đang đòi",
            "data.parentId in [a, b]" to "phải đối chiếu parentId",
            "data.childId in [a, b]" to "phải đối chiếu childId",
            "data.parentId !=" to "hai uid phải là đúng cặp, không phải hai cặp trùng một người"
        ).forEach { (needle, why) ->
            assertTrue("pairOfTwo $why (thiếu: $needle)", fn.contains(needle))
        }
        // Nguoi hoi phai duoc truyen bang request.auth.uid, khong phai mot gia
        // tri client tu chon.
        assertTrue("Cặp phải được đối chiếu với request.auth.uid, không với uid đọc từ doc",
            rules.contains("pairOfTwo(resource.data.pairingId, request.auth.uid, userId)"))
    }

    /**
     * Id ghep noi duoc sinh trong Kotlin (`"pair_" + parentId + "_" + childId`).
     * Rules doi cong thuc ay — ai doi ben nay ma quen ben kia la toan nha mat
     * nhau tren ban do ma CI van xanh.
     */
    @Test
    fun congThucIdGhepNoiKhopNhauGiuaKotlinVaRules() {
        val repo = String(
            File(repoRoot(), "app/src/main/java/com/example/data/repository/VesperaRepository.kt")
                .readBytes(), Charsets.UTF_8
        )
        assertTrue("Kotlin vẫn phải sinh id ghép nối theo công thức pair_<parent>_<child>",
            repo.contains("val pairingId = \"pair_\${parentProfile.id}_\${student.id}\""))
        assertTrue("Rules phải thử CẢ HAI thứ tự (không biết bên nào là cha)",
            rules.contains("pairOfTwo('pair_' + request.auth.uid + '_' + otherUid") &&
                rules.contains("pairOfTwo('pair_' + otherUid + '_' + request.auth.uid"))
    }

    // ── 4. Ngoai le can giu: duyet phieu quyen rieng ──────────────────────────

    /**
     * May PHU HUYNH ghi len `locations/<childId>` khi duyet phieu "gio trong
     * quyen rieng". Do la lan duy nhat mot tai khoan cham vao du lieu cua tai
     * khoan khac, va phai bo hai: dung nguyen vong `set(merge)`, chi doi
     * `isPrivacyActive` + `updatedAt`. Test nay chang ai phai mo them.
     */
    @Test
    fun phieuQuyenRiengVanGhiDuocLenMayCuaCon() {
        val loc = block("/locations/{userId}")
        val exception = "['isPrivacyActive', 'updatedAt']"
        val lan = loc.split(exception).size - 1
        assertTrue(
            "Ca create lan update phai cho phep ngoai le quyen rieng, hien thay $lan lan " +
                "(can 2)",
            lan == 2
        )
        // update thi so sanh bang diff(), vi set(merge) tra ve CA document sau hop nhat.
        assertTrue("Phai dung diff().affectedKeys(), khong kiem keys tran lang",
            loc.contains("request.resource.data.diff(resource.data).affectedKeys()"))
        assertTrue("Create cua ngoai le phai kiem keys tran lang (doc chua ton tai)",
            loc.contains("request.resource.data.keys().hasOnly("))
    }

    @Test
    fun lenhCamCatCaDuongDuLieuDinhVi() {
        // Neu chi /users bi notBanned() thi tai khoan bi cam van tiep tuc gui GPS
        // va van doc duoc vi tri cua me — "lenh Cam" chi la chu ghi chu.
        listOf("/locations/{userId}", "/location_history/{historyId}", "/sos_alerts/{alertId}", "/calls/{callId}")
            .forEach { path ->
                assertTrue("$path phai co notBanned()", block(path).contains("notBanned()"))
            }
        assertTrue("/users van phai khong cho xoa (ban 55.0)",
            block("/users/{userId}").contains("allow delete: if false;"))
    }

    // ── 5. Thu tu khai bao ham ───────────────────────────────────────────────

    /**
     * Firestore khai ham theo thu tu: de mot ham dung ham chua khai bao la ly
     * do rules tu choi deploy luc minh khong mong (commit 5cc4367).
     */
    @Test
    fun moiHamDuocKhaiBaoTruocNoiDungDenNo() {
        val names = Regex("function (\\w+)\\(").findAll(rules).map { it.groupValues[1] }.toList()
        assertTrue("Phat hien it nhat 3 ham trong rules", names.size >= 3)
        names.forEach { name ->
            val khaiBao = rules.indexOf("function $name(")
            val dungSaiCho = Regex("(?<!function )\\b$name\\(").findAll(rules)
                .map { it.range.first }
                .filter { it < khaiBao }
                .toList()
            assertTrue("Ham $name bi dung truoc khi khai bao tai ${dungSaiCho}",
                dungSaiCho.isEmpty())
        }
    }
}
