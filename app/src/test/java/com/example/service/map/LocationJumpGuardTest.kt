package com.example.service.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Test cho bo loc nhay coc dinh vi — loi nguoi dung bao "di chuyển co vai met ma
 * no bay ca cay" (ban 69.x). Toa o day la du lieu vao cua test, KHONG phai toa do
 * dat trong nguon app (FixRegressionTest.source_hasNoFabricatedCoordinates chi
 * scan src/main). Cac so duoc chon tuy y quanh 10.8/106.7, tranh Bon gia tri
 * Ha Noi/Da Nang bi cam.
 */
class LocationJumpGuardTest {

    private val lat0 = 10.8000
    private val lng0 = 106.7000

    /** 1 met kinh do ~ 1/111194.8 do vi do; dung offset do de tao khoang cach that. */
    private fun metersLat(deltaM: Double): Double = deltaM / 111_194.8
    private fun metersLng(deltaM: Double): Double =
        deltaM / (111_320.0 * Math.cos(Math.toRadians(lat0)))

    private fun fix(
        eastMeters: Double = 0.0,
        northMeters: Double = 0.0,
        accuracy: Float = 8f,
        speed: Float = 0f,
        t: Long
    ) = GuardedFix(
        latitude = lat0 + metersLat(northMeters),
        longitude = lng0 + metersLng(eastMeters),
        accuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        timestampMs = t
    )

    @Test
    fun firstFixIsAlwaysAccepted() {
        val g = LocationJumpGuard()
        assertEquals(FixVerdict.ACCEPT, g.decide(fix(t = 0)))
        assertNotNull(g.currentAnchor())
    }

    // ── Trieu chung chinh: mot phieu coarse chen giua khong duoc keo marker di ──
    @Test
    fun coarseFixDoesNotOverwriteRecentTrustedFix() {
        val g = LocationJumpGuard()
        g.decide(fix(accuracy = 6f, t = 1_000))
        val coarse = fix(eastMeters = 900.0, accuracy = 500f, t = 11_000)
        assertEquals(
            "Phieu sai so 500 m khong duoc de len phieu GPS tot con han",
            FixVerdict.SKIP_POOR_QUALITY, g.decide(coarse)
        )
        // Anchor van la diem tot dau tien (gan goc), khong phai diem coarse.
        val anchor = g.currentAnchor()!!
        assertTrue(
            "Vi tri giu lai phai o gan diem tot, khong phai noi coarse 900 m",
            LocationJumpGuard.distanceMeters(anchor, fix(t = 11_000)) < 50.0
        )
    }

    // ── Mot phieu GPS loi nhay 1 km roi quay lai: khong bao gio duoc cong bo ──
    @Test
    fun singleGlitchJumpIsRejectedThenPositionReturnsToRealPlace() {
        val g = LocationJumpGuard()
        g.decide(fix(accuracy = 8f, t = 0))
        val glitch = fix(eastMeters = 900.0, accuracy = 8f, t = 10_000)
        assertEquals(
            "Mot phieu nhay 900 m voi van toc 0 phai bi cho dong thuan",
            FixVerdict.SKIP_UNCONFIRMED_JUMP, g.decide(glitch)
        )
        // Phieu ke tiep quay ve cho cu => buoc nhay bi bac bo hoan toan.
        val backHome = fix(eastMeters = 3.0, accuracy = 8f, t = 20_000)
        assertEquals(FixVerdict.ACCEPT, g.decide(backHome))
        assertNull("Sau khi quay ve cho cu, khong con vung nghi ngo ton tai", g.pendingForTest())
    }

    // ── Di chuyen THAT duoc xac nhan sau vai phieu lien tiep ──
    @Test
    fun genuineRelocationConfirmsAfterConsecutiveAgreeingFixes() {
        val g = LocationJumpGuard()
        g.decide(fix(accuracy = 8f, t = 0))
        val new1 = fix(eastMeters = 900.0, accuracy = 10f, t = 10_000)
        assertEquals(FixVerdict.SKIP_UNCONFIRMED_JUMP, g.decide(new1))
        val new2 = fix(eastMeters = 905.0, accuracy = 10f, t = 20_000)
        assertEquals(
            "Hai phieu lien tiep cung o cho moi => di chuyển that, duoc phep",
            FixVerdict.ACCEPT, g.decide(new2)
        )
        val anchor = g.currentAnchor()!!
        val moved = LocationJumpGuard.distanceMeters(anchor, fix(t = 20_000))
        assertTrue("Anchor phai da nhay toi cho moi (~900 m), thuc te $moved", moved > 700.0)
    }

    // ── Du bo/dung yen vai met van duoc cap nhat lien tuc, khong bi tre ──
    @Test
    fun fewMetersOfWalkingUpdatesImmediately() {
        val g = LocationJumpGuard()
        g.decide(fix(accuracy = 5f, t = 0))
        val walked = fix(eastMeters = 4.0, northMeters = 3.0, accuracy = 5f, speed = 1.3f, t = 10_000)
        assertEquals(
            "Di 5 m voi GPS tot => trong dung sai, chap nhan ngay",
            FixVerdict.ACCEPT, g.decide(walked)
        )
    }

    // ── Di xe that (van toc cao) khong bi chan nhu loi ──
    @Test
    fun fastDrivingHopIsAcceptedBySpeedAllowance() {
        val g = LocationJumpGuard()
        g.decide(fix(accuracy = 8f, speed = 25f, t = 0))
        val moved = fix(eastMeters = 200.0, accuracy = 8f, speed = 25f, t = 10_000)
        assertEquals(
            "200 m trong 10 giay voi van toc 25 m/s (~90 km/h) hop ly => ACCEPT",
            FixVerdict.ACCEPT, g.decide(moved)
        )
    }

    // ── Chong dong vi tri: giu qua lau thi nha khoa, du phieu coarse ──
    @Test
    fun longHoldReleasesEvenToAPoorFix() {
        val g = LocationJumpGuard(maxHoldMs = 60_000L)
        g.decide(fix(accuracy = 6f, t = 0))
        // Sau nguong giu, mot phieu coarse cung duoc chap nhan de may khong ket
        // o vi tri cu mai khi that su da doi cho.
        val coarseLate = fix(eastMeters = 50.0, accuracy = 400f, t = 61_000)
        assertTrue(
            "Qua nguong giu thi khong duoc con tra ve SKIP_POOR_QUALITY",
            g.decide(coarseLate) != FixVerdict.SKIP_POOR_QUALITY
        )
    }

    /** Lo ham trang thai trong de kiem chung buoc nhay bi bac bo hoan toan. */
    private fun LocationJumpGuard.pendingForTest(): GuardedFix? {
        val field = LocationJumpGuard::class.java.getDeclaredField("pending")
        field.isAccessible = true
        return field.get(this) as GuardedFix?
    }
}
