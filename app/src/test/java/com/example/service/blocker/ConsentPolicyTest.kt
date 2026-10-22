package com.example.service.blocker

import com.example.data.model.RequestStatus
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole
import com.example.data.model.windowCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Kiem thu [ConsentPolicy] — chay tren may tinh, khong can thiet bi.
 *
 * Tu ban 40.0 khong con che do "phieu cho ben kia duyet". Ba dieu test nay
 * giu:
 *   (1) phu huynh doi luat thi luat MOI co hieu luc ngay tren may con,
 *       khong treo cho ai duyet;
 *   (2) don sach cac ban ghi con sot che cu de khong may nao bi ket o
 *       PENDING mai mai;
 *   (3) khi don sach, LUAT DANG CHAY khong duoc bi xoa — day chinh la ly do
 *       phan "xin doi" nam o truong rieng ngay tu dau.
 *
 * Chay bang GitHub Actions — repo khong co Gradle wrapper tren may nay.
 */
class ConsentPolicyTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val game = "com.gameloft.android.ANMP.GloftA9HM"
    private val own = "com.aistudio.vesperafamily.vspxql"
    private val t = 1_700_000_000_000L

    private fun at(hour: Int, minute: Int = 0): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }

    /** luat goc moi test: dang chan that 1 app (TikTok), 200 phut/ngay, 7h-21h */
    private fun agreed(
        blocked: List<String> = listOf(tiktok),
        reminderOnly: Boolean = false,
        target: Int = 200
    ) = UsageSchedule(
        id = "default_sched",
        pairingId = "pair_1",
        blockedPackages = blocked,
        isReminderOnly = reminderOnly,
        dailyTargetMinutes = target,
        allowedStartHour = 7,
        allowedEndHour = 21,
        isActive = true,
        changeStatus = RequestStatus.APPROVED
    )

    /** Ban ghi kieu che cu: luat dang chay + mot phieu PENDING treo tren dau. */
    private fun legacyPendingRow() = agreed().copy(
        changeStatus = RequestStatus.PENDING,
        proposedByRole = UserRole.CHILD,
        pendingBlockedPackages = listOf(tiktok, game),
        pendingAllowedApps = listOf("com.android.chrome"),
        pendingIsReminderOnly = true,
        pendingDailyTargetMinutes = 90,
        pendingAllowedWindow = "09:00-17:00",
        proposedAt = t
    )

    // ── (1) Doi luat la co hieu luc ngay ─────────────────────────────────

    @Test
    fun phuHuynhDoiLuatThiApDungNgay() {
        val r = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok, game),
            now = t
        )
        assertEquals(ProposeOutcome.APPLIED, r.outcome)
        assertEquals(setOf(tiktok, game), r.schedule.blockedPackages.toSet())
        assertEquals(RequestStatus.APPROVED, r.schedule.changeStatus)
        assertEquals(UserRole.PARENT, r.schedule.proposedByRole)
        assertTrue("Luat co hieu luc thi phai bat may chan", r.schedule.isActive)
        assertEquals(t, r.schedule.respondedAt)
    }

    @Test
    fun khongConDuongTraVePhieuChoDuyet() {
        val r = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(game),
            now = t
        )
        assertEquals(ProposeOutcome.APPLIED, r.outcome)
        assertTrue(
            "Van con phan xin doi tren ban luu: ${r.schedule.pendingBlockedPackages}",
            r.schedule.pendingBlockedPackages.isEmpty()
        )
        assertEquals("", r.schedule.pendingAllowedWindow)
        assertEquals(null, r.schedule.pendingIsReminderOnly)
        assertEquals(null, r.schedule.pendingDailyTargetMinutes)
    }

    @Test
    fun banGuiTrungLuatDangChayThiKhongGhi() {
        val r = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok),
            now = t
        )
        assertEquals(ProposeOutcome.NO_CHANGE, r.outcome)
    }

    @Test
    fun thuTuDanhSachKhongDuocTinhLaNoiDungKhac() {
        val first = ConsentPolicy.propose(
            base = agreed(), byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok, game), now = t
        )
        val again = ConsentPolicy.propose(
            base = first.schedule, byRole = UserRole.PARENT,
            blockedPackages = listOf(game, tiktok), now = t + 1
        )
        assertEquals(ProposeOutcome.NO_CHANGE, again.outcome)
    }

    @Test
    fun xoaKhungGioDuocTinhLaThayDoi() {
        val r = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok),
            windowCode = "",
            now = t
        )
        assertEquals(
            "Ban co 07:00-21:00 ma ban xin xoa het thi phai ghi lai",
            ProposeOutcome.APPLIED, r.outcome
        )
        assertEquals("", r.schedule.windowCode())
    }

    @Test
    fun doiTuNhcNhoSangChanThatLaThayDoi() {
        val r = ConsentPolicy.propose(
            base = agreed(reminderOnly = true),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok),
            reminderOnly = false,
            now = t
        )
        assertEquals(ProposeOutcome.APPLIED, r.outcome)
        assertFalse("Van o che do nhac nho", r.schedule.isReminderOnly)
    }

    /** Y nghia that cua yeu cau: bam tick xong la may con chan, khong phai cho ai do. */
    @Test
    fun luatVuaLuocDuocBlockThiHanhNgay() {
        val r = ConsentPolicy.propose(
            base = agreed(blocked = emptyList(), reminderOnly = true),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(tiktok),
            reminderOnly = false,
            now = t
        )
        assertNotNull(
            "Vua luu xong ma may chua chan TikTok",
            BlockPolicy.blockReason(tiktok, r.schedule, own, usedMinutesToday = 10, now = at(20))
        )
    }

    // ── (2) Don sach ban ghi che cu ───────────────────────────────────────

    @Test
    fun banPendingCuDuocDonSachKhiDoc() {
        val clean = ConsentPolicy.normalizePending(legacyPendingRow())
        assertEquals(RequestStatus.APPROVED, clean.changeStatus)
        assertTrue(clean.pendingBlockedPackages.isEmpty())
        assertTrue(clean.pendingAllowedApps.isEmpty())
        assertEquals(null, clean.pendingIsReminderOnly)
        assertEquals(null, clean.pendingDailyTargetMinutes)
        assertEquals("", clean.pendingAllowedWindow)
    }

    @Test
    fun donSachKhongDuocXoaLuatDangChay() {
        val clean = ConsentPolicy.normalizePending(legacyPendingRow())
        assertEquals(
            "Don phieu xong may khong duoc bo chan gi ca",
            listOf(tiktok), clean.blockedPackages
        )
        assertEquals(200, clean.dailyTargetMinutes)
        assertEquals("07:00-21:00", clean.windowCode())
        assertTrue(clean.isActive)
    }

    @Test
    fun banSachKhongBiDoiToi() {
        val untouched = agreed()
        assertSame(
            "normalizePending phai tra chinh object neu khong co gi phai don",
            untouched, ConsentPolicy.normalizePending(untouched)
        )
    }

    @Test
    fun deXuatTrenBanPendingCuCungApDungNgay() {
        val r = ConsentPolicy.propose(
            base = legacyPendingRow(),
            byRole = UserRole.PARENT,
            blockedPackages = listOf(game),
            now = t
        )
        assertEquals(ProposeOutcome.APPLIED, r.outcome)
        assertEquals(listOf(game), r.schedule.blockedPackages)
        assertTrue(r.schedule.pendingBlockedPackages.isEmpty())
    }

    // ── (3) Chu mo ta phai khop voi luat dang chay ────────────────────────

    @Test
    fun moTaNeuDungSoAppHanMucVaKhungGio() {
        val text = ConsentPolicy.describe(agreed())
        assertTrue("Thieu so app chan that: $text", text.contains("chặn thật 1 app"))
        assertTrue("Thieu han muc: $text", text.contains("200 phút/ngày"))
        assertTrue("Thieu khung gio: $text", text.contains("07:00"))
    }

    @Test
    fun khongChanTheoGioPhaiNoiRanhChuaDat() {
        val text = ConsentPolicy.describe(
            agreed().copy(allowedStartHour = null, allowedEndHour = null)
        )
        assertTrue("De truong 'khong chan theo gio' thanh so khong: $text", text.contains("chưa đặt"))
    }

    @Test
    fun cheDoNhacNhoKhongDuocGoiLaChanThat() {
        val text = ConsentPolicy.describe(
            agreed(blocked = listOf(tiktok, game), reminderOnly = true)
        )
        assertTrue("Noi 'chan that' trong khi may chi nhac: $text", text.contains("chưa chặn thật"))
        assertFalse(
            "Vẫn mở đầu bằng 'chặn thật': $text",
            text.startsWith("chặn thật")
        )
    }

    @Test
    fun tenGoiVaiTroKhongBiDoi() {
        assertEquals("Phụ huynh", ConsentPolicy.label(UserRole.PARENT))
        assertEquals("Con", ConsentPolicy.label(UserRole.CHILD))
        assertEquals("Gia đình", ConsentPolicy.label(null))
    }
}
