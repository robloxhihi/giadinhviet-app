package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho bài học bản 68.0: **một quyền "được đọc" viết đúng cho GET có thể
 * giết chết cả một query.**
 *
 * Firestore đánh giá điều kiện theo hai cách khác nhau. Với GET/UPDATE/DELETE
 * trên một đường dẫn đã biết, `get(/pairings/$(pairingId))` mở document thật và
 * so hai uid — chắc nhất, và vẫn là cách viết ở mọi chỗ ghi. Với LIST/QUERY thì
 * `{pairingId}` **chưa có giá trị**, nên mọi `get()`/`exists()` dựng đường dẫn từ
 * nó là không quyết định được: Firestore từ chối TOÀN BỘ câu lệnh bằng
 * PERMISSION_DENIED, không lọc riêng từng kết quả.
 *
 * Đo trực tiếp trên production ngày 26/10 bằng `qa-probe-bounds.cjs` (POST
 * `:runQuery` — hình duy nhất không thể bị router REST nhầm thành GetDocument):
 *
 *   QUERY pairings where parentId/childId   máy phụ huynh  403   <-- chết ở đây
 *   GET  pairings/<cap cua minh>            máy phụ huynh  200
 *   LIST 7 collection ngon                  quan tri       200
 *   QUERY pairings/<id>/messages (duong dan ro)    phu huynh 200
 *   QUERY pairings/<id>/rule_audit          quan tri        403  <-- block duy
 *                                                                 nhieu thieu
 *                                                                 isAdmin()
 *
 * Hai hệ quả mà test này khoá lại, vì cả hai đều đã bị phát biểu SAI trong dự
 * án này một lần:
 *  1. Máy con nhận document ghép nối bằng `findPairingFor` (query theo childId).
 *     Rules 67.0 chặn query đó -> hồ sơ con không bao giờ tự ghi `activePairingId`
 *     -> `attestedPair()` chặn mọi đường đọc vị trí/nhật ký -> người dùng thấy
 *     "ghép nối không được, map không chính xác". Chữa: `allow get` + `allow list`
 *     tách đôi, LIST lấy thành viên từ chính document trả về.
 *  2. BẢY subcollection **không** cần nới: app listen theo đường dẫn
 *     `pairings/<id>/<sub>` đã ràng buộc, ở đó `get()` đánh giá bình thường.
 *     Ai đó đọc kết luận "mọi LIST đều chết" rồi đổi subcollection sang suy quyền
 *     từ chuỗi id sẽ làm MỞ hệ thống (đường dẫn là thứ client tự đặt).
 *
 * Thêm một cạm bẫy nữa, cũng đã từng làm cả một bản kết luận sai:
 * `GET .../documents/pairings/<id>/messages` có ba đoạn đường dẫn nên REST đọc nó
 * như GetDocument của một document không match rule nào -> 403 KỂ CẢ KHI LIST
 * thật trả về 200. Vì vậy mọi probe LIST trong test này đều đi qua `:runQuery`.
 */
class RulesQuerySafetyRegressionTest {

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

    private fun stripComments(source: String): String = source
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    private val rules: String by lazy { stripComments(repoFile("firestore.rules")) }

    /** Than cua mot `match <duong dan> { ... }` — dem ngoac hoa, khong lan sang block ke tiep. */
    private fun matchBlock(header: String): String {
        val start = rules.indexOf(header)
        assertTrue("Thiếu `$header` trong firestore.rules", start >= 0)
        val open = rules.indexOf('{', start)
        var depth = 0
        for (i in open until rules.length) {
            when (rules[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return rules.substring(open, i + 1)
                }
            }
        }
        error("$header không đóng ngoặc")
    }

    /**
     * Lay nhung dong `allow <verb> ... ;` trong một block.
     */
    private fun allowLines(block: String, verb: String): List<String> =
        Regex("allow\\s+$verb[^;]*;").findAll(block).map { it.value.trim() }.toList()

    /** Phan `match /pairings/{pairingId}` TRUOC nested match dau tien — chi do la dieu kien cua chinh no. */
    private fun topLevelOf(block: String): String =
        block.substringBefore("\n      match /")

