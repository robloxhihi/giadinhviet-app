package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Hồi quy cho lô "đẹp hơn và dày hơn" đo được trên máy thật ngày 2026-10-23.
 *
 * Ảnh chụp màn hình (BlueStacks, 1080x1920, bản 52.0) cho thấy ba lỗi hình
 * thức và một lỗ hổng chức năng, tất cả đều nhìn thấy bằng mắt thường:
 *
 * 1. Tab Tổng quan khi ĐÃ ghép nối chỉ có ba thẻ, nửa dưới màn hình trống
 *    trơn. Lý do không phải thiếu số liệu: `extraTimeRequests`, `messages`,
 *    `ruleAudit` và `lastRealtimeSyncAt` đều đã được listener nạp vào
 *    `VesperaUiState` nhưng không màn nào vẽ. Riêng hai tham số
 *    `onApproveExtraTime` / `onRejectExtraTime` của DashboardScreen nằm chết,
 *    nghĩa là con xin thêm giờ thì bố mẹ không bao giờ thấy phiếu.
 * 2. Chấm "trực tuyến" cạnh avatar sơn xanh CO DINH, nên máy mất mạng vẫn báo
 *    đang online.
 * 3. Chữ trong avatar gim "H"/"P" theo vai trò, không lấy từ tên — người dùng
 *    tên "hah" vẫn thấy chữ "P".
 * 4. Ba nút hành động dùng ký tự biểu cảm (emoji) làm icon. Emoji do hệ điều
 *    hành vẽ nên mỗi máy ra một hình và lệch cỡ so với chữ.
 * 5. Tab Giờ dùng đặt thẻ "Thỏa thuận giờ dùng" ABOVE tiêu đề trang, nên
 *    người dùng thấy một thẻ lạ trước rồi mới biết mình đang ở đâu.
 *
 * Test này đọc mã nguồn, không dựng Compose (Robolectric không render được
 * BentoGrid), và cố tình KHÔNG kiểm tra chuỗi giải thích trong comment: xem
 * `stripComments` bên dưới — bài học từ lần CI v50 chết vì chính test của tôi.
 */
class DashboardDensityRegressionTest {

    /**
     * Thu muc lam viec cua don test khong phai luc nao cung la thu muc `app`:
     * tren CI no co the la goc repo. Cac test khac trong repo nay da dung two
     * candidates, nen doc theo mot kieu de that bai muon cung la that bai vi
     * chuoi sai, khong vi duong dan.
     */
    private fun read(rel: String): String {
        val root = "src/main/java/com/example/ui/screens/$rel"
        val candidates = listOf(File(root), File("app/$root"))
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp nguồn: $root (thử cả " + candidates.joinToString() + ")")
        return String(file.readBytes(), Charsets.UTF_8)
    }

    /**
     * Cat chu thich truoc khi noi "ma nguon KHONG con chuoi X". Mot dong giai
     * thich ve cai loi cu cung du lam phan dinh nay that bai — do la thu da
     * lam CI cua ban 50 chet.
     *
     * Phai cat theo DONG cho `//`, khong dung Regex("//[^\\n]*"): no an ca
     * `https://` nam ben trong chuoi URL.
     */
    private fun stripComments(source: String): String = source
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    @Test
    fun phieuXinThemGioPhaiDuocDuyetNgayTongQuan() {
        val code = stripComments(read("DashboardScreen.kt"))
        assertTrue("Không còn đọc danh sách phiếu xin thêm giờ", code.contains("uiState.extraTimeRequests"))
        assertTrue("Không còn lọc phiếu PENDING", code.contains("RequestStatus.PENDING"))
        assertTrue("onApproveExtraTime vẫn là tham số chết", code.contains("onApproveExtraTime(req.id"))
        assertTrue("onRejectExtraTime vẫn là tham số chết", code.contains("onRejectExtraTime(req.id"))
        assertTrue("Thiếu nhãn phiếu", code.contains("CHỜ BẠN DUYỆT"))
    }

