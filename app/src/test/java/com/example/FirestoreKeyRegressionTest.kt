package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho lô lỗi tìm ra khi chạy bản 54.0 trên máy thật (BlueStacks,
 * 2026-10-24).
 *
 * Ba nhóm lỗi, cả ba đều nhìn thấy từ logcat hoặc từ màn hình chứ không phải
 * từ đoán code:
 *
 * 1. **Khóa Firestore bị mất khi đọc.** Kotlin sinh getter `isSos()`, Firestore
 *    cắt chữ "is" nên model luôn đợi đọc khóa `sos`. Trong khi đó
 *    `respondToPrivacyRequest` ghi thẳng `"isPrivacyActive"`,
 *    `toggleMessageStar` ghi thẳng `"isStarred"`, và document cũ cũng đang mang
 *    tên đầy đủ. Logcat của bản 54.0 báo "No setter/field for isSos": nghĩa là
 *    nút SOS của con có lên máy chủ thì bố mẹ vẫn không thấy banner nguy cấp.
 *    `UsageSchedule.isReminderOnly` / `isActive` từng được để nguyên vì sợ đổi
 *    tên khóa sẽ làm bảng chặn đang chạy đọc thành "chỉ nhắc". Logcat của bản
 *    57.0 bác bỏ nỗi sợ đó: nó báo "No setter/field for reminderOnly" và
 *    "No setter/field for active" — tức hai trường ấy **vốn đã không đọc được**,
 *    máy con luôn nhận giá trị mặc định. Vì vậy bản 58.0 gắn `@get:PropertyName`
 *    và thêm `ConsentPolicy.healLegacyKeys` để document cũ vẫn còn tác dụng.
 * 2. **Tab Tổng quan in tên enum.** Thẻ "Trò chuyện gần nhất" từng hiện
 *    "Gửi cảm xúc STUDY_STRESS" — người dùng không đọc được dòng đó.
 * 3. **Chữ on máy nói sai.** Cài đặt báo "máy này chưa đăng nhập tài khoản nào"
 *    trong khi máy đang đăng nhập (chỉ thiếu email); màn Giờ dùng viết "GPS vệ
 *    sinh"; và khi đăng nhập bằng tài khoản có vai trò khác vai trò vừa chọn,
 *    app nhận vai trò máy chủ mà không nói một lời — đó là lý do lần test tài
 *    khoản con hiện ra "hah / Vai trò: Phụ huynh".
 */
class FirestoreKeyRegressionTest {

