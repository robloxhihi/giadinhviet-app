package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho "không thay avatar được" (người dùng báo bản 68, sửa trong 69.0).
 *
 * Ba chỗ hỏng thật, mỗi chỗ một assert:
 *  1. Máy HỌC SINH không có bộ chọn ảnh nào cả — avatar chỉ là chữ cái đầu,
 *     bấm không có phản hồi. Trẻ em vì thế không bao giờ đổi được hình.
 *  2. Bộ chọn cũ là `GetContent`, và khi không mở được hoặc đọc ảnh hỏng thì
 *     app im lặng — người dùng không phân biệt được "app lỗi" với "máy không
 *     có ứng dụng chọn ảnh".
 *  3. Ảnh đã chọn nhưng không hiển thị lại được nếu chuỗi base64 bị đọc sai.
 *
 * Test này quét NGUỒN (unit test không chạy được Compose), nên nó chặn đúng cái
 * đã hỏng chứ không mô phỏng. Kiểm chứng thật vẫn làm trên BlueStacks sau khi
 * cài APK.
 *
 * CI ban v69.1 chet o Chinh tep nay: ten ham tro thanh `updateAvatarVanChanChuoi
 * QuaDai` — mot dau cach chen giua hai chu cai khien Kotlin hieu la mot ham
 * khong tham so khong dao ham ("Function ... without a body must be abstract"
 * + "Expecting '('"). Nguon app da dich xong, chi test fail, nen loi nay chi
 * lo ra sau khi moi thu hoan chinh — do that moi lan dat ten ham test.
 */
class AvatarRegressionTest {

