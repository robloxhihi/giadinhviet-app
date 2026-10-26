package com.example

import com.example.service.AddressHelper
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

    /**
     * Cat comment truoc khi noi "ma nguon KHONG con chuoi X", neu khong mot dong
     * giai thich ve cai loi cu cung du lam test do that bai.
     *
     * Phai cat theo DONG. Ban truoc dung Regex("//[^\\n]*") — no an ca `https://`
     * ben trong chuoi URL, nen "server.arcgisonline.com" bien mat khỏi ma that
     * va CI run v50 do ngay o dong 151. String khong bi dong.
     */
    private fun stripComments(source: String): String = source
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")

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
        // Dong 628 van ghi ro "fillMaxWidth(0.62f)" nhu mot loi giai thich. Doi voi
        // cac assert "khong con X" thi phai tim tren ma da cat comment.
        val code = stripComments(card)

        // Bảng cũ dùng fillMaxWidth(0.62f) với 7-9 dòng chữ, che gần nửa bản đồ.
        assertFalse("Thẻ vị trí không còn choán 62% chiều ngang bản đồ",
            code.contains("fillMaxWidth(0.62f)"))
        assertTrue("Chip phải tự co theo nội dung, tối đa 240.dp",
            code.contains(".widthIn(max = 240.dp)"))
        assertTrue("Chip gọn phải có nhãn ổn định để test trên máy",
            code.contains("testTag(\"map_info_chip\")"))
        assertTrue("Bảng chi tiết chỉ hiện khi chạm vào chip",
            code.contains("if (!isPrivacyActive && infoExpanded)"))

        // Hộp trắng "Con Ga / Lat:.. Lng:.." trong ảnh người dùng gửi.
        // Marker.setOnMarkerClickListener CHỈ nhận một tham số (khac MapView) —
        // them `map` vao la compileDebugKotlin chet, CI run 110/111 do ngay.
        // Ban 67.0: theo yêu cầu "chỉ có mũi tên mình thôi ở map", pin điểm
        // xuất phát + tên người khác đã bị BỎ — chỉ còn MỘT marker (mũi tên
        // định vị), vẫn phải chặn bong bóng mặc định của osmdroid.
        val suppression = "setOnMarkerClickListener { _, _ -> true }"
        val suppressedMarkers = code.split(suppression).size - 1
        assertTrue(
            "Marker mũi tên duy nhất phải chặn bong bóng mặc định, hiện find được $suppressedMarkers",
            suppressedMarkers == 1
        )
        assertTrue("Không được vẽ lại pin/tên người khác trên map",
            !code.contains("setTitle(") && !code.contains("setSnippet("))

        assertTrue("Card phải hỏi bộ phân loại nhãn đường",
            code.contains("AddressHelper.isUnmappedRoadLabel(displayAddress)"))
        assertTrue("Phải nói rõ toạ độ mới là dữ liệu thật",
            card.contains("toạ độ bên dưới mới là dữ liệu thật"))
    }

    // ── 3. Bộ chọn lớp: lớp chi tiết phải tồn tại và phải nằm CUỐI cùng ──────────

    /**
     * `ordinal` của enum MapLayer chính là giá trị lưu trong SharedPreferences
     * "map_view"/"layer2". Cắm một lớp mới vào giữa sẽ âm thầm đổi lớp của người
     * dùng đang dùng sang lớp khác khi mở lại app. Ban 67.0 cắt 6 lop xuong 3
     * (chi con HYBRID, SATELLITE, ESRI_STREET) nen bat buoc phai co ham
     * migratedLayerIndex() chuyen chi so cu, va lop moi them sau nay van phai
     * nam CUOI enum.
     */
    @Test
    fun detailStreetLayerIsAppendedLastSoStoredOrdinalsStayValid() {
        val card = stripComments(
            projectFile("src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt").readText()
        )
        val enumBody = card.substringAfter("private enum class MapLayer(")
            .substringBefore("\n}")
        val order = listOf("HYBRID", "SATELLITE", "ESRI_STREET")
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
        assertTrue(
            "Cắt enum phải kèm hàm chuyển chỉ số cũ (migratedLayerIndex), nếu không người dùng bị nhảy lớp",
            card.contains("private fun migratedLayerIndex(stored: Int)")
        )
    }

    /**
     * tile.openstreetmap.org đo ngày 2026-10-21 và 2026-10-23 đều không trả về
     * HTTP nào; Carto Voyager trả đúng một ảnh lỗi 2049 byte ở mọi zoom. Lớp
     * "chi tiết" vì thế phải dùng nguồn đã đo là có tile thật.
     */
    @Test
    fun detailLayerUsesAMeasuredWorkingHostNotADeadOne() {
        val raw = projectFile(
            "src/main/java/com/example/service/map/MapTileSources.kt"
        ).readText()
        // "tile.openstreetmap.org" CO MAT trong tep nay, nhưng chỉ trong lời giải
        // thích vì sao KHONG dùng host đó. So sánh trên toàn văn bản sẽ đỏ vì lý do
        // sai, nên phải cắt comment trước.
        val tiles = stripComments(raw)
        assertTrue("Lớp đường phố chi tiết phải lấy từ Esri World_Street_Map",
            tiles.contains("World_Street_Map"))
        assertTrue("Esri Street phải khoá max zoom theo kết quả đo thật (z18 là ô xám)",
            tiles.contains("ESRI_STREET_MAX_ZOOM = 17"))
        assertTrue("Mã nguồn tile phải trỏ host ArcGIS đã đo được tile thật",
            tiles.contains("server.arcgisonline.com"))
        assertFalse("Không được trỏ host tile.openstreetmap.org đã chết trong code",
            tiles.contains("tile.openstreetmap.org"))
        assertTrue("Comment phải giữ lại bằng chứng đo", raw.contains("HTTP 000"))
    }

    // ── 4. Lô 65.0: thẻ bản đồ phải nói bằng ĐỒNG HỒ CỦA MÁY ĐANG CẦM ─────────

    /**
     * `LocationData.updatedAt` do may ben kia ghi bang
     * `System.currentTimeMillis()` CUA NO, khong phai cua server. Mot may lech
     * dong ho ve tuong lai (may gia lap, may doi múi gio, phuc hoi tu ban sao
     * luu) khien `nowTick - updatedAt` AM, ep ve 0 -> the ban do in "vua cap
     * nhat" cho mot diem da chet tu lau. May dang xem chi co mot du lieu dang
     * tin duoc: luc CHINH NO nhan duoc phieu.
     */
    @Test
    fun tuoiViTriPhaiTinhTheoDongHoCuaMayDangCam() {
        val card = projectFile(
            "src/main/java/com/example/ui/components/GoogleMapsStudentLocationCard.kt"
        ).readText()
        assertTrue("Card phải nhận được mốc máy này nhận phiếu",
            card.contains("receivedAtMs: Long = 0L"))
        assertTrue("Có mốc máy này thì KHÔNG được tin đồng hồ máy kia nữa",
            card.contains("val ageAnchorMs = if (receivedAtMs > 0L) receivedAtMs else lastUpdatedAt"))
        assertTrue("Phai noi ro lech dong ho may kia, dung im lang kieu '5 phút trước'",
            card.contains("đồng hồ máy kia đi trước") &&
                card.contains("đồng hồ máy kia đi sau"))
        assertTrue("Phải ghi rõ tuổi tính từ lúc máy này nhận",
            card.contains("(tính từ lúc máy này nhận)"))

        val screen = projectFile(
            "src/main/java/com/example/ui/screens/LocationScreen.kt"
        ).readText()
        assertTrue("The cua may kia phai duoc truyen moc nhan cua may nay",
            screen.contains("receivedAtMs = uiState.partnerFixReceivedAtMs"))

        val vm = stripComments(projectFile(
            "src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt"
        ).readText())
        assertTrue("ViewModel phải ghi đồng hồ MÁY NÀY khi nhận phiếu",
            vm.contains("partnerFixReceivedAtMs = if (loc != null) System.currentTimeMillis() else 0L"))
        assertTrue("Doi may duoc theo doi phai xoa moc cu, neu khong diem cua nguoi " +
            "cu se in 'vừa cập nhật' trên máy mới",
            vm.contains("partnerLocation = null, partnerFixReceivedAtMs = 0L"))
    }

    // ── 5. Lô 65.0: thẻ "máy này" và bản đồ "máy kia" khong duoc lan vao nhau ──

    /**
     * Ba loi nguoi dung bao "dinh vi tam bay" deu la loi HUONG DAN:
     *  - "Thiết bị học sinh cần bật GPS và mở ứng dụng để cập nhật." sai mot nua
     *    (toa do duoc day len tu dich vu chay nen, khong can mo app), va xui
     *    nguoi dung mo app vo nghia trong khi van de that nam o quyen/mang may con.
     *  - Nut "Cấp quyền vị trí" / "Kiểm tra lại" dat trong the mo ta MAY BAN,
     *    Nhung da len ban do cua may CON -> phu huynh bam mieng mai van khong thay gi.
     */
    @Test
    fun theMayNayVaBanDoMayKiaPhaiChuyenTRachRoRang() {
        val screen = stripComments(projectFile(
            "src/main/java/com/example/ui/screens/LocationScreen.kt"
        ).readText())

        assertTrue("Phải bỏ câu xui người dùng mở app phía con",
            !screen.contains("cần bật GPS và mở ứng dụng"))
        assertTrue("Phải nói thật: vị trí đi lên từ dịch vụ chạy nền",
            screen.contains("không cần mở ứng dụng"))
        assertTrue("Phải có dòng chỉ rõ thẻ này nói về máy nào",
            screen.contains("Định vị trên máy bạn đang cầm"))
        listOf(
            "Cấp quyền cho máy này",
            "Mở Cài đặt định vị máy này",
            "Kiểm tra lại máy này"
        ).forEach {
            assertTrue("Nút phải nói rõ nó thay đổi MÁY NÀO: thiếu '$it'",
                screen.contains(it))
        }
        assertTrue("Thẻ phải biết bản đồ đang chiếu máy khác",
            screen.contains("mapShowsOtherDevice = isPaired"))
        assertTrue("Da ghep noi thi phai noi thang: nut o day khong mo duoc diem cua con",
            screen.contains("Các nút ở trên chỉ thay đổi máy bạn đang cầm"))
    }

    // ── 6. Lô 65.0: vi tri bi bo loc phai co LY DO, khong duoc im lang ────────

    /**
     * `fetchCurrentLocation` tra `null` khi diem vuot nguong sai so, nen may van
     * dinh vi duoc (diem wifi ±300 m) ma man hinh bao "Chưa lấy được GPS" — tức
     * noi oan thiet bi. Ngoai ra `updateLocation` bi tu choi (thieu quyen / bi cam
     * / mat mang) chi ghi Log.w -> nguoi dung nhin ban do dung im va nghi may
     * chua do GPS. Ca hai deu phai thanh loi nhin thay duoc.
     */
    @Test
    fun diemBiBoLotVaLoiGhiPhaiDuocNoiRa() {
        val raw = projectFile("src/main/java/com/example/ui/viewmodel/VesperaViewModel.kt").readText()
        val vm = stripComments(raw)
        assertTrue("Phai dung fetchLocationFix de con biet LY DO that",
            vm.contains("telemetryHelper.fetchLocationFix { fix ->"))
        assertTrue("Van chi gui diem VUA DO va duoi nguong sai so (khong phong long)",
            vm.contains("fix.source == LocationFixSource.LIVE_FIX && !fix.accuracyTooLow"))
        assertTrue("Sai số quá lớn phải được nói ra kèm con số",
            vm.contains("Máy định vị được nhưng sai số ±"))
        assertTrue("Điểm cached cũ phải bị gọi là điểm CŨ kèm tuổi",
            vm.contains("Điểm đang có là vị trí CŨ đo"))
        assertTrue("Lỗi ghi lên máy chủ phải hiện ra màn hình, không chỉ log",
            vm.contains("errorMessage = \"Không tải được vị trí lên máy chủ: \""))
        assertTrue("Comment phai giu lai vi sao doi API", raw.contains("fetchCurrentLocation"))
    }
}
