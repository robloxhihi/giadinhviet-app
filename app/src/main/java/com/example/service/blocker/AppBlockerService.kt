package com.example.service.blocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.data.FirestoreDb
import com.example.data.model.UsageSchedule
import com.google.firebase.firestore.ListenerRegistration
import java.util.Calendar
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Chan ung dung dang chay bang AccessibilityService.
 *
 * Cach hoat dong: moi lan mot ung dung buoc len man hinh dau tien, Android
 * gui su kien TYPE_WINDOW_STATE_CHANGED. Service lay ten goi tuc, hoi
 * BlockPolicy xem co bi chan khong; neu bi chan thi day ve man hinh Home
 * va hien ly do.
 *
 * Khong can Device Owner, khong can root. Nguoi dung phai tu bat trong
 * Cai dat -> Tro nang tiep can. Tat bat ky luc nao.
 */
class AppBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlockerService"
        private const val SCHEDULE_DOC = "default_sched"

        /** De ScreenTimeScreen biet chan dang bat that hay chua. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    private var schedule: UsageSchedule? = null
    private var userReg: ListenerRegistration? = null
    private var scheduleReg: ListenerRegistration? = null
    private var watchedPairingId: String? = null

    /** UID ma listener `users/{uid}` dang theo doi — de phat hien doi tai khoan. */
    private var watchedUid: String? = null

    /**
     * Mach song nguoi dung DANG NHAP sau khi Tro nang da bat. Truoc day
     * `listenSchedule()` return ngam khi `currentUser == null` va chi duoc goi
     * lai trong `onServiceConnected()`, nen neu bat Tro nang truoc khi dang
     * nhap (thu tiep dien cua me: cai dat quyen truoc, dang nhap sau) thi bo
     * chan chay trong suot ma khong doc duoc noi quy nao — phai tat/bat Tro
     * nang lai may moi dung.
     */
    private var authReg: com.google.firebase.auth.FirebaseAuth.AuthStateListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: LinearLayout? = null
    private var lastBlockedPackage: String? = null
    private var lastBlockedAt: Long = 0L

    /** Khong ghi de toast/overlay lien tuc khi user lien tuc mo lai app. */
    private val cooldownMs = 4_000L

    /**
     * So phut da dung hom nay — doc TU NEN, khong doc truc tiep trong
     * `onAccessibilityEvent`.
     *
     * `queryUsageStats()` la mot lenh doc dien tren may, mat den chuc ms va
     * chua them tong hop tung app. Service Tro nang chay tren main thread:
     * goi no moi lan TYPE_WINDOW_STATE_CHANGED (tuc moi lan mo app, moi lan
     * doi activity, moi lan ra khoi Home) lam giay khi chuyen canh — nguoi
     * dung nhan thay "cham khi tich app". Giu gia tri trong 60 giay va tinh
     * tren the nen moi lan doi cua so chi la mot phep doc bien.
     */
    @Volatile
    private var usedMinutesCache: Int = 0

    /** Luc gan `usedMinutesCache` duoc tinh — dung de biet khi nao Hoi lai. */
    @Volatile
    private var usedMinutesAt: Long = 0L

    /** NGAY cua lan tinh gan nhat; sang ngay moi thi 0 phut, khong phai cong dan. */
    private var usedMinutesDayKey: Int = -1

    /** Khong nop them lay khi lay truoc chua ve xong. */
    @Volatile
    private var usageRefreshInFlight: Boolean = false

    private val usageRefreshMs = 60_000L
    private val usageExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
        listenSchedule()
        startAuthWatcher()
        // Am don so phut hom nay ngay khi bat: lan kiem tra dau tien cua bo
        // chan se co ngay so lieu that, thay vi dung gia tri 0.
        usedMinutesToday(Calendar.getInstance())
        Log.i(TAG, "Da ket noi — che do chan ung dung dang hoat dong")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        if (ev.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = ev.packageName?.toString() ?: return
        val s = schedule ?: return

        // So phut hom nay lay tu bo nho dem (xem usedMinutesToday) — day KHONG
        // phai lan doc dien.
        val nowCal = Calendar.getInstance()
        val used = usedMinutesToday(nowCal)
        val reason = BlockPolicy.blockReason(
            pkg = pkg,
            schedule = s,
            ownPackage = packageName,
            usedMinutesToday = used,
            now = nowCal
        ) ?: return

        val now = System.currentTimeMillis()
        if (pkg == lastBlockedPackage && now - lastBlockedAt < cooldownMs) return
        lastBlockedPackage = pkg
        lastBlockedAt = now

        // Dem SANG TAI LAN that su bi chan, khong dem danh sach app bi chan.
        // Bo dem nay la dau vao cua bang dieu tra gui cho tro ly AI, noi
        // "hom nay con 3 lan mo app bi chan" phai la so that.
        runCatching { com.example.data.ai.DailyUsageStore(applicationContext).recordBlockedAttempt(pkg) }

        // Ve Home truoc, sau do moi noi ly do.
        val wentHome = performGlobalAction(GLOBAL_ACTION_HOME)
        Log.i(TAG, "Chan $pkg (ve Home = $wentHome) — $reason")
        showNotice(pkg, reason)
    }

    /**
     * Tai lich gan nhat cua cap gia dinh dang dang nhap.
     *
     * Dung SnapshotListener cho ca document user, khong dung `get()` mot lan:
     * neu bat Tro nang TRUOC khi ghep noi thi `get()` thay activePairingId rong
     * va nghi is hinh — bo chan vinh vien khong ap dung cho toi khi service
     * khoi dong lai (tat/bat Tro nang). Lang nghe giup tu ket noi ngay khi
     * nguoi dung nhap ma ghep doi.
     */
    private fun listenSchedule() {
        val uid = currentUid()
        if (uid == null) {
            Log.i(TAG, "Chua dang nhap — bo chan se tu doc lich ngay khi dang nhap")
            return
        }
        watchedUid = uid
        val db = FirestoreDb.instance()

        userReg = db.collection("users").document(uid)
            .addSnapshotListener { userSnap, err ->
                if (err != null) {
                    Log.w(TAG, "Doc users/$uid loi: ${err.message}")
                    return@addSnapshotListener
                }
                val pairingId = userSnap?.getString("activePairingId").orEmpty()
                if (pairingId.isEmpty()) {
                    scheduleReg?.remove()
                    scheduleReg = null
                    watchedPairingId = null
                    schedule = null
                    Log.i(TAG, "Chua ghep noi — chua ap dung chan nao")
                    return@addSnapshotListener
                }
                if (pairingId != watchedPairingId) {
                    watchedPairingId = pairingId
                    attachScheduleListener(pairingId)
                }
            }
    }

    private fun attachScheduleListener(pairingId: String) {
        scheduleReg?.remove()
        scheduleReg = FirestoreDb.instance()
            .collection("pairings").document(pairingId)
            .collection("schedules").document(SCHEDULE_DOC)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "Tai lich that bai: ${err.message}")
                    return@addSnapshotListener
                }
                schedule = snap?.toObject(UsageSchedule::class.java)?.let(ConsentPolicy::normalizePending)
                Log.i(
                    TAG,
                    "Lich: isReminderOnly=${schedule?.isReminderOnly} " +
                        "blocked=${schedule?.blockedPackages?.size ?: 0}"
                )
            }
    }

    private fun stopListening() {
        userReg?.remove(); userReg = null
        scheduleReg?.remove(); scheduleReg = null
        watchedPairingId = null
        watchedUid = null
        schedule = null
    }

    private fun startAuthWatcher() {
        if (authReg != null) return
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != watchedUid) {
                Log.i(TAG, "Tai khoan doi ($watchedUid -> $uid) — doc lai lich chan")
                stopListening()
                listenSchedule()
            }
        }
        authReg = listener
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener(listener) }
            .onFailure { Log.w(TAG, "Gan AuthStateListener loi: ${it.message}") }
    }

    private fun stopAuthWatcher() {
        authReg?.let { reg ->
            runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().removeAuthStateListener(reg) }
        }
        authReg = null
    }

    private fun currentUid(): String? =
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    /**
     * Tong so phut ma moi app cung chay TU 00:00 DEN GIAY NAY, phuc vu kiem tra
     * han ngay. Tra loi bang gia tri da nho; neu du 60 giay thi nop mot lan
     * tinh lai nen (khong cho ket qua — cham 1 phut tre so voi viec dong may).
     *
     * Cua so tinh tu NUA DEM chu khong phai "24 gio qua": dung `now - 24h` thi
     * luc 8 toi dem duoc ca thoi xai cua hom qua, va may chan het moi thu vi
     * tuong rang con da vuot han muc ngay.
     */
    private fun usedMinutesToday(now: Calendar): Int {
        val dayKey = now.get(Calendar.DAY_OF_YEAR)
        if (dayKey != usedMinutesDayKey) {
            usedMinutesDayKey = dayKey
            usedMinutesCache = 0
            usedMinutesAt = 0L
        }
        val t = System.currentTimeMillis()
        if (t - usedMinutesAt >= usageRefreshMs && !usageRefreshInFlight) {
            usedMinutesAt = t
            usageRefreshInFlight = true
            val beginOfDay = midnightOf(now)
            usageExecutor.execute {
                try {
                    usedMinutesCache = queryMinutesBetween(beginOfDay, System.currentTimeMillis())
                } finally {
                    usageRefreshInFlight = false
                }
            }
        }
        return usedMinutesCache
    }

    private fun midnightOf(now: Calendar): Long = (now.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Doc UsageStats — CHI duoc goi tren the nen cua `usageExecutor`. */
    private fun queryMinutesBetween(begin: Long, end: Long): Int = try {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, begin, end) ?: return 0
        stats.sumOf { TimeUnit.MILLISECONDS.toMinutes(it.totalTimeInForeground).toInt() }
    } catch (e: Exception) {
        // SecurityException = chua cap quyen "Truy cau thong ke su dung"; cac loi
        // khac (RemoteException/OOM tu PackageManager) cung khong duoc phep lam
        // sập cả service — tra 0, bo chan se khong chan vi vuot han.
        Log.w(TAG, "Doc thong ke su dung loi: ${e.message}")
        0
    }

    /**
     * Noi ly do. Co overlay thi hien giua man hinh, khong thi dung Toast —
     * overlay can quyen Ve tren ung dung khac.
     */
    private fun showNotice(pkg: String, reason: String) {
        val label = appLabel(pkg)
        // Dat ten la `message`, dung goi la `text`: trong `TextView.apply { text = ... }`
        // ten local se che thuoc tinh cua receiver va bi xem la gan lai `val`.
        val message = "$label\n$reason"

        if (!Settings.canDrawOverlays(this)) {
            toast(message)
            return
        }

        mainHandler.post {
            dismissOverlay()
            runCatching {
                val wm = getSystemService(WINDOW_SERVICE) as WindowManager
                val root = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(56, 44, 56, 44)
                    // KIN HOAN TOAN (#FF): mau E6 cho app bi chan boi xuyen
                    // qua sau tam thong bao — cam giac "chong nen" ngay giua
                    // man hinh.
                    setBackgroundColor(Color.parseColor("#FF04050A"))
                }
                root.addView(TextView(this).apply {
                    text = message
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER
                })
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }
                wm.addView(root, lp)
                overlayView = root
                mainHandler.postDelayed({ dismissOverlay() }, 2_600L)
            }.onFailure {
                Log.w(TAG, "Overlay loi: ${it.message}")
                toast(message)
            }
        }
    }

    private fun dismissOverlay() {
        overlayView?.let { v ->
            runCatching {
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v)
            }
        }
        overlayView = null
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(
            packageManager.getApplicationInfo(pkg, 0)
        ).toString()
    }.getOrDefault(pkg)

    private fun toast(text: String) {
        mainHandler.post {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        }
    }

    override fun onInterrupt() {
        dismissOverlay()
    }

    override fun onDestroy() {
        isRunning = false
        stopListening()
        stopAuthWatcher()
        dismissOverlay()
        runCatching { usageExecutor.shutdownNow() }
        super.onDestroy()
        Log.i(TAG, "Service tat — khong con chan ung dung nao")
    }
}