    @Test
    fun khongDuyetMotPhieuKhongCoSoPhut() {
        val code = stripComments(read("DashboardScreen.kt"))
        // Nut Duyệt chỉ được tồn tại khi phiếu có số phút lớn hơn 0; nếu không
        // thì duyệt là đặt hạn mức 0 phút.
        assertTrue("Nut Duyệt không bị chặn bởi số phút", code.contains("if (mins > 0)"))
        assertTrue(
            "Phải nói rõ phiếu không có số phút thay vì in \"0 phút\"",
            code.contains("phiếu không kèm số phút")
        )
        assertFalse("Không được in chuỗi \"0 phút\"", code.contains("\"\$who xin thêm 0 phút\""))
    }

    @Test
    fun soLieuCuaConKhongDuocLaiThanhSoLieuCuaMe()
    {
        val code = stripComments(read("DashboardScreen.kt"))
        // Tin nhắn và nhật ký nội quy phải lấy đúng danh sách đã ghép nối,
        // không được tự đặt tên người gửi.
        assertTrue("Không vẽ tin nhắn gần nhất", code.contains("uiState.messages.maxByOrNull"))
        assertTrue("Không vẽ nhật ký nội quy", code.contains("uiState.ruleAudit"))
        assertTrue(
            "Tên người gửi phải lấy từ chính bản ghi, thiếu thì nói chung",
            code.contains("lastMessage.senderName.ifEmpty")
        )
        assertFalse("Không được đoán tên người nhà", code.contains("\"Mẹ\""))
    }

    @Test
    fun mocThoiGianDongBoPhaiInRaDuoc() {
        val code = stripComments(read("DashboardScreen.kt"))
        assertTrue("lastRealtimeSyncAt vẫn không màn nào vẽ", code.contains("uiState.lastRealtimeSyncAt"))
        assertTrue("Thiếu hàm định dạng thời gian tương đối", code.contains("private fun minutesAgoLabel"))
        assertTrue("Phải nói rõ khi chưa có mốc nào", code.contains("chưa có mốc thời gian"))
    }

    @Test
    fun chamTuyenPhaiTheoNetworkMonitor() {
        val code = stripComments(read("DashboardScreen.kt"))
        assertTrue("Chấm trạng thái vẫn không đọc isOnline", code.contains("when (uiState.isOnline)"))
        assertFalse(
            "Avatar vẫn gim chữ H/P theo vai trò",
            code.contains("if (isChild) \"H\" else \"P\"")
        )
        assertTrue("Chữ avatar phải lấy từ tên hiển thị", code.contains("displayName.take(1)"))
    }

    @Test
    fun nutHanhDongKhongDungEmojiLamIcon() {
        val code = stripComments(read("DashboardScreen.kt"))
        // Ký tự biểu cảm bị hệ điều hành vẽ, nên mỗi máy một hình. Dò theo
        // khoảng mã hơn là dò từng emoji cụ thể.
        val emoji = Regex("[\\u{1F300}-\\u{1FAFF}\\u{2600}-\\u{27BF}]")
        val hits = emoji.findAll(code).map { it.value }.toList()
        assertTrue("Tab Tổng quan vẫn còn ký tự biểu cảm trong mã chạy: $hits", hits.isEmpty())
        assertTrue("Thiếu nút icon vector", code.contains("DashboardActionButton("))
        assertTrue("bumpStateLabel phải còn được dùng", code.contains("bumpStateLabel("))
    }

    @Test
    fun tieuDeTrangPhaiNamTrenCungCuaTabGioDung() {
        val code = stripComments(read("ScreenTimeScreen.kt"))
        val header = code.indexOf("QUẢN LÝ THỜI GIAN")
        val agreement = code.indexOf("THỎA THUẬN GIỜ DÙNG")
        assertTrue("Thiếu một trong hai nhãn", header >= 0 && agreement >= 0)
        assertTrue(
            "Thẻ \"Thỏa thuận giờ dùng\" vẫn đứng trên tiêu đề trang",
            header < agreement
        )
    }
}
