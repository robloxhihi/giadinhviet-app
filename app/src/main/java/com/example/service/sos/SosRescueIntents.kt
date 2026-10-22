package com.example.service.sos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest

private const val TAG = "SosRescueIntents"

/**
 * Ket qua mo mot hanh dong cua he thong (ban quay so, soan tin, Zalo) kem
 * CHUOI DAHN nguoi dung, khong phai exception. Luc cuu nan thi may hoac
 * khong co dung cu, hoac thieu quyen, hoac dung cu bi tat: ca ba la duong
 * thang, khong duoc Crashlytics thay bang man hinh trang.
 */
data class SosIntentOutcome(
    val ok: Boolean,
    /** Dung chuoi nay in len man hinh — da duoc viet bang tieng Viet pho thong. */
    val note: String,
    /** true khi dang phai rut lui vi thieu quyen (CALL -> DIAL). */
    val usedFallback: Boolean = false
)

/**
 * Tang Android cua luu cuu nan: doc moi truong that (Zalo cai hay chua, quyen
 * CALL_PHONE duoc hay chua) va mo dung hanh dong, co gan cho moi `startActivity`.
 *
 * Quyet dinh (dung ACTION nao, soan chuoi gi) nam ben [SosPlan] de unit test
 * cham duoc; o day chi con chuyen chuoi thanh `Intent` va bao ket qua.
 */
object SosRescueIntents {

    /**
     * Zalo co cai tren may khong.
     *
     * Android 11+ cat khoi `getPackageInfo` moi ung dung khong neu trong
     * `<queries>` cua Manifest. Thieu the `<package android:name="com.zing.zalo"/>`
     * thi ham nay LUON tra false — va nut "Gọi qua Zalo" bien mat tren moi may
     * co Zalo that, yen lang, khong loi. Do la ly do `SosBrandingRegressionTest`
     * kim luan chuoi trong Manifest.
     */
    fun isZaloInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(SosPlan.ZALO_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        // Mot so ROM/Go Edition nem RuntimeException khi quet package la.
        Log.w(TAG, "Khong doc duoc trang thai Zalo: ${e.message}")
        false
    }

    fun hasCallPermission(context: Context): Boolean = try {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) {
        false
    }

    /**
     * Mo cuoc goi cuu nan (tong dai hoac so nguoi than).
     *
     * Thu tu an toan:
     *  1. Da co quyen CALL_PHONE -> `ACTION_CALL`, bam la ket noi.
     *  2. Quyen bi thu hoi giua luc doc va luc bam -> `SecurityException` ->
     *     thu lai `ACTION_DIAL`. Day la loi cu: ham cu tra ve false va nguoi
     *     dung khong thay gi xay ra ca.
     *  3. May khong co dung cu quay so -> `ActivityNotFoundException` -> noi
     *     ro, khong nem exception len Compose.
     *  4. MOI intent phai duoc gim vao app quay so mac dinh cua he thong.
     *     Do tren BlueStacks ngay 2026-10-22: bam "Hotline 113" lai mo man
     *     dang nhap ZALO, vi Zalo dang ky la noi xu ly lien ket `tel:` va may
     *     khong co dialer mac dinh.Nguoi dang cuu nan ma bam ra Zalo la loi
     *     khong the chap nhan duoc.
     */
    fun call(
        context: Context,
        number: String,
        hasCallPermission: Boolean,
        redirectingMessage: String = SosPlan.REDIRECTING_TO_RESCUE
    ): SosIntentOutcome {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return SosIntentOutcome(false, SosPlan.NO_SAVED_NUMBER)

        val data = Uri.parse(SosPlan.dialUri(clean))
        val dialPkg = systemDialerPackage(context)
        if (dialPkg == null && !acceptsNonDialer(context, data)) {
            return SosIntentOutcome(false, SosPlan.noDialerHint(clean))
        }
        if (hasCallPermission) {
            if (launch(context, callIntent(data, dialPkg)) == LaunchResult.STARTED) {
                return SosIntentOutcome(true, "$redirectingMessage — ${SosPlan.CALL_STARTED}")
            }
            // ACTION_CALL bi tu choi (quyen bi thu hoi, ROM chan): rut ve ban
            // quay so. Day la loi cu — ham cu tra false lang, khong mo gi ca.
            val retry = launch(context, dialIntent(data, dialPkg))
            return SosIntentOutcome(
                ok = retry == LaunchResult.STARTED,
                note = dialerNote(retry, redirectingMessage),
                usedFallback = true
            )
        }
        val opened = launch(context, dialIntent(data, dialPkg))
        return SosIntentOutcome(
            ok = opened == LaunchResult.STARTED,
            note = dialerNote(opened, redirectingMessage)
        )
    }

