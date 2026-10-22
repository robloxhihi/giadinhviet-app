package com.example.service.blocker

import com.example.data.model.RequestStatus
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Kiem thu [ConsentPolicy] — chay tren may tinh, khong can thiet bi.
 *
 * File nay dang test hon nom la: co che "me con dong long" chi co nghia neu
 * (1) phieu de xuat khong vo tinh tat bo chan dang chay, va (2) ben de xuat
 * khong the tu thong qua chinh minh. Hai dieu do la logic thuan, test duoc
 * ma chua can may that.
 *
 * Chay: ./gradlew :app:testDebugUnitTest
 */
class ConsentPolicyTest {

    private val tiktok = "com.zhiliaoapp.musically"
    private val game = "com.gameloft.android.ANMP.GloftA9HM"
    private val fb = "com.facebook.katana"
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

    // ── De xuat ───────────────────────────────────────────────────────────

    @Test
    fun `me de xuat thi tao phieu PENDING chu khong ap dung thang`() {
        val r = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(tiktok, game, fb),
            now = t
        )
        assertEquals(ProposeOutcome.PENDING, r.outcome)
        assertEquals(RequestStatus.PENDING, r.schedule.changeStatus)
        assertEquals(UserRole.PARENT, r.schedule.proposedByRole)
        assertEquals(3, r.schedule.pendingBlockedPackages.size)
        assertEquals(t, r.schedule.proposedAt)
    }

    @Test
    fun `de xuat khong ghi de len luat dang chay`() {
        // Loi nghiem trong nhat: neu propose() dong `blockedPackages` thi may
        // cua con se khong chan gi ca trong thoi gian cho duyet.
        val base = agreed(blocked = listOf(tiktok))
        val r = ConsentPolicy.propose(
            base = base,
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(game),
            now = t
        )
        assertEquals(listOf(tiktok), r.schedule.blockedPackages)
        assertEquals(listOf(game), r.schedule.pendingBlockedPackages)
    }

    @Test
    fun `chua ghep noi thi ap dung thang vi khong co ai de hoi`() {
        val r = ConsentPolicy.propose(
            base = agreed(blocked = emptyList(), reminderOnly = true),
            byRole = UserRole.PARENT,
            hasCounterparty = false,
            blockedPackages = listOf(tiktok),
            reminderOnly = false,
            now = t
        )
        assertEquals(ProposeOutcome.APPLIED, r.outcome)
        assertEquals(RequestStatus.APPROVED, r.schedule.changeStatus)
        assertEquals(listOf(tiktok), r.schedule.blockedPackages)
        assertFalse(r.schedule.isReminderOnly)
        assertTrue(r.schedule.pendingBlockedPackages.isEmpty())
    }

    @Test
    fun `de xuat trung voi luat dang chay thi khong tao phieu`() {
        val base = agreed(blocked = listOf(tiktok))
        val r = ConsentPolicy.propose(
            base = base,
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(tiktok),
            now = t
        )
        assertEquals(ProposeOutcome.NO_CHANGE, r.outcome)
        assertEquals(base, r.schedule)
    }

    @Test
    fun `bo chon ve dung nhu cu thi phieu dang mo bi go`() {
        // Me mo phieu (them game), roi bo quay lai dung luat cu -> phieu phai
        // bi go, treo lai chu "cho duyet" voi noi dung rong la loi.
        val withVote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(tiktok, game), now = t
        ).schedule
        val r = ConsentPolicy.propose(
            withVote, UserRole.PARENT, hasCounterparty = true, listOf(tiktok), now = t + 1000
        )
        assertEquals(ProposeOutcome.WITHDRAWN, r.outcome)
        assertEquals(RequestStatus.APPROVED, r.schedule.changeStatus)
        assertTrue(r.schedule.pendingBlockedPackages.isEmpty())
    }

    @Test
    fun `bam luu hai lan cung noi dung thi lan hai la NO_CHANGE`() {
        val first = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game), now = t
        )
        val second = ConsentPolicy.propose(
            first.schedule, UserRole.PARENT, hasCounterparty = true, listOf(game), now = t + 5
        )
        assertEquals(ProposeOutcome.NO_CHANGE, second.outcome)
    }

    @Test
    fun `thu tu danh sach khong duoc tinh la noi dung khac`() {
        val first = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(tiktok, game), now = t
        )
        val again = ConsentPolicy.propose(
            first.schedule, UserRole.PARENT, hasCounterparty = true, listOf(game, tiktok), now = t + 1
        )
        assertEquals(ProposeOutcome.NO_CHANGE, again.outcome)
    }

    // ── Duyet ─────────────────────────────────────────────────────────────

    @Test
    fun `con dong y thi luat moi bat dau co hieu luc`() {
        val vote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game, fb), now = t
        ).schedule
        val r = ConsentPolicy.respond(vote, UserRole.CHILD, accept = true, now = t + 10)
        assertEquals(RespondOutcome.APPROVED, r.outcome)
        assertEquals(listOf(game, fb), r.schedule.blockedPackages)
        assertEquals(RequestStatus.APPROVED, r.schedule.changeStatus)
        assertEquals(t + 10, r.schedule.respondedAt)
        assertTrue(r.schedule.pendingBlockedPackages.isEmpty())
    }

    @Test
    fun `con tu choi thi luat cu van nguyen ven`() {
        val base = agreed(blocked = listOf(tiktok))
        val vote = ConsentPolicy.propose(
            base, UserRole.PARENT, hasCounterparty = true, listOf(game), now = t
        ).schedule
        val r = ConsentPolicy.respond(vote, UserRole.CHILD, accept = false, now = t + 10)
        assertEquals(RespondOutcome.REJECTED, r.outcome)
        assertEquals(listOf(tiktok), r.schedule.blockedPackages)
        assertEquals(RequestStatus.REJECTED, r.schedule.changeStatus)
        assertEquals(200, r.schedule.dailyTargetMinutes)
    }

    @Test
    fun `me khong the tu duyet phieu me da de xuat`() {
        val vote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game), now = t
        ).schedule
        val r = ConsentPolicy.respond(vote, UserRole.PARENT, accept = true, now = t + 1)
        assertEquals(RespondOutcome.WRONG_SIDE, r.outcome)
        assertEquals(RequestStatus.PENDING, r.schedule.changeStatus)
        assertEquals(listOf(tiktok), r.schedule.blockedPackages)
    }

    @Test
    fun `con cung khong tu duyet duoc de xuat cua chinh con`() {
        val vote = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.CHILD,
            hasCounterparty = true,
            blockedPackages = emptyList(),
            reminderOnly = true,
            now = t
        ).schedule
        val r = ConsentPolicy.respond(vote, UserRole.CHILD, accept = true, now = t + 1)
        assertEquals(RespondOutcome.WRONG_SIDE, r.outcome)
    }

    @Test
    fun `duyet khi khong co phieu mo thi khong doi gi`() {
        val base = agreed()
        val r = ConsentPolicy.respond(base, UserRole.CHILD, accept = true, now = t)
        assertEquals(RespondOutcome.NOTHING_PENDING, r.outcome)
        assertEquals(base, r.schedule)
    }

    @Test
    fun `chuyen sang chan that chi co hieu luc sau khi ben kia dong y`() {
        val base = agreed(blocked = listOf(tiktok), reminderOnly = true)
        val vote = ConsentPolicy.propose(
            base = base,
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(tiktok),
            reminderOnly = false,
            now = t
        ).schedule
        assertEquals(RequestStatus.PENDING, vote.changeStatus)
        assertTrue(vote.isReminderOnly) // van la "chi nhac" cho toi khi duyet

        val ok = ConsentPolicy.respond(vote, UserRole.CHILD, accept = true, now = t + 1).schedule
        assertFalse(ok.isReminderOnly)
        assertEquals(listOf(tiktok), ok.blockedPackages)
    }

    @Test
    fun `rut de xuat chi ben goi ra phep lam`() {
        val vote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game), now = t
        ).schedule
        assertEquals(
            RespondOutcome.WRONG_SIDE,
            ConsentPolicy.withdraw(vote, UserRole.CHILD, now = t + 1).outcome
        )
        val r = ConsentPolicy.withdraw(vote, UserRole.PARENT, now = t + 2)
        assertEquals(RespondOutcome.APPROVED, r.outcome)
        assertEquals(RequestStatus.APPROVED, r.schedule.changeStatus)
        assertEquals(listOf(tiktok), r.schedule.blockedPackages)
    }

    // ── Noi voi BlockPolicy: dieu xay ra that tren may ────────────────────

    @Test
    fun `trong luc cho duyet may van chan theo luat cu`() {
        val vote = ConsentPolicy.propose(
            agreed(blocked = listOf(tiktok)), UserRole.PARENT,
            hasCounterparty = true, listOf(game), now = t
        ).schedule
        // TikTok bi chan theo luat da dong y -> phai con bi chan.
        assertNotNull(BlockPolicy.blockReason(tiktok, vote, own, usedMinutesToday = 10, now = at(20)))
        // Game la noi dung MOI, chua duoc duyet -> chua duoc quyen chan.
        assertNull(BlockPolicy.blockReason(game, vote, own, usedMinutesToday = 10, now = at(20)))
    }

    @Test
    fun `che do chi nhac khong chan du khi phieu dang cho duyet`() {
        val base = agreed(blocked = listOf(tiktok), reminderOnly = true)
        val vote = ConsentPolicy.propose(
            base = base,
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(tiktok),
            reminderOnly = false,
            now = t
        ).schedule
        assertNull(BlockPolicy.blockReason(tiktok, vote, own, 10, now = at(20)))
        val ok = ConsentPolicy.respond(vote, UserRole.CHILD, accept = true, now = t + 1).schedule
        assertNotNull(BlockPolicy.blockReason(tiktok, ok, own, 10, now = at(20)))
    }

    @Test
    fun `tu choi khong lam mat kha nang chan cua luat dang chay`() {
        val vote = ConsentPolicy.propose(
            base = agreed(blocked = listOf(tiktok)),
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = emptyList(),
            reminderOnly = true,
            now = t
        ).schedule
        val no = ConsentPolicy.respond(vote, UserRole.CHILD, accept = false, now = t + 1).schedule
        assertNotNull(BlockPolicy.blockReason(tiktok, no, own, 10, now = at(20)))
    }

    @Test
    fun `bo chan toan bo cung la mot thay doi can duyet`() {
        val vote = ConsentPolicy.propose(
            base = agreed(blocked = listOf(tiktok)),
            byRole = UserRole.CHILD,
            hasCounterparty = true,
            blockedPackages = emptyList(),
            reminderOnly = true,
            now = t
        ).schedule
        assertEquals(RequestStatus.PENDING, vote.changeStatus)
        assertEquals(UserRole.CHILD, vote.proposedByRole)
        assertNotNull(BlockPolicy.blockReason(tiktok, vote, own, 10, now = at(20)))
        // Me duyet -> khong con app nao bi chan theo ten.
        val ok = ConsentPolicy.respond(vote, UserRole.PARENT, accept = true, now = t + 1).schedule
        assertTrue(ok.blockedPackages.isEmpty())
        assertTrue(ok.isReminderOnly)
        assertNull(BlockPolicy.blockReason(tiktok, ok, own, 10, now = at(20)))
    }

    // ── Giao dien: banner va quyen bam nut ────────────────────────────────

    @Test
    fun `banner bao ro ai de xuat va ai can duyet`() {
        val vote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game, fb), now = t
        ).schedule
        val childView = ConsentPolicy.summary(vote, UserRole.CHILD)!!
        val parentView = ConsentPolicy.summary(vote, UserRole.PARENT)!!
        assertTrue(childView, childView.contains("chờ bạn duyệt"))
        assertTrue(parentView, parentView.contains("chờ Con duyệt"))
        assertTrue(childView, childView.startsWith("Phụ huynh đề xuất"))
    }

    @Test
    fun `khong hien banner khi trang thai binh thuong`() {
        assertNull(ConsentPolicy.summary(agreed(), UserRole.PARENT))
        assertNull(ConsentPolicy.summary(null, UserRole.PARENT))
    }

    @Test
    fun `chi ben kia thay nut dong y con ben goi ra thay nut rut`() {
        val vote = ConsentPolicy.propose(
            agreed(), UserRole.PARENT, hasCounterparty = true, listOf(game), now = t
        ).schedule
        assertTrue(ConsentPolicy.canVote(vote, UserRole.CHILD))
        assertFalse(ConsentPolicy.canVote(vote, UserRole.PARENT))
        assertTrue(ConsentPolicy.canWithdraw(vote, UserRole.PARENT))
        assertFalse(ConsentPolicy.canWithdraw(vote, UserRole.CHILD))
    }

    @Test
    fun `mo ta noi dung de xuat du de nguoi doc hieu ngay`() {
        val vote = ConsentPolicy.propose(
            base = agreed(),
            byRole = UserRole.PARENT,
            hasCounterparty = true,
            blockedPackages = listOf(game, fb),
            reminderOnly = false,
            dailyTargetMinutes = 90,
            now = t
        ).schedule
        val d = ConsentPolicy.describe(vote, pending = true)
        assertTrue(d, d.contains("chặn thật 2 app"))
        assertTrue(d, d.contains("hạn mức 200 → 90 phút/ngày"))
    }

    @Test
    fun `ten goi vai tro khong bi loi`() {
        assertEquals("Phụ huynh", ConsentPolicy.label(UserRole.PARENT))
        assertEquals("Con", ConsentPolicy.label(UserRole.CHILD))
        assertEquals("Gia đình", ConsentPolicy.label(null))
        assertEquals(UserRole.CHILD, ConsentPolicy.opposite(UserRole.PARENT))
    }
}
