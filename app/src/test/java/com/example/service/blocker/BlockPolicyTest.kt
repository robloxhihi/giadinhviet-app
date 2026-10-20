package com.example.service.blocker

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
