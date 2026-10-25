package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test cho app QUAN TRI VIEN ban 66.0: lenh cam co pham vi + co han, va nut
 * "Xóa tài khoản" don THAT su.
 *
 * `admin/src/main/java/com/aistudio/admin/MainActivity.kt` duoc doc bang nguon
 * (junit chay trong `:app` khong compile duoc module `:admin`), nen day la lop
 * phong duy nhat cho ba thu da that bai o nhung ban truoc:
 *  1. Nut "xóa tài khoản" tung chi la dang xuat, va tung chi xoa /users — con
 *     lai van tinh tien va van ton tai tren may chu.
 *  2. Payload lenh cam phai khop `banWriteIsValid` trong rules (uid + scope +
 *     expiresAt tuong lai); lech mot truong la PERMISSION_DENIED ma nguoi bam
 *     khong hieu vi sao.
 *  3. Bien nhan xoa phai la so THAT. `delete()` mot document khong ton tai duoc
 *     Firestore coi la thanh cong, nen dem lien la noi dua.
 *
 * Ba thu test nay KHONG giu duoc, va do la gioi han that cua phuong phap:
 * hieu luc cua rules chi test duoc tren production sau khi Publish (task #41),
 * va Firebase Authentication thi khong co Admin SDK nen khong the test tu xoa.
 */
class AdminBanAndWipeRegressionTest {

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

    /** `:admin` nam ben canh `:app`, nen pha thu ca hai duong dan. */
    private fun source(relative: String): String {
        val root = repoRoot()
        val candidates = listOf(
            File(root, "app/$relative"),
            File(root, "admin/$relative"),
            File(root, relative)
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    private val admin: String by lazy {
        source("src/main/java/com/aistudio/admin/MainActivity.kt")
    }

    /** Than cua ham don, dung de kiem "ham nay khong cham vao /bans". */
    private fun wipeFunction(): String =
        admin.substringAfter("private suspend fun wipeAccountFromAdmin").substringBefore("\n}")

    /**
     * Cat comment truoc khi noi "ma nguon KHONG con chuoi X". Chinh cac dong giai
     * thich trong file nay viet lai ba chuoi bi cam ("đã xóa tất cả dữ liệu") de
     * ghi ro vi sao bi cam, nen doan code cua mot ban "strip comment" dung luc
     * nay se danh fail vi ly do sai. Phai cat theo DONG.
     */
    private val adminCodeOnly: String by lazy {
        admin.lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
    }

    // ── 1. Payload lenh cam khop `banWriteIsValid` ───────────────────────────

    /**
     * `banWriteIsValid(uid)` trong rules doi ba thu: `uid` trung voi document id,
     * `scope` trong ['ALL','AI'], va `expiresAt` (neu co) la timestamp TUONG LAI.
     * Thieu bat ky thu nao trong ba thu la toan bo lenh cam bi tu choi — ma
     * rules chua Publish thi chua ai biet, nen loi chi no ra khi moi nguoi dung
     * bat dau bi cam. Vi the ba needle nay phai nam trong repo.
     */
    @Test
    fun lenhCamGhiDungBaTruongRulesDoiHoi() {
        assertTrue("Phải ghi scope ('ALL'/'AI') — rules bắt buộc",
            admin.contains("\"scope\" to phamVi"))
        assertTrue("Phải ghi uid trùng document id — rules đối chiếu",
            admin.contains("\"uid\" to target.uid"))
        assertTrue("expiresAt phải là Timestamp, không phải epoch millis",
            admin.contains("com.google.firebase.Timestamp(java.util.Date(hetLuc))"))
        // Lop Timestamp nam trong firebase-common (com.google.firebase), KHONG phai
        // com.google.firebase.firestore — viet sai ten goi la CI chet o buoc
        // :admin:compileDebugKotlin, sau khi toan bo 233 test nguon da xan green.
        assertTrue("Không được dùng cái tên lớp không tồn tại com.google.firebase.firestore.Timestamp",
            !admin.contains("com.google.firebase.firestore.Timestamp"))
        // Ghi expiresAt qua khu thi rules tu choi, nen chi ghi khi chon 7/30 ngay.
        val payloadBlock = admin.substringAfter("val payload = mutableMapOf").substringBefore(".await()")
        assertTrue("Chỉ ghi expiresAt khi chọn thời hạn (0 = vĩnh viễn, không ghi trường)",
            payloadBlock.contains("if (hetLuc != null)"))
        assertTrue("Vĩnh viễn = KHÔNG có expiresAt",
            admin.contains("val hetLuc = if (soNgay > 0L)"))
    }

    /**
     * Bon lenh ma chu project da goi ten: vinh vien, 7 ngay tu mo, 30 ngay tu mo,
     * va cam hoi AI. Thieu mot trong bon la khong dung yeu cau.
     */
    @Test
    fun duBonLenhCamDaThoaThuan() {
        listOf("7 ngày", "30 ngày", "Vĩnh viễn", "Chỉ hỏi AI", "Cả ứng dụng").forEach { label ->
            assertTrue("Thiếu lựa chọn \"$label\" trong hộp thoại cấm", admin.contains(label))
        }
        // 'ALL' phai la mac dinh cho lenh moi, va doc lenh cu (khong co scope)
        // thanh 'ALL' — khong duoc thanh 'AI', vi lenh cu la cam ca app.
        assertTrue("Scope mặc định và kế thừa phải là ALL",
            admin.contains("?: SCOPE_ALL") && admin.contains("private const val SCOPE_ALL = \"ALL\""))
    }

    /**
     * "Đổi lệnh cấm" mo ra voi thoi han dang chay duoc chon san — va mot lenh da
     * HET HAN thi phai quay ve mac dinh (vinh vien), chu khong duoc chon "7 ngay"
     * theo mot han da qua: duoc nhu vay la ghi mot lenh ma nguoi bam tuong dang
     * mo lai cho dung ra.
     */
    @Test
    fun presetKhongChonMotHanDaQua() {
        val preset = admin.substringAfter("private fun presetDaysFor").substringBefore("\n}")
        assertTrue("Hết hạn / vĩnh viễn phải trả về 0 (không ghi expiresAt quá khứ)",
            preset.contains("if (until == null) return 0L") && preset.contains("if (con <= 0L) return 0L"))
        assertTrue("Còn hạn thì bám theo mức 7/30 gần nhất",
            preset.contains("7L * DAY_MS) 7L else 30L"))
    }

    /**
     * Ban 64.0 da go 100 luot thanh mot lenh cam TU DONG va bi bac bo: "ban hay
     * ko ben app admin cua toi quyet dinh". Nen ghi lenh cam chi duoc xay ra
     * trong hop thoai da mo (`banTarget?.let`), khong phai trong `refresh()`.
     */
    @Test
    fun khongCoLenhCamTuDong() {
        val moHopThoai = admin.indexOf("banTarget?.let")
        val ghiLenh = admin.indexOf("db().collection(BANS).document(target.uid).set(payload)")
        assertTrue("Phải bấm vào hộp thoại mới ghi lệnh cấm", moHopThoai in 0 until ghiLenh)
        assertTrue("Không được tự động ghi /bans theo ngưỡng AI",
            !admin.contains("if (row.aiToday >= AI_DAILY_LIMIT)"))
        assertTrue("Phải nói rõ không ai bị cấm tự động", admin.contains("không ai bị"))
    }

    // ── 2. Noi that ve cai ma lenh cam khong lam duoc ────────────────────────

    /**
     * "Cấm hỏi AI" khong chan duoc cuoc goi toi tro ly: cau hoi di thang tu may
     * len nha cung cap bang khoa nam trong APK, rules o day chi cat duong ghi bo
     * dem. Hop thoai phai noi dieu do — neu khong, nguoi quan tri tuong minh da
     * tat mot thu ma thuc te van chay.
     */
    @Test
    fun hopThoaiCamNoiThatPhamViAI() {
        assertTrue("Phải nói lệnh AI chỉ cắt bộ đếm",
            admin.contains("BỘ ĐẾM") && admin.contains("khóa có sẵn trong APK"))
        assertTrue("Phải nói muốn chặn tận gốc thì cần proxy", admin.contains("proxy"))
        // Va lenh cam chi bite sau khi Publish — chu khong phai sau khi bam.
        assertTrue("Phải nhắc Publish firestore.rules",
            admin.contains("Publish") && admin.contains("firestore.rules"))
    }

    // ── 3. Don tai khoan: dung thu tu, dung so luong ────────────────────────

    /**
     * Thu tu la mot co che bao mat, khong phai thoi quen trinh bay:
     *  - ho so truoc (nhip voi app nguoi dung, va rules dung `accountIsBeingDeleted`
     *    de phan biet "don tai khoan" voi "xoa don duong di"),
     *  - subcollection truoc document ghep noi (Firestore khong tu xoa con, va
     *    `isPairMember()` doc tren chinh document do),
     *  - vi tri / vet di chuyen / phieu SOS / tin hieu cuoc goi nam giua.
     */
    @Test
    fun donTaiKhoanXoaDungThuTu() {
        val f = wipeFunction()
        assertTrue("Phải xóa hồ sơ trước (bước 1)", f.contains("BUOC 1"))
        assertTrue("Phải liệt kê rồi mới xóa (bước 2)", f.contains("BUOC 2"))
        assertTrue("Phải xóa theo từng nhóm ở bước cuối", f.contains("BUOC 3"))
        val viTri = f.indexOf("scan(\"vị trí\")")
        val conCuaGhepNoi = f.indexOf("scan(\"nội quy & nhật ký ghép nối\")")
        val ghepNoi = f.indexOf("groups.add(\"ghép nối\"")
        assertTrue("Ba nhóm phải có thật trong kế hoạch xóa",
            viTri >= 0 && conCuaGhepNoi >= 0 && ghepNoi >= 0)
        assertTrue("Subcollection phải xóa TRƯỚC document ghép nối",
            conCuaGhepNoi < ghepNoi)
        assertTrue("Vet di chuyen va phieu SOS phai duoc liet ke",
            f.contains("scan(\"lịch sử vị trí\")") && f.contains("scan(\"phiếu SOS\")") &&
                f.contains("scan(\"tin hiệu cuộc gọi\")"))
        // `delete()` mot document khong ton tai van "thanh cong" o Firebase, nen
        // hai nhom co id dinh truoc (locations/<uid>, calls/room_<uid>) phai loc
        // bang exists() — neu khong thi bien nhan "da xoa N muc" luon cao hon
        // thuc te voi nhung may chua tung bat dinh vi / chua tung goi.
        assertTrue("Phải kiểm tra tồn tại trước khi đếm",
            f.contains(".get().await().exists()"))
        assertTrue("Hồ sơ chỉ được đếm khi thật sự tồn tại",
            f.contains("val hoSoCon = try {"))
    }

    /**
     * Nut "Bỏ cấm" la chut duy nhat de mo mot lenh vinh vien. Don tai khoan ma
     * xoa luon /bans/<uid> thi se mo lenh cam cho chinh nguoi vua bi don — va
     * vi uid van con (khong xoa duoc Firebase Auth), "xóa tài khoản" thanh mot
     * duong thoat an. Vi vay ham don KHONG duoc phep cham vao /bans.
     */
    @Test
    fun donTaiKhoanKhongXoaLenhCam() {
        val f = wipeFunction()
        assertTrue("Hàm xóa không được đụng tới collection bans", !f.contains("BANS"))
        assertTrue("Biên nhận phải nói lệnh cấm vẫn còn",
            admin.contains("Lệnh cấm trong /bans/"))
        assertTrue("Phải giữ nút bỏ cấm riêng cho việc đó", admin.contains("\"Bỏ cấm\""))
    }

    /**
     * `:app` va `:admin` la hai module khong dung chung source, nen danh sach
     * thu collection duoi document ghep noi phai duoc doi chieu bang chuoi.
     * Lech nhau nghia la hai nut "xóa tài khoản" tren cung mot tai khoản cho
     * hai ket qua khac nhau — va khong co test nao duoi runtime phat hien duoc.
     */
    @Test
    fun adminVaAppDonCungMotDanhSachSubcollection() {
        val names = Regex("\"([a-z_]+)\"").findAll(
            admin.substringAfter("PAIRING_SUBCOLLECTIONS = listOf(")
                .substringBefore(")")
        ).map { it.groupValues[1] }.toSet()
        val repo = source("src/main/java/com/example/data/repository/VesperaRepository.kt")
        val benApp = Regex("\"([a-z_]+)\"").findAll(
            repo.substringAfter("for (sub in listOf(").substringBefore("))")
        ).map { it.groupValues[1] }.toSet()
        assertEquals(
            "Danh sách subcollection phải khớp nhau giữa :app và :admin " +
                "(thêm một loại dữ liệu là phải sửa cả hai chỗ)",
            benApp, names
        )
        assertTrue("Phải là bảy subcollection, hiện thấy ${names.size}: $names", names.size == 7)
    }

    // ── 4. Bien nhan: khong duoc noi dua ─────────────────────────────────────

    /**
     * Sau khi bam "Xóa trên máy chủ", hop thoai doi thanh bien nhan: X/Y muc,
     * nhung nhom bi chan, va ba thu VẪN CÒN (email/mat khau trong Firebase Auth,
     * lenh cam, may ben kia cua ghep noi). Do la ly do `found` duoc dem tam thoi
     * la "liet ke duoc" chu khong phai " chac chan con dau".
     */
    @Test
    fun bienNhanXoaInCaSoBiChan() {
        assertTrue("Receipt phải in theo tỉ lệ X/Y, không phải con số tự tin",
            admin.contains("Đã xóa \${receipt.deleted}/\${receipt.found} mục"))
        assertTrue("Receipt phải liệt kê nhóm KHÔNG xóa được",
            admin.contains("KHÔNG xóa được:") && admin.contains("mục không xóa được"))
        assertTrue("Phải nói hồ sơ chưa chắc đã xóa được gì khi quy tắc chặn",
            admin.contains("không đọc được danh sách"))
        // Khong duoc xuat hien lai ba chuoi "da xoa het" cua ban cu — trong PHAN
        // CODE, vi chinh cac comment duoi day lai viet lai chuoi do de ghi ro vi
        // sao no bi cam.
        listOf("đã xóa tất cả", "xóa vĩnh viễn theo chính sách").forEach { phrase ->
            assertTrue("Không được nói \"$phrase\" trong nội dung hiển thị — app không làm được thế",
                !adminCodeOnly.contains(phrase))
        }
        assertTrue("Phải nói trần không vượt được: Firebase Authentication",
            admin.contains("Firebase Authentication") && admin.contains("Admin SDK"))
        assertTrue("Phải cảnh báo không hoàn tác được",
            admin.contains("KHÔNG hoàn tác được"))
        assertTrue("Phải nói máy bên kia mất liên kết", admin.contains("MẤT liên kết"))
    }

    /**
     * Nut danh cho viec khong the hoan tac phai qua mot hop thoai xac nhan, va
     * `refresh()` sau khi chay xong de danh sach khong con hien ma nguoi ta vua
     * don. Bam vao nut ngay lap tuc la mot cai nut tu.
     */
    @Test
    fun xoaPhaiQuaHopThoaiXacNhan() {
        assertTrue("Nút dòng chỉ mở hộp thoại, không xóa ngay",
            admin.contains("onClick = { deleteTarget = row }"))
        assertTrue("Hộp thoại xác nhận phải gọi hàm wipe",
            admin.contains("wipeAccountFromAdmin(target.uid)"))
        assertTrue("Phải có nút phụ để chạy tiếp phần còn dang dở",
            admin.contains("\"Thử lại\""))
        assertTrue("Phải khóa nút khi đang chạy (tránh xóa hai lần cùng lúc)",
            admin.contains("enabled = !wipeRunning"))
    }
}