    private fun read(rel: String): String {
        val candidates = listOf(
            File("src/main/java/com/example/$rel"),
            File("app/src/main/java/com/example/$rel")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $rel (đã thử " + candidates.joinToString() + ")")
        return file.readText()
    }

    /**
     * Bo qua comment. Cac chuoi kiem tra ben duoi xuat hien nhieu lan trong
     * phan giai thich, neu doc ca comment thi test xanh ngay khi loi con nguyen.
     */
    private fun stripComments(src: String): String =
        src.lineSequence()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
            .let { Regex("/\\*[\\s\\S]*?\\*/").replace(it, " ") }

    /** Khoi than cua `data class X(...)` — dem dau ngoac de khong lan sang lop ke tiep. */
    private fun classBody(src: String, header: String): String {
        val start = src.indexOf(header)
        assertTrue("Thiếu class $header — ai đó đã đổi tên model mà không sửa test", start >= 0)
        val open = src.indexOf('(', start)
        var depth = 0
        for (i in open until src.length) {
            when (src[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return src.substring(open, i + 1)
                }
            }
        }
        error("Thân class $header không đóng ngoặc")
    }

    @Test
    fun locationDataKhaiBaoRoTenKhoaCuaBaTruongBoolean() {
        val models = stripComments(read("data/model/Models.kt"))
        val body = classBody(models, "data class LocationData")
        listOf("isCharging", "isPrivacyActive", "isSos").forEach { key ->
            assertTrue(
                "LocationData.$key thiếu @get:PropertyName → đọc document sẽ bỏ sót nó",
                body.contains("@get:PropertyName(\"$key\") val $key")
            )
        }
    }

    @Test
    fun familyMessageKhaiBaoTenKhoaDungNhuChoGhiBangMerge() {
        val models = stripComments(read("data/model/Models.kt"))
        val body = classBody(models, "data class FamilyMessage")
        assertTrue(
            "FamilyMessage.isStarred phải mang @get:PropertyName",
            body.contains("@get:PropertyName(\"isStarred\") val isStarred")
        )
        // Bang chung ben ghi dung ten day du, khong phai doan cua rieng model.
        val repo = stripComments(read("data/repository/VesperaRepository.kt"))
        assertTrue("toggleMessageStar phải ghi khóa \"isStarred\"", repo.contains("\"isStarred\""))
        assertTrue(
            "respondToPrivacyRequest phải ghi khóa \"isPrivacyActive\"",
            repo.contains("\"isPrivacyActive\"")
        )
    }

    @Test
    fun usageScheduleDocCuVanDuocDocKhongBiLap() {
        // Ban 57.0 de hai truong nay tran (khong @get:PropertyName) vi so doi ten
        // khoa lam mat bang chan cu. Logcat cho thay chieu nguoc lai: chung chua
        // bao gio doc duoc, nen can BOTH — khai bao ten day du de khoa moi chay,
        // va doc them khoa cu de gia dinh luu tu truoc khong bi "chua bat".
        val models = stripComments(read("data/model/Models.kt"))
        val body = classBody(models, "data class UsageSchedule")
        listOf("isReminderOnly", "isActive").forEach { key ->
            assertTrue(
                "UsageSchedule.$key thiếu @get:PropertyName → Firestore bỏ qua giá trị, máy con luôn ở chế độ nhắc",
                body.contains("@get:PropertyName(\"$key\") val $key")
            )
        }
        val policy = stripComments(read("service/blocker/ConsentPolicy.kt"))
        assertTrue("ConsentPolicy phải còn đường đọc khóa cũ", policy.contains("fun healLegacyKeys("))
        // Ca HAI noi doc noi quy phai di qua buoc nay — mot noi cho man hinh,
        // mot noi chinh luc chan app cua may con.
        val repo = stripComments(read("data/repository/VesperaRepository.kt"))
        assertTrue("observeSchedule phải gọi healLegacyKeys", repo.contains("ConsentPolicy.healLegacyKeys(it, snapshot.data)"))
        val blocker = stripComments(read("service/blocker/AppBlockerService.kt"))
        assertTrue("AppBlockerService phải gọi healLegacyKeys", blocker.contains("ConsentPolicy.healLegacyKeys(it, d.data)"))
    }

    @Test
    fun moiTruongBooleanIsTrongModelsDeuKhaiBaoTenKhoa() {
        // Loi nay lap lai duoc o moi truong moi viet: `val isX: Boolean` luon bi
        // cat chu "is" khi doc, va loi chi ro ra tren may that sau mot lan build.
        val models = stripComments(read("data/model/Models.kt"))
        val offenders = models.lines()
            .map { it.trim() }
            .filter { it.matches(Regex("""val is[A-Z]\w*: Boolean.*""")) }
            .filterNot { it.startsWith("@get:PropertyName(") }
        assertTrue("Thiếu @get:PropertyName: $offenders", offenders.isEmpty())
    }

    @Test
    fun thuDaDanhDauDocCaKhoaCu() {
        val repo = stripComments(read("data/repository/VesperaRepository.kt"))
        assertTrue(
            "observeMessages phải fallback khóa \"starred\" cho tin ghi bằng bản cũ",
            repo.contains("doc.getBoolean(\"starred\")")
        )
        assertFalse("Không được để OR hai khóa (tắt sao không bao giờ có tác dụng)", repo.contains("msg.isStarred || doc.getBoolean"))
    }

    @Test
    fun tongQuanKhongConInTenEnumCamXuc() {
        val code = stripComments(read("ui/screens/DashboardScreen.kt"))
        assertFalse("Vẫn in thẳng tên enum: " + code.contains("Gửi cảm xúc"), code.contains("\"Gửi cảm xúc"))
        assertTrue("Phải dịch emotion qua emotionLabel(", code.contains("emotionLabel(lastMessage.emotion)"))
        assertTrue("Thiếu hàm emotionLabel", code.contains("private fun emotionLabel(raw: String?)"))
        // Ten tieng Viet cua enum nam trong Models, la nguon duy nhat.
        assertTrue(read("data/model/Models.kt").contains("STUDY_STRESS(\"Áp lực học tập\""))
    }

    @Test
    fun tinNhanDaDanhDauDocDungTuMayChu() {
        val chat = stripComments(read("ui/screens/ChatScreen.kt"))
        assertTrue("ChatScreen phải lọc sao theo msg.isStarred", chat.contains("it.isStarred"))
    }

    @Test
    fun settingsKhongBaoChuaDangNhapKhiVanDangDangNhap() {
        val code = stripComments(read("ui/screens/SettingsScreen.kt"))
        assertFalse(
            "Dòng chữ cũ gộp hai trạng thái thành một, máy đang đăng nhập vẫn bị báo chưa",
            code.contains("máy này chưa đăng nhập tài khoản nào")
        )
        assertTrue("Phải có nhánh riêng cho tài khoản chưa có email", code.contains("Máy chưa đăng nhập tài khoản nào."))
    }

    @Test
    fun dangNhapKhongImLangDungRoleMayChu() {
        val vm = stripComments(read("ui/viewmodel/VesperaViewModel.kt"))
        assertTrue("Phải bù email/displayName còn thiếu vào document cũ", vm.contains("existing.email.ifBlank"))
        assertTrue("Phải nói rõ khi vai trò máy chủ khác vai trò vừa chọn", vm.contains("khác vai trò bạn vừa chọn"))
    }

    @Test
    fun phieuXinThemGioKhongDuocGhiTuMayPhuHuanh() {
        // Nut "Xin thêm giờ" o man Giờ dùng đã đặt trong `if (isChild)`, nhưng
        // luồng vẫn phải tự chối: mot document cu mang role PARENT thi may hoc
        // sinh van co the goi len phieu ma `childId` la uid cua bo me, hoac
        // phieu "0 phút" khi người dùng quên nhập số.
        val vm = stripComments(read("ui/viewmodel/VesperaViewModel.kt"))
        assertTrue("requestExtraTime phải chặn máy phụ huynh", vm.contains("child.role != UserRole.CHILD"))
        assertTrue("requestExtraTime phải từ chối phiếu 0 phút", vm.contains("if (minutes <= 0)"))
    }

    @Test
    fun giayPhepTuViKhongConVietSai() {
        val code = stripComments(read("ui/screens/ScreenTimeScreen.kt"))
        assertFalse("Vẫn còn \"vệ sinh\"", code.contains("vệ sinh"))
        assertTrue("Phải là GPS vệ tinh", code.contains("GPS vệ tinh"))
    }

    @Test
    fun tongQuanCoTheNoiQuyLuonHien() {
        // Ba the duoi chi moc khi co phieu/tin nhan/nhat ky. The noi quy la
        // trang thang LUON ton tai (ke ca "chua dat") nen no phu kin phan duoi
        // man hinh trong cua nhung ngay dau dung app.
        val code = stripComments(read("ui/screens/DashboardScreen.kt"))
        assertTrue("Thiếu thẻ nội quy đang áp dụng", code.contains("\"NỘI QUY ĐANG ÁP DỤNG\""))
        assertTrue("Thẻ nội quy phải có testTag", code.contains("dashboard_active_rules"))
        assertTrue("Phải mượn mô tả luật thật từ ConsentPolicy", code.contains("ConsentPolicy.describe("))
    }
}
