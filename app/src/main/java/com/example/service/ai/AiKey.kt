package com.example.service.ai

import android.content.Context
import com.example.BuildConfig

/**
 * khoa Gemini dung cho tro ly AI.
 *
 * THU TUC: key KHONG nam trong repo. `.env` da bi .gitignore, CI viet no tu
 * GitHub Secret `GEMINI_API_KEY` (Settings -> Secrets and variables -> Actions).
 * `.env.example` chi co gia tri hoa toan de BuildConfig luon sinh ra truong
 * GEMINI_API_KEY — neu build ma khong co secret thi APK nhan duoi chuoi
 * REPLACE_ME_va app tu hoi key lan dau tien mo tro ly.
 *
 * Vi sao khong commit key thang: APK tai tu GitHub Release la cong khai, va
 * BuildConfig nam trong classes.dex dang chuoi plain — bat cu nao giai nap
 * APK cung lay duoc key va tieu tien tai khoan Google cua ban. Nguoi dung
 * nhap key ngay tren may (luu trong SharedPreferences rieng cua app) la
 * duong an toan nhat ma van chi mat mot lan gop chu.
 */
object AiKey {

    private const val PREFS = "ai_config_v1"
    private const val KEY_MANUAL = "gemini_key_manual"

    /** Key nguoi lon da tu nhap tren may nay, neu co. */
    fun entered(context: Context): String = prefs(context).getString(KEY_MANUAL, null).orEmpty().trim()

    fun save(context: Context, key: String) {
        prefs(context).edit().putString(KEY_MANUAL, key.trim()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_MANUAL).apply()
    }

    /** Thu tu uu tien: nhap tay tren may > tiem tu luc build (CI secret). */
    fun effective(context: Context): String =
        entered(context).ifBlank { BuildConfig.GEMINI_API_KEY.orEmpty().trim() }

    /**
     * Chuoi nao duoc xem la khong dung duoc: thieu, hoac la cho cho cua
     * `.env.example`. Do dai toi thieu 20 ky tu bo qua nhung chuoi roi loan
     * va giu nguyen loi ban dau cua Google ("AIza...", "AQ....").
     */
    fun isUsable(key: String): Boolean =
        key.length >= 20 && !key.contains("REPLACE_ME", ignoreCase = true) && !key.contains(" ")

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