    private fun source(relative: String): String {
        val file = listOf(File(relative), File("app/$relative")).firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    /** Bản chỉ còn code: KDoc của các tệp này chứa đúng những chuỗi mà test cấm. */
    private fun codeOf(relative: String): String = source(relative)
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    private val picker = "src/main/java/com/example/ui/components/AvatarPicker.kt"
    private val dashboard = "src/main/java/com/example/ui/screens/DashboardScreen.kt"
    private val student = "src/main/java/com/example/ui/screens/StudentMainScreen.kt"
    private val app = "src/main/java/com/example/ui/screens/MainAppScreen.kt"

    // ── 1. Một bộ chọn dùng chung, có đường lùi thật trên máy cũ ────────────────

    @Test
    fun boChonAnhDungChungVaKhongCanQuyenDocBoNho() {
        val code = codeOf(picker)
        assertTrue("Phai dung PickVisualMedia (khong can permission doc bo nho)",
            code.contains("ActivityResultContracts.PickVisualMedia()"))
        assertTrue("Phai mo voi ImageOnly", code.contains("PickVisualMedia.ImageOnly"))
        assertFalse("Khong duoc quay lai GetContent cho avatar",
            code.contains("ActivityResultContracts.GetContent()"))
        assertTrue("Nen anh phai vuong 96px truoc khi ghi Firestore",
            code.contains("const val AVATAR_EDGE_PX = 96"))
        assertTrue("Phai doc kich thuoc truoc khi nap bitmap (inJustDecodeBounds)",
            code.contains("inJustDecodeBounds = true"))
    }

    /**
     * Do tren BlueStacks ngay 2026-10-27, sau khi ban 69.2 da cai: bam avatar o
     * CA HAI man (Tong quan va may hoc sinh), bo chon mo that, bam xong anh thi
     * ve app — va KHONG CO GI XA RA ca: `users/{uid}.avatarBase64` van rong,
     * khong mot dong chu nao noi len. Do la hop chon anh chuan nhan duoc ket qua
     * rong (vi du Huy) nen im lang. May duoi Android 13 vi the phai tu mo lay
     * duong dan trong ket qua thay vi chi tin mot con duong.
     */
    @Test
    fun mayCuPhaiTuMoKetQuaBoChon() {
        val code = codeOf(picker)
        assertTrue("May duoi Android 13 phai co duong GET_CONTENT",
            code.contains("Build.VERSION.SDK_INT >= 33") &&
                code.contains("Intent.ACTION_GET_CONTENT"))
        assertTrue("Phai doc duoc ket qua dang ClipData", code.contains("clipData"))
        assertTrue("Phai doc duoc ket qua dang extra", code.contains("extras"))
        assertTrue("Bo chon mo ma khong tra anh thi PHAI co chu",
            code.contains("không trả tệp ảnh nào về app"))
        assertTrue("Nguoi dung Huy thi van im lang",
            code.contains("result.resultCode != Activity.RESULT_OK"))
    }

    /**
     * Do that thu 27/10 tren BlueStacks: ACTION_GET_CONTENT tu do roi vao
     * `com.bluestacks.filemanager` (FileSharer cua gia lap). No liet ke du anh
     * nhung cham vao anh thi Activity khong bao gio finish — dem chon "0" dung
     * nguyen, khong co nut Mo — nen app khong bao gio nhan duoc Intent ket qua
     * nao. 69.4 da mo het duong doc ket qua (data/clipData/extras) nen loi con
     * lai la chon SAI bo chon, khong phai doc sai. Fix: pin ve DocumentsUI goc
     * khi may co no. Test nay khop len CHUOI CODE that (khong phai comment) de
     * khong ai vo tinh bo mat gan bo chon nay di.
     */
    @Test
    fun pinBoChonVeDocumentsUIKhiMayCo() {
        val code = codeOf(picker)
        assertTrue("Phai dinh nghia package DocumentsUI",
            code.contains("com.android.documentsui"))
        assertTrue("Phai gan setPackage ve DocumentsUI",
            code.contains("setPackage(DOCUMENTS_UI)"))
        assertTrue("Chi pin khi may that su co bo chon do (resolveActivity)",
            code.contains("resolveActivity"))
        assertTrue("Ham mo bo chon phai nhan context de resolve",
            code.contains("avatarGetContentIntent(context)"))
    }

    @Test
    fun loiBoChonPhaiHienRaThanhChu() {
        val code = codeOf(picker)
        // Ba duong het cua moi lan bam: mo khong duoc host, doc anh ra null,
        // va Huy (im lang la dung). Hai duong dau PHAI co chu.
        assertTrue("May khong co ung dung chon anh phai duoc noi ro",
            code.contains("ActivityNotFoundException") &&
                code.contains("Máy này không có ứng dụng chọn ảnh."))
        assertTrue("Anh doc khong ra phai duoc noi ro",
            code.contains("Không đọc được ảnh vừa chọn"))
        assertTrue("Nguoi dung Huy thi KHONG duoc bao loi", code.contains("uri != null"))
    }

    // ── 2. Ca hai may deu co the doi avatar ────────────────────────────────────

    @Test
    fun caHaiManDeuCoTheDoiAvatar() {
        listOf(dashboard to "may phu huynh", student to "may hoc sinh").forEach { (path, who) ->
            val code = codeOf(path)
            assertTrue("$who phai mo bo chon avatar", code.contains("rememberAvatarPicker("))
            assertTrue("$who phai co vung bam dinh danh cho QA",
                code.contains("testTag(\"avatar_picker\")"))
            assertTrue("$who phai ve lai anh da chon", code.contains("avatarBitmapFromBase64("))
        }
    }

    @Test
    fun manHocSinhDuocNoiVaoDuongLuThat() {
        val wiring = codeOf(app)
        assertTrue("MainAppScreen phai goi man hoc sinh", wiring.contains("StudentMainScreen("))
        // Dem thay vi cut theo khoangtrang: cat chuoi theo thut le thu cong de
        // mo cua goi thi doi ten tham so cung khong bao gio lam do test.
        val wired = Regex("onAvatarPicked = \\{ base64 -> viewModel\\.updateAvatar\\(base64\\) }")
            .findAll(wiring).count()
        assertTrue("Ca hai man (Tong quan + hoc sinh) phai noi vao updateAvatar, dem duoc $wired",
            wired == 2)
        assertTrue("Man hoc sinh phai khai bao tham so avatar (mac dinh rong de noi cu khong do)",
            codeOf(student).contains("onAvatarPicked: (String) -> Unit = {}"))
    }

    // ── 3. Chuoi qua dai van bi chan ───────────────────────────────────────────

    /**
     * Firestore giới hạn 1MB/document; anh goc chen thang base64 vao la vo sach.
     * `updateAvatar` chang chuoi >300_000 ky tu va phai noi ro khi bi chan —
     * dung im lang tra ve mot vong tron khong doi mau.
     */
    @Test
    fun updateAvatarVanChanChuoiQuaDai() {
        val vm = source("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")
        val body = vm.substringAfter("fun updateAvatar(base64: String) {")
        assertTrue("Phai co tran do dai", body.contains("base64.length > 300_000"))
        assertTrue("Phai bao khi anh qua lon", body.contains("Ảnh đại diện quá lớn"))
        assertTrue("Phai bao khi luu that bai, khong only log",
            body.contains("Không thể lưu ảnh đại diện."))
        assertTrue("Phai co thanh cong ro rang de Snackbar hien duoc",
            body.contains("Đã đổi ảnh đại diện!"))
    }
}
