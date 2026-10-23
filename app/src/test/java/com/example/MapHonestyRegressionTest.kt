package com.example

import com.example.service.AddressHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hồi quy cho lô "bản đồ phải nói thật" (2026-10-23).
 *
 * Hai thứ người dùng phàn nàn đều là vấn đề THÔNG TIN, không phải vấn đề định vị:
 *  1. Thẻ vị trí chiếm gần nửa bản đồ -> giờ là một chip một dòng, chi tiết chỉ
 *     hiện khi chạm.
 *  2. "Địa điểm không chính xác tí nào" -> toạ độ vẫn đúng những gì thiết bị báo;
 *     cái sai nằm ở NHÃN. Google Maps trả "Đường Chưa Đặt Tên" cho vùng quê chưa
 *     được bản đồ hoá, và bong bóng mặc định của osmdroid lặp lại toạ độ trong
 *     một hộp trắng không theo màu nào của app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapHonestyRegressionTest {

    private fun projectFile(relative: String): java.io.File {
        val candidates = listOf(
            java.io.File(relative),
            java.io.File("app/$relative")
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Không tìm thấy tệp: $relative")
    }

    private fun stripComments(source: String): String = source
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
        .replace(Regex("//[^\\n]*"), "")

    // ── 1. Nhãn đường chưa được bản đồ hoá không được vẽ ra như địa chỉ thật ──

    @Test
    fun unmappedRoadLabelsAreDetectedInBothLanguages() {
        listOf(
            "Đường Chưa Đặt Tên, Huyện Sơn Tịnh, Quảng Ngãi",
            "đường không có tên, Quận 1, TP Hồ Chí Minh",
            "Đường Không Tên, Huyện Bình Chánh",
            "Unnamed Road, Some District",
            "UNNAMED-ROAD, Something",
            "No Name",
            AddressHelper.DEFAULT_ADDRESS
        ).forEach {
            assertTrue("Phải nhận diện được nhãn đường ảo: $it",
                AddressHelper.isUnmappedRoadLabel(it))
        }

        listOf(
            "Nguyễn Trãi, Quận 1, TP Hồ Chí Minh",
            "Quốc lộ 1A, Thành phố Quảng Ngãi",
            "School of Languages, Hanoi",
            AddressHelper.NO_FIX_ADDRESS
        ).forEach {
            assertFalse("Địa chỉ thật phải giữ nguyên màu: $it",
                AddressHelper.isUnmappedRoadLabel(it))
        }

        assertFalse("Chuỗi rỗng không được tính là đường chưa đặt tên",
            AddressHelper.isUnmappedRoadLabel(""))
    }

    // ── 2. Card phải dùng bộ phân loại đó, và phải là chip chứ không phải tường ──

    @Test
    fun locationCardIsAChipNotAWallAndWarnsAboutFakeStreetNames() {
        val card = projectFile(
            "src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt"
        ).readText()

        // Bảng cũ dùng fillMaxWidth(0.62f) với 7-9 dòng chữ, che gần nửa bản đồ.
        assertFalse("Thẻ vị trí không còn choán 62% chiều ngang bản đồ",
            card.contains("fillMaxWidth(0.62f)"))
        assertTrue("Chip phải tự co theo nội dung, tối đa 240.dp",
            card.contains(".widthIn(max = 240.dp)"))
        assertTrue("Chip gọn phải có nhãn ổn định để test trên máy",
            card.contains("testTag(\"map_info_chip\")"))
        assertTrue("Bảng chi tiết chỉ hiện khi chạm vào chip",
            card.contains("if (!isPrivacyActive && infoExpanded)"))

        // Hộp trắng "Con Ga / Lat:.. Lng:.." trong ảnh người dùng gửi.
        val suppression = "setOnMarkerClickListener({ _, _ -> true }, map)"
        assertEquals(
            "Cả hai marker (điểm xuất phát + vị trí hiện tại) đều phải chặn bong bóng mặc định",
            2,
            card.split(suppression).size - 1
        )

        assertTrue("Card phải hỏi bộ phân loại nhãn đường",
            card.contains("AddressHelper.isUnmappedRoadLabel(displayAddress)"))
        assertTrue("Phải nói rõ toạ độ mới là dữ liệu thật",
            card.contains("toạ độ bên dưới mới là dữ liệu thật"))
    }

    // ── 3. Bộ chọn lớp: lớp chi tiết phải tồn tại và phải nằm CUỐI cùng ──────────

    /**
     * `ordinal` của enum MapLayer chính là giá trị lưu trong SharedPreferences
     * "map_view"/"layer2". Cắm một lớp mới vào giữa sẽ âm thầm đổi lớp của người
     * dùng đang dùng sang lớp khác khi mở lại app.
     */
    @Test
    fun detailStreetLayerIsAppendedLastSoStoredOrdinalsStayValid() {
        val card = stripComments(
            projectFile("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt").readText()
        )
        val enumBody = card.substringAfter("private enum class MapLayer(")
            .substringBefore("\n}")
        val order = listOf("SATELLITE", "STREET", "HYBRID", "ESRI", "TERRAIN", "ESRI_STREET")
            .map { it to enumBody.indexOf("\n    $it(") }

        order.forEach { (name, index) ->
            assertTrue("Thiếu lớp bản đồ: $name", index >= 0)
        }
        val lastPosition = order.last().second
        assertTrue(
            "Lớp mới phải nằm CUỐI để ordinal đã lưu không đổi nghĩa: " +
                order.joinToString(", ") { "${it.first}@${it.second}" },
            lastPosition == order.maxOf { it.second }
        )
    }

    /**
     * tile.openstreetmap.org đo ngày 2026-10-21 và 2026-10-23 đều không trả về
     * HTTP nào; Carto Voyager trả đúng một ảnh lỗi 2049 byte ở mọi zoom. Lớp
     * "chi tiết" vì thế phải dùng nguồn đã đo là có tile thật.
     */
    @Test
    fun detailLayerUsesAMeasuredWorkingHostNotADeadOne() {
        val tiles = projectFile(
            "src/main/java/com/example/service/map/MapTileSources.kt"
        ).readText()
        assertTrue("Lớp đường phố chi tiết phải lấy từ Esri World_Street_Map",
            tiles.contains("World_Street_Map"))
        assertTrue("Esri Street phải khoá max zoom theo kết quả đo thật (z18 là ô xám)",
            tiles.contains("ESRI_STREET_MAX_ZOOM = 17"))
        assertFalse("Không được trỏ host tile.openstreetmap.org đã chết",
            tiles.contains("tile.openstreetmap.org"))
    }
}
