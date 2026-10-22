package com.example.ui.components

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.service.sos.SosPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val TAG = "SosVoice"

/**
 * Doc toa do cuu nan bang Tieng Viet.
 *
 * Ly do can doc: luc con nhan vuon thi tay co the dang chim va mat dang nhin
 * duong; nguoi cuu nan dung ngay canh cung can NGHE thay vi tri chu khong chi
 * doc. Toa do van IN ra man hinh — loi doc la them, khong thay the.
 *
 * Tat khi man hinh roi: `onDispose` goi `stop()` roi `shutdown()`, de mot may
 * cuu nan khong keu lien tuc sau khi da ghe trang thai.
 *
 * May khong co giong Tieng Viet (ROM la, data Google TTS chua tai) thi KHONG
 * doc bang ngon ngu khac: "two one point zero" ghi toa do la sai luc cuu nan.
 * App bao thang dieu do len man hinh.
 */
class SosVoice(context: Context) {

    private var engine: TextToSpeech? = null
    private val initialized = CompletableDeferred<Boolean>()

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS khoi dong that bai (status $status)")
                if (!initialized.isCompleted) initialized.complete(false)
                return@TextToSpeech
            }
            val ok = try {
                val result = engine?.setLanguage(Locale("vi"))
                result != null &&
                    result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            } catch (e: Exception) {
                Log.w(TAG, "Khong doi duoc ngon ngu tieng Viet: ${e.message}")
                false
            }
            Log.d(TAG, "TTS san sang, giong tieng Viet = $ok")
            if (!initialized.isCompleted) initialized.complete(ok)
        }
    }

    /** Cho TTS khoi dong xong (toi da 5 giay) va noi ro may co giong Viet khong. */
    suspend fun awaitVietnameseVoice(): Boolean =
        withTimeoutOrNull(5_000L) { initialized.await() } ?: false

    fun speak(text: String) {
        if (text.isBlank()) return
        try {
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sos_vitri")
        } catch (e: Exception) {
            Log.w(TAG, "Khong doc duoc: ${e.message}")
        }
    }

    fun stop() {
        try {
            engine?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        stop()
        try {
            engine?.shutdown()
        } catch (_: Exception) {
        }
        engine = null
    }
}

/** TTS song cung man hinh: ra khoi man hinh la dung doc ngay. */
@Composable
fun rememberSosVoice(): SosVoice {
    val appContext = LocalContext.current.applicationContext
    val voice = remember { SosVoice(appContext) }
    DisposableEffect(voice) {
        onDispose {
            voice.stop()
            voice.shutdown()
        }
    }
    return voice
}

/**
 * Qua [delayMs] (mac dinh 2 giay dung nhu yeu cau) thi doc toa do ra.
 *
 * Tra loi doc "co that duoc hay khong" qua `onStatus` de man hinh noi thang,
 * khong im lang: nguoi dung dung cu bang rang may van dang doc.
 */
@Composable
fun SosAutoReadPosition(
    voice: SosVoice,
    text: String,
    autoRead: Boolean = true,
    delayMs: Long = SosPlan.SPEECH_DELAY_MS,
    onStatus: (String?) -> Unit = {}
) {
    // `visible` that theo vong doi composition: neu bang SOS dong truoc khi kip
    // doc thi khong duoc mo mieng nua.
    var visible by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        visible = true
        onDispose { visible = false }
    }

    LaunchedEffect(text, autoRead) {
        if (!autoRead || text.isBlank()) return@LaunchedEffect
        delay(delayMs)
        if (!visible) return@LaunchedEffect
        val hasVietnamese = voice.awaitVietnameseVoice()
        onStatus(SosPlan.speechUnavailableMessage(hasVietnamese))
        if (hasVietnamese && visible) voice.speak(text)
    }
}
