package com.example

import com.example.data.model.hasRealFix
import com.example.service.map.OfflineMapDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kiểm thử tĩnh (không cần thiết bị/emulator) cho các lỗi đã sửa trong vòng nâng cấp 2:
 *  - Manifest khai báo đủ quyền cho luồng runtime + quyền đặc biệt.
 *  - Danh sách 34 tỉnh thành cho bộ tải bản đồ offline.
 *  - Nguồn không còn mẫu "báo thành công giả" nguy hiểm.
 */
class FixRegressionTest {

    private fun projectFile(relative: String): File {
        // Unit test chạy với working dir = app/ (module dir)
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    @Test
    fun manifest_declaresCriticalPermissions() {
        val manifest = projectFile("src/main/AndroidManifest.xml").readText()
        val required = listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.FOREGROUND_SERVICE_LOCATION",
            "android.permission.PACKAGE_USAGE_STATS"
        )
        required.forEach { perm ->
            assertTrue("Thiếu quyền trong manifest: $perm", manifest.contains(perm))
        }
    }

    @Test
    fun offlineMap_has34Provinces() {
        assertEquals(34, OfflineMapDownloader.provinces.size)
    }

    @Test
    fun smsSender_checksSimBeforeClaimingSuccess() {
        val src = projectFile("src/main/java/com/example/service/sos/FallbackSmsManager.kt").readText()
        assertTrue("Phải kiểm tra trạng thái SIM", src.contains("SIM_STATE_ABSENT"))
        assertTrue("Phải kiểm tra chế độ máy bay", src.contains("AIRPLANE_MODE_ON"))
        assertTrue("Phải chờ xác nhận từ hệ thống", src.contains("ACTION_SMS_SENT") || src.contains("SMS_SENT_ACTION"))
        assertTrue("Phải dùng sentIntent để biết kết quả thật", src.contains("sendMultipartTextMessage"))
    }

    @Test
    fun sosDispatcher_requiresRealDelivery() {
        val src = projectFile("src/main/java/com/example/service/sos/SmartSosDispatcher.kt").readText()
        assertTrue("Mesh chỉ thành công khi gửi đi thật", src.contains("sentCount > 0"))
        assertTrue("Phải có kiểm tra kết quả cloud thật", src.contains("CloudDelivery.SENT"))
    }

    @Test
    fun locationMap_noFakeStayDuration() {
        val src = projectFile("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt").readText()
        assertTrue("Không được mô phỏng thời gian ở (18 phút giả)", !src.contains("mutableStateOf(18)"))
        assertTrue("Phải vẽ tuyến đường di chuyển", src.contains("Polyline"))
    }

    /**
     * Nền tối phải GẦN ĐEN. Test cũ so chuỗi cứng "0xFF02030B" nên chỉ cần đổi
     * mã màu là đỏ, dù màu mới vẫn đạt yêu cầu. Ở đây kiểm tra tính chất: mọi
     * dải của SpaceNightPalette phải có kênh sáng nhất <= 0x12.
     */
    @Test
    fun darkTheme_isDarker() {
        val src = projectFile("src/main/java/com/example/ui/theme/PremiumTheme.kt").readText()
        val palette = src.substringAfter("val SpaceNightPalette")
        val gradient = palette.substringAfter("backgroundGradient = listOf(").take(400)
        val colors = Regex("0xFF([0-9A-Fa-f]{6})").findAll(gradient).map {
            val v = it.groupValues[1].toInt(16)
            Triple((v shr 16) and 0xFF, (v shr 8) and 0xFF, v and 0xFF)
        }.toList()
        assertTrue("Phải tìm thấy dải nền của SpaceNightPalette", colors.size >= 3)
        colors.forEach { (r, g, b) ->
            val brightest = maxOf(r, g, b)
            assertTrue("Màu nền tối $r,$g,$b không gần đen (trần 18)", brightest <= 0x12)
        }
    }

    /**
     * Toa do bịa (Ha Noi / Da Nang) tung bi dung lam "vi tri mac dinh", nen SOS
     * gui dung toa do cho luc hai nguy. Test tinh: khong file nguon nao duoc
     * chua cac hang so do nua (tru bo tai ban do offline, tam tai la cho duoc
     * phep).
     */
    @Test
    fun source_hasNoFabricatedCoordinates() {
        val root = projectFile("src/main/java/com/example")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filterNot { it.name == "OfflineMapDownloader.kt" }
            .filter { f ->
                val t = f.readText()
                t.contains("21.0285") || t.contains("105.8542") ||
                    t.contains("15.1205") || t.contains("108.7923")
            }
            .map { it.name }
            .toList()
        assertTrue("Con toa do bia ở: $offenders", offenders.isEmpty())
    }

