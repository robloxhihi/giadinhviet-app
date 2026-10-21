package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.AddressHelper
import com.example.ui.theme.FacebookBlue
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Lop "Ve tinh" — Esri World Imagery.
 *
 * osmdroid 6.1.18 KHONG co TileSourceFactory.ESRI_WORLDMAP (da doi cheu constant
 * pool cua TileSourceFactory.class), nen tu khai báo source theo đúng cach
 * osmdroid lam cho USGS ArcGIS: XYTileSource(ten, zoomMin, zoomMax, 256, duoi, baseUrls).
 *
 * max = 18 la ket qua DO TRUC TIEP 2026-10-21: z16-z18 tra anh that o moi diem
 * thu, nhung z19-20 tra ve 2521 byte giong het nhau (md5 f27d9de7) = ô xám
 * "khong co du lieu", nen khong duoc phep phat toi z19.
 */
private object EsriWorldImageryTileSource : org.osmdroid.tileprovider.tilesource.XYTileSource(
    "EsriWorldImagery", 0, 18, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    /** Esri dung thu tu {z}/{y}/{x} (row/col), khong giong {z}/{x}/{y} cua OSM. */
    override fun getTileURLString(pMapTileIndex: Long): String {
        val sb = StringBuilder(getBaseUrl())
        sb.append(MapTileIndex.getZoom(pMapTileIndex)).append('/')
        sb.append(MapTileIndex.getY(pMapTileIndex)).append('/')
        sb.append(MapTileIndex.getX(pMapTileIndex))
        sb.append(".jpg")
        return sb.toString()
    }
}

/**
 * Nen cua Google (`/vt/lyrs=...`) — nguồn CO ANH THAT SAU NHAT da do duoc.
 *
 * Do truc tiep 2026-10-21 tren 10 diem (nong thon Quang Ngai ngay tai toa do
 * 14.45722/107.91763, Ha Noi, Sai Gon, Ha Giang, Da Nang, Hai Phong, rung
 * Tay Nguyen, Tuyen Son, bien Phu Quy) voi `lyrs=y`:
 *   z18 14 341 B · z19 13 178 B · z20 11 250 B · z21 7 669 B o nong thon Quang
 *   Ngai, moi tile deu la anh mau that (256 gia tri byte khac nhau trong tile),
 *   va chi co diem giữa bien Phu Quy tra ve 664 B xanh nhat quan = dung la nuoc.
 * Khong co ky hieu ô xám nhu Esri, nen lop nay cho phep toi z21.
 */
private class GoogleTileSource(
    keyName: String,
    layers: String,
    imageExt: String
) : org.osmdroid.tileprovider.tilesource.XYTileSource(
    keyName, 0, 21, 256, ".$imageExt",
    arrayOf("https://mt1.google.com/vt/lyrs=$layers&")
) {
    /** Google dung query string, khong dung duong danh {z}/{x}/{y}. */
    override fun getTileURLString(pMapTileIndex: Long): String = StringBuilder(getBaseUrl())
        .append("x=").append(MapTileIndex.getX(pMapTileIndex))
        .append("&y=").append(MapTileIndex.getY(pMapTileIndex))
        .append("&z=").append(MapTileIndex.getZoom(pMapTileIndex))
        .toString()
}

private val GoogleHybridSource = GoogleTileSource("GoogleHybrid", "y", "jpg")
private val GoogleSatelliteSource = GoogleTileSource("GoogleSatellite", "s", "jpg")
private val GoogleStreetSource = GoogleTileSource("GoogleStreet", "m", "png")

/**
 * Các lớp bản đồ có thể chuyển đổi.
 *
 * DA LOAI CAC LOP CHET do do tile truc tiep 2026-10-19 va 2026-10-21:
 *  - HIKEBIKEMAP (tiles.wmflabs.org/hikebike) => HTTP 000, host ngung, trang tran.
 *  - OPEN_SEAMAP (tiles.openseamap.org/seamark) => 200 nhung 334 byte, chi la
 *    overlay bai ngam, dung lam nen thi gan nhu rong.
 *  - TileSourceFactory.MAPNIK (tile.openstreetmap.org) => HTTP 000 moi lan do
 *    hom nay, va chinh la nguyen nhan "map bi trang": o nong thon lop nay trong
 *    gan nhu khong co noi dung, khong phai do doi do hoa.
 *
 * zoomCap: max zoom DA DO du lieu that cua lop; 0 nghia la lay theo
 * source.maximumZoomLevel (OpenTopo). maxZoom khop tile that
 * => khong con ô xám khi phong to.
 */
