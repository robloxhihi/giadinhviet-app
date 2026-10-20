package com.example.service.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Tải bản đồ OFFLINE thật: tải các ô bản đồ (tiles) của OpenStreetMap về kho
 * tiles của osmdroid để xem được khi không có Internet.
 *
 * Đây là bản sửa lỗi "báo đã tải thành công nhưng không tải gì" — mọi ô đều
 * được tải thực, và kết quả trả về phản ánh đúng số ô thành công/thất bại.
 */
object OfflineMapDownloader {

    data class Result(
        val downloaded: Int,
        val failed: Int,
        val total: Int,
        val cancelled: Boolean
    ) {
        val isSuccess: Boolean get() = !cancelled && downloaded > 0 && failed == 0
        val message: String
            get() = when {
                cancelled -> "Đã hủy tải bản đồ (đã tải $downloaded/$total ô)."
                total == 0 -> "Không có ô bản đồ nào trong vùng chọn."
                failed == 0 -> "Đã tải xong $downloaded/$total ô bản đồ offline."
                else -> "Đã tải $downloaded/$total ô, $failed ô lỗi (kiểm tra mạng rồi thử lại)."
            }
    }

    data class Region(
        val name: String,
        val centerLat: Double,
        val centerLng: Double,
        val spanDegrees: Double = 0.30
    )

    /** 34 tỉnh/thành — toạ độ tâm gần đúng để tải vùng bản đồ. */
    val provinces: List<Region> = listOf(
        Region("Hà Nội", 21.0285, 105.8342),
        Region("TP. Hồ Chí Minh", 10.8231, 106.6297),
        Region("Đà Nẵng", 16.0544, 108.2022),
        Region("Hải Phòng", 20.8650, 106.6833),
        Region("Cần Thơ", 10.0452, 105.7469),
        Region("Thừa Thiên Huế", 16.4637, 107.5844),
        Region("Khánh Hòa", 12.2388, 109.1967),
        Region("Quảng Ninh", 20.9599, 107.0837),
        Region("Bình Dương", 11.3254, 106.4770),
        Region("Đồng Nai", 10.9453, 106.8241),
        Region("Lâm Đồng", 11.5753, 108.1429),
        Region("An Giang", 10.3861, 105.4353),
        Region("Bà Rịa - Vũng Tàu", 10.5417, 107.2429),
        Region("Bắc Ninh", 21.1861, 106.0763),
        Region("Bình Định", 13.7830, 109.2194),
        Region("Đắk Lắk", 12.6670, 108.0378),
        Region("Đồng Tháp", 10.4934, 105.6882),
        Region("Gia Lai", 13.9833, 108.0000),
        Region("Hà Tĩnh", 18.3554, 105.8878),
        Region("Hải Dương", 20.9379, 106.3144),
        Region("Hòa Bình", 20.8172, 105.3376),
        Region("Hưng Yên", 20.6464, 106.0511),
        Region("Kiên Giang", 10.0125, 105.0809),
        Region("Nghệ An", 18.6790, 105.6813),
        Region("Nam Định", 20.4382, 106.1621),
        Region("Ninh Bình", 20.2581, 105.9744),
        Region("Phú Thọ", 21.3227, 105.4020),
        Region("Quảng Nam", 15.5735, 108.4740),
        Region("Quảng Ngãi", 15.1214, 108.8044),
        Region("Quảng Trị", 16.7500, 107.1852),
        Region("Sóc Trăng", 9.6025, 105.9800),
        Region("Tây Ninh", 11.3352, 106.1091),
        Region("Thái Bình", 20.4464, 106.3363),
        Region("Thái Nguyên", 21.5939, 105.8482)
    )

    /**
     * Tải một vùng. [onProgress] được gọi trên luồng IO: (đãTải, tổng, tênVùng).
     */
    suspend fun downloadRegion(
        context: Context,
        region: Region,
        minZoom: Int = 11,
        maxZoom: Int = 13,
        cancelFlag: AtomicBoolean = AtomicBoolean(false),
        onProgress: (downloaded: Int, total: Int) -> Unit = { _, _ -> }
    ): Result = withContext(Dispatchers.IO) {
        val tileSource: org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase = TileSourceFactory.MAPNIK
        try {
            Configuration.getInstance().load(
                context,
                android.preference.PreferenceManager.getDefaultSharedPreferences(context)
            )
            if (Configuration.getInstance().userAgentValue.isNullOrBlank()) {
                Configuration.getInstance().userAgentValue = "VesperaFamilySafe/${context.packageName}"
            }
        } catch (_: Exception) {}

        val half = region.spanDegrees / 2.0
        val minLat = region.centerLat - half
        val maxLat = region.centerLat + half
        val minLon = region.centerLng - half
        val maxLon = region.centerLng + half

        val tiles = mutableListOf<Long>()
        for (z in minZoom..maxZoom) {
            val x0 = lonToTileX(minLon, z)
            val x1 = lonToTileX(maxLon, z)
            val y0 = latToTileY(maxLat, z) // north = smaller y
            val y1 = latToTileY(minLat, z)
            for (x in x0..x1) {
                for (y in y0..y1) {
                    tiles.add(MapTileIndex.getTileIndex(z, x, y))
                }
            }
        }

        val writer = SqlTileWriter()
        var downloaded = 0
        var failed = 0
        val total = tiles.size

        try {
            for ((i, index) in tiles.withIndex()) {
                coroutineContext.ensureActive()
                if (cancelFlag.get()) {
                    return@withContext Result(downloaded, failed, total, cancelled = true)
                }
                if (writer.exists(tileSource, index)) {
                    downloaded++
                    if (i % 5 == 0) onProgress(downloaded, total)
                    continue
                }
                val url = try {
                    tileSource.getTileURLString(index)
                } catch (e: Exception) {
                    null
                }
                if (url.isNullOrBlank()) {
                    failed++
                    continue
                }
                val ok = fetchTile(url, writer, tileSource, index)
                if (ok) downloaded++ else failed++
                if (i % 5 == 0 || i == total - 1) onProgress(downloaded, total)
            }
        } catch (e: Exception) {
            Log.w("OfflineMapDownloader", "Tải vùng ${region.name} lỗi: ${e.message}")
        } finally {
            try { writer.onDetach() } catch (_: Exception) {}
        }
        Result(downloaded, failed, total, cancelled = false)
    }

    private fun fetchTile(url: String, writer: SqlTileWriter, source: ITileSource, index: Long): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty("User-Agent", Configuration.getInstance().userAgentValue ?: "VesperaFamilySafe")
            }
            val code = conn.responseCode
            if (code != 200) return false
            BufferedInputStream(conn.inputStream).use { input ->
                writer.saveFile(source, index, input, null)
            }
        } catch (e: Exception) {
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // ── Web-Mercator (slippy map) helpers ──────────────────────────────────
    private fun lonToTileX(lon: Double, zoom: Int): Int {
        val n = 1 shl zoom
        return floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
    }

    private fun latToTileY(lat: Double, zoom: Int): Int {
        val n = 1 shl zoom
        val latRad = lat * PI / 180.0
        val y = (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
        return floor(y).toInt().coerceIn(0, n - 1)
    }

    @Suppress("unused")
    private fun tileYToLat(y: Double, zoom: Int): Double {
        val n = PI - 2.0 * PI * y / (1 shl zoom)
        return 180.0 / PI * atan(sinh(n))
    }
}
