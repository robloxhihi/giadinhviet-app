package com.example.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Anh dai dien: nen thanh JPEG vuong ~96px roi nhap base64 vao document
 * `users/{uid}`. Du an chua bat Firebase Storage, day la con duong duy nhat de
 * avatar dong bo giua hai may. 96px du cho vong tron 46dp ke ca man 2.5x, va
 * base64 cua no chi vai KB — Firestore gioi han 1MB/document nen anh goc 12MP
 * chen thang vao la vo.
 */
const val AVATAR_EDGE_PX = 96

/** Doc nguoc lai base64 da luu. Chuoi rac (anh bi cat nua, key cu) thi tra null. */
fun avatarBitmapFromBase64(base64: String): Bitmap? {
    if (base64.isBlank()) return null
    return runCatching {
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * Doc anh tu bo chon va nen ve 96px. Anh duoc decode hai lan: lan dau
 * `inJustDecodeBounds` chi lay kich thuoc that (khong nap ca buc 12MP vao RAM),
 * lan hai moi decode that voi `inSampleSize`.
 */
fun readImageAsAvatarBase64(context: Context, uri: Uri): String? = runCatching {
    val resolver = context.contentResolver

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= AVATAR_EDGE_PX &&
        bounds.outHeight / (sample * 2) >= AVATAR_EDGE_PX
    ) sample *= 2

    val src = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(
            it, null, BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } ?: return null

    // Co canh ngan ve dung 96px; hinh dai/hong giu nguyen ty le, vong tron hien
    // thi tu cat qua ContentScale.Crop.
    val scale = AVATAR_EDGE_PX.toFloat() / minOf(src.width, src.height)
    val scaled = Bitmap.createScaledBitmap(
        src,
        (src.width * scale).toInt().coerceAtLeast(1),
        (src.height * scale).toInt().coerceAtLeast(1),
        true
    )
    val out = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
    Base64.encodeToString(out.toByteArray(), Base64.DEFAULT)
}.getOrNull()

/**
 * Bo chon anh dai dien dung chung cho ca may phu huynh (Tong quan) lan may hoc
 * sinh. Doi avatar tung bi bao "khong thay duoc": tren duong do co nhung buoc
 * chet im lang — bam khong mo gi, hoac mo xong doc anh khong ra — va nguoi dung
 * khong phan biet duoc voi "app loi".
 *
 * Vi sao PickVisualMedia o Android 13 tro len: do la hop chon anh chuan hien
 * nay, khong can quyen doc bo nho va khong mo khuon "chon tap tin". Tren may cu
 * hon thi KHONG the tin no. Do that thu 27/10 tren BlueStacks 5 (Android 9, API
 * 28): bam avatar mo ra `com.bluestacks.filemanager` (FileSharer cua gia lap),
 * khong phai DocumentsUI. No liet ke du anh trong may, nhung cham vao mot anh thi
 * Activity cua no khong bao gio finish — dem chon "0" dung nguyen, khong co nut
 * Mo, va app khong bao gio nhan duoc Intent ket qua nao de ma doc. 69.4 da mo het
 * duong tra ve (data, clipData, moi extra) nen loi khong con o cho doc ket qua;
 * con lai la chon sai bo chon. Vi vay 69.5 pin ACTION_GET_CONTENT ve DocumentsUI
 * goc khi may co no — bo chon do tra ve `content://` chuan va co finish — may 13
 * tro len van giu PhotoPicker goc, may khong co DocumentsUI thi de he tu chon.
 * Xay ra o CA HAI man (Tong quan cua phụ huynh lan man hoc sinh) nen loi nam o
 * duong chon bo chon, khong phai o man hinh. Moi nhanh khong lay duoc anh deu
 * phai noi mot dong chu chu khong phai mot cai bam khong toi dau.
 *
 * THU QUY UOC cua ngon ngu dinh tuyen (khong phai loi chinh ta): KHONG duoc viet
 * tai day chuoi "xuyen qua + dau sao" (slash roi star liet nhau). Kotlin dem
 * comment block LONG NHAU, nen hai ky tu do mo them mot lop comment va lop ngoai
 * khong bao gio dong — CI ban v69.0 chet ngay o "Syntax error: Unclosed comment".
 *
 * @param onPicked chi goi khi co anh va doc ra base64 that.
 * @return ham goi de mo bo chon (bam vao avatar).
 */
@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current

    fun receive(uri: Uri?) {
        // uri == null la nguoi dung Huy — im lang la dung, day khong phai loi.
        if (uri != null) {
            val base64 = readImageAsAvatarBase64(context, uri)
            if (base64 != null) onPicked(base64)
            else Toast.makeText(
                context,
                "Không đọc được ảnh vừa chọn (file hỏng hoặc định dạng lạ).",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Android 13 tro len: PhotoPicker goc cua he dieu hanh, khong can quyen doc
    // bo nho. May cu hon (va BlueStacks 5 la Android 9): du PickVisualMedia mo
    // ra thi host tra ket qua o dang ma hop nay doc ra null, nen dung duong
    // GET_CONTENT cu va ty mo be ket qua lay ra — xem `urisFromResult`.
    val modern = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> receive(uri) }

    val legacy = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = urisFromResult(result.data).firstOrNull()
        if (uri != null) receive(uri)
        else Toast.makeText(
            context,
            "Bộ chọn của máy này mở ra nhưng không trả tệp ảnh nào về app. " +
                "Thử chọn ảnh khác hoặc chọn từ Album.",
            Toast.LENGTH_LONG
        ).show()
    }

    return {
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                modern.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                legacy.launch(avatarGetContentIntent(context))
            }
        }.onFailure { error ->
            val reason = if (error is ActivityNotFoundException)
                "Máy này không có ứng dụng chọn ảnh." else "Không mở được bộ chọn ảnh."
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        }
    }
}

