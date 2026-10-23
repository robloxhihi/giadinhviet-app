package com.example.service.map

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * Nền bản đồ dùng chung cho thẻ vị trí TRÊN MÀN HÌNH và cho tải vùng OFFLINE.
 *
 * Bắt buộc dùng chung một file: osmdroid tra kho theo TEN cua tile source
 * (`SqlTileWriter` luong theo tileSourceName + tile index). Neu cho offline dung
 * source khac voi cho hien thi thi tai xong van thay trang, va do la loi
 * "bao tai thanh cong nhung khong tai duoc" cua ban cu.
 *
 * MOI ZOOM TOI DA LA SO DO TRUC TIEP hom 2026-10-21 tren 10 diem o VN (nong thon
 * Quang Ngai 14.45722/107.91763, Ha Noi, Sai Gon, Da Nang, Hai Phong, Ha Giang,
 * Tuyen Son, rung Tay Nguyen, bien Phu Quy) = 40+ tile tai that:
 *  - Google `/vt/lyrs=y`: 200, anh mau that toi z21 (Quang Ngai z18 14.341 B,
 *    z19 13.178 B, z20 11.250 B, z21 7.669 B).
 *  - Esri World Imagery: z18 van la anh that, nhung z19-20 tra ve dung 2.521 byte
 *    co md5 giong het nhau (f27d9de7) = ô xám "khong co du lieu".
 *  - tile.openstreetmap.org (TileSourceFactory.MAPNIK): HTTP 000, khong co phan
 *    hoi nao => da bi loai khoi app, vi no chinh la ly do map trong trang.
 */
object MapTileSources {

    /** Google tra tile theo query string, khong theo duong danh {z}/{x}/{y}. */
    private class GoogleSource(
        keyName: String,
        layers: String,
        imageExt: String
    ) : XYTileSource(
        keyName, 0, 21, 256, ".$imageExt",
        arrayOf("https://mt1.google.com/vt/lyrs=$layers&")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String = StringBuilder(getBaseUrl())
            .append("x=").append(MapTileIndex.getX(pMapTileIndex))
            .append("&y=").append(MapTileIndex.getY(pMapTileIndex))
            .append("&z=").append(MapTileIndex.getZoom(pMapTileIndex))
            .toString()
    }

    /**
     * Esri dung thu tu {z}/{y}/{x} (row/col) va khong co duoi tren URL.
     * [maxZoom] khong phai do Esri tu khai — do la tran da do truc tiep tai
     * toa do nong thon Quang Ngai hom 2026-10-23 (xem ghi chu o dau file).
     */
    private class EsriSource(
        keyName: String,
        service: String,
        maxZoom: Int
    ) : XYTileSource(
        keyName, 0, maxZoom, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/$service/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String = StringBuilder(getBaseUrl())
            .append(MapTileIndex.getZoom(pMapTileIndex)).append('/')
            .append(MapTileIndex.getY(pMapTileIndex)).append('/')
            .append(MapTileIndex.getX(pMapTileIndex))
            .append(".jpg")
            .toString()
    }

    val GOOGLE_MAX_ZOOM = 21
    val ESRI_MAX_ZOOM = 18

    /**
     * Tran du lieu THAT cua World_Street_Map, do hom 2026-10-23 tai
     * 14.4572191/107.9176331: z15 = 9.565 B, z16 = 5.174 B, z17 = 2.421 B (md5
     * f7f490c2, anh that), z18 = 2.521 B md5 f27d9de7 = dung o xam "khong du lieu"
     * nhu World_Imagery. De maxZoom = 18 la ban do trong trang o cap cao nhat.
     */
    val ESRI_STREET_MAX_ZOOM = 17

    // Kieu phai ghi tuong minh: class ben duoi la private, de Kotlin tu suy ra thi
    // "public property exposes its private type" la loi bien dich.
    val HYBRID: OnlineTileSourceBase = GoogleSource("GoogleHybrid", "y", "jpg")
    val SATELLITE: OnlineTileSourceBase = GoogleSource("GoogleSatellite", "s", "jpg")
    val STREET: OnlineTileSourceBase = GoogleSource("GoogleStreet", "m", "png")
    val ESRI: OnlineTileSourceBase = EsriSource("EsriWorldImagery", "World_Imagery", 18)

    /**
     * Nen duong pho bo sung theo yeu cau "chon ban do OSM chi tiet".
     *
     * KHONG phai tile.openstreetmap.org: host do do truc tiep hom 2026-10-23 van
     * tra HTTP 000 (khong phan hoi), va hom 2026-10-21 cung da nhu vay — dung no
     * thi map trong trang, dung cai nguoi dung kha. Carto Voyager (duong dan ve
     * lai tu OSM) cung bi chan: moi zoom tra ve dung 2.049 byte giong het nhau,
     * tuc la mot anh loi. World_Street_Map cua Esri thi tai that va du lieu duong
     * pho cua no cung den tu OpenStreetMap, nen ghi ro credit la OSM.
     */
    val ESRI_STREET: OnlineTileSourceBase =
        EsriSource("EsriWorldStreetMap", "World_Street_Map", ESRI_STREET_MAX_ZOOM)

    /**
     * Max zoom ma tile that su co du lieu — khong phai max zoom source tu khai.
     * Goi y mac dinh cho vung offline: du de doc ten duong o cap tinh, van nam
     * duoi tran cua tung nen.
     */
    fun suggestedMaxZoom(source: OnlineTileSourceBase): Int = when (source) {
        ESRI -> ESRI_MAX_ZOOM
        ESRI_STREET -> ESRI_STREET_MAX_ZOOM
        HYBRID, SATELLITE, STREET -> GOOGLE_MAX_ZOOM
        else -> source.maximumZoomLevel.coerceAtMost(GOOGLE_MAX_ZOOM)
    }
}
