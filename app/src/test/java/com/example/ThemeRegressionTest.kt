package com.example

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Kiểm thử tĩnh cho vòng 40.0: bỏ hẳn accent ngọc lam và bỏ bộ chọn màu,
 * người dùng chỉ còn quyền chọn tối hoặc sáng.
 */
class ThemeRegressionTest {

    private fun projectFile(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    private fun read(rel: String): String = projectFile("src/main/java/com/example/$rel").readText()

    private val themeApi = read("ui/theme/Theme.kt")
    private val palettes = read("ui/theme/PremiumTheme.kt")
    private val tokens = read("ui/theme/Color.kt")

    @Test
    fun khongConMauNgocLamTrongBoToken() {
        val banned = listOf(
            "3E93B4", // NeonCyan cu
            "0EA5E9", // accent cua nen sang cu
            "4C9CC0", // chan gradient tam
            "6EE7B7", // bac ha sang
            "38BDF8", // OceanAzure
            "4CC9F0"  // lam ngoc cua mau "purple"
        )
        for (hex in banned) {
            assertFalse("Van con mau ngoc lam 0xFF$hex", palettes.contains(hex, true) || tokens.contains(hex, true))
        }
    }

    @Test
    fun tokenAccentTroSangXanhDuong() {
        val line = Regex("val NeonCyan = ([A-Za-z0-9_.]+)").find(tokens)
        assertTrue("NeonCyan phai tro thang OceanBlue", line?.groupValues?.get(1) == "OceanBlue")
    }

    @Test
    fun bangChonDaumauDaBienMatKhoiCaiDat() {
        val settings = read("ui/screens/SettingsScreen.kt")
        assertFalse("Con bang chon mau chu dao", settings.contains("MÀU SẮC CHỦ ĐẠO"))
        assertFalse("Con lua chon ngoc bich", settings.contains("\"emerald\""))
        assertFalse("Con lua chon lam ngoc", settings.contains("\"purple\""))
    }

    @Test
    fun chiConToiSangTrongHamTheme() {
        assertTrue(
            "premiumPaletteFor van con nhan colorTheme",
            Regex("fun premiumPaletteFor\\(\\s*darkTheme: Boolean\\s*\\)").containsMatchIn(themeApi)
        )
        assertFalse("Con nhanh mau chet 'sunset'", themeApi.contains("\"sunset\""))
        assertFalse("Con ham tam mau", themeApi.contains(".tinted("))
    }

    @Test
    fun khongConAliasChet() {
        assertFalse("Con alias EmotionStudyStress", tokens.contains("EmotionStudyStress"))
        assertFalse("Con token OceanAzure khong ai dung", tokens.contains("OceanAzure"))
    }
}