/** Chuoi MIME xin tu bo chon — ghep hai doan de khong lan nao viet no trong comment. */
private const val IMAGE_MIME = "image/" + "*"

/** Package cua bo chon tai lieu goc cua he dieu hanh — noi duy nhat tra ket qua chuan. */
private const val DOCUMENTS_UI = "com.android.documentsui"

/**
 * Mo bo chon anh kieu cu tren may duoi Android 13; chi xin anh, khong xin quyen.
 *
 * Gan `setPackage` ve DocumentsUI goc khi may co no. Do that thu 27/10 tren
 * BlueStacks 5: ACTION_GET_CONTENT tu do roi vao `com.bluestacks.filemanager`
 * (FileSharer cua gia lap). Bo chon do mo len, liet ke het anh, nhung cham vao
 * mot anh thi Activity khong bao gio finish — dem chon "0" dung nguyen, khong co
 * nut Mo, nen `urisFromResult` khong bao gio chay vi app khong bao gio nhan duoc
 * Intent ket qua. Do la han che cua rieng FileSharer cua gia lap, khong phai loi
 * doc ket qua. DocumentsUI la bo chon he dieu hanh tra ve duong dan `content://`
 * chuan va co finish, nen pin ve do thi chup duoc anh. May that nao khong co
 * DocumentsUI thi de nguyen cho he tu chon bo chon mac dinh cua no.
 */
private fun avatarGetContentIntent(context: Context): Intent {
    val base = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = IMAGE_MIME
        addCategory(Intent.CATEGORY_OPENABLE)
        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(IMAGE_MIME))
        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
    }
    val pinned = Intent(base).setPackage(DOCUMENTS_UI)
    return if (pinned.resolveActivity(context.packageManager) != null) pinned else base
}

/**
 * Mo het nhung cho bo chon co the dat duong dan anh vao: `data`, `clipData`,
 * va bat ky extra nao la `Uri` hay chuoi bat dau bang `content:`. BlueStacks
 * dung bo chon anh cua Google, va do la cho ket qua nam ma hop chon anh chuan
 * khong nhin thay — app mo duoc bo chon, nguoi dung bam anh, xong khong gi xay
 * ra ca. Do chinh la "khong thay avatar duoc".
 */
private fun urisFromResult(data: Intent?): List<Uri> {
    if (data == null) return emptyList()
    val out = mutableListOf<Uri>()
    data.clipData?.let { clip ->
        for (i in 0 until clip.itemCount) runCatching { clip.getItemAt(i).uri }.getOrNull()?.let { out.add(it) }
    }
    data.data?.let { out.add(it) }
    val extras = data.extras ?: Bundle.EMPTY
    for (key in extras.keySet()) {
        when (val value = runCatching { extras.get(key) }.getOrNull()) {
            is Uri -> out.add(value)
            is String -> if (value.startsWith("content://") || value.startsWith("file://")) out.add(Uri.parse(value))
            is Iterable<*> -> value.forEach { item ->
                when {
                    item is Uri -> out.add(item)
                    item is String && (item.startsWith("content://") || item.startsWith("file://")) ->
                        out.add(Uri.parse(item))
                }
            }
        }
    }
    return out.distinct()
}