    @Test
    fun `pairings phai co allow list khong cham document nao`() {
        val block = matchBlock("match /pairings/{pairingId} {")
        val lists = allowLines(block, "list")
        assertEquals("/pairings phai co DUNG MOT allow list", 1, lists.size)
        val list = lists.first()
        assertFalse(
            "allow list goi `get()`/`exists()` theo duong dan chua wildcard -> Firestore " +
                "khong quyet duoc va che TOAN BO query (403 do tren production 26/10). " +
                "Lay thanh vien tu chinh document tra ve: $list",
            list.contains("get(") || list.contains("exists(")
        )
        assertTrue("allow list doi chieu parentId tren document tra ve", list.contains("resource.data.parentId == request.auth.uid"))
        assertTrue("allow list doi chieu childId tren document tra ve", list.contains("resource.data.childId == request.auth.uid"))
        assertTrue("Quan tri van liet ke duoc de don tai khoan", list.contains("isAdmin()"))
        val top = topLevelOf(block)
        assertFalse(
            "Top-level /pairings khong duoc dung `allow read` nua (no bam ca get lan list, " +
                "ma list thi khong danh gia duoc get): " + top,
            top.contains("allow read:")
        )
    }

    @Test
    fun `pairings van giu allow get theo hai uid that`() {
        val block = matchBlock("match /pairings/{pairingId} {")
        val gets = allowLines(block, "get")
        assertEquals("/pairings phai co DUNG MOT allow get", 1, gets.size)
        assertTrue(
            "GET thi duoc mo document that: isPairMember() doi chieu parentId/childId tren " +
                "pairings/<id>, khong tin duong dan. Khong duoc thay bang `resource.data` " +
                "vi `allow get` la lenh doc duy nhat ma `resource` co nghip gi do.",
            gets.first().contains("isPairMember(pairingId)") && gets.first().contains("isAdmin()")
        )
    }

    @Test
    fun `bay subcollection giu isPairMember vi duong dan da rang buoc`() {
        // App listen `pairings/<id>/<sub>` — o day pairingId DA co gia tri nen
        // get() danh gia duoc (da do: 200 cho may phu huynh). Doi sang suy quyen
        // tu chuoi id la mo cua: duong dan la thu client tu dat.
        val subs = listOf(
            "messages", "location_history", "schedules", "agreements",
            "extra_time", "privacy_requests", "rule_audit"
        )
        subs.forEach { sub ->
            val block = matchBlock("match /$sub/{")
            assertTrue(
                "$sub phai van kiem thanh vien bang document ghep noi that",
                block.contains("isPairMember(pairingId)")
            )
        }
    }

    @Test
    fun `admin doc duoc nhat ky noi quy nhung khong tao duoc`() {
        val block = matchBlock("match /rule_audit/{entryId} {")
        val reads = allowLines(block, "read")
        val creates = allowLines(block, "create")
        assertTrue(
            "Quan tri khong liet ke duoc rule_audit (403 do 26/10) nen nut \"Xóa tài " +
                "khoản\" phia quan tri bo sot nhat ky noi quy: " + reads,
            reads.any { it.contains("isAdmin()") }
        )
        assertFalse(
            "Nhat ky ma quan tri tao/sua duoc thi khong con la nhat ky: $creates",
            creates.any { it.contains("isAdmin()") }
        )
        assertTrue("update van phong do", block.contains("allow update: if false;"))
    }

    /**
     * Moi lan app NGUOI DUNG hoach `pairings` ma khong bo loc la mot lan tu
     * chay vao 403: `allow list` chi dung khi Firestore kiem duoc tung document
     * tra ve thuoc ve may do, va mot document cua gia dinh khac lam chet ca cau
     * lenh. App quan tri duoc mien nhiem vi `isAdmin()` dung sai co dinh.
     */
    @Test
    fun `may nguoi dung khong duoc bao gio hoach pairings khong bo loc`() {
        val srcRoot = File(repoRoot(), "app/src/main/java")
        assertTrue("Không tìm thấy thư mục nguồn app", srcRoot.isDirectory)
        val offenders = srcRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val src = stripComments(file.readText())
                Regex("""collection\("pairings"\)""").findAll(src).map { m ->
                    val after = src.substring(m.range.last + 1, minOf(src.length, m.range.last + 260))
                    // `.document(id)...` la duong dan rang buoc — khong phai query.
                    val isQuery = !after.trimStart().startsWith(".document(")
                    val filtered = after.contains("whereEqualTo") || after.contains("whereIn")
                    val executed = after.contains(".get()") || after.contains("addSnapshotListener")
                    if (isQuery && executed && !filtered) "${file.name}: ${after.take(120)}" else null
                }.filterNotNull()
            }
            .toList()
        assertTrue("Query /pairings khong bo loc (se bi 403): " + offenders.joinToString(" | "), offenders.isEmpty())
    }
}
