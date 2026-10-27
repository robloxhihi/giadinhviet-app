package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Test tinh (khong can may/emulator) cho lo 69.x:
 *  - #124 May phu huynh KHONG day thong ke app cua minh len /locations — du o
 *    ca hai cua publish (service + viewmodel). May con doc doc cua bo me thi
 *    thay rong, khong phai "bo me dang dung TikTok".
 *  - #127 May CON khong co quyen dat noi quy ung dung — chan ngay o ViewModel.
 *  - #110 Dap phan quyet dinh cua LocationJumpGuard duoc noi vao dong location,
 *    de chong tai phat "di vai met bay ca cay".
 *  - #126 Hien thi pin that bai: khong in "-1%"/"0%" gia, khong ve bieu tuong
 *    sac khi may khong sac.
 *
 * Chi so sanh tren PHAN CODE (comments da cat) vi loi giai thich trong KDoc co
 * the neu ten bien bi cam — test do vi ly do dung moi co gia tri.
 */
class TelemetryPrivacyBatteryRegressionTest {

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Khong tim thay tep: $relative")
    }

    private fun codeOf(relative: String): String = projectFile(relative).readText()
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

    private val service =
        codeOf("src/main/java/com/example/service/VesperaTelemetryService.kt")
    private val viewModel =
        codeOf("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt")
    private val dashboard =
        codeOf("src/main/java/com/example/ui/screens/DashboardScreen.kt")

    // #124 — Service: khi tu phan loai la PARENT thi ba truong thong ke bi lam
    // trang trong LocationData day len Firestore.
    @Test
    fun parentPublishesNoSelfUsageFromService() {
        assertTrue("Service phai tinh hideSelfUsage theo selfRole PARENT",
            service.contains("selfRole(currentUserId) == UserRole.PARENT"))
        listOf("topActiveApp", "topActiveAppMinutes", "totalUsedMinutes").forEach { field ->
            assertTrue("$field phai bi che khi hideSelfUsage",
                service.contains("$field = if (hideSelfUsage)"))
        }
    }

    // #124 — ViewModel: cua publish thu hai (broadcastSelfTelemetry) cung phai
    // lam trang thong ke cua phu huynh truoc khi updateLocation.
    @Test
    fun parentPublishesNoSelfUsageFromViewModel() {
        assertTrue("ViewModel phai co bien publishLocation",
            viewModel.contains("val publishLocation"))
        assertTrue("publishLocation phai gate theo role PARENT",
            viewModel.contains("user?.role == UserRole.PARENT"))
        assertTrue("Phai ghi publishLocation (ban da che), khong phai locationData goc",
            viewModel.contains("repository.updateLocation(publishLocation)"))
        // that bai o day se mo duong tro lai cho loi "con thay me dung app".
        assertFalse("Khong duoc ghi ban locationData chua thong ke len /locations",
            Regex("repository\\.updateLocation\\(locationData\\)").containsMatchIn(viewModel))
    }

    // #127 — May con bi chan o cua updateBlockRules, khong ghi duoc luat.
    @Test
    fun childCannotSetBlockRules() {
        assertTrue("ViewModel phai reject khi role == CHILD",
            viewModel.contains("role == UserRole.CHILD"))
        assertTrue("Phai goi rejectBlockRules khi bi chan",
            viewModel.contains("rejectBlockRules("))
    }

    // #110 — LocationJumpGuard noi vao dong location: quyet dinh skip/accept.
    @Test
    fun locationJumpGuardIsWiredIn() {
        assertTrue("Service phai hoi LocationJumpGuard.trước khi publish",
            service.contains("jumpGuard.decide(candidate)"))
        assertTrue("Phai xu ly verdict nhay chua xac nhan",
            service.contains("FixVerdict.SKIP_UNCONFIRMED_JUMP"))
        // Guard la lop loi thuan tuyet doi doc lap — file cua no phai ton tai.
        assertTrue("File LocationJumpGuard phai ton tai",
            File("src/main/java/com/example/service/map/LocationJumpGuard.kt").exists() ||
                File("app/src/main/java/com/example/service/map/LocationJumpGuard.kt").exists())
    }

    // #126 — Dashboard: khong in gia tri pin am/0 gia, ve muc pin that.
    @Test
    fun dashboardBatteryIsHonest() {
        assertFalse("Khong duoc in thô batteryLevel (inh '-1%')",
            Regex("\"\\$\\{partnerLoc\\.batteryLevel\\}%\"").containsMatchIn(dashboard))
        assertTrue("Pin <= 0 phai in '—' qua bien hasBat",
            dashboard.contains("val hasBat = bat > 0"))
        assertTrue("Khong co so pin thi in '—', khong in gia tri tho",
            dashboard.contains("if (hasBat) \"\$bat%\" else \"—\""))
        // Bieu tuong sac chi khi isCharging THAT — khong phai moi telemetry.
        assertTrue("Chi bao sac khi isCharging",
            dashboard.contains("partnerLoc.isCharging -> Icons.Default.BatteryChargingFull"))
    }

    // #126 — Service: khi may khong bao pin thi notification noi "chua doc duoc",
    // khong phai "0%".
    @Test
    fun serviceNotificationBatteryIsHonest() {
        assertTrue("Notification phai guard batteryLevel >= 0",
            service.contains("if (batteryLevel >= 0)"))
        assertTrue("Pin -1 phai thanh 'chua doc duoc' trong notification",
            service.contains("chưa đọc được"))
    }
}