    /** LocationData mac dinh phai la "chua do duoc GPS", khong phai mot dia chi that. */
    @Test
    fun locationData_defaultsToNoFix() {
        val blank = com.example.data.model.LocationData()
        assertTrue("Mac dinh 0/0 phai bi coi la khong co vi tri", !blank.hasRealFix)
        assertTrue("Khong duoc mo phong pin day", blank.batteryLevel == 0)
        val real = blank.copy(latitude = 10.7824, longitude = 106.6494)
        assertTrue("Toa do that phai duoc nhan", real.hasRealFix)
    }

    // ── Vong nang cap 3: duong luu noi quy chan app ────────────────────────

    /**
     * `usedMinutes` tung co mac dinh 45 trong khi khong duong nao ghi lai no,
     * nen moi man hinh deu bao "hom nay da dung 45 phut" — du hom do may chua
     * mo. Mac dinh phai la 0, va so phut hien thi phai lay tu danh sach app doc
     * duoc that (`todayUsedMinutes`).
     */
    @Test
    fun schedule_noFakeUsedMinutes() {
        assertEquals(
            "usedMinutes mac dinh khong duoc bịa số liệu",
            0,
            com.example.data.model.UsageSchedule().usedMinutes
        )
        val detailed = listOf(
            com.example.data.model.DetailedAppUsage("a", "A", 12, "Game"),
            com.example.data.model.DetailedAppUsage("b", "B", 30, "Học")
        )
        assertEquals(42, com.example.data.model.todayUsedMinutes(detailed, fallback = 7))
        // Danh sach rong (chua cap quyen Usage Access) thi moi dung gia tri cu.
        assertEquals(7, com.example.data.model.todayUsedMinutes(emptyList(), fallback = 7))
        assertEquals(0, com.example.data.model.todayUsedMinutes(emptyList()))
    }

    /**
     * Bo chan chay tren main thread cua he thong. `queryUsageStats()` la lenh
     * doc dien, goi truc tiep moi lan doi cua so lam giay khi cham tich app —
     * dung tinh chen so lieu va chi doc tren the nen.
     */
    @Test
    fun blockerService_readsUsageStatsOffMainThread() {
        val src = projectFile("src/main/java/com/example/service/blocker/AppBlockerService.kt").readText()
        assertTrue("Phải có worker riêng đọc UsageStats", src.contains("Executors.newSingleThreadExecutor"))
        assertTrue("Phải giữ giá trị trong bộ nhớ đệm", src.contains("usedMinutesCache"))
        assertTrue(
            "Không được dùng cửa sổ rolling 24h (tinh dan ca hom qua)",
            !src.contains("TimeUnit.DAYS.toMillis(1)")
        )
        assertTrue("Phải tính từ nửa đêm", src.contains("HOUR_OF_DAY, 0"))
    }

    /**
     * Han muc phut soan o Dashboard khong bao luu duoc (card do chi hien khi
     * CHUA ghep noi), nen no phaibien thanh noi dung + nut roi, khong con
     * thanh keo tu ghi may chủ.
     */
    @Test
    fun dashboard_dailyTargetIsNotEditedWhileUnpaired() {
        val src = projectFile("src/main/java/com/example/ui/screens/DashboardScreen.kt").readText()
        assertTrue("Dashboard không được tự ghi hạn mức", !src.contains("onUpdateDailyTarget"))
        assertTrue("Phải chỉ đường tới nơi soạn được thật", src.contains("Đổi hạn mức trong Cài đặt"))
    }

    /** Cot "hom nay" tren bieu do phai theo thu tu mang (T2..CN), khong co dinh CN. */
    @Test
    fun screenTime_highlightsRealToday() {
        val src = projectFile("src/main/java/com/example/ui/screens/ScreenTimeScreen.kt").readText()
        assertTrue("Không được tô cứng cột Chủ nhật", !src.contains("day == \"CN\""))
        assertTrue("Phải suy ra thứ trong tuần từ Calendar", src.contains("Calendar.DAY_OF_WEEK"))
    }

    /**
     * `firestore.rules` chan `allow delete: if false` cho /users/{uid}, nen
     * nut "xóa tài khoản" khong the xoa gi. Noi such la noi tran; nay phai noi
     * dung viec xay ra (dang xuat) + huong dan thu tuc con lai.
     */
    @Test
    fun settings_accountActionDoesNotOverclaim() {
        val src = projectFile("src/main/java/com/example/ui/screens/SettingsScreen.kt").readText()
        assertTrue(
            "Không được hứa xóa vĩnh viễn khi nút chỉ đăng xuất",
            !src.contains("xóa vĩnh viễn theo chính sách quyền riêng tư")
        )
        assertTrue("Phải nói rõ nút không xóa dữ liệu máy chủ", src.contains("KHÔNG xóa hồ sơ"))
    }
}
