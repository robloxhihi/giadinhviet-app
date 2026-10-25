package com.example

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Bảng xếp hạng "ai dùng AI nhiều nhất" trong app Quản Trị Viên chỉ có giá trị
 * nếu số liệu được ghi thật từ máy người dùng. Trước lô này lịch sử chat nằm
 * trọn trong `getSharedPreferences`, máy chủ không biết gì — mọi con số đưa ra
 * đều là bịa.
 *
 * Test này giữ ba điều, vì cả ba đều từng bị làm sai trong dự án:
 *  1. Có một chỗ duy nhất ghi bộ đếm, và nó dùng `increment` (không đọc- rồi-ghi,
 *     vì hai máy cùng gửi một lúc sẽ đè lên nhau).
 *  2. Bộ đếm chỉ tăng khi request AI thực sự thành công.
 *  3. App quản trị phân biệt "0 lượt" với "chưa có số liệu" — hiển thị 0 cho tài
 *     khoản chưa từng được đo là nói dối theo cách khác.
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

    @Test
    fun demSoDuocGhiLenFirestoreBangIncrement() {
        val counter = source("src/main/java/com/example/data/AiUsageCounter.kt")
        assertTrue("Phải dùng FieldValue.increment, không đọc-rồi-ghi", counter.contains("FieldValue.increment(1)"))
        assertTrue("Phải merge-set vì tài khoản cũ chưa có trường này", counter.contains("SetOptions.merge()"))
        assertTrue("Phải đi qua FirestoreDb (database có tên, không phải default)", counter.contains("FirestoreDb.instance()"))
        assertTrue("Không được bịa số khi chưa đăng nhập", counter.contains("return") && counter.contains("currentUser?.uid"))
    }

    @Test
    fun chiTinhKhiAiTraLoiThanhCong() {
        val screen = source("src/main/java/com/example/ui/screens/AiChatScreen.kt")
        val goiDem = screen.indexOf("AiUsageCounter.recordHit()")
        assertTrue("Màn hình AI phải báo cho bộ đếm", goiDem > 0)
        val thanhCong = screen.indexOf("answer.onSuccess")
        val thatBai = screen.indexOf(".onFailure")
        assertTrue("Chỉ được đếm trong onSuccess, không đếm ở onFailure", thanhCong < goiDem && goiDem < thatBai)
    }

    @Test
    fun adminPhanBietChuaCoSoLieuVoiKhongZeroLuot() {
        val admin = source("src/main/java/com/aistudio/admin/MainActivity.kt")
        assertTrue("Trường đọc phải là aiCallCount", admin.contains("get(\"aiCallCount\")"))
        assertTrue("Thiếu trường phải thành -1, không thành 0", admin.contains("?: -1L"))
        assertTrue("Phải hiện chữ 'chưa có số liệu' chứ không hiện 0", admin.contains("AI: chưa có số liệu"))
    }
}
