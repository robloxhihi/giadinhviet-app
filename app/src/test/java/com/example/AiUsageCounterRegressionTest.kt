package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bảng xếp hạng "ai dùng AI nhiều nhất" và nguong 100 luot/ngay chi co gia tri
 * neu so lieu duoc ghi THAT va duoc giu o tang may chu.
 *
 * Test nay giu nam dieu, vi ca nam deu tung bi lam sai hoac de sai trong du an:
 *  1. Co mot cho duy nhat ghi bo dem, dung `increment` cho tong ca doi (hai may
 *     cung ghi mot luc se de nhau nen khong doc-rui-ghi).
 *  2. Bo dem chi tang khi request AI that su thanh cong.
 *  3. Tu 100 luot thi app PHAI cat truoc khi goi mang — "chặn" mà vẫn gửi câu
 *     hỏi lên Gemini là nói dối người dùng.
 *  4. `firestore.rules` giu con so: khong vuot 100, khong duoc gia trong cung
 *     mot ngay (xoa du lieu app khong reset duoc luot hom nay).
 *  5. App quan tri phan biet "0 luot" voi "chua co so lieu", va may nguoi dung
 *     khong co quyen tu cam ai — nguong 100 khong kem theo lenh cam nao.
 */
class AiUsageCounterRegressionTest {

    private fun source(relative: String): String {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../admin/$relative"),
            File("admin/$relative")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $relative")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    private fun repoFile(name: String): String {
        val candidates = listOf(File(name), File("../$name"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp dự án: $name")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    @Test
    fun demSoDuocGhiLenFirestoreBangIncrement() {
        val counter = source("src/main/java/com/example/data/AiUsageCounter.kt")
        assertTrue("Phải dùng FieldValue.increment, không đọc-rồi-ghi", counter.contains("FieldValue.increment(1)"))
        assertTrue("Phải merge-set vì tài khoản cũ chưa có trường này", counter.contains("SetOptions.merge()"))
        assertTrue("Phải đi qua FirestoreDb (database có tên, không phải default)", counter.contains("FirestoreDb.instance()"))
        assertTrue("Không được bịa số khi chưa đăng nhập", counter.contains("return") && counter.contains("currentUser?.uid"))
    }

    @Test
    fun luotHomNayGhiKemNhayNgayVaTheoGioVN() {
        val counter = source("src/main/java/com/example/data/AiUsageCounter.kt")
        assertTrue("Phai ghi ca ngay chuoi de rules biet con so cua ngay nao", counter.contains("\"aiUsageDay\""))
        assertTrue("Phai co so luot cua rieng hom nay", counter.contains("\"aiUsageToday\""))
        assertTrue(
            "Phai gim múi gio Asia/Ho_Chi_Minh, dung lay gio may vi tat dong ho la duoc ngay moi",
            counter.contains("Asia/Ho_Chi_Minh")
        )
        assertTrue("Nguong 100 phai la mot hang so, khong phai phan trong chuoi", counter.contains("DAILY_LIMIT = 100"))
        assertTrue("Nguong canh bao 50 phai la hang so", counter.contains("WARN_AT = 50"))
    }

    @Test
    fun chiTinhKhiAiTraLoiThanhCong() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        val goiDem = screen.indexOf("AiUsageCounter.recordHit(context)")
        assertTrue("Màn hình AI phải báo cho bộ đếm", goiDem > 0)
        val thanhCong = screen.indexOf("answer.onSuccess")
        val thatBai = screen.indexOf(".onFailure")
        assertTrue("Chỉ được đếm trong onSuccess, không đếm ở onFailure", thanhCong < goiDem && goiDem < thatBai)
    }

    @Test
    fun hetSuotThiCatTruocKhiGoiMayChuAI() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        // So trong than ham send(): if (exhausted) ... return PHAI xuong truoc
        // dong goi AiModels.chat. Thu `indexOf` ca file se cho ket lua vi hang
        // so va banner cung chua chu "exhausted".
        val sendBody = screen.substringAfter("fun send(question: String")
        val chan = sendBody.indexOf("AI_EXHAUSTED_MESSAGE")
        val goiMayChu = sendBody.indexOf("AiModels.chat(")
        assertTrue("Phải báo cho người dùng vì sao không gửi được", chan > 0)
        assertTrue("Phải gọi mạng trong send()", goiMayChu > 0)
        assertTrue("Hết suất thì phải chặn TRƯỚC khi gửi request", chan < goiMayChu)
        assertTrue("Nút gửi phải tắt khi hết suất", screen.contains("hasKey && !busy && !exhausted"))
    }

    @Test
    fun rulesGiuConSoKhongChoVuotVaKhongChoGia() {
        val rules = repoFile("firestore.rules")
        assertTrue("Rules phải từ chối con số trên 100", rules.contains("request.resource.data.aiUsageToday <= 100"))
        assertTrue("Rules phải có hàm chặn giảm trong cùng ngày", rules.contains("aiQuotaNotShrunk"))
        assertTrue(
            "allow update cua /users phai goi ca hai ham kiem nguong",
            rules.contains("aiQuotaFieldsValid() && aiQuotaNotShrunk()")
        )
    }

    @Test
    fun mayNguoiDungKhongTuCamDuocAi() {
        // Nguong 100 chi NGUNG HOI. Lenh cam la quyet dinh cua con nguoi trong
        // app quan tri — neu code tu dong ghi /bans thi may cham may con bi cam.
        val counter = source("src/main/java/com/example/data/AiUsageCounter.kt")
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        assertTrue("Bộ đếm không được đụng tới collection /bans", !counter.contains("\"bans\""))
        assertTrue("Màn hình AI không được ghi lenh cam", !screen.contains("collection(\"bans\")"))
    }

    @Test
    fun adminPhanBietChuaCoSoLieuVoiKhongZeroLuot() {
        val admin = source("src/main/java/com/aistudio/admin/MainActivity.kt")
        assertTrue("Trường đọc phải là aiCallCount", admin.contains("get(\"aiCallCount\")"))
        assertTrue("Thiếu trường phải thành -1, không thành 0", admin.contains("?: -1L"))
        assertTrue("Phải hiện chữ 'chưa có số liệu' chứ không hiện 0", admin.contains("AI: chưa có số liệu"))
    }

    @Test
    fun adminThayNguongHomNayVaKhongCamTuDong() {
        val admin = source("src/main/java/com/aistudio/admin/MainActivity.kt")
        assertTrue("Admin phải đọc số lượt hôm nay", admin.contains("get(\"aiUsageToday\")"))
        assertTrue("Admin phải kiểm tra con số đó là của hôm nay", admin.contains("getString(\"aiUsageDay\")"))
        assertTrue(
            "Thiếu số liệu hôm nay phải hiện 'chưa đo', không hiện 0",
            admin.contains("Hôm nay: chưa đo")
        )
        assertTrue("Nguong trong admin phai trung voi app nguoi dung", admin.contains("AI_DAILY_LIMIT = 100L"))
        assertTrue("App quan tri phai noi ro khong ai bi cam tu dong", admin.contains("không ai bị"))
    }
}