    /** App quay so mac dinh cua may; null khi ROM chua chon cai nao (BlueStacks). */
    private fun systemDialerPackage(context: Context): String? = try {
        (context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager)
            ?.defaultDialerPackage?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    private fun dialIntent(data: Uri, pkg: String?): Intent =
        Intent(Intent.ACTION_DIAL, data).apply { pkg?.let { setPackage(it) } }

    private fun callIntent(data: Uri, pkg: String?): Intent =
        Intent(Intent.ACTION_CALL, data).apply { pkg?.let { setPackage(it) } }

    /**
     * Khi may khong co dialer mac dinh, chi cho dung intent khi noi xu ly KHONG
     * phai mot app nhan tin (Zalo). `resolveActivity` chi tin duoc sau khi
     * AndroidManifest khai `<queries>` cho hanh dong DIAL + scheme `tel:`.
     */
    private fun acceptsNonDialer(context: Context, data: Uri): Boolean {
        val hit = runCatching {
            Intent(Intent.ACTION_DIAL, data).resolveActivity(context.packageManager)
        }.getOrNull()?.packageName ?: return false
        return hit != SosPlan.ZALO_PACKAGE
    }

    private fun dialerNote(result: LaunchResult, redirecting: String): String = when (result) {
        LaunchResult.STARTED -> "$redirecting — ${SosPlan.DIALER_OPENED}"
        LaunchResult.NO_APP -> SosPlan.NO_DIALER_APP
        LaunchResult.PERMISSION_DENIED, LaunchResult.CRASHED -> SosPlan.NO_CALL_CHANNEL
    }

    /**
     * Soan SAN tin nhan cho nguoi than: `ACTION_SENDTO` + `sms:<so>` + thuc the
     * "sms_body".
     *
     * Khong can quyen SEND_SMS vi app khong ty gui: dung cu tin nhan cua may mo
     * len voi noi dung da dien san, nguoi dung chi bam Gui. Do la ly do duong
     * nay van chay duoc tren may chua cap quyen SMS — dung duoc luc cuu nan.
     */
    fun composeFamilySms(context: Context, number: String, body: String): SosIntentOutcome {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return SosIntentOutcome(false, SosPlan.MISSING_NUMBER_FOR_SHARE)
        val intent = Intent(SosPlan.ACTION_SENDTO, Uri.parse(SosPlan.smsUri(clean))).apply {
            // "sms_body" la chuoi dung cua AOSP Messages; EXTRA_TEXT la luong
            // cho cac dung cu tin nhan khong dung "sms_body".
            putExtra(SosPlan.EXTRA_BODY, body)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        // Thu mo thang, KHONG can `resolveActivity`: tren Android 11+ ham bi loc
        // theo <queries> va co the bao "khong co" trong khi may van co dung cu.
        val opened = launch(context, intent)
        return SosIntentOutcome(
            ok = opened == LaunchResult.STARTED,
            note = when (opened) {
                LaunchResult.STARTED ->
                    "${SosPlan.REDIRECTING_TO_RESCUE} — đã soạn sẵn tin nhắn cứu nạn kèm toạ độ, bấm Gửi."
                LaunchResult.NO_APP -> SosPlan.NO_DIALER_APP
                LaunchResult.PERMISSION_DENIED, LaunchResult.CRASHED -> SosPlan.NO_CALL_CHANNEL
            }
        )
    }

    /**
     * Mo cuoc tro chuyen Zalo theo so dien thoai.
     *
     * Co `setPackage` de chac chan rang Zalo mo, khong phai trinh duyet. neu
     * Zalo khong xu ly duoc duong dan (ban cu, bi tat) thi thu lai mot lan khong
     * han package: zalo.me se mo trong trinh duyet va van cho nhan tin/goi.
     */
    fun openZaloChat(context: Context, number: String): SosIntentOutcome {
        val clean = number.filter { it.isDigit() || it == '+' }
        if (clean.isBlank()) return SosIntentOutcome(false, SosPlan.MISSING_NUMBER_FOR_SHARE)
        val uri = Uri.parse("https://zalo.me/$clean")
        val pinned = Intent(Intent.ACTION_VIEW, uri).apply { setPackage(SosPlan.ZALO_PACKAGE) }
        if (launch(context, pinned) == LaunchResult.STARTED) {
            return SosIntentOutcome(
                true,
                "${SosPlan.REDIRECTING_TO_RESCUE} — đã mở Zalo, bấm Nghe máy để gọi."
            )
        }
        val openedInBrowser = launch(context, Intent(Intent.ACTION_VIEW, uri))
        return SosIntentOutcome(
            ok = openedInBrowser == LaunchResult.STARTED,
            note = if (openedInBrowser == LaunchResult.STARTED)
                "Zalo không mở được liên kết này — đã mở trang Zalo bằng trình duyệt."
            else SosPlan.NO_CALL_CHANNEL
        )
    }

    // ── Nha dung rieng ────────────────────────────────────────────────────

    /**
     * Ket qua mo mot hanh dong cua he thong. `PERMISSION_DENIED` va `NO_APP`
     * tach rieng vi nguoi dung can biet thieu gi: quyen (cap lai duoc, trong
     * app) hay dung cu (khong cai duoc thi goi bang may khac).
     */
    enum class LaunchResult { STARTED, PERMISSION_DENIED, NO_APP, CRASHED }

    /**
     * DUY NHAT mot cho goi `startActivity` trong ca luu cuu nan. Bat
     * `ActivityNotFoundException` va `SecurityException` rieng de nguoi dung
     * thay mot cau tieng Viet thay vi crash, va de `call` rut duoc ve
     * `ACTION_DIAL` khi quyen goi thang bi tu choi.
     */
    private fun launch(context: Context, intent: Intent): LaunchResult = try {
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        Log.d(TAG, "Da mo ${intent.action} ${intent.data}")
        LaunchResult.STARTED
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "May khong co ung dung xu ly ${intent.action}: ${e.message}")
        LaunchResult.NO_APP
    } catch (e: SecurityException) {
        Log.w(TAG, "Tu choi ${intent.action} vi thieu quyen: ${e.message}")
        LaunchResult.PERMISSION_DENIED
    } catch (e: Exception) {
        Log.e(TAG, "Loi bat ngo khi mo ${intent.action}: ${e.message}", e)
        LaunchResult.CRASHED
    }
}
