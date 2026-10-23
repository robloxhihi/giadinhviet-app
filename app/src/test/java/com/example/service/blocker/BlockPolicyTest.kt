package com.example.service.blocker

import com.example.data.model.StudySession
import com.example.data.model.UsageSchedule
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Kiem thu tinh cho [BlockPolicy] — chay tren may tinh, khong can thiet bi.
 *
 * Ly do co file nay: bo chan app quyet dinh "cài gì người dùng không mở được",
 * nên một sai nhỏ ở đây là khoá loạn chiếc điện thoại. Chạy:
 *   ./gradlew :app:testDebugUnitTest
 */
class BlockPolicyTest {

    /** applicationId that cua app (khac namespace "com.example"). */
    private val own = "com.aistudio.vesperafamily.vspxql"
    private val tiktok = "com.zhiliaoapp.musically"
    private val game = "com.gameloft.android.ANMP.GloftA9HM"

    private fun at(hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

    private fun schedule(
        reminderOnly: Boolean = false,
        blocked: List<String> = emptyList(),
        allowed: List<String> = emptyList(),
        start: Int = 7,
        end: Int = 21,
        target: Int = 10_000,
        days: String = "T2, T3, T4, T5, T6, T7, CN",
        active: Boolean = true
    ) = UsageSchedule(
        id = "default_sched",
        pairingId = "pairing-test",
        dailyTargetMinutes = target,
        allowedStartHour = start,
        allowedEndHour = end,
        daysOfWeek = days,
        isReminderOnly = reminderOnly,
        blockedPackages = blocked,
        allowedApps = allowed,
        isActive = active
    )

    /** Nguoi dung co the vo tinh cho ca ban phim/CH Play vao danh sach chan. */
    @Test
    fun heThongKhongBaoGioBiChan_keCaKhiDuocLietKe() {
        val protectedPackages = listOf(
            "com.google.android.inputmethod.latin",   // ban phim
            "com.samsung.android.honeyboard",         // ban phim Samsung
            "com.google.android.gms",                 // dang nhap Google
            "com.google.android.gsf",
            "com.android.vending",                    // CH Play — de go app
            "com.android.settings",                   // noi tat chan
            "com.sec.android.app.launcher",           // man hinh chinh
            "com.google.android.apps.nexuslauncher",
            "com.android.dialer",
            "com.android.emergency"
        )
        for (pkg in protectedPackages) {
            val s = schedule(blocked = listOf(pkg))
            assertNull(
                "$pkg bi chan la loi nghiem trong (mat duong thoat)",
                BlockPolicy.blockReason(pkg, s, own, usedMinutesToday = 0, now = at(23, 30))
            )
        }
    }

    @Test
    fun khongTuChanChinhMinh() {
        val s = schedule(blocked = listOf(own))
        assertNull(BlockPolicy.blockReason(own, s, own, 0, at(23, 30)))
    }

    @Test
    fun chiNhacNho_thiKhongChan() {
        val s = schedule(reminderOnly = true, blocked = listOf(tiktok))
        assertNull(BlockPolicy.blockReason(tiktok, s, own, 0, at(12, 0)))
    }

    @Test
    fun lichTat_thiKhongChan() {
        val s = schedule(active = false, blocked = listOf(tiktok))
        assertNull(BlockPolicy.blockReason(tiktok, s, own, 0, at(12, 0)))
    }

    @Test
    fun ngoaiGio_duyNhatChoPhepTrongDanhSach() {
        val s = schedule()
        assertNotNull(
            "23:30 la ngoai khung 7:00-21:00 nen game phai bi chan",
            BlockPolicy.blockReason(game, s, own, 0, at(23, 30))
        )
        assertNull(
            "ung dung trong allowedApps thi van mo duoc ngoai gio",
            BlockPolicy.blockReason(game, schedule(allowed = listOf(game)), own, 0, at(23, 30))
        )
    }

    @Test
    fun trongGio_chiChanAppDuocLieKe() {
        val s = schedule(blocked = listOf(tiktok))
        assertNotNull(BlockPolicy.blockReason(tiktok, s, own, 0, at(12, 0)))
        assertNull(BlockPolicy.blockReason(game, s, own, 0, at(12, 0)))
    }

    /** Vi du khung dem 21:00 -> 06:00: 23:00 duoc dung, 12:00 thi khong. */
    @Test
    fun khungGioDem() {
        val dem = schedule(start = 21, end = 6)
        assertNull(BlockPolicy.blockReason(game, dem, own, 0, at(23, 0)))
        assertNull(BlockPolicy.blockReason(game, dem, own, 0, at(2, 0)))
        assertNotNull(BlockPolicy.blockReason(game, dem, own, 0, at(12, 0)))
    }

    @Test
    fun vuotHanNgay_thiChanNgoaiDanhSachChoPhep() {
        val s = schedule(target = 120)
        assertNotNull(BlockPolicy.blockReason(game, s, own, 120, at(12, 0)))
        assertNotNull(BlockPolicy.blockReason(game, s, own, 999, at(12, 0)))
        assertNull(BlockPolicy.blockReason(game, s, own, 119, at(12, 0)))
    }

    @Test
    fun ngayKhongCoTrongLichThiKhongChan() {
        val homNay = labelOfToday()
        val khac = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")
            .first { it != homNay }
        val s = schedule(blocked = listOf(tiktok), days = khac)
        assertNull(BlockPolicy.blockReason(tiktok, s, own, 0, at(12, 0)))

        val trung = schedule(blocked = listOf(tiktok), days = homNay)
        assertNotNull(BlockPolicy.blockReason(tiktok, trung, own, 0, at(12, 0)))
    }

    @Test
    fun lyDoPhaiLaChuTiengVietCoNghia() {
        val s = schedule(blocked = listOf(tiktok))
        val reason = BlockPolicy.blockReason(tiktok, s, own, 0, at(12, 0))
        assertNotNull(reason)
        assertTrue("Ly do chan phai la chuoi khong rong", reason!!.isNotEmpty())
    }

    @Test
    fun pkgRongHoacNull_thiBoQua() {
        val s = schedule(blocked = listOf(tiktok))
        assertNull(BlockPolicy.blockReason(null, s, own, 0, at(12, 0)))
        assertNull(BlockPolicy.blockReason("", s, own, 0, at(12, 0)))
        assertNull(BlockPolicy.blockReason("   ", s, own, 0, at(12, 0)))
    }

    @Test
    fun khongCoLich_thiKhongChan() {
        assertNull(BlockPolicy.blockReason(tiktok, null, own, 999, at(23, 30)))
    }

    /**
     * Phiên "học tập trung" do ba mẹ bấm từ máy họ. Khác biệt cốt lõi so với
     * Nội quy: nó KHÔNG phụ thuộc nội quy đang bật hay ở chế độ chỉ nhắc —
     * bố bấm "45 phút học" thì máy con phải chặn ngay, không cần mẹ vào bật
     * lại nội quy.
     */
    private fun study(now: Calendar, minutes: Int = 25, by: String = "Mẹ") =
        StudySession(until = now.timeInMillis + minutes * 60_000L, byName = by)

    private fun studyEnded(now: Calendar, minutesAgo: Int = 1) =
        StudySession(until = now.timeInMillis - minutesAgo * 60_000L, byName = "Mẹ")

    @Test
    fun phienHocVanChanKhiNoiQuyDaTat() {
        val s = schedule(active = false, blocked = listOf(tiktok))
        val n = at(12, 0)
        assertNotNull(
            "Nội quy tắt nhưng ba mẹ đang mở phiên học thì phải chặn",
            BlockPolicy.blockReason(game, s, own, 0, n, study = study(n))
        )
    }

    @Test
    fun phienHocVanChanKhiNoiQuyChiNhacNho() {
        val s = schedule(reminderOnly = true, blocked = listOf(tiktok))
        val n = at(12, 0)
        assertNotNull(
            "Chế độ chỉ nhắc không được vô hiệu hoá phiên học",
            BlockPolicy.blockReason(game, s, own, 0, n, study = study(n))
        )
    }

    @Test
    fun khongCoNoiQuyGiCa_phienHocVanChan() {
        val n = at(12, 0)
        assertNotNull(
            "Máy chưa có nội quy nào vẫn phải theo phiên học",
            BlockPolicy.blockReason(game, null, own, 0, n, study = study(n))
        )
    }

    @Test
    fun phienHocChoPhepUngDungTrongDanhSachMienChan() {
        val n = at(12, 0)
        val s = schedule(allowed = listOf(game))
        assertNull(
            "App đã được gia đình cho phép thì phiên học không chặn",
            BlockPolicy.blockReason(game, s, own, 0, n, study = study(n))
        )
    }

    /** Phiên học chặn DÀI hơn nội quy thường, nên danh sách thoát vẫn phải còn. */
    @Test
    fun heThongVanDuocMoTrongPhienHoc() {
        val n = at(12, 0)
        val s = schedule()
        for (pkg in listOf(
            "com.android.settings", "com.sec.android.app.launcher",
            "com.google.android.inputmethod.latin", "com.android.vending",
            "com.android.dialer", own
        )) {
            assertNull("$pkg không được phép bị phiên học chặn",
                BlockPolicy.blockReason(pkg, s, own, 0, n, study = study(n)))
        }
    }

    @Test
    fun phienHocHetHan_thiRoiVeLuatThuong() {
        val banNgay = at(12, 0)
        val s = schedule()
        assertNull(
            "Hết giờ học thì trong khung giờ ban ngày phải được dùng máy bình thường",
            BlockPolicy.blockReason(game, s, own, 0, banNgay, study = studyEnded(banNgay))
        )
        val banDem = at(23, 30)
        assertNotNull(
            "Nhưng luật ngoài khung giờ vẫn còn hiệu lực",
            BlockPolicy.blockReason(game, s, own, 0, banDem, study = studyEnded(banDem))
        )
    }

    @Test
    fun lyDoPhienHocPhaiNeuTenNguoiBatDau() {
        val n = at(12, 0)
        val reason = BlockPolicy.blockReason(game, schedule(), own, 0, n, study = study(n, by = "Bà"))
        assertNotNull(reason)
        assertTrue("Lý do chặn phải nói ai đã bật phiên học: $reason", reason!!.contains("Bà"))
    }

    private fun labelOfToday(): String = when (Calendar.getInstance()
        .get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "T2"
        Calendar.TUESDAY -> "T3"
        Calendar.WEDNESDAY -> "T4"
        Calendar.THURSDAY -> "T5"
        Calendar.FRIDAY -> "T6"
        Calendar.SATURDAY -> "T7"
        else -> "CN"
    }
}