private enum class MapLayer(
    val label: String,
    val source: org.osmdroid.tileprovider.tilesource.ITileSource,
    val zoomCap: Int = 0,
    val credit: String
) {
    HYBRID("Vệ tinh + tên đường", GoogleHybridSource, 21, "© Google, Maxar"),
    SATELLITE("Vệ tinh", GoogleSatelliteSource, 21, "© Google, Maxar"),
    STREET("Đường phố", GoogleStreetSource, 21, "© Google"),
    ESRI("Vệ tinh Esri", EsriWorldImageryTileSource, 18, "© Esri, Maxar"),
    TERRAIN("Địa hình", TileSourceFactory.OpenTopo, 0, "© OpenTopoMap")
}

/** An toàn khi danh sach lop ngan lai: mod theo size, KHONG bao gio index ngoai mang. */
private fun layerAt(index: Int): MapLayer = MapLayer.entries[index.mod(MapLayer.entries.size)]

/** Toa do hop le that su — 0/0 nghia la CHUA CO FIX (see Models.LocationData). */
private fun isPlausibleCoordinate(latitude: Double, longitude: Double): Boolean =
    !(latitude == 0.0 && longitude == 0.0) &&
        latitude >= -90.0 && latitude <= 90.0 &&
        longitude >= -180.0 && longitude <= 180.0

/** Max zoom của một lớp, luôn lấy theo đúng khả năng tile source đang chọn. */
private fun MapLayer.maxZoom(): Double {
    val cap = if (zoomCap > 0) zoomCap.toDouble() else source.maximumZoomLevel.toDouble()
    return cap.coerceIn(minZoomLevel(), 21.0)
}

/** Min zoom chung cua MapView (khop minZoomLevel dat trong factory). */
private fun MapLayer.minZoomLevel(): Double = 3.0

/**
 * Bản đồ OSMDroid: polyline tuyến đường, vòng chính xác, pin tương phản trên cả
 * nền đường phố lẫn vệ tinh, lớp Vệ tinh Esri, chế độ Theo dõi (tự animateTo),
 * và TUYỆT ĐỐI không vẽ marker khi chưa có fix GPS thật.
 */
