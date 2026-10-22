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

    /** Bo comment de chu giai thich khong bi tinh la code that. */
    private fun code(src: String): String = src
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//[^\\n]*"), "")

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

    /**
     * Do hom 2026-10-22 tren BlueStacks: `am start -W` bao 3,3 giay de mo app,
     * Choreographer bo 54 frame va mot frame ke 1,15 giay. Hai nguyen nhan nam
     * ngay trong nen: anh 1024x1792 giai ma trong frame dau, va mot
     * `rememberInfiniteTransition` ve lai Canvas full-man-hinh 60 lan/giay.
     */
    @Test
    fun nenKhongConVeLaiMoiFrame() {
        val code = code(palettes)
        assertFalse(
            "Nen van con rememberInfiniteTransition — app rung khi cuon",
            code.contains("rememberInfiniteTransition")
        )
        assertFalse("Nen van con animateFloat", code.contains("animateFloat"))
    }

    @Test
    fun anhNenDuocGiaiMaNgoaiMainTo() {
        val code = code(palettes)
        assertFalse(
            "painterResource giai ma anh lon ngay trong frame dau",
            code.contains("painterResource")
        )
        assertTrue("Phai co ham giai ma bat dong bo", code.contains("fun asyncWallpaper("))
        assertTrue("Phai giai ma tren Dispatchers.IO", code.contains("withContext(Dispatchers.IO)"))
        assertTrue("Phai dung RGB_565 de giam nua bo nho", code.contains("Bitmap.Config.RGB_565"))
    }
}
