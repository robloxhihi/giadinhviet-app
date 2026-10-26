package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 * Vi sao PickVisualMedia: do la hop chon anh chuan hien nay, khong can quyen
 * doc bo nho va khong mo khuon "chon tap tin" tren may Android 13+. May cu hon
 * thi chinh androidx roi no ve ACTION_GET_CONTENT — do la dieu can kiem chung
 * tren may that: BlueStacks 5 la Android 9 (API 28, do ngay 2026-10-26), va
 * `pm resolve-activity -a android.intent.action.GET_CONTENT -t image/*` tren may
 * do tra ve `com.android.documentsui/.picker.PickActivity`, tuc la duong van
 * song. May nao khong co host nao thi nguoi dung phai thay mot dong chu chu
 * khong phai mot cai bam khong toi dau.
 *
 * @param onPicked chi goi khi co anh va doc ra base64 that.
 * @return ham goi de mo bo chon (bam vao avatar).
 */
@Composable
fun rememberAvatarPicker(onPicked: (String) -> Unit): () -> Unit {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
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

    // Khong `remember` vong lap thu hai: `launcher` da on-dinh, con lambda nay
    // chi la mot tui goi ham, tai tao moi lan recomposition cung khong doi gi.
    return {
        runCatching {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }.onFailure { error ->
            val reason = if (error is ActivityNotFoundException)
                "Máy này không có ứng dụng chọn ảnh." else "Không mở được bộ chọn ảnh."
            Toast.makeText(context, reason, Toast.LENGTH_LONG).show()
        }
    }
}