@Composable
fun GoogleMapsStudentLocationCard(
    studentName: String,
    latitude: Double,
    longitude: Double,
    safeZoneName: String,
    batteryLevel: Int,
    isCharging: Boolean,
    speedKmh: Float,
    isPrivacyActive: Boolean,
    modifier: Modifier = Modifier,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    stayDurationMillis: Long = -1L,
    lastUpdatedAt: Long = 0L,
    accuracyMeters: Float = 0f,
    headingDegrees: Float? = null,
    /** false => map khong ve marker, hien bang "dang do GPS" + nut Thu lai.
     *  Khong co gia tri mac dinh: moi cho goi phai TU XAC NHAN toa do nay that. */
    hasRealFix: Boolean,
    /** Nut "Thu lai ngay" khi chua co vi tri that. */
    onRetryLocation: () -> Unit = {}
) {
    val context = LocalContext.current

    // Live clock
    var currentTimeString by remember { mutableStateOf("") }
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss - dd/MM/yyyy", Locale.getDefault())
        while (true) {
            val now = System.currentTimeMillis()
            nowTick = now
            currentTimeString = timeFormat.format(Date(now))
            delay(1000L)
        }
    }

    val showMap = hasRealFix && isPlausibleCoordinate(latitude, longitude) && !isPrivacyActive

    // Địa chỉ người đọc được — chỉ reverse-geocode khi thật sự có tọa độ.
    var displayAddress by remember(latitude, longitude, safeZoneName, showMap) {
        mutableStateOf(
            if (safeZoneName.isNotBlank() && safeZoneName != "Khu vực an toàn" &&
                !safeZoneName.contains("Đang định vị")
            ) safeZoneName else AddressHelper.DEFAULT_ADDRESS
        )
    }
    LaunchedEffect(latitude, longitude, showMap) {
        if (!showMap) return@LaunchedEffect
        val addr = AddressHelper.getReadableAddress(context, latitude, longitude)
        if (addr.isNotBlank()) displayAddress = addr
    }

    var isExpanded by remember { mutableStateOf(false) }
    // Lop va muc zoom tung chon bang nut "Lớp" phai song qua lan mo lai man hinh.
    // Key mang ten moi ("layer2"/"zoom2"): ban cu luu "layer" = lop Duong pho cua
    // OSM, ma host tile.openstreetmap.org do hom 2026-10-21 khong tra ve HTTP nao
    // => tai su dung gia tri cu se hien gan nhu trang, dung cai nguoi dung bao
    // "bi doi do hoa". Voi key moi, ai nang cap cung mo duoc lop co anh that z19.
    val mapPrefs = remember {
        context.getSharedPreferences("map_view", android.content.Context.MODE_PRIVATE)
    }
    var layerIndex by remember { mutableStateOf(mapPrefs.getInt("layer2", MapLayer.HYBRID.ordinal)) }
    var zoomLevel by remember { mutableStateOf(mapPrefs.getFloat("zoom2", 19f).toDouble()) }
    LaunchedEffect(layerIndex, zoomLevel) {
        mapPrefs.edit()
            .putInt("layer2", layerIndex.coerceIn(0, MapLayer.entries.lastIndex))
            .putFloat("zoom2", zoomLevel.toFloat())
            .apply()
    }
    var followMode by remember { mutableStateOf(true) }
    var mapViewRef by remember { mutableStateOf<org.osmdroid.views.MapView?>(null) }

    // Điểm đã animate tới gần nhất. Dung Array thuong (khong phai MutableState)
    // de ghi trong update block ma khong kich recomposition vong lap.
    val followAnchor = remember { arrayOfNulls<Pair<Double, Double>>(1) }

    // Nhịp nhấp nháy cho bảng "đang dò GPS" (chi dung animateFloatAsState da co tien le).
    var pulseOn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            pulseOn = !pulseOn
            delay(900L)
        }
    }
    val pulseScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.18f else 0.92f,
        label = "gpsPulse"
    )

    val mapHeight by animateDpAsState(
        targetValue = if (isExpanded) 560.dp else 400.dp,
        label = "mapHeight"
    )

    // Tuyến đường: loại mọi điểm 0/0 / ngoài khoảng hợp lệ trước khi vẽ.
    val validRoutePoints = remember(routePoints) {
        routePoints.filter { isPlausibleCoordinate(it.first, it.second) }
    }

    val openExternalMaps = {
        try {
            val label = Uri.encode("$studentName ($displayAddress)")
            val uri = Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude($label)")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        } catch (_: Exception) {
            val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
            context.startActivity(Intent(Intent.ACTION_VIEW, webUri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK })
        }
    }

    val rgbGradientBrush = Brush.linearGradient(
        colors = listOf(
            FacebookBlue.copy(alpha = 0.85f),
            NeonCyan.copy(alpha = 0.85f),
            NeonEmerald.copy(alpha = 0.85f),
            NeonAmber.copy(alpha = 0.85f),
            NeonViolet.copy(alpha = 0.85f)
        )
    )

    // Khoảng cách từ điểm xuất phát (nếu có tuyến đường)
    val distanceFromStartKm: Double? = if (validRoutePoints.size >= 2) {
        val (lat0, lng0) = validRoutePoints.first()
        haversineKm(lat0, lng0, latitude, longitude)
    } else null

    val stayText = when {
        stayDurationMillis < 0 -> "—"
        else -> {
            val mins = stayDurationMillis / 60_000L
            val hrs = mins / 60
            when {
                hrs > 0 -> "${hrs}g ${mins % 60}p"
                mins > 0 -> "${mins}p"
                else -> "${stayDurationMillis / 1000L}s"
            }
        }
    }

    // Tuổi của vị trí, tính bằng giây THẬT từ lastUpdatedAt tới nowTick.
    val fixAgeSeconds: Long = if (lastUpdatedAt > 0) {
        ((nowTick - lastUpdatedAt) / 1000L).coerceAtLeast(0L)
    } else -1L
    val isStaleFix = fixAgeSeconds > 180L
    val lastUpdateText = when {
        fixAgeSeconds < 0 -> "chưa có lần fix nào"
        fixAgeSeconds < 60 -> "${fixAgeSeconds}s trước"
        fixAgeSeconds < 3600 -> "${fixAgeSeconds / 60} phút trước"
        else -> "${fixAgeSeconds / 3600} giờ trước"
    }

    val pinColorInt = if (isStaleFix) {
        android.graphics.Color.rgb(255, 150, 0)
    } else {
        android.graphics.Color.rgb(0, 132, 255)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ============ THẺ BẢN ĐỒ: map chiếm toàn bộ chiều cao thẻ ============
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
                .border(1.5.dp, rgbGradientBrush, RoundedCornerShape(16.dp))
        ) {
            if (isPrivacyActive) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier.size(46.dp).clip(CircleShape)
                                .background(NeonAmber.copy(alpha = 0.2f))
                                .border(1.5.dp, NeonAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.Lock, null, tint = NeonAmber, modifier = Modifier.size(24.dp)) }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Vị trí đang được ẩn an toàn", style = MaterialTheme.typography.titleSmall, color = NeonAmber, fontWeight = FontWeight.Bold)
                        Text("Trong khung giờ riêng tư đã thỏa thuận", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            } else if (!showMap) {
                // CHƯA CÓ VỊ TRÍ THẬT — không vẽ marker, không bịa tọa độ.
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size((56f * pulseScale).dp)
                                .clip(CircleShape)
                                .background(NeonAmber.copy(alpha = 0.16f))
                                .border(1.5.dp, NeonAmber, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.GpsFixed, null, tint = NeonAmber, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Chưa có vị trí thật — đang dò GPS",
                            style = MaterialTheme.typography.titleSmall,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            if (fixAgeSeconds < 0)
                                "Máy chưa nhận được fix GPS nào, nên bản đồ không vẽ điểm giả."
                            else
                                "Độ trễ từ lần fix gần nhất: ${fixAgeSeconds}s ($lastUpdateText)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onRetryLocation,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FacebookBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("retry_fix_btn")
                        ) {
                            Icon(Icons.Default.Refresh, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thử lại ngay", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        try {
                            org.osmdroid.config.Configuration.getInstance().load(
                                ctx, android.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
                            )
                            org.osmdroid.config.Configuration.getInstance().userAgentValue = "VesperaFamilySafe/${ctx.packageName}"
                        } catch (_: Exception) {}

                        org.osmdroid.views.MapView(ctx).apply {
                            isTilesScaledToDpi = true
                            val initialLayer = layerAt(layerIndex)
                            setTileSource(initialLayer.source)
                            // maxZoom lay theo DUNG tile source dang chon va theo
                            // MUC DA DO (nhom Google co that toi z21, Esri het o z19,
                            // OpenTopo theo source.maximumZoomLevel).
                            maxZoomLevel = initialLayer.maxZoom()
                            minZoomLevel = 3.0
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            controller.setZoom(zoomLevel.coerceAtMost(initialLayer.maxZoom()))
                            controller.setCenter(org.osmdroid.util.GeoPoint(latitude, longitude))

                            overlays.add(
                                org.osmdroid.views.overlay.ScaleBarOverlay(this).apply {
                                    setCentred(false); setScaleBarOffset(20, 30)
                                }
                            )

                            // Người dùng tự thao tác => THOÁT chế độ theo dõi. Ghi
                            // state trong callback ngoai composition nen an toan.
                            // Pinch zoom cung phai tat follow: neu khong animateTo
                            // cua followMode gianh lai tam khi user dang phong to.
                            // Chi can ACTION_MOVE: `actionMasked` da an bit chi so
                            // ngon tay nen moi lan pinch cung sinh ra ACTION_MOVE
                            // (MotionEvent khong co hang so ACTION_POINTER_MOVE).
                            setOnTouchListener { view, event ->
                                val masked = event.actionMasked
                                val isManualGesture = masked == MotionEvent.ACTION_MOVE ||
                                        masked == MotionEvent.ACTION_POINTER_DOWN ||
                                        masked == MotionEvent.ACTION_POINTER_UP
                                if (isManualGesture && followMode) {
                                    followMode = false
                                    followAnchor[0] = null
                                }
                                if (masked == MotionEvent.ACTION_UP || masked == MotionEvent.ACTION_POINTER_UP) {
                                    val mv = view as? org.osmdroid.views.MapView
                                    if (mv != null) zoomLevel = mv.zoomLevelDouble
                                }
                                false
                            }

                            mapViewRef = this
                        }
                    },
                    onRelease = { m ->
                        // AndroidView bi an (showMap = false) => ngung tile-loader
                        // thread va handler. MapView 6.1.18 CHI co onPause/onResume
                        // (da doi chieu javap), khong co onDestroy.
                        mapViewRef = null
                        m.onPause()
                    },
                    update = { map ->
                        map.isTilesScaledToDpi = true
                        val layer = layerAt(layerIndex)
                        val targetSource = layer.source
                        if (map.tileProvider.tileSource != targetSource) map.setTileSource(targetSource)

                        val sourceMax = layer.maxZoom()
                        if (map.maxZoomLevel != sourceMax) map.maxZoomLevel = sourceMax
                        var currentZoom = map.zoomLevelDouble
                        if (currentZoom > sourceMax) {
                            map.controller.setZoom(sourceMax)
                            currentZoom = sourceMax
                        }

                        val point = org.osmdroid.util.GeoPoint(latitude, longitude)

                        // Vẽ lại toàn bộ overlay mỗi lần cập nhật
                        map.overlays.removeAll {
                            it is org.osmdroid.views.overlay.Marker ||
                                    it is org.osmdroid.views.overlay.Polygon ||
                                    it is org.osmdroid.views.overlay.Polyline
                        }

                        // Kinh te: 1 pixel man hinh ~ bao nhieu met (Web Mercator),
                        // dung de kich thuoc "pin" khong bi to/teo theo zoom.
                        val metersPerPixel = 156543.03392 *
                                cos(Math.toRadians(latitude)) /
                                2.0.pow(currentZoom.coerceIn(3.0, 21.0))
                        val dotRadius = (10.0 * metersPerPixel).coerceIn(3.0, 60.0)
                        val haloRadius = (22.0 * metersPerPixel).coerceIn(8.0, 150.0)

                        // Vòng độ chính xác
                        val accRadius = (if (accuracyMeters > 3f) accuracyMeters else 25f)
                            .toDouble().coerceAtLeast(haloRadius)
                        map.overlays.add(org.osmdroid.views.overlay.Polygon(map).apply {
                            points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(point, accRadius)
                            fillPaint.color = android.graphics.Color.argb(38, 0, 132, 255)
                            outlinePaint.color = android.graphics.Color.argb(210, 255, 255, 255)
                            outlinePaint.strokeWidth = 2.5f
                        })

                        // Tuyến đường: viền trắng bên dưới + nét màu bên trên,
                        // nhìn rõ cả trên nền vệ tinh tối.
                        if (validRoutePoints.size >= 2) {
                            val geoPoints = validRoutePoints.map { org.osmdroid.util.GeoPoint(it.first, it.second) }
                            map.overlays.add(org.osmdroid.views.overlay.Polyline(map).apply {
                                setPoints(geoPoints)
                                outlinePaint.color = android.graphics.Color.argb(220, 255, 255, 255)
                                outlinePaint.strokeWidth = 15f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                            })
                            map.overlays.add(org.osmdroid.views.overlay.Polyline(map).apply {
                                setPoints(geoPoints)
                                outlinePaint.color = android.graphics.Color.argb(235, 79, 141, 253)
                                outlinePaint.strokeWidth = 9f
                                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
                                outlinePaint.strokeJoin = android.graphics.Paint.Join.ROUND
                            })
                            // Điểm xuất phát
                            map.overlays.add(org.osmdroid.views.overlay.Marker(map).apply {
                                position = geoPoints.first()
                                setAnchor(org.osmdroid.views.overlay.Marker.ANCHOR_CENTER, org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM)
                                title = "Điểm xuất phát"
                                snippet = "Bắt đầu hành trình"
                            })
                        }

                        // Mũ trắng lớn làm nền cho pin (tương phản cả nền sáng lẫn tối)
                        map.overlays.add(org.osmdroid.views.overlay.Polygon(map).apply {
                            points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(point, haloRadius)
                            fillPaint.color = android.graphics.Color.argb(235, 255, 255, 255)
                            outlinePaint.color = android.graphics.Color.argb(220, 20, 30, 60)
                            outlinePaint.strokeWidth = 2f
                        })
                        // Chấm vị trí hiện tại (cam đỏ khi vị trí đã cũ >3 phút)
                        map.overlays.add(org.osmdroid.views.overlay.Polygon(map).apply {
                            points = org.osmdroid.views.overlay.Polygon.pointsAsCircle(point, dotRadius)
                            fillPaint.color = pinColorInt
                            outlinePaint.color = android.graphics.Color.rgb(255, 255, 255)
                            outlinePaint.strokeWidth = 2f
                        })

                        // Pin mặc định để chạm xem thông tin
                        map.overlays.add(org.osmdroid.views.overlay.Marker(map).apply {
                            position = point
                            setAnchor(
                                org.osmdroid.views.overlay.Marker.ANCHOR_CENTER,
                                org.osmdroid.views.overlay.Marker.ANCHOR_BOTTOM
                            )
                            title = studentName.ifEmpty { "Vị trí học sinh" } +
                                    if (isStaleFix) " (vị trí có thể đã cũ)" else ""
                            snippet = "$displayAddress\nLat: $latitude, Lng: $longitude"
                        })

                        // Chế độ theo dõi: animateTo tới tọa độ mới, nhưng chỉ khi
                        // thật sự lệch > ~5m để không giật mỗi giây.
                        if (followMode) {
                            val anchor = followAnchor[0]
                            val moved = anchor == null ||
                                    haversineKm(anchor.first, anchor.second, latitude, longitude) > 0.005
                            if (moved) {
                                map.controller.animateTo(point)
                                followAnchor[0] = latitude to longitude
                            }
                        }

                        map.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Góc trên trái: thẻ thông tin gọn + bán trong suốt (không che đường)
            if (!isPrivacyActive) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
                        .fillMaxWidth(0.62f).shadow(2.dp, RoundedCornerShape(10.dp)),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, rgbGradientBrush)
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape)
                                .background(
                                    when {
                                        isPrivacyActive -> NeonAmber
                                        !showMap -> NeonAmber
                                        isStaleFix -> NeonRose
                                        else -> NeonEmerald
                                    }
                                ))
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.LocationOn, null, tint = FacebookBlue, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = when {
                                    isPrivacyActive -> "Đang ẩn"
                                    !showMap -> "Đang dò GPS…"
                                    else -> displayAddress
                                },
                                color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            "🕒 $currentTimeString • ⏳ $lastUpdateText",
                            color = TextSecondary, fontSize = 9.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (stayDurationMillis >= 0) {
                            Text(
                                "⏳ Đã ở đây: $stayText",
                                color = NeonEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (showMap) {
                            Text(
                                text = buildString {
                                    append(String.format(Locale.US, "%.5f, %.5f", latitude, longitude))
                                    if (accuracyMeters > 0) append(" ±${accuracyMeters.toInt()}m")
                                    append(" • ${layerAt(layerIndex).label} z${"%.1f".format(zoomLevel)}")
                                    append(" • ${layerAt(layerIndex).credit}")
                                },
                                color = FacebookBlue, fontSize = 9.sp, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (headingDegrees != null) {
                                Text(
                                    "🧭 Hướng ${compassLabel(headingDegrees)} (${headingDegrees.toInt()}°)",
                                    color = NeonViolet, fontSize = 9.sp, fontWeight = FontWeight.Medium
                                )
                            }
                            if (isStaleFix) {
                                Text(
                                    "⚠️ Vị trí có thể đã cũ",
                                    color = NeonRose, fontSize = 9.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Góc trên phải: nút mở Google Maps ngoài (chỉ khi có vị trí thật)
            if (showMap) {
                Button(
                    onClick = openExternalMaps,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).height(26.dp)
                        .testTag("open_google_maps_btn"),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FacebookBlue.copy(alpha = 0.85f),
                        contentColor = Color.White
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("Maps", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ============ HÀNG ĐIỀU KHIỂN — NĂM DƯỚI THẺ MAP, KHÔNG ĐÈ LÊN MAP ============
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(1.dp, rgbGradientBrush, RoundedCornerShape(10.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapControlButton(Icons.Default.Layers, "Lớp: ${layerAt(layerIndex).label}", FacebookBlue) {
                val next = (layerIndex + 1) % MapLayer.entries.size
                layerIndex = next
                mapViewRef?.let { m ->
                    val layer = layerAt(next)
                    m.setTileSource(layer.source)
                    val mx = layer.maxZoom()
                    m.maxZoomLevel = mx
                    if (m.zoomLevelDouble > mx) {
                        m.controller.setZoom(mx)
                        zoomLevel = mx
                    } else {
                        zoomLevel = m.zoomLevelDouble
                    }
                    m.invalidate()
                }
            }
            MapControlButton(Icons.Default.Add, "Phóng to", TextPrimary) {
                mapViewRef?.let { m ->
                    m.controller.zoomIn()
                    zoomLevel = m.zoomLevelDouble
                }
            }
            MapControlButton(Icons.Default.Remove, "Thu nhỏ", TextPrimary) {
                mapViewRef?.let { m ->
                    m.controller.zoomOut()
                    zoomLevel = m.zoomLevelDouble
                }
            }
            MapControlButton(Icons.Default.Explore, "Căn hướng Bắc", TextSecondary) {
                mapViewRef?.mapOrientation = 0f
            }
            if (showMap) {
                MapControlButton(Icons.Default.MyLocation, "Căn giữa", FacebookBlue) {
                    val p = org.osmdroid.util.GeoPoint(latitude, longitude)
                    mapViewRef?.let { m ->
                        m.controller.animateTo(p)
                        m.controller.setZoom(18.0)
                        zoomLevel = 18.0
                    }
                    followAnchor[0] = latitude to longitude
                    followMode = true
                }
                MapControlButton(
                    Icons.Default.GpsFixed,
                    if (followMode) "Đang theo dõi — bấm để tắt" else "Bật lại chế độ theo dõi",
                    if (followMode) NeonEmerald else TextSecondary
                ) {
                    val next = !followMode
                    followMode = next
                    if (next) {
                        val p = org.osmdroid.util.GeoPoint(latitude, longitude)
                        mapViewRef?.let { it.controller.animateTo(p) }
                        followAnchor[0] = latitude to longitude
                    } else {
                        followAnchor[0] = null
                    }
                }
            } else {
                MapControlButton(Icons.Default.Refresh, "Thử định vị lại ngay", NeonAmber) {
                    onRetryLocation()
                }
            }
            MapControlButton(
                if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                "Mở rộng", TextSecondary
            ) { isExpanded = !isExpanded }
        }

        if (showMap && !followMode) {
            Text(
                "Bạn đang tự di chuyển bản đồ — bấm nút 🛰 để map bám vị trí thật trở lại.",
                style = MaterialTheme.typography.labelSmall,
                color = NeonAmber,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp)
            )
        }

        // ============ THANH THÔNG SỐ — CŨNG NĂM NGOÀI THẺ MAP ============
        if (showMap) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                    .border(1.dp, rgbGradientBrush, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        studentName.ifEmpty { "Vị trí học sinh" },
                        style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Bold
                    )
                    Text(
                        displayAddress,
                        style = MaterialTheme.typography.bodySmall, color = FacebookBlue,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (isStaleFix) {
                        Text(
                            "Vị trí có thể đã cũ • $lastUpdateText",
                            style = MaterialTheme.typography.labelSmall, color = NeonRose, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    // batteryLevel <= 0 = CHƯA có telemetry (LocationData mặc định 0).
                    // In "—" tint xám; chỉ báo amber khi thực sự yếu (1..19).
                    val batteryText = if (batteryLevel <= 0) "—"
                        else "$batteryLevel%${if (isCharging) "⚡" else ""}"
                    val batteryTint = when {
                        batteryLevel <= 0 -> TextMuted
                        batteryLevel < 20 -> NeonAmber
                        else -> NeonEmerald
                    }
                    StatPill(Icons.Default.BatteryChargingFull, batteryText, batteryTint)
                    StatPill(Icons.Default.Speed, "${speedKmh.toInt()} km/h", FacebookBlue)
                    if (validRoutePoints.size >= 2) {
                        StatPill(Icons.Default.Route, String.format(Locale.US, "%.2f km", distanceFromStartKm ?: 0.0), NeonCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.size(32.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StatPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(2.dp))
            Text(text, fontSize = 10.sp, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
    }
}

private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return r * 2 * atan2(sqrt(a), sqrt(1 - a))
}

private fun compassLabel(deg: Float): String {
    val dirs = listOf("Bắc", "Đông Bắc", "Đông", "Đông Nam", "Nam", "Tây Nam", "Tây", "Tây Bắc")
    val idx = (((deg % 360) + 360) % 360 / 45f).toInt() % 8
    return dirs[idx]
}
