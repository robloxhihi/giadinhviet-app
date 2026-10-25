package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.AppCategoryUsage
import com.example.data.model.EmotionTag
import com.example.data.model.ExtraTimeRequest
import com.example.data.model.FamilyMessage
import com.example.data.model.LocationData
import com.example.data.model.Pairing
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.PairingStatus
import com.example.data.model.PrivacyRequest
import com.example.data.model.ScreenTimeAgreement
import com.example.data.model.RequestStatus
import com.example.data.model.RuleAuditEntry
import com.example.data.model.UsageSchedule
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.example.data.ai.DailyUsageStore
import com.example.data.model.hasRealFix
import com.example.data.model.todayUsedMinutes
import com.example.data.model.windowCode
import com.example.data.repository.VesperaRepository
import com.example.service.blocker.BlockPolicy
import com.example.service.blocker.ConsentPolicy
import com.example.service.blocker.ProposeOutcome
import com.example.data.model.DetailedAppUsage
import com.example.service.AddressHelper
import com.example.service.LocationFixSource
import com.example.service.TelemetryAndUsageHelper
import com.example.service.VesperaTelemetryService
import com.example.service.sos.MeshPeer
import com.example.service.sos.MeshMessage
import com.example.service.sos.SmartSosDispatcher
import com.example.service.sos.SosChannelUsed
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import java.security.MessageDigest
import java.io.File
import java.util.UUID

private const val TAG = "VesperaViewModel"

// Ten tai khoan trong o dang nhap chi la phia truoc `@`; duoi nay app noi vao
// truoc khi goi Firebase.
private const val EMAIL_SUFFIX = "@giadinhviet.vn"

data class VesperaUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val isDarkMode: Boolean = true,
    val authUserId: String? = null,
    val userProfile: UserProfile? = null,
    val activePairing: Pairing? = null,
    val partnerLocation: LocationData? = null,
    /**
     * Luc MAY NAY thuc nhan duoc phieu vi tri cua may kia (`System.
     * currentTimeMillis()` cua may dang xem), 0 = chua nhan duoc phieu nao.
     * `partnerLocation.updatedAt` la dong ho cua MAY BEN KIA — may chua do
     * sai gio (may gia lap, may doi múi gio, hieu ung phong may mo lai) se
     * gui mot moc tuong lai va "vua cap nhat" gia mot diem da chet tu lau.
     */
    val partnerFixReceivedAtMs: Long = 0L,
    val myLocation: LocationData? = null,
    val usageSchedule: UsageSchedule? = null,
    val usageCategories: List<AppCategoryUsage> = emptyList(),
    val detailedAppUsage: List<DetailedAppUsage> = emptyList(),
    val hasUsagePermission: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val hasBackgroundLocationPermission: Boolean = false,
    val hasCallPermission: Boolean = false,
    /**
     * null = chưa kiểm tra. Đây là dịch vụ định vị CỦA MÁY, khác với quyền của
     * app: cấp đủ quyền mà máy tắt GPS thì vẫn không có fix nào để vẽ.
     */
    val isLocationServicesEnabled: Boolean? = null,
    /** null = chưa kiểm tra; true = máy đang ra Internet qua VPN. */
    val isVpnActive: Boolean? = null,
    val permissionSnapshot: List<com.example.service.PermissionManager.PermissionItem> = emptyList(),
    val lastRealtimeSyncAt: Long = 0L,
    val extraTimeRequests: List<ExtraTimeRequest> = emptyList(),
    val privacyRequests: List<PrivacyRequest> = emptyList(),
    val agreementRequests: List<ScreenTimeAgreement> = emptyList(),
    val messages: List<FamilyMessage> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedEmotion: EmotionTag? = null,
    /**
     * Thoi diem "chạm tay" gan nhat MAI DUOC trong document ghep noi, khong
     * phai bo dem trong bo nho nhu ban cu (ban cu tang len khi bam nut va ben
     * kia khong nhan duoc gi). 0 = chua co lan nao.
     */
    val lastBumpAt: Long = 0L,
    val lastBumpByRole: UserRole? = null,
    val lastBumpByName: String = "",
    val weeklyUsage: List<Pair<String, Int>> = listOf(
        "T2" to 0, "T3" to 0, "T4" to 0, "T5" to 0, "T6" to 0, "T7" to 0, "CN" to 0
    ),
    val locationHistory: List<LocationHistoryEntry> = emptyList(),
    /**
     * null = CHUA kiem tra duoc mạng. Truoc day mac dinh la `true`, nen khung
     * chat bao "Trực tuyến" ngay ca khi NetworkMonitor chua kip tra loi (va
     * ca khi may chang co mang). Chi `NetworkMonitor` (init:216) duoc phep
     * gan gia tri that.
     */
    val isOnline: Boolean? = null,
    val discoveredMeshPeers: List<MeshPeer> = emptyList(),
    val connectedMeshPeers: List<MeshPeer> = emptyList(),
    val meshMessages: List<MeshMessage> = emptyList(),
    val isOfflineScanning: Boolean = false,
    val emergencyPhoneNumber: String = "",
    val isSosRestricted: Boolean = false,
    val sosStartHour: Int = 6,
    val sosEndHour: Int = 22,
    val currentSosChannel: SosChannelUsed = SosChannelUsed.INTERNET_CLOUD,
    val isSosAlertActive: Boolean = false,
    val activeSosMessage: String = "",
    /**
     * null = chua kip kiem tra; true = may cai Zalo. Quyet dinh nut "Gọi qua
     * Zalo" co hien hay khong. Android 11+ chi doc duoc package nay khi
     * Manifest co `<queries>` cho `com.zing.zalo`.
     */
    val zaloInstalled: Boolean? = null,
    /** Trang thai ngan cua luu cuu nan: "Đang chuyển hướng đến cứu hộ"… */
    val sosStatusMessage: String = "",
    /** Vĩ độ / kinh độ / địa điểm hien trong bang cuu nan va loi doc sau 2 giay. */
    val sosPosition: com.example.service.sos.SosPlan.Position? = null,
    val isLocationHistoryEnabled: Boolean = true,
    /** Trang thai luu luat chan app — de the hien tich xanh that. */
    val blockRulesSaveState: BlockRulesSaveState = BlockRulesSaveState.Idle,
    val blockRulesSavedAtMillis: Long = 0L,
    /**
     * Ly do THUC SU cua lan luu luat chan that bai gan nhat. De rieng thay vi
     * doc `errorMessage`: `errorMessage` la loi toan cuc (GPS, dang nhap, SOS…)
     * nen if the card doc no thi banner "Chưa lưu được" se noi tran hoac kep
     * mot nguyen nhan khong lien quan den noi quy.
     */
    val blockRulesError: String? = null,
    /** Nhat ky noi quy: ai doi gi, luc nao, ben kia duyet hay tu choi. */
    val ruleAudit: List<RuleAuditEntry> = emptyList(),
    /**
     * Luồng "Xóa tài khoản" (bản 66.0). Ba trang thai phai tach lam ba, vi man
     * hinh doit NOI KHAC NHAU cho tung cai: dang chay (khoa nut, dung cho bam
     * twice), xong nhung bi rules chan mot phan (nguoi dung phai biet duoc
     * phan con lai), va that bai hoan toan (may chua Publish rules / tai khoan
     * dang bi cam). Dung gop vao `successMessage`: bi dong co che xoa tin nhan
     * toan cuc va nguoi dung con lai man hinh voi mot cai nut khong ro ket qua.
     */
    val accountDeletion: AccountDeletionState = AccountDeletionState.Idle,
    /** So document con thu dc khi tai khoan bi cam — de in ra TRUOC khi xoa. */
    val accountDeletionPreviewCount: Int? = null
)

/**
 * Trang thai cua nut "Xóa tài khoản". `Done.deleted` la con so THAT tu may chu
 * tra ve (tinh theo document delete() thanh cong), `Done.failed` la ten tung
 * nhom KHONG xoa duoc — app phai in ca hai chu khong duoc noi "đã xóa tất cả"
 * khi mot nhom van con.
 */
sealed class AccountDeletionState {
    object Idle : AccountDeletionState()
    object Working : AccountDeletionState()
    data class Done(val deleted: Int, val failed: List<String>) : AccountDeletionState()
    data class Failed(val reason: String) : AccountDeletionState()
}

/**
 * Trang thai luu bang luat chan ung dung. Nguoi dung phai nhin thay duoc ca 4
 * trang thai nay: truoc day updateBlockRules return ngam khi chua ghep noi,
 * nen viec danh dau app bi mat tit, khong loi, khong bao cao.
 */
enum class BlockRulesSaveState { Idle, Saving, Saved, Failed }

class VesperaViewModel(
    private val repository: VesperaRepository,
    private val telemetryHelper: TelemetryAndUsageHelper,
    private val context: Context? = null
) : ViewModel() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val _uiState = MutableStateFlow(VesperaUiState())
    val uiState: StateFlow<VesperaUiState> = _uiState.asStateFlow()

    var smartSosDispatcher: SmartSosDispatcher? = null
        private set

    private var profileJob: Job? = null
    private var pairingJob: Job? = null

    /** vong lap "tu gan minh vao document ghep noi" — chi mot vong dung mot luc. */
    private var pairingAdoptJob: Job? = null

    /** pairingId da tu xoa lien ket o may nay — de khong ghi lap lai. */
    private var unlinkedPairingId: String? = null
    private var locationJob: Job? = null
    private var historyJob: Job? = null
    private var scheduleJob: Job? = null
    private var extraTimeJob: Job? = null
    private var privacyJob: Job? = null
    private var agreementJob: Job? = null
    private var auditJob: Job? = null
    private var messagesJob: Job? = null

    /**
     * PairingID da thuc su co snapshot noi quy chay ve tu Firestore.
     *
     * `usageSchedule` trong UiState co the la ban Do dung (che do demo gay
     * san `UsageSchedule(dailyTargetMinutes = 120)`), khong phai bang noi quy
     * that. `repository.saveSchedule()` ghi bang `set()` — ghi ca toan bo tai
     * lieu — nen neu luu truoc khi snapshot keo ve thi may se de len noi quy
     * cua ben kia bang mot ban gia dinh. Vi vay luu chi duoc phep khi cot nay
     * trung voi pairingID dang hoat dong.
     */
    @Volatile
    private var scheduleSnapshotPairingId: String? = null

    /**
     * Co mot lan `refreshUsageStats()` dang doc dien khong? MainActivity goi ham
     * nay 2 lan / 10 giay; khong chan thi cac lan doc chong len nhau va may
     * luc nao cung dang tong hop UsageStats.
     */
    @Volatile
    private var usageRefreshInFlight: Boolean = false

    init {
        if (context != null) {
            val dispatcher = SmartSosDispatcher(context, repository, viewModelScope)
            smartSosDispatcher = dispatcher

            val (restricted, startH, endH) = dispatcher.getSosScheduleConfig()
            val phone = dispatcher.smsManager.getEmergencyPhoneNumber()
            val prefs = context.getSharedPreferences("vespera_prefs", Context.MODE_PRIVATE)
            val historyEnabled = prefs.getBoolean("history_enabled", true)

            _uiState.value = _uiState.value.copy(
                emergencyPhoneNumber = phone,
                isSosRestricted = restricted,
                sosStartHour = startH,
                sosEndHour = endH,
                isLocationHistoryEnabled = historyEnabled
            )

            viewModelScope.launch {
                dispatcher.networkMonitor.isOnline.collect { online ->
                    _uiState.value = _uiState.value.copy(isOnline = online)
                }
            }
            viewModelScope.launch {
                dispatcher.networkMonitor.isVpnActive.collect { vpn ->
                    _uiState.value = _uiState.value.copy(isVpnActive = vpn)
                }
            }
            viewModelScope.launch {
                dispatcher.currentActiveChannel.collect { ch ->
                    _uiState.value = _uiState.value.copy(currentSosChannel = ch)
                }
            }
            viewModelScope.launch {
                dispatcher.meshManager.discoveredPeers.collect { peers ->
                    _uiState.value = _uiState.value.copy(discoveredMeshPeers = peers)
                }
            }
            viewModelScope.launch {
                dispatcher.meshManager.connectedPeers.collect { peers ->
                    _uiState.value = _uiState.value.copy(connectedMeshPeers = peers)
                }
            }
            viewModelScope.launch {
                dispatcher.meshManager.incomingMessages.collect { msgs ->
                    _uiState.value = _uiState.value.copy(meshMessages = msgs)
                }
            }
            viewModelScope.launch {
                dispatcher.isOfflineAutoScanning.collect { scanning ->
                    _uiState.value = _uiState.value.copy(isOfflineScanning = scanning)
                }
            }
        }

        val initialUser = auth.currentUser
        if (initialUser != null) {
            _uiState.value = _uiState.value.copy(
                isAuthenticated = true,
                authUserId = initialUser.uid,
                isLoading = false
            )
            startObservingUserData(initialUser.uid)
        }

        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    authUserId = user.uid,
                    isLoading = false
                )
                startObservingUserData(user.uid)
            } else {
                stopAllObservers()
                // `NetworkMonitor` chi phat mot lan khi gia tri THAY DOI, nen
                // reset ca VesperaUiState() khi chu ket noi van la "Online" se
                // xoa mat ket luan mang va khong bao gio co lai nua — khung
                // chat dung im bao "Chưa kiểm tra được kết nối mạng" tren may
                // van co mang. Giu lai cac ket luan do may tu do: mang, VPN,
                // dinh vi, quyen, mau giao dien.
                val keep = _uiState.value
                _uiState.value = VesperaUiState(
                    isAuthenticated = false,
                    isDarkMode = keep.isDarkMode,
                    hasUsagePermission = keep.hasUsagePermission,
                    hasLocationPermission = keep.hasLocationPermission,
                    hasSmsPermission = keep.hasSmsPermission,
                    hasNotificationPermission = keep.hasNotificationPermission,
                    hasOverlayPermission = keep.hasOverlayPermission,
                    hasBackgroundLocationPermission = keep.hasBackgroundLocationPermission,
                    hasCallPermission = keep.hasCallPermission,
                    isLocationServicesEnabled = keep.isLocationServicesEnabled,
                    isVpnActive = keep.isVpnActive,
                    permissionSnapshot = keep.permissionSnapshot,
                    isOnline = keep.isOnline
                )
            }
        }

        // Nạp trạng thái quyền THẬT ngay từ đầu để UI không hiển thị sai.
        refreshPermissions()
    }

    private fun stopAllObservers() {
        profileJob?.cancel()
        pairingJob?.cancel()
        locationJob?.cancel()
        historyJob?.cancel()
        scheduleJob?.cancel()
        extraTimeJob?.cancel()
        privacyJob?.cancel()
        agreementJob?.cancel()
        messagesJob?.cancel()
        pairingAdoptJob?.cancel()
        // Đăng xuất / đổi cặp: không còn snapshot nào được xác nhận, nên
        // việc lưu nội quy phải chờ listener của pairing mới.
        scheduleSnapshotPairingId = null
        unlinkedPairingId = null
    }

    /**
     * Gio cua lan chạm tay ma MAY NAY da rung. Listener cua document ghep noi
     * dua lan nay tra ve chinh no, nen phai co moc so de khong rung lan thu hai.
     * -1 = CHUA nhan snapshot nao: moc dau tien chi duoc ghi lai chu khong rung,
     * neu khong mo app len lai rung may vi cai cham tay tu hom qua.
     */
    @Volatile
    private var lastRungBumpAt: Long = -1L

    /**
     * Interactive Zenly "Bump / Chạm tay" feature with tactile vibration
     */
    fun sendZenlyBump(context: Context? = null) {
        val pairing = _uiState.value.activePairing
        val profile = _uiState.value.userProfile
        if (pairing == null || pairing.status != PairingStatus.PAIRED) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chưa ghép nối với máy nào nên không có ai để chạm tay. " +
                    "Hai máy phải nhập mã ghép nối ở màn Tổng quan trước đã."
            )
            return
        }
        val at = System.currentTimeMillis()
        // Ghi trước khi rung: tự nhận lan nay la cua minh de listener khong
        // rung may lan thu hai khi document quay lai.
        lastRungBumpAt = at
        viewModelScope.launch {
            try {
                repository.sendBump(
                    pairingId = pairing.id,
                    atMillis = at,
                    role = profile?.role ?: UserRole.PARENT,
                    actorName = profile?.displayName.orEmpty()
                )
                context?.let { vibrateForBump(it) }
                _uiState.value = _uiState.value.copy(
                    lastBumpAt = at,
                    lastBumpByRole = profile?.role,
                    lastBumpByName = profile?.displayName.orEmpty(),
                    successMessage = "Đã ghi chạm tay lên máy chủ lúc " +
                        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date(at)) +
                        ". Máy bên kia chỉ rung khi đang mở app và còn kết nối — app này không xác nhận được điều đó."
                )
            } catch (e: Exception) {
                Log.w(TAG, "sendZenlyBump: khong ghi duoc lastBumpAt", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Không gửi được chạm tay: máy không ghi được lên máy chủ " +
                        "(mất mạng hoặc quyền Firestore). Máy bạn cũng không rung, vì chưa có ai nhận được."
                )
            }
        }
    }

    /**
     * Ba me mo mot gio "hoc tap trung" tren may con. Luu len `pairings/{id}`
     * va bo chan cua may con doc duoc that, nen no chan duoc ung dung — voi
     * dieu kien may con da bat Tro nang.
     */
    fun startFamilyStudy(minutes: Int) {
        val pairing = _uiState.value.activePairing
        val profile = _uiState.value.userProfile
        if (pairing == null || pairing.status != PairingStatus.PAIRED) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chưa ghép nối với máy con nào nên không gửi được giờ học. " +
                    "Hai máy phải nhập mã ghép nối ở màn Tổng quan trước."
            )
            return
        }
        viewModelScope.launch {
            try {
                repository.startStudySession(
                    pairingId = pairing.id,
                    minutes = minutes,
                    actorName = profile?.displayName.orEmpty().ifBlank { "Ba mẹ" }
                )
                _uiState.value = _uiState.value.copy(
                    successMessage = "🎯 Đã gửi giờ học $minutes phút sang máy con. " +
                        "Máy con chỉ chặn thật khi đã bật Tro năng; tắt Tro năng thì nó nhắc thôi."
                )
            } catch (e: Exception) {
                Log.w(TAG, "startFamilyStudy: khong ghi duoc", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Không bật được giờ học: máy không ghi được lên máy chủ " +
                        "(mất mạng hoặc quyền Firestore). Máy con vẫn dùng bình thường."
                )
            }
        }
    }

    fun stopFamilyStudy() {
        val pairing = _uiState.value.activePairing ?: return
        viewModelScope.launch {
            try {
                repository.stopStudySession(pairing.id)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Đã tắt giờ học tập trung trên máy con."
                )
            } catch (e: Exception) {
                Log.w(TAG, "stopFamilyStudy: khong ghi duoc", e)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Không tắt được giờ học: máy không ghi được lên máy chủ. " +
                        "Giờ học sẽ tự hết khi đếm về 0."
                )
            }
        }
    }

    /** Rung theo kieu Zenly: ngan - ngat - dai. */
    private fun vibrateForBump(ctx: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                mgr?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createWaveform(longArrayOf(0, 100, 70, 150), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        android.os.VibrationEffect.createWaveform(longArrayOf(0, 100, 70, 150), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(200)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Mot snapshot cua `pairings/{id}` di qua day. Chi rung khi thoi diem chạm
     * tay MOI HON cai ma may nay da rung; snapshot dau tien sau khi mo app chi
     * ghi moc — neu khong thi mo app len lai rung vi lan cham tu hom qua.
     */
    private fun applyBumpFromServer(pairing: Pairing) {
        if (pairing.lastBumpAt <= 0L) {
            if (_uiState.value.lastBumpAt != 0L) {
                _uiState.value = _uiState.value.copy(
                    lastBumpAt = 0L, lastBumpByRole = null, lastBumpByName = ""
                )
            }
            return
        }
        if (lastRungBumpAt < 0) {
            lastRungBumpAt = pairing.lastBumpAt
        } else if (pairing.lastBumpAt > lastRungBumpAt) {
            lastRungBumpAt = pairing.lastBumpAt
            context?.let { vibrateForBump(it) }
            _uiState.value = _uiState.value.copy(
                successMessage = pairing.lastBumpByName.ifEmpty { "Máy bên kia" } +
                    " vừa chạm tay — máy này đã rung."
            )
        }
        if (_uiState.value.lastBumpAt != pairing.lastBumpAt) {
            _uiState.value = _uiState.value.copy(
                lastBumpAt = pairing.lastBumpAt,
                lastBumpByRole = pairing.lastBumpByRole,
                lastBumpByName = pairing.lastBumpByName
            )
        }
    }

    private fun startObservingUserData(userId: String) {
        stopAllObservers()

        if (_uiState.value.userProfile == null) {
            val user = auth.currentUser
            if (user != null && user.uid == userId) {
                val fallbackProfile = UserProfile(
                    id = user.uid,
                    email = user.email.orEmpty(),
                    displayName = user.displayName.orEmpty().ifEmpty { "Gia Đình Việt" },
                    photoUrl = user.photoUrl?.toString().orEmpty(),
                    role = UserRole.PARENT
                )
                _uiState.value = _uiState.value.copy(userProfile = fallbackProfile)
            }
        }

        profileJob = viewModelScope.launch {
            repository.observeUserProfile(userId).collect { profile ->
                if (profile != null) {
                    _uiState.value = _uiState.value.copy(userProfile = profile)
                    if (profile.activePairingId.isNotEmpty()) {
                        observeActivePairing(profile.activePairingId, profile)
                    } else {
                        observeLocationHistoryForUser(userId)
                        watchPairingArrival(profile)
                    }
                }
            }
        }

        refreshUsageStats()
        broadcastSelfTelemetry()
    }

    /**
     * May nay chua co `activePairingId` trong ho so — nhung khong co nghia la
     * chua duoc ghep. Phia ben kia ghi ho so cua minh bang cach nao:
     * `firestore.rules` chi cho `isOwner` sua `users/{uid}`, nen loi "phu huynh
     * gan con vao ho so cua no" tung lam toan bo phep ghep chi chay mot chieu
     * (may me bao "Đã kết nối", may con van "chưa ghép nối"). Day la buoc con
     * thieu: tu tim document ghep noi mang ten minh va tu ghi lay.
     *
     * vong lap 20 giay: bam "Ghep noi" phia phu huynh thi may con dang mo app
     * phai nhan ra ngay, khong buoc mo lai. That bai (chua ai ghep) la trang
     * thai binh thuong nen chi kiem tra toi da ~30 phut roi dung, de khong
     * dot luot doc Firestore vo han.
     */
    private fun watchPairingArrival(profile: UserProfile) {
        if (pairingAdoptJob?.isActive == true) return
        pairingAdoptJob = viewModelScope.launch {
            var attempts = 0
            while (attempts < 90) {
                val found = repository.findPairingFor(profile)
                if (found != null) {
                    val partnerId =
                        if (profile.role == UserRole.PARENT) found.childId else found.parentId
                    runCatching {
                        repository.linkSelfToPairing(profile.id, found.id, partnerId)
                        // Ma hoc sinh dung de ghep da bi su dung: doi ma de phu
                        // huynh khac khong nhap lai duoc no. Chi may con moi
                        // doi duoc ma cua no.
                        if (profile.role == UserRole.CHILD) repository.rotateStudentCode(profile.id)
                    }.onFailure { Log.w(TAG, "Không tự ghi được liên kết ghép nối", it) }
                    return@launch
                }
                attempts++
                delay(20_000)
            }
        }
    }

    private fun observeActivePairing(pairingId: String, currentProfile: UserProfile) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            repository.observePairing(pairingId).collect { pairing ->
                _uiState.value = _uiState.value.copy(activePairing = pairing)
                // Chạm tay bay qua document ghep noi: ben kia ghi thi may nay rung.
                if (pairing != null) applyBumpFromServer(pairing)
                if (pairing != null && pairing.status == PairingStatus.PAIRED) {
                    // Cong tat ghi dinh vi lay theo document ghep noi, khong lay
                    // theo SharedPreferences nua — neu khong thi ben kia tat la
                    // ben nay tuong dang ghi. SharedPreferences chi van la bo
                    // nho cho VesperaTelemetryService doc khi may mat mang.
                    applyHistoryFlagFromServer(
                        enabled = pairing.locationHistoryEnabled,
                        persistToLocalPrefs = true
                    )
                    val partnerId = if (currentProfile.role == UserRole.PARENT) pairing.childId else pairing.parentId
                    observePartnerLocation(partnerId)
                    observeLocationHistory(pairingId)
                    observeSchedule(pairingId)
                    observeExtraTime(pairingId)
                    observePrivacy(pairingId)
                    observeAgreements(pairingId)
                    observeRuleAudit(pairingId)
                    observeChatMessages(pairingId)
                } else if (pairing != null && unlinkedPairingId != pairingId) {
                    // Ben kia da huy ghep noi tren document chung. May nay tu
                    // xoa lien ket cua minh — khong the cho ben kia xoa ho so
                    // cua minh (rules tu choi), va cung khong duoc giu mai mot
                    // cap da ngung chay.
                    unlinkedPairingId = pairingId
                    runCatching { repository.unlinkSelf(currentProfile.id) }
                        .onFailure { Log.w(TAG, "Không tự gỡ liên kết ghép nối được", it) }
                }
            }
        }
    }

    private fun observeLocationHistory(pairingId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.observeLocationHistory(pairingId).collect { list ->
                _uiState.value = _uiState.value.copy(locationHistory = list)
            }
        }
    }

    private fun observeLocationHistoryForUser(userId: String) {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            repository.observeLocationHistoryForUser(userId).collect { list ->
                _uiState.value = _uiState.value.copy(locationHistory = list)
            }
        }
    }

    /**
     * Bat / tat viec luu diem di chuyen.
     *
     * Qua: chi ghi SharedPreferences cua may -> hoc sinh tat di thi me khong
     * he biet, va "thoai mai" tro thanh mot loi hinh thuc. Nay: ghi len
     * document ghep noi de ca hai ben cung nhin thay, kem ten va gio nguoi
     * thay doi (+ mot dong nhat ky). Van doi duoc boi bat ky ben nao — that
     * bai cua app la o cho KHONG AIM THAM duoc, khong phai o cho mot ben
     * bi kek chat.
     */
    fun toggleLocationHistory(enabled: Boolean) {
        val pairing = _uiState.value.activePairing
        val role = _uiState.value.userProfile?.role ?: UserRole.PARENT
        val actorName = _uiState.value.userProfile?.displayName.orEmpty()
        val before = _uiState.value.isLocationHistoryEnabled

        applyHistoryFlagFromServer(enabled, persistToLocalPrefs = true)

        if (pairing == null) {
            _uiState.value = _uiState.value.copy(
                successMessage = if (enabled)
                    "Đã bật ghi lịch sử trên máy này (chưa ghép nối nên máy kia chưa biết)."
                else
                    "Đã tắt ghi lịch sử trên máy này (chưa ghép nối nên máy kia chưa biết)."
            )
            return
        }

        viewModelScope.launch {
            try {
                repository.updateHistoryConsent(pairing.id, enabled, role, actorName)
                logRuleAudit(
                    pairing.id, role, actorName,
                    action = "lich_su",
                    detail = ConsentPolicy.label(role) +
                        if (enabled) " đã bật lại ghi lịch sử di chuyển" else " đã tắt ghi lịch sử di chuyển",
                    status = RequestStatus.APPROVED
                )
                _uiState.value = _uiState.value.copy(
                    successMessage = ConsentPolicy.label(role) +
                        if (enabled) " đã bật ghi lịch sử • cả hai máy cùng thấy"
                        else " đã tắt ghi lịch sử • cả hai máy cùng thấy"
                )
            } catch (e: Exception) {
                applyHistoryFlagFromServer(before, persistToLocalPrefs = true)
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Không lưu được trạng thái ghi lịch sử: ${e.message ?: "lỗi mạng"}. " +
                        "Máy vẫn đang theo trạng thái cũ."
                )
            }
        }
    }

    /**
     * Dong bo co quan ghi dinh vi (SharedPreferences ma VesperaTelemetryService
     * doc) voi gia tri tren server. Tam = mang roi thi van luu quyet dinh gan
     * nhat ma ca hai ben da nhin thay.
     */
    private fun applyHistoryFlagFromServer(enabled: Boolean, persistToLocalPrefs: Boolean) {
        if (persistToLocalPrefs) {
            context?.getSharedPreferences("vespera_prefs", Context.MODE_PRIVATE)
                ?.edit()?.putBoolean("history_enabled", enabled)?.apply()
        }
        if (_uiState.value.isLocationHistoryEnabled != enabled) {
            _uiState.value = _uiState.value.copy(isLocationHistoryEnabled = enabled)
        }
    }

    private fun observeRuleAudit(pairingId: String) {
        auditJob?.cancel()
        auditJob = viewModelScope.launch {
            repository.observeRuleAudit(pairingId).collect { entries ->
                _uiState.value = _uiState.value.copy(ruleAudit = entries)
            }
        }
    }

    private fun observePartnerLocation(partnerId: String) {
        locationJob?.cancel()
        // Doi may duoc theo doi = phieu cu cua may khong con gia tri. Neu khong
        // xoa, the ban do moi se in "vua cap nhat" cho mot diem cua nguoi cu.
        _uiState.value = _uiState.value.copy(partnerLocation = null, partnerFixReceivedAtMs = 0L)
        locationJob = viewModelScope.launch {
            repository.observeLocation(partnerId).collect { loc ->
                _uiState.value = _uiState.value.copy(
                    partnerLocation = loc,
                    // Dong ho MAY NAY, ghi ngay khi nhận duoc tai lieu — đây là
                    // mốc duy nhất mà máy này có quyền nói là thật.
                    partnerFixReceivedAtMs = if (loc != null) System.currentTimeMillis() else 0L
                )
            }
        }
    }

    private fun observeSchedule(pairingId: String) {
        scheduleJob?.cancel()
        if (pairingId.isEmpty()) scheduleSnapshotPairingId = null
        scheduleJob = viewModelScope.launch {
            repository.observeSchedule(pairingId).collect { sched ->
                // Emit dau tien (ke ca "chua co tai lieu") = may chu da tra loi
                // cho dung pairing nay -> tu day duoc phep luu noi quy.
                if (pairingId.isNotEmpty()) scheduleSnapshotPairingId = pairingId
                val resolved = if (sched == null && pairingId.isNotEmpty()) {
                    // Cua pairings/<id>/schedules/default_sched con trong: dung
                    // ban mac dinh CO ID + pairingID de cac lan luu sau khong
                    // bi roi vao "pairings//schedules/".
                    UsageSchedule(id = "default_sched", pairingId = pairingId)
                } else sched
                _uiState.value = _uiState.value.copy(usageSchedule = resolved)
            }
        }
    }

    private fun observeExtraTime(pairingId: String) {
        extraTimeJob?.cancel()
        extraTimeJob = viewModelScope.launch {
            repository.observeExtraTimeRequests(pairingId).collect { reqs ->
                _uiState.value = _uiState.value.copy(extraTimeRequests = reqs)
            }
        }
    }

    private fun observePrivacy(pairingId: String) {
        privacyJob?.cancel()
        privacyJob = viewModelScope.launch {
            repository.observePrivacyRequests(pairingId).collect { reqs ->
                _uiState.value = _uiState.value.copy(privacyRequests = reqs)
            }
        }
    }

    private fun observeAgreements(pairingId: String) {
        agreementJob?.cancel()
        agreementJob = viewModelScope.launch {
            repository.observeAgreements(pairingId).collect { agreements ->
                _uiState.value = _uiState.value.copy(agreementRequests = agreements)
            }
        }
    }

    fun proposeAgreement(minutes: Int) {
        val pairing = _uiState.value.activePairing ?: return
        val user = _uiState.value.userProfile ?: return
        val agreement = ScreenTimeAgreement(
            id = UUID.randomUUID().toString(),
            pairingId = pairing.id,
            requestedMinutes = minutes,
            proposerRole = user.role,
            status = RequestStatus.PENDING
        )
        viewModelScope.launch {
            repository.saveAgreement(agreement)
        }
    }

    fun respondToAgreement(agreement: ScreenTimeAgreement, accept: Boolean) {
        val status = if (accept) RequestStatus.APPROVED else RequestStatus.REJECTED
        val updated = agreement.copy(status = status, respondedAt = System.currentTimeMillis())
        viewModelScope.launch {
            repository.saveAgreement(updated)
            if (accept) {
                // If agreed, update the daily target
                applyDailyTargetFromAgreement(updated.requestedMinutes)
            }
        }
    }

    private fun observeChatMessages(pairingId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            repository.observeMessages(pairingId).collect { msgs ->
                _uiState.value = _uiState.value.copy(messages = msgs)
            }
        }
    }

    /**
     * Sign in with Google via Credential Manager
     */
    fun signInWithGoogle(context: Context, selectedRole: UserRole = UserRole.PARENT) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val serverClientId = context.getString(R.string.default_web_client_id)
                val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = context as Activity
                )

                val idToken = when (val credential = result.credential) {
                    is GoogleIdTokenCredential -> credential.idToken
                    is CustomCredential -> {
                        if (credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                            try {
                                GoogleIdTokenCredential.createFrom(credential.data).idToken
                            } catch (e: Exception) {
                                Log.e(TAG, "Cannot extract token from CustomCredential", e)
                                null
                            }
                        } else {
                            null
                        }
                    }
                    else -> null
                }

                if (idToken.isNullOrEmpty()) {
                    throw IllegalStateException("Không nhận được token xác thực từ tài khoản Google.")
                }

                val firebaseCred = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(firebaseCred).await()
                val user = authResult.user

                if (user != null) {
                    val studentCode = if (selectedRole == UserRole.CHILD) repository.generateStudentCode() else ""
                    val initialProfile = UserProfile(
                        id = user.uid,
                        email = user.email.orEmpty(),
                        displayName = user.displayName.orEmpty().ifEmpty {
                            if (selectedRole == UserRole.CHILD) "Học sinh" else "Phụ huynh"
                        },
                        photoUrl = user.photoUrl?.toString().orEmpty(),
                        role = selectedRole,
                        studentCode = studentCode
                    )

                    // Immediately transition to Authenticated state so the app navigates without delay!
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        authUserId = user.uid,
                        userProfile = initialProfile,
                        isLoading = false,
                        errorMessage = null
                    )

                    // Sync or load profile in background
                    launch {
                        try {
                            val existing = repository.getUserProfile(user.uid)
                            if (existing == null) {
                                repository.saveUserProfile(initialProfile)
                            } else {
                                val updated = if (selectedRole == UserRole.CHILD && existing.studentCode.isEmpty()) {
                                    val newCode = repository.generateStudentCode()
                                    existing.copy(role = selectedRole, studentCode = newCode).also {
                                        repository.saveUserProfile(it)
                                    }
                                } else existing
                                _uiState.value = _uiState.value.copy(userProfile = updated)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Sync profile to Firestore exception", e)
                        }
                    }

                    startObservingUserData(user.uid)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Đăng nhập không thành công, vui lòng thử lại."
                    )
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "Google Sign-in was cancelled by user", e)
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                Log.w(TAG, "Credential Manager flow exception: ${e.message}", e)
                // Nhanh nay TUNG tao ho so voi uid `user_<ngau nhien>` va email
                // `phuhuynh@giadinhviet.vn` roi bao la dang nhap thanh cong. Do la
                // tai khoan ao: khong ai tim duoc, khong dong bo duoc, ma van ghi
                // thang len `users/`. Nay bao that loi de nguoi dung chon cach khac.
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Không đăng nhập được bằng Google: " +
                        (e.localizedMessage ?: "lỗi không xác định") +
                        ". Hãy thử lại, hoặc đăng nhập bằng tài khoản và mật khẩu."
                )
            }
        }
    }

    /**
     * O dang nhap chi hoi ten tai khoản, nhung nguoi dung hay dan ca email
     * da dang ky. Noi thuong ra `me@giadinhviet.vn@giadinhviet.vn` va
     * Firebase bao "The email address is badly formatted" — loi do khong he
     * noi sai o dau, nen phai go bo duoi bi thua truoc khi noi.
     */
    private fun emailForUsername(raw: String): String {
        val name = raw.trim()
        return if (name.endsWith(EMAIL_SUFFIX, ignoreCase = true)) name else "$name$EMAIL_SUFFIX"
    }

    fun signInWithUsername(username: String, pass: String, selectedRole: UserRole) {
        val email = emailForUsername(username)
        if (username.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập đầy đủ tên tài khoản và mật khẩu.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email.trim(), pass).await()
                val user = authResult.user
                if (user != null) {
                    val studentCode = if (selectedRole == UserRole.CHILD) repository.generateStudentCode() else ""
                    val profile = UserProfile(
                        id = user.uid,
                        email = email,
                        displayName = username,
                        role = selectedRole,
                        studentCode = studentCode
                    )
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        authUserId = user.uid,
                        userProfile = profile,
                        isLoading = false,
                        errorMessage = null
                    )
                    try {
                        val existing = repository.getUserProfile(user.uid)
                        if (existing == null) {
                            repository.saveUserProfile(profile)
                        } else {
                            // Document tren may chu la tai lieu cu: co the thieu
                            // email, va co the mang role KHAC cai vai tro nguoi
                            // dung vua chon o man dang nhap. Truoc day app im
                            // lang dung doc cu, nen dang nhap bang tai khoan "Học
                            // sinh" van ra man Phụ huynh ma khong ai giai thich
                            // duoc. Van lay may chu lam chuan (hoc sinh khong
                            // duoc tu phong minh len bang cha me) nhung phai bo
                            // sung cho du thong tin va phai noi ro khi lech.
                            val merged = existing.copy(
                                email = existing.email.ifBlank { profile.email },
                                displayName = existing.displayName.ifBlank { profile.displayName }
                            )
                            val roleHint = { r: UserRole ->
                                if (r == UserRole.CHILD) "Học sinh" else "Phụ huynh"
                            }
                            if (merged != existing) {
                                // Chi ghi lai khi that su co them du lieu, de
                                // mot lan dang nhap khong viet gi up xuong may
                                // chu. Ghi that bai thi van con du lieu tren
                                // man hinh; saveUserProfile nem loi to cuc.
                                repository.saveUserProfile(merged)
                            }
                            _uiState.value = _uiState.value.copy(
                                userProfile = merged,
                                errorMessage = if (existing.role != profile.role)
                                    "Máy chủ đang lưu tài khoản này là ${roleHint(existing.role)}, " +
                                        "khác vai trò bạn vừa chọn (${roleHint(profile.role)}). " +
                                        "App hiển thị theo vai trò máy chủ."
                                else null
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Sync profile error", e)
                    }
                    startObservingUserData(user.uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-in error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Đăng nhập thất bại: ${e.localizedMessage ?: "Kiểm tra lại tài khoản/mật khẩu"}"
                )
            }
        }
    }

    fun signUpWithUsername(username: String, pass: String, selectedRole: UserRole) {
        val email = emailForUsername(username)
        if (username.isBlank() || pass.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập đầy đủ tên tài khoản và mật khẩu.")
            return
        }
        if (pass.length < 6) {
            _uiState.value = _uiState.value.copy(errorMessage = "Mật khẩu phải có ít nhất 6 ký tự.")
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email.trim(), pass).await()
                val user = authResult.user
                if (user != null) {
                    val studentCode = if (selectedRole == UserRole.CHILD) repository.generateStudentCode() else ""
                    val profile = UserProfile(
                        id = user.uid,
                        email = email,
                        displayName = username,
                        role = selectedRole,
                        studentCode = studentCode
                    )
                    repository.saveUserProfile(profile)
                    _uiState.value = _uiState.value.copy(
                        isAuthenticated = true,
                        authUserId = user.uid,
                        userProfile = profile,
                        isLoading = false,
                        errorMessage = null
                    )
                    startObservingUserData(user.uid)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sign-up error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Tạo tài khoản thất bại: ${e.localizedMessage ?: "Tài khoản đã tồn tại"}"
                )
            }
        }
    }



    fun selectRole(role: UserRole) {
        val current = _uiState.value.userProfile
        val studentCode = if (role == UserRole.CHILD) {
            current?.studentCode?.ifEmpty { repository.getOrGenerateDeviceStudentCode() } ?: repository.getOrGenerateDeviceStudentCode()
        } else {
            current?.studentCode.orEmpty()
        }
        // `verificationPin` KHONG duoc sinh o day nua. Tung co dong `getOrGenerate`
        // + toast "PIN: 1234", nhung khong mot noi nao trong app doi chieu no:
        // khong co buoc xac nhan nao -- phu huynh nhap ma la ghep. Sinh ra mot
        // so "bao mat" khong bao gio dung den la noi doi gia voi nguoi dung.
        // Chua dang nhap thi KHONG biem ten hay email. `user_local` +
        // "phuhuynh@giadinhviet.vn" tung khien man hinh in ra mot tai khoan
        // khong ton tai; giu rong de cac man hinh tu hien thi "chua dang nhap".
        val updated = (current ?: UserProfile(
            id = auth.currentUser?.uid.orEmpty(),
            email = auth.currentUser?.email.orEmpty()
        )).copy(
            role = role,
            studentCode = studentCode,
            displayName = (current?.displayName ?: auth.currentUser?.displayName.orEmpty())
                .ifEmpty { if (role == UserRole.CHILD) "Học sinh" else "Phụ huynh" }
        )
        _uiState.value = _uiState.value.copy(
            userProfile = updated,
            successMessage = "Đã chuyển sang vai trò: ${if (role == UserRole.CHILD) "Học sinh 🎒" else "Phụ huynh 🛡️"}"
        )
        if (auth.currentUser != null) {
            viewModelScope.launch {
                try {
                    repository.saveUserProfile(updated)
                } catch (e: Exception) {
                    Log.w(TAG, "Save user profile error", e)
                }
            }
        }
    }

    fun toggleRole() {
        val currentRole = _uiState.value.userProfile?.role ?: UserRole.PARENT
        selectRole(if (currentRole == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT)
    }

    /**
     * Buoc duy nhat cua phep ghep noi: phu huynh nhap ma may con dang hien.
     *
     * May con khong nhap gi va cung khong xac nhan gi — no tu nhan document
     * mang ten minh trong vong ~20 giay (`watchPairingArrival`). Vi vay hai
     * lan kiem tra duoi day chi de nguoi dung khoi doi oan: ma sai dinh dang
     * thi phep thu tay da ro, con may dang vai tro HOC SINH thi ghep voi ai?
     */
    fun pairDirectly(studentCode: String) {
        val parent = _uiState.value.userProfile ?: return
        val cleanCode = studentCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập mã học sinh")
            return
        }
        if (parent.role == UserRole.CHILD) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Máy này đang ở vai trò Học sinh nên không ghép nối bằng mã. " +
                    "Mã của máy này để phụ huynh nhập — hãy chuyển sang vai trò Phụ huynh " +
                    "trên máy của bố mẹ."
            )
            return
        }
        if (!repository.isPlausibleStudentCode(cleanCode)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Mã học sinh có dạng ${repository.studentCodeExample} — " +
                    "không dùng các chữ 0, 1, I, O nên rất dễ chép nhầm."
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                val pairing = repository.pairDirectly(cleanCode, parent)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    activePairing = pairing,
                    successMessage = "Đã ghép nối với ${pairing.childName}. Nếu máy của con " +
                        "đang mở app thì nó tự nhận liên kết trong khoảng 20 giây; nếu đang " +
                        "tắt thì lần mở kế tiếp sẽ tự nhận — app không ghi được hồ sơ của máy " +
                        "khác, nên bố mẹ không cần làm gì thêm ở máy con."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Pairing error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ghép nối thất bại: ${e.localizedMessage ?: "Vui lòng kiểm tra lại mã"}"
                )
            }
        }
    }

    fun unpairConnection() {
        val pairing = _uiState.value.activePairing ?: return
        val myId = _uiState.value.userProfile?.id.orEmpty()
        viewModelScope.launch {
            try {
                repository.unpair(pairing.id, myId)
                _uiState.value = _uiState.value.copy(
                    activePairing = null,
                    partnerLocation = null,
                    successMessage = "Đã hủy ghép nối thiết bị thành công."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    /**
     * Doc thong ke su dung dung. `getTodayUsageCategories()` va
     * `getDetailedAppUsageList()` phai loc va dat ten cho hang tram goi tuc
     * ung dung — lenh do chay tren may mat den chuc ms. Truoc day no chay
     * THANG tren main thread, ma MainActivity thi goi lien tuc 2 lan / 10
     * giay, nen moi khi vuot tay tren man hinh la bi giat. Chuyen nen IO +
     * chan de don mot lan doc mot luc.
     */
    fun refreshUsageStats() {
        if (usageRefreshInFlight) return
        usageRefreshInFlight = true
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val hasPerm = telemetryHelper.hasUsageStatsPermission()
                    UsageSnapshot(
                        hasPerm = hasPerm,
                        categories = if (hasPerm) telemetryHelper.getTodayUsageCategories() else emptyList(),
                        detailed = if (hasPerm) telemetryHelper.getDetailedAppUsageList() else emptyList(),
                        weekly = telemetryHelper.getWeeklyUsageMinutes()
                    )
                }
                val cur = _uiState.value
                // Ghi de du lieu giong het -> UIState mang danh tinh moi -> toan
                // bo the thong dung recompose 2 lan / 10 giay, ngay ca khi con
                // chua mo may nao. So sanh cac danh sach (data class, so theo
                // gia tri) va chi ghi khi thuc su doi.
                if (cur.hasUsagePermission == snapshot.hasPerm &&
                    cur.usageCategories == snapshot.categories &&
                    cur.detailedAppUsage == snapshot.detailed &&
                    cur.weeklyUsage == snapshot.weekly
                ) return@launch

                _uiState.value = cur.copy(
                    hasUsagePermission = snapshot.hasPerm,
                    usageCategories = snapshot.categories,
                    detailedAppUsage = snapshot.detailed,
                    weeklyUsage = snapshot.weekly,
                    lastRealtimeSyncAt = System.currentTimeMillis()
                )

                // Luu bang dieu tra cua ngay hom nay. Chi ghi khi so lieu THUC
                // SU doi (nhung dong o tren da chan truong hop khong doi),
                // dung dem 10 giay mot lan xuong bo nho. Day la nguon duy nhat
                // de tro ly AI tra loi dua tren so that.
                val ctx = context ?: return@launch
                val todayCal = java.util.Calendar.getInstance()
                val pingsToday = cur.locationHistory.count { e ->
                    val c = java.util.Calendar.getInstance().apply { timeInMillis = e.timestamp }
                    c.get(java.util.Calendar.YEAR) == todayCal.get(java.util.Calendar.YEAR) &&
                        c.get(java.util.Calendar.DAY_OF_YEAR) == todayCal.get(java.util.Calendar.DAY_OF_YEAR)
                }
                withContext(Dispatchers.IO) {
                    DailyUsageStore(ctx).writeTodaySnapshot(
                        usedMinutes = todayUsedMinutes(snapshot.detailed, snapshot.categories.sumOf { it.minutes }),
                        quotaMinutes = _uiState.value.usageSchedule?.dailyTargetMinutes ?: 0,
                        categories = snapshot.categories,
                        detailed = snapshot.detailed,
                        locationPings = pingsToday,
                        emotion = _uiState.value.selectedEmotion?.label.orEmpty()
                    )
                }
            } finally {
                usageRefreshInFlight = false
            }
        }
    }

    private data class UsageSnapshot(
        val hasPerm: Boolean,
        val categories: List<AppCategoryUsage>,
        val detailed: List<DetailedAppUsage>,
        val weekly: List<Pair<String, Int>>
    )

    /**
     * Đọc LẠI trạng thái quyền THẬT từ Android và cập nhật UI ngay lập tức.
     * Gọi mỗi khi màn hình quay lại foreground (onResume) để không còn cảnh
     * "đã cấp quyền rồi mà app vẫn báo chưa cấp".
     */
    fun refreshPermissions() {
        val ctx = context ?: return
        val snap = com.example.service.PermissionManager.snapshot(ctx)
        _uiState.value = _uiState.value.copy(
            permissionSnapshot = snap,
            hasUsagePermission = com.example.service.PermissionManager.hasUsageAccess(ctx),
            hasLocationPermission = com.example.service.PermissionManager.isGranted(
                ctx, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) || com.example.service.PermissionManager.isGranted(
                ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            hasSmsPermission = com.example.service.PermissionManager.isGranted(
                ctx, android.Manifest.permission.SEND_SMS
            ),
            hasNotificationPermission = com.example.service.PermissionManager.hasNotificationPermission(ctx),
            hasOverlayPermission = com.example.service.PermissionManager.hasOverlay(ctx),
            hasBackgroundLocationPermission = com.example.service.PermissionManager.hasBackgroundLocation(ctx),
            hasCallPermission = com.example.service.PermissionManager.isGranted(
                ctx, android.Manifest.permission.CALL_PHONE
            ),
            isLocationServicesEnabled = com.example.service.PermissionManager
                .isLocationServicesEnabled(ctx)
        )
    }

    /**
     * Đồng bộ realtime: đọc lại vị trí + thời gian sử dụng + trạng thái dịch vụ
     * để mọi số liệu đổi ngay, không cần mở lại app.
     */
    fun syncRealtime() {
        refreshPermissions()
        refreshUsageStats()
        broadcastSelfTelemetry()
    }

    /**
     * Ghi thang han muc phut — CHI dung khi hai ben da dong y qua the thuc
     * "THOA THUAN GIU DUNG" (respondToAgreement). Do la phieu duyet roi,
     * khong can mo them phieu nua.
     */
    private fun applyDailyTargetFromAgreement(minutes: Int) {
        // Phieu thieu con so (mac dinh 0) khong duoc phep van may thanh "0 phut
        // toi da" — may se chan con ngay lap tuc vi da "qua han muc".
        if (minutes <= 0) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Phiếu thỏa thuận không có số phút nên chưa đổi hạn mức."
            )
            return
        }
        val currentSched = _uiState.value.usageSchedule ?: com.example.data.model.UsageSchedule(dailyTargetMinutes = minutes, usedMinutes = 0)
        
        // Optimistic update
        _uiState.value = _uiState.value.copy(usageSchedule = currentSched.copy(dailyTargetMinutes = minutes))
        
        val pairing = _uiState.value.activePairing ?: return
        viewModelScope.launch {
            try {
                repository.updateDailyTargetMinutes(pairing.id, minutes)
            } catch (e: Exception) {
                // Revert on error
                _uiState.value = _uiState.value.copy(usageSchedule = currentSched)
            }
        }
    }


    /**
     * Mot lan soan/luu noi quy khong thanh: bao Fail cho the BlockRulesCard va
     * ghi DUNG ly do vao `blockRulesError`. Khong dung `errorMessage` o day vi
     * the Card da tu hien banner inline — dua them ra Snackbar thi man hinh kep
     * hai dong chu giong het nhau.
     */
    private fun rejectBlockRules(reason: String) {
        _uiState.value = _uiState.value.copy(
            blockRulesSaveState = BlockRulesSaveState.Failed,
            blockRulesError = reason
        )
    }

    /**
     * Bam "Luu luat chan": khong con la lenh ap dung, ma la mot PHIEU DE XUAT.
     *
     * - Da ghep noi  -> luu vao hang cho (`changeStatus = PENDING`), ben kia
     *   bam Dong y thi luat moi moi chay. Luật cũ vẫn chạy trong lúc chờ.
     * - Chua ghep noi -> ap dung thang, vi khong co ai de hoi.
     * - Bi tu choi   -> luat cu nguyen ven, co dong nhat ky ro rang.
     *
     * Do la cai "hai ben me con dong long" ban dung ngo: phu huynh dat luat
     * nhung khong tu thong qua duoc phieu cua minh.
     */
    fun updateBlockRules(
        blockedPackages: List<String>,
        reminderOnly: Boolean,
        /**
         * null = GIU NGUYEN danh sach "luon duoc phep chay" cua luat dang
         * chay. Truoc day mac dinh la `emptyList()`, ma giao dien khong bao
         * gio truyen tham so nay -> moi lan luu tat ca app duoc me mien tran
         * (Zalo hoc, tap ghi...) bi xoa im lang.
         */
        allowedApps: List<String>? = null,
        /** null = khong doi han muc phut/ngay so voi luat dang chay. */
        dailyTargetMinutes: Int? = null,
        /**
         * Khung gio duoc dung máy ma ban nay muon ap dung: `"07:00-21:30"` = dat,
         * `""` = xin xoa (may khong chan theo nua), `null` = giu nguyen nhu
         * luat dang chay. The BlockRulesCard luon truyen chuoi ro rang (khong
         * dung null) vi ban nhap mang TOAN BO noi quy, khong phai tung mon.
         */
        windowCode: String? = null
    ) {
        val pairing = _uiState.value.activePairing
        val role = _uiState.value.userProfile?.role ?: UserRole.PARENT
        val actorName = _uiState.value.userProfile?.displayName.orEmpty()
        val base = _uiState.value.usageSchedule ?: UsageSchedule()
        val pairingId = pairing?.id.orEmpty().ifEmpty { base.pairingId }
        val isPaired = pairing != null && pairing.status == PairingStatus.PAIRED

        // (1) Dang PAIRED ma chua co snapshot cua chinh pairing nay chay ve:
        // `base` luc do co the la ban do che do demo gay ra, ma saveSchedule()
        // ghi bang `set()` (de ca toan bo tai lieu) — luu ngay de len la mat
        // noi quy that cua hai ben. Chua PAIRED thi khong co listener noi quy
        // nao chay, nen khong can chan o day.
        if (isPaired && scheduleSnapshotPairingId != pairingId) {
            rejectBlockRules(
                "Đang tải nội quy của cặp đôi này từ máy chủ về, chờ vài giây " +
                    "rồi bấm lại — nếu lưu ngay bây giờ máy sẽ ghi đè lên nội quy đang chạy."
            )
            return
        }

        // (3) Nguoi dung co the tick nham app ma [BlockPolicy] kiien trien
        // khong chan (launcher, ban phim, Cai dat, CH Play, chinh app nay).
        // Giua chung trong danh sach thi ghi xuong Firestore la no nam do mai
        // mai va khong bao gio chan duoc — loc bo va noi ro cho nguoi dung.
        val ownPackage = context?.packageName.orEmpty()
        val requested = blockedPackages.distinct()
        val blockable = requested.filter {
            !BlockPolicy.isSelf(it, ownPackage) && !BlockPolicy.isNeverBlocked(it)
        }
        val skippedCount = requested.size - blockable.size
        val skippedNote = if (skippedCount > 0)
            " • Bỏ $skippedCount app không thể chặn (hệ thống, launcher, bàn phím, CH Play)"
        else ""

        val result = ConsentPolicy.propose(
            base = base,
            byRole = role,
            blockedPackages = blockable,
            allowedApps = allowedApps ?: base.allowedApps,
            reminderOnly = reminderOnly,
            dailyTargetMinutes = dailyTargetMinutes ?: base.dailyTargetMinutes,
            windowCode = windowCode ?: base.windowCode()
        )

        if (result.outcome == ProposeOutcome.NO_CHANGE) {
            _uiState.value = _uiState.value.copy(
                blockRulesSaveState = BlockRulesSaveState.Saved,
                blockRulesSavedAtMillis = System.currentTimeMillis(),
                blockRulesError = null,
                successMessage = "Không có gì thay đổi so với nội quy đang chạy." + skippedNote
            )
            return
        }

        val toSave = result.schedule.copy(
            // Buoc hai truong nay vi `?: UsageSchedule()` co id/pairingId rong,
            // ma Firestore khong the ghi vao "pairings//schedules/".
            id = base.id.ifEmpty { "default_sched" },
            pairingId = pairingId
        )

        if (toSave.pairingId.isEmpty()) {
            rejectBlockRules(
                "Chưa ghép nối nên chưa lưu được nội quy. Vào tab Tổng quan để ghép nối " +
                    "trước — máy chỉ chặn app khi có cặp phụ huynh – học sinh."
            )
            return
        }

        val old = _uiState.value.usageSchedule
        _uiState.value = _uiState.value.copy(
            usageSchedule = toSave,
            blockRulesSaveState = BlockRulesSaveState.Saving,
            blockRulesError = null
        )

        viewModelScope.launch {
            try {
                repository.saveSchedule(toSave)
                val now = System.currentTimeMillis()
                val message = "Đã áp dụng • ${ConsentPolicy.describe(toSave)}$skippedNote"
                logRuleAudit(
                    pairingId = toSave.pairingId,
                    role = role,
                    actorName = actorName,
                    action = "ap_dung",
                    detail = ConsentPolicy.label(role) + " áp dụng: " +
                        ConsentPolicy.describe(toSave),
                    status = toSave.changeStatus
                )
                _uiState.value = _uiState.value.copy(
                    blockRulesSaveState = BlockRulesSaveState.Saved,
                    blockRulesSavedAtMillis = now,
                    blockRulesError = null,
                    successMessage = message
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(usageSchedule = old)
                rejectBlockRules("Không lưu được nội quy: ${e.message ?: "lỗi mạng"}")
            }
        }
    }


    /**
     * Ghi mot dong nhat ky noi quy. That bai o day KHONG duoc that bai ca
     * viec luu luat — luat quan trong hon bang ghi — nen goi trong
     * runCatching va chi ghi log khi loi.
     */
    private suspend fun logRuleAudit(
        pairingId: String,
        role: UserRole?,
        actorName: String,
        action: String,
        detail: String,
        status: RequestStatus?
    ) {
        runCatching {
            repository.appendRuleAudit(
                RuleAuditEntry(
                    pairingId = pairingId,
                    actorRole = role,
                    actorName = actorName,
                    action = action,
                    detail = detail,
                    status = status
                )
            )
        }.onFailure { Log.w("VesperaViewModel", "logRuleAudit failed", it) }
    }

    /** Tro nang tiep can da bat chua — xem `isAppBlockerEnabled()` trong BlockRulesCard.
     *  O day khong kiem tra lai vi chuoi luu trong Settings.Secure dung
     *  `applicationId` (com.aistudio.vesperafamily.vspxql), khong phai namespace. */

    fun toggleBackgroundService(context: Context) {
        val newState = !_uiState.value.isBackgroundServiceRunning
        if (newState) {
            // Không báo "đã bật" nếu thiếu quyền — dịch vụ sẽ không chạy thật.
            if (!telemetryHelper.hasLocationPermission()) {
                _uiState.value = _uiState.value.copy(
                    isBackgroundServiceRunning = false,
                    errorMessage = "Cần cấp quyền Vị trí (GPS) trước khi bật dịch vụ chạy nền."
                )
                return
            }
            VesperaTelemetryService.start(context)
            // `startForegroundService` la lenh BAT dong bo, con `isServiceRunning`
            // do chinh service gan len trong onStartCommand. Viet ra UI ngay luc
            // do la noi vay; cho toi da 3 giay roi moi bao "Đang chạy".
            viewModelScope.launch {
                var confirmed = false
                repeat(15) {
                    if (VesperaTelemetryService.isServiceRunning) { confirmed = true; return@repeat }
                    delay(200L)
                }
                _uiState.value = _uiState.value.copy(
                    isBackgroundServiceRunning = confirmed,
                    successMessage = if (confirmed)
                        "🟢 Dịch vụ chạy ngầm đang chạy: máy sẽ tự cập nhật vị trí và thống kê giờ dùng."
                    else null,
                    errorMessage = if (confirmed) _uiState.value.errorMessage else
                        "Dịch vụ KHÔNG chạy: Android không khởi động được Foreground Service. " +
                            "Kiểm tra quyền Thông báo và Quyền pin (đặt thành Không giới hạn)."
                )
            }
        } else {
            VesperaTelemetryService.stop(context)
            _uiState.value = _uiState.value.copy(
                isBackgroundServiceRunning = VesperaTelemetryService.isServiceRunning,
                successMessage = if (!VesperaTelemetryService.isServiceRunning)
                    "Đã dừng dịch vụ chạy ngầm." else null,
                errorMessage = if (VesperaTelemetryService.isServiceRunning)
                    "Đã gửi lệnh dừng nhưng dịch vụ vẫn đang chạy." else null
            )
        }
    }

    fun broadcastSelfTelemetry() {
        val user = _uiState.value.userProfile
        val pairing = _uiState.value.activePairing
        val (batteryLevel, isCharging) = telemetryHelper.getBatteryInfo()

        // Goi `fetchLocationFix`, KHONG phai `fetchCurrentLocation`. Ban cu tra
        // `null` roi in "Chưa lấy được GPS" ke ca khi may dang co mot diem wifi
        // ±300 m bi bo vi sai so — tuc noi oan thiet bi khong dinh vi duoc. Bo loc
        // o day GIUNG NGUYEN (chi diem vua do, duoi 100 m, khong mock moi duoc
        // gui); chi thay dieu la giu lai LY DO that de noi voi nguoi dung.
        telemetryHelper.fetchLocationFix { fix ->
            val droppedFix = fix.location
            // loc == null = KHONG GUI DUOC toa do: giu sentinel 0.0 cua LocationData,
            // khong toa do mau.
            val loc = droppedFix?.takeIf {
                fix.source == LocationFixSource.LIVE_FIX && !fix.accuracyTooLow
            }
            val lat = loc?.latitude ?: 0.0
            val lng = loc?.longitude ?: 0.0
            val acc = loc?.accuracy ?: 0f
            val spd = loc?.speed ?: 0.0f
            val detailed = telemetryHelper.getDetailedAppUsageList()
            val topApp = detailed.firstOrNull()?.appName.orEmpty()
            val topMins = detailed.firstOrNull()?.minutesUsed ?: 0
            val totalMins = detailed.sumOf { it.minutesUsed }

            viewModelScope.launch {
                val noFixReason = when {
                    loc == null && droppedFix != null && fix.accuracyTooLow ->
                        "Máy định vị được nhưng sai số ±${droppedFix.accuracy.toInt()} m, vượt " +
                            "${TelemetryAndUsageHelper.MAX_ACCEPTED_ACCURACY_METERS.toInt()} m nên " +
                            "app chưa gửi vị trí này lên (chưa có vị trí nào khác để gửi)."
                    loc == null && fix.source == LocationFixSource.STALE_CACHED_FIX ->
                        "Điểm đang có là vị trí CŨ đo ${(fix.ageMs / 1000L).coerceAtMost(86_400L)} giây " +
                            "trước; app chỉ gửi điểm vừa đo nên chưa gửi vị trí này lên."
                    else -> fix.failureReason
                        ?: telemetryHelper.missingPermissionReason()
                        ?: "Chưa lấy được GPS nên chưa gửi vị trí (vẫn gửi pin và thói quen dùng máy)."
                }

                val realAddr = if (loc == null) {
                    "Chưa xác định được vị trí GPS"
                } else context?.let { ctx ->
                    AddressHelper.getReadableAddress(ctx, lat, lng)
                } ?: "Tọa độ GPS (${com.example.service.map.FixPrecision.formatCoordinates(
                    lat, lng, com.example.service.map.FixPrecision.honestAccuracyMeters(acc)
                )})"

                val safeZone = when {
                    loc == null -> "Không có vị trí GPS"
                    spd > 8f -> "Đang di chuyển (${(spd * 3.6f).toInt()} km/h)"
                    else -> realAddr
                }

                val locationData = LocationData(
                    userId = user?.id ?: "my_device",
                    pairingId = pairing?.id.orEmpty(),
                    userName = user?.displayName ?: "Thiết bị này",
                    latitude = lat,
                    longitude = lng,
                    accuracy = acc,
                    speed = spd,
                    batteryLevel = batteryLevel,
                    isCharging = isCharging,
                    isPrivacyActive = false,
                    currentSafeZone = safeZone,
                    topActiveApp = topApp,
                    topActiveAppMinutes = topMins,
                    totalUsedMinutes = totalMins,
                    updatedAt = System.currentTimeMillis()
                )

                val canUpload = user != null && _uiState.value.isAuthenticated
                // Repository CHỈ có một đường ghi gộp (VesperaRepository.kt:371 dùng .set(),
                // ghi đè TOÀN BỘ doc locations/<userId>). Ghi bản 0/0 lúc này = xoá toạ độ thật
                // mà phụ huynh đang xem, nên bắt buộc chặn; người dùng được nói rõ vì sao.
                val uploadBlockedNote = if (canUpload && !locationData.hasRealFix)
                    " Pin và thói quen dùng máy cũng chưa tải lên: ứng dụng chỉ có một bản ghi gộp," +
                    " gửi tiếp sẽ xoá mất vị trí thật phụ huynh đang theo dõi."
                else null

                _uiState.value = _uiState.value.copy(
                    myLocation = locationData,
                    // Bam "lam moi" KHONG duoc bien dich vu dang chay: doc
                    // trang thai that cua service, neu khong thi moi lan bam
                    // la the hien "Đang chạy" gia.
                    isBackgroundServiceRunning = VesperaTelemetryService.isServiceRunning,
                    errorMessage = if (!locationData.hasRealFix)
                        (noFixReason + (uploadBlockedNote.orEmpty()))
                    else _uiState.value.errorMessage
                )

                if (canUpload) {
                    if (locationData.hasRealFix) {
                        try {
                            repository.updateLocation(locationData)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update location", e)
                            // Truoc day chi Log.w: bam "Lam moi" khi loi ghi Firestore
                            // (thieu quyen / bi cam / mat mang) thi man hinh KHONG thay
                            // gi ca — khong loi, khong dong chu, ban do dung im va
                            // nguoi dung tu nghi may chua do duoc GPS.
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Không tải được vị trí lên máy chủ: " +
                                    (e.message ?: e.javaClass.simpleName)
                            )
                        }
                    } else {
                        Log.i(TAG, "Bo qua updateLocation: chua co GPS that, tranh de 0/0 len toa do dang co.")
                    }
                }
            }
        }
    }

    /**
     * Chờ tối đa [timeoutMs] cho một điểm định vị THẬT (helper không bao giờ trả tọa độ mẫu).
     * Trả `null` khi hết giờ, thiếu quyền hoặc điểm bị loại (mock/sai số) — caller đi nhánh
     * "khẩn cấp không kèm vị trí", cấm tự chế tọa độ.
     */
    private suspend fun awaitRealFixLocation(timeoutMs: Long = 12_000L): LocationData? {
        // Đồng hồ trong PHẢI nổ trước đồng hồ ngoài: hai timer cùng `timeoutMs` khiến
        // withTimeoutOrNull cắt coroutine vài ms trước khi callback kịp trả điểm GPS thật,
        // làm mất fix hợp lệ và SOS báo oan "không định vị được".
        val innerTimeoutMs = (timeoutMs - 500L).coerceAtLeast(1_000L)
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<LocationData?> { cont ->
                telemetryHelper.fetchLocationFix(timeoutMs = innerTimeoutMs) { fix ->
                    val loc = fix.location
                    val data = if (loc != null) {
                        val (bat, chg) = telemetryHelper.getBatteryInfo()
                        LocationData(
                            userId = _uiState.value.userProfile?.id.orEmpty().ifEmpty { "my_device" },
                            pairingId = _uiState.value.activePairing?.id.orEmpty(),
                            userName = _uiState.value.userProfile?.displayName.orEmpty(),
                            latitude = loc.latitude,
                            longitude = loc.longitude,
                            accuracy = loc.accuracy,
                            speed = loc.speed,
                            batteryLevel = bat,
                            isCharging = chg,
                            // `currentSafeZone` la TEN NOI CHO, khong phai nguon
                            // do diem. Truoc day day chuoi "Vị trí GPS thực tế"
                            // vao day nen man hinh/SMS in ra no nhu ten cua mot
                            // dia chi. Diem nay chua keo duoc dia chi thi noi
                            // thang la chua biet.
                            currentSafeZone = "Chưa xác định được khu vực (chỉ có tọa độ GPS)",
                            updatedAt = telemetryHelper.fixTimeMillis(loc)
                        )
                    } else null
                    if (cont.isActive) cont.resume(data)
                }
            }
        }
    }

    /** Điểm SOS khi KHÔNG có GPS: giữ nguyên khẩn cấp, tọa độ là sentinel 0/0 + chú thích giờ. */
    private fun noFixSosLocation(userName: String): LocationData {
        val (bat, chg) = telemetryHelper.getBatteryInfo()
        val timeText = java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.US).format(java.util.Date())
        return LocationData(
            userId = _uiState.value.userProfile?.id.orEmpty().ifEmpty { "my_device" },
            pairingId = _uiState.value.activePairing?.id.orEmpty(),
            userName = userName,
            latitude = 0.0,
            longitude = 0.0,
            batteryLevel = bat,
            isCharging = chg,
            currentSafeZone = "KHẨN CẤP nhưng không xác định được vị trí GPS lúc $timeText",
            isSos = true,
            updatedAt = System.currentTimeMillis()
        )
    }

    /** Vị trí dùng cho SOS: ưu tiên fix đang có (must còn tươi), hết thì chờ tối đa 12 s lấy fix mới. */
    private suspend fun resolveSosLocation(userName: String): Pair<LocationData, Boolean> {
        // `updatedAt` luôn là mốc giờ máy (epoch ms): set ở :1498/:1565 = System.currentTimeMillis(),
        // ở :1543 = fixTimeMillis() đã clamp về giờ máy, và mọi bản ghi cloud
        // (VesperaRepository.sendSosAlert:396) cũng ghi System.currentTimeMillis().
        // Vì vậy lấy tuổi điểm ra làm điều kiện được: điểm 3 tiếng trước KHÔNG còn là "vị trí hiện tại".
        val maxAgeMs = 5 * 60_000L
        val cached = _uiState.value.myLocation?.takeIf {
            it.hasRealFix && System.currentTimeMillis() - it.updatedAt in 0L..maxAgeMs
        }
        if (cached != null) return cached to true
        val fresh = awaitRealFixLocation()
        return (fresh ?: noFixSosLocation(userName)) to (fresh?.hasRealFix == true)
    }

    fun requestExtraTime(minutes: Int, reason: String) {
        val pairing = _uiState.value.activePairing ?: return
        val child = _uiState.value.userProfile ?: return
        // May PHU HUYNH cung co nut "Xin them gio" o man Gio dung (tung lien
        // `onRequestExtraTimeClick`), nen neu khong chan thi bam nut do ghi len
        // mot phieu ma `childId` chinh la uid cua bo me, va app bao "Da gui
        // yeu cau ... den phu huynh" — tuc la gui cho chinh minh. Phieu do cung
        // moc len bang "CHỜ BẠN DUYỆT" cua Tong quan voi ten bo me. Xin them
        // gio la chuyen cua may con; bo me dat han muc thi dung Cài đặt.
        if (child.role != UserRole.CHILD) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chỉ máy con mới xin thêm giờ được. Muốn đổi hạn mức, " +
                    "hãy sửa trong mục Nội quy ở Cài đặt."
            )
            return
        }
        if (minutes <= 0) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chưa nhập số phút xin thêm, nên chưa gửi phiếu."
            )
            return
        }

        viewModelScope.launch {
            try {
                val req = ExtraTimeRequest(
                    pairingId = pairing.id,
                    childId = child.id,
                    childName = child.displayName,
                    minutesRequested = minutes,
                    reason = reason,
                    status = RequestStatus.PENDING
                )
                repository.requestExtraTime(req)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Đã gửi yêu cầu xin thêm $minutes phút đến phụ huynh!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun respondToExtraTime(requestId: String, approved: Boolean, minutesToAdd: Int) {
        val pairing = _uiState.value.activePairing ?: return


        viewModelScope.launch {
            try {
                repository.respondToExtraTime(pairing.id, requestId, approved, minutesToAdd)
                _uiState.value = _uiState.value.copy(
                    successMessage = if (approved) "Đã duyệt cộng $minutesToAdd phút sử dụng." else "Đã từ chối yêu cầu."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun requestPrivacyHours(
        startDate: String = "",
        startHour: Int = 14,
        startMinute: Int = 0,
        endDate: String = "",
        endHour: Int = 17,
        endMinute: Int = 0,
        reason: String = ""
    ) {
        val pairing = _uiState.value.activePairing ?: return
        val child = _uiState.value.userProfile ?: return


        viewModelScope.launch {
            try {
                val req = PrivacyRequest(
                    pairingId = pairing.id,
                    childId = child.id,
                    childName = child.displayName,
                    startDate = startDate,
                    endDate = endDate,
                    startHour = startHour,
                    startMinute = startMinute,
                    endHour = endHour,
                    endMinute = endMinute,
                    reason = reason,
                    status = RequestStatus.PENDING
                )
                repository.requestPrivacyHours(req)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Đã gửi yêu cầu khung giờ riêng tư ($startHour:00 - $endHour:00 ngày $startDate)!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun respondToPrivacyRequest(requestId: String, childId: String, approved: Boolean) {
        val pairing = _uiState.value.activePairing ?: return


        viewModelScope.launch {
            try {
                repository.respondToPrivacyRequest(pairing.id, requestId, childId, approved)
                _uiState.value = _uiState.value.copy(
                    successMessage = if (approved) "Đã duyệt khung giờ riêng tư cho học sinh." else "Đã từ chối yêu cầu riêng tư."
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun setSelectedEmotion(emotion: EmotionTag?) {
        _uiState.value = _uiState.value.copy(selectedEmotion = emotion)
    }

    fun sendMessage(text: String, mediaUrl: String? = null, mediaType: String? = null) {
        val pairing = _uiState.value.activePairing ?: return
        val profile = _uiState.value.userProfile ?: return
        val emotion = _uiState.value.selectedEmotion

        if (text.isBlank() && emotion == null && mediaUrl == null) return


        viewModelScope.launch {
            try {
                val msg = FamilyMessage(
                    pairingId = pairing.id,
                    senderId = profile.id,
                    senderName = profile.displayName,
                    senderRole = profile.role,
                    text = text.trim(),
                    emotion = emotion?.name,
                    mediaUrl = mediaUrl,
                    mediaType = mediaType,
                    isStarred = false,
                    createdAt = System.currentTimeMillis()
                )
                repository.sendMessage(msg)
                _uiState.value = _uiState.value.copy(selectedEmotion = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun toggleMessageStar(messageId: String, isStarred: Boolean) {
        val pairing = _uiState.value.activePairing ?: return


        viewModelScope.launch {
            try {
                repository.toggleMessageStar(pairing.id, messageId, isStarred)
            } catch (e: Exception) {
                Log.w(TAG, "toggleMessageStar error", e)
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    /**
     * uid dang dung: lay tu ho so truoc, vi sau khi xoa thi `auth.currentUser`
     * van con (tai khoan Google khong the xoa tu client) trong khi ho so da mat
     * — can dung dung cai ma luon luon co truoc luc bam.
     */
    private fun currentUserId(): String? =
        _uiState.value.userProfile?.id?.takeIf { it.isNotEmpty() }
            ?: auth.currentUser?.uid?.takeIf { it.isNotEmpty() }

    /**
     * Dem truoc so document se bi don. Hop thoai "Xóa tài khoản" in con so nay
     * ra de nguoi dung biet minh sap mat gi thay vi mo mot cai hop doan lenh.
     */
    fun previewAccountDeletion() {
        val userId = currentUserId() ?: return
        viewModelScope.launch {
            val count = try {
                repository.inventoryForDeletion(userId).total
            } catch (e: Exception) {
                Log.w(TAG, "previewAccountDeletion", e)
                null
            }
            _uiState.value = _uiState.value.copy(accountDeletionPreviewCount = count)
        }
    }

    /**
     * Nut "Xóa tài khoản" — ban 66.0. Day la lenh duy nhat trong app xoa du lieu
     * tren may chu, va thu tu cua no bi rules rang buoc (`accountIsBeingDeleted`:
     * vi tri, vet di chuyen, phieu SOS va nhat ky noi quy chi xoa duoc khi ho so
     * `users/<uid>` KHONG CON), nen `VesperaRepository.deleteAccount` phai gay
     * ho so truoc khi don phan lai.
     *
     * KHONG the xoa tai khoan Google: client khong co Admin SDK, nen email + mat
     * khau van nam trong Firebase Authentication. App phai noi thang dieu do —
     * dang nhap lai bang dung tai khoan ay se tao mot ho so trong.
     */
    fun deleteAccount() {
        val userId = currentUserId()
        if (userId == null) {
            _uiState.value = _uiState.value.copy(
                accountDeletion = AccountDeletionState.Failed("Máy này chưa đăng nhập tài khoản nào.")
            )
            return
        }
        _uiState.value = _uiState.value.copy(accountDeletion = AccountDeletionState.Working)
        viewModelScope.launch {
            val outcome = try {
                repository.deleteAccount(userId, repository.inventoryForDeletion(userId))
            } catch (e: Exception) {
                Log.w(TAG, "deleteAccount that bai", e)
                null
            }
            if (outcome == null) {
                _uiState.value = _uiState.value.copy(
                    accountDeletion = AccountDeletionState.Failed(
                        "Không liên lạc được với máy chủ — chưa xóa gì cả."
                    )
                )
                return@launch
            }
            if (outcome.deleted > 0) clearLocalDeviceData()
            _uiState.value = _uiState.value.copy(
                accountDeletion = AccountDeletionState.Done(outcome.deleted, outcome.failed),
                // Ho so khong con tren may chu: moi thu phat sinh tu no (vi tri,
                // ghep noi, noi quy) deu vo nghia, de lai thi man hinh van hien
                // du lieu cua mot tai khoan da chet.
                userProfile = null,
                activePairing = null,
                partnerLocation = null,
                myLocation = null,
                messages = emptyList(),
                ruleAudit = emptyList(),
                locationHistory = emptyList(),
                usageSchedule = null
            )
        }
    }

    /**
     * Dong hop thoai xong thi tra ve `Idle`, neu khong lan mo hop thoai ke tiep
     * se hien bien nhan cua lan truoc va nguoi dung tuong la vua xoa them.
     */
    fun resetAccountDeletionState() {
        _uiState.value = _uiState.value.copy(
            accountDeletion = AccountDeletionState.Idle,
            accountDeletionPreviewCount = null
        )
    }

    /**
     * Don du lieu rieng TRÊN MÁY: SharedPreferences (bộ đếm hỏi AI, key AI,
     * lich su chat, luat chan app, GPS lu khi mat mang, so dien thoi SOS).
     *
     * Xoa ca tep chu khong xoa tung khoa: moi lan them mot khoa moi la them mot
     * noi du lieu co the sot lai. `Context.dataDir` (API 24) tra ve thu muc
     * rong cua ung dung nen `File.delete()` binh thuong dung duoc.
     */
    private fun clearLocalDeviceData() {
        val ctx = context ?: return
        runCatching {
            File(ctx.dataDir, "shared_prefs").listFiles()?.forEach { file ->
                if (!file.delete()) Log.w(TAG, "không xóa được local file ${file.name}")
            }
        }.onFailure { Log.w(TAG, "clearLocalDeviceData", it) }
        runCatching { ctx.cacheDir.listFiles()?.forEach { it.delete() } }
        // Dich vu duoi nen van con chay thi no se ghi lai telemetry/bo dem ngay
        // sau khi don — phai bat no dung lai.
        runCatching { VesperaTelemetryService.stop(ctx) }
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun dismissSosAlert() {
        _uiState.value = _uiState.value.copy(isSosAlertActive = false, sosStatusMessage = "")
    }

    // ── Bảng cứu nạn: môi trường thật của máy + vị trí đang cầu cứu ──────

    /**
     * Đọc lại TỪ MÁY: Zalo có cài không, quyền gọi còn không.
     *
     * `isZaloInstalled` chi thay duoc Zalo tu Android 11 neu Manifest co
     * `<package android:name="com.zing.zalo"/>` trong `<queries>`. Thieu dong
     * do thi nut "Gọi qua Zalo" khong bao gio hien — khong loi, khong bao cao.
     */
    fun refreshSosEnvironment() {
        val ctx = context ?: return
        _uiState.value = _uiState.value.copy(
            zaloInstalled = com.example.service.sos.SosRescueIntents.isZaloInstalled(ctx),
            hasCallPermission = com.example.service.PermissionManager.isGranted(
                ctx, android.Manifest.permission.CALL_PHONE
            )
        )
    }

    /**
     * Vi tri + dia chi THAT cho bang cuu nan. Khong dung diem cu trong UiState
     * lam "vi tri hien tai": luc cuu nan thi mot diem hom qua la tin sai lech.
     */
    private suspend fun resolveSosPosition(userName: String): Pair<LocationData, com.example.service.sos.SosPlan.Position> {
        val (loc, hasFix) = resolveSosLocation(userName)
        val address = if (hasFix) {
            context?.let { AddressHelper.getReadableAddress(it, loc.latitude, loc.longitude) }.orEmpty()
        } else ""
        val position = com.example.service.sos.SosPlan.Position(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = if (hasFix) loc.accuracy else 0f,
            address = address,
            fixTimeMillis = if (hasFix) loc.updatedAt else 0L,
            senderName = userName
        )
        return loc to position
    }

    /** Goi khi mo bang SOS: lay vi tri + dia chi va kiem tra Zalo/quyen goi. */
    fun prepareSosConsole() {
        refreshSosEnvironment()
        _uiState.value = _uiState.value.copy(sosStatusMessage = "Đang lấy vị trí hiện tại của máy…")
        viewModelScope.launch {
            val name = sosSenderName()
            val (loc, position) = resolveSosPosition(name)
            _uiState.value = _uiState.value.copy(
                myLocation = if (loc.hasRealFix) loc else _uiState.value.myLocation,
                sosPosition = position,
                sosStatusMessage = ""
            )
        }
    }

    private fun sosSenderName(): String =
        _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Thành viên gia đình" }

    /**
     * Goi nguoi than theo kenh nguoi dung chon: Zalo (neu may cai Zalo) hoac
     * dien thoai thuong voi so da luu trong danh ba cua app.
     */
    fun callFamilyForSos(channel: com.example.service.sos.SosPlan.CallChannel) {
        val ctx = context
        val number = _uiState.value.emergencyPhoneNumber.trim()
        if (ctx == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = com.example.service.sos.SosPlan.NO_CALL_CHANNEL
            )
            return
        }
        if (number.isBlank()) {
            _uiState.value = _uiState.value.copy(
                errorMessage = com.example.service.sos.SosPlan.NO_SAVED_NUMBER
            )
            return
        }
        refreshSosEnvironment()
        val canCall = _uiState.value.hasCallPermission
        _uiState.value = _uiState.value.copy(
            sosStatusMessage = com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE,
            isSosAlertActive = true,
            activeSosMessage = com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE
        )
        viewModelScope.launch {
            val outcome = when (channel) {
                com.example.service.sos.SosPlan.CallChannel.ZALO ->
                    com.example.service.sos.SosRescueIntents.openZaloChat(ctx!!, number)
                else ->
                    com.example.service.sos.SosRescueIntents.call(ctx!!, number, canCall)
            }
            _uiState.value = _uiState.value.copy(
                sosStatusMessage = if (outcome.ok)
                    com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE else "",
                isSosAlertActive = true,
                activeSosMessage = outcome.note,
                successMessage = if (outcome.ok) outcome.note else null,
                errorMessage = if (outcome.ok) null else outcome.note
            )
        }
    }

    /**
     * Mo o SOAN TIN cuu nan da dien san toa do (vĩ độ / kinh độ / địa điểm).
     * `ACTION_SENDTO` + `sms:<so>` + "sms_body" — khong can quyen SEND_SMS nen
     * duong nay van chay tren may chua cap quyen SMS.
     */
    fun openFamilySmsDraft() {
        val ctx = context
        val number = _uiState.value.emergencyPhoneNumber.trim()
        if (number.isBlank() || ctx == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = com.example.service.sos.SosPlan.NO_SAVED_NUMBER
            )
            return
        }
        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val (loc, position) = resolveSosPosition(sosSenderName())
            val outcome = com.example.service.sos.SosRescueIntents.composeFamilySms(
                ctx, number, com.example.service.sos.SosPlan.familySmsBody(position)
            )
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                myLocation = if (loc.hasRealFix) loc else _uiState.value.myLocation,
                sosPosition = position,
                sosStatusMessage = if (outcome.ok)
                    com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE else "",
                isSosAlertActive = true,
                activeSosMessage = outcome.note,
                successMessage = if (outcome.ok) outcome.note else null,
                errorMessage = if (outcome.ok) null else outcome.note
            )
        }
    }

    fun sendSosAlert() {
        sendSmartSosToFamily()
    }

    fun sendSmartSosToFamily(customMessage: String? = null) {
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Thành viên gia đình" }

        // Validating SOS contact
        if (_uiState.value.emergencyPhoneNumber.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isSosAlertActive = false,
                errorMessage = "Chưa thiết lập số điện thoại người thân. Vui lòng cài đặt trong Cài đặt."
            )
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            // Ke ca dia chi dich duoc: hop thu bao sau khi gui cho gia dinh phai
            // in vi do / kinh do / dia diem va doc ra sau 2 giay.
            val (loc, position) = resolveSosPosition(name)
            val hasLocation = position.hasFix
            val timeText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
            val dispatcher = smartSosDispatcher
            val res: com.example.service.sos.SosDispatchResult = if (dispatcher != null) {
                dispatcher.sendSosToFamily(loc, name, customMessage)
            } else {
                // Không có dispatcher: dùng trực tiếp repository và báo ĐÚNG kết quả.
                val cloud = repository.sendSosAlert(loc)
                if (cloud.delivery == VesperaRepository.CloudDelivery.SENT) {
                    com.example.service.sos.SosDispatchResult(
                        true,
                        SosChannelUsed.INTERNET_CLOUD,
                        "Đã gửi tín hiệu SOS qua Internet tới gia đình thành công."
                    )
                } else {
                    com.example.service.sos.SosDispatchResult(
                        false,
                        SosChannelUsed.INTERNET_CLOUD,
                        "Chưa gửi được SOS: ${cloud.detail ?: "không rõ nguyên nhân"}"
                    )
                }
            }
            // Không có GPS thì cấm báo "đã gửi vị trí".
            val honestMessage = if (hasLocation) res.detailMessage else {
                if (res.isSuccess)
                    "Đã gửi tín hiệu khẩn cấp tới gia đình NHƯNG không xác định được vị trí GPS lúc $timeText — tin nhắn KHÔNG kèm tọa độ."
                else
                    "${res.detailMessage} (chưa định vị được GPS lúc $timeText)"
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                myLocation = if (hasLocation) loc else _uiState.value.myLocation,
                sosPosition = position,
                isSosAlertActive = res.isSuccess,
                activeSosMessage = honestMessage,
                successMessage = if (res.isSuccess) honestMessage else null,
                errorMessage = if (!res.isSuccess) honestMessage else null
            )
        }
    }

    fun sendSmartSosToRescue(hotline: String = "112") {
        val ctx = context
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Công dân khẩn cấp" }

        // BUOC 1 — MO BAN QUAY SO NGAY TUC THOI, truoc khi cho GPS.
        // Tam so 113 thi moi giay la moi su song: 12 giay di tim toa do khong
        // duoc chen vao giua luc nguoi dung bam va tong dai nghe may. Toa do
        // se di theo bang SMS o buoc 2, va neu may da ket noi thi cuoc goi da
        // chay roi.
        refreshSosEnvironment()
        val dialNow = if (ctx != null)
            com.example.service.sos.SosRescueIntents.call(
                ctx, hotline, _uiState.value.hasCallPermission
            )
        else null
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            isSosAlertActive = true,
            sosStatusMessage = com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE,
            activeSosMessage = dialNow?.note
                ?: com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE
        )

        viewModelScope.launch {
            // BUOC 2 — lay vi tri roi gui SMS toa do toi SO NGUOI THAN (tong
            // dai chi duoc goi, ma ngan khong nhan SMS). startCall =
            // false vi ban quay so DA mo o buoc 1, mo lan hai la chen len cuoc
            // goi dang ket noi.
            val (loc, position) = resolveSosPosition(name)
            val dispatcher = smartSosDispatcher
            val dispatchResult = dispatcher?.sendSosToRescueForce(
                hotline, loc, name, startCall = dialNow == null
            )
            val dispatched = dispatchResult?.isSuccess == true
            val raw = dispatchResult?.detailMessage
            val message = when {
                dispatcher == null ->
                    "Chưa gửi được yêu cầu cứu hộ $hotline: bộ phận SOS chưa sẵn sàng." +
                        if (!loc.hasRealFix) " Ngoài ra chưa định vị được GPS lúc ${timeText()}." else ""
                !dispatched -> raw.orEmpty() +
                    if (!loc.hasRealFix) " (chưa định vị được GPS lúc ${timeText()})." else ""
                loc.hasRealFix -> "$raw — tin nhắn gửi kèm vị trí GPS lúc ${timeText()}."
                else -> "Đã báo khẩn cấp cứu hộ $hotline nhưng KHÔNG xác định được vị trí GPS lúc ${timeText()}" +
                    " — tin nhắn gửi đi KHÔNG kèm tọa độ."
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                myLocation = if (loc.hasRealFix) loc else _uiState.value.myLocation,
                sosPosition = position,
                isSosAlertActive = true,
                // Van giu dong "Đang chuyển hướng đến cứu hộ": cuoc goi dang chay.
                sosStatusMessage = com.example.service.sos.SosPlan.REDIRECTING_TO_RESCUE,
                activeSosMessage = message,
                // luôn có đúng một phản hồi: dispatcher thành công mà thiếu vị trí
                // trước đây rơi vào successMessage = null VÀ errorMessage = null => câm lặng.
                successMessage = if (dispatched && loc.hasRealFix) message else null,
                errorMessage = if (dispatched && loc.hasRealFix) null else message
            )
        }
    }

    /** Chuoi gio hien tai, dung trong loi bao cao SOS — de may khong bao "vi tri" ma khong noi luc nao. */
    private fun timeText(): String =
        java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())

    /** Lý do hiện tại KHÔNG gửi được SMS cứu nạn (null nếu gửi được) — dùng cho UI cảnh báo. */
    fun smsAvailabilityIssue(): String? = smartSosDispatcher?.smsPreflightReason()


    fun connectMeshPeer(endpointId: String) {
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Thiết bị Gia Đình Việt" }
        val dispatcher = smartSosDispatcher
        if (dispatcher == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chưa gửi được yêu cầu kết nối: bộ phận SOS/mesh chưa khởi động " +
                    "(mở app trên máy và cấp quyền Vị trí + thiết bị gần đó trước)."
            )
            return
        }
        dispatcher.meshManager.requestConnection(endpointId, name)
        // requestConnection chi DAY yeu cau vao Wi-Fi P2P, khong phai bang chung
        // ben kia da noi — chu do la `connectedPeers` (listener that).
        _uiState.value = _uiState.value.copy(
            successMessage = "Đã gửi yêu cầu kết nối Wi-Fi P2P. Máy kia sẽ vào danh sách " +
                "\"Đã kết nối\" chỉ khi thực sự nối được."
        )
    }

    fun sendMeshMessage(endpointId: String, text: String) {
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Tôi" }
        val ok = smartSosDispatcher?.meshManager?.sendMessageToPeer(endpointId, text, name) ?: false
        if (ok) {
            _uiState.value = _uiState.value.copy(successMessage = "Đã gửi tin nhắn Mesh P2P!")
        } else {
            _uiState.value = _uiState.value.copy(errorMessage = "Gửi tin qua Mesh thất bại.")
        }
    }

    fun saveEmergencyPhoneNumber(phone: String) {
        val smsManager = smartSosDispatcher?.smsManager
        val clean = phone.trim()
        if (smsManager == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Không lưu được số khẩn cấp: bộ phận SMS chưa khởi động, " +
                    "mở app lại và cấp quyền Điện thoại/SMS rồi thử lại."
            )
            return
        }
        smsManager.saveEmergencyPhoneNumber(clean)
        // `apply()` ghi khong dong bo nhung SharedPreferences phuc vu gia tri
        // tu bo nho ngay, nen doc lai la cach duyet nhat de bao "đã lưu" co
        // can cu.
        val savedBack = smsManager.getEmergencyPhoneNumber()
        _uiState.value = _uiState.value.copy(
            emergencyPhoneNumber = savedBack,
            successMessage = if (savedBack == clean) "Đã lưu số điện thoại người thân khẩn cấp: $clean" else null,
            errorMessage = if (savedBack == clean) null else
                "Không lưu được số $clean: máy đọc lại ra \"$savedBack\" (trống hoặc khác)."
        )
    }

    fun saveSosSchedule(isRestricted: Boolean, startHour: Int, endHour: Int) {
        val dispatcher = smartSosDispatcher
        if (dispatcher == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Không lưu được khung giờ SOS: bộ phận SOS chưa khởi động."
            )
            return
        }
        dispatcher.setSosAllowedSchedule(isRestricted, startHour, endHour)
        val (savedRestricted, savedStart, savedEnd) = dispatcher.getSosScheduleConfig()
        val ok = savedRestricted == isRestricted && savedStart == startHour && savedEnd == endHour
        _uiState.value = _uiState.value.copy(
            isSosRestricted = savedRestricted,
            sosStartHour = savedStart,
            sosEndHour = savedEnd,
            successMessage = if (!ok) null else if (savedRestricted)
                "Đã lưu khung giờ cho phép SOS: $savedStart:00 - $savedEnd:00"
            else "Đã lưu: SOS dùng được 24/7.",
            errorMessage = if (ok) null else
                "Máy chưa ghi được khung giờ SOS (đọc lại ra $savedStart:00 - $savedEnd:00)."
        )
    }

    fun isSosAllowedNow(): Boolean {
        return smartSosDispatcher?.isSosAllowedAtCurrentTime() ?: true
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    fun updateUserProfile(newName: String, newPhotoUrl: String) {
        val currentProfile = _uiState.value.userProfile ?: return
        val updatedProfile = currentProfile.copy(displayName = newName, photoUrl = newPhotoUrl)
        _uiState.value = _uiState.value.copy(userProfile = updatedProfile)
        viewModelScope.launch {
            try {
                repository.saveUserProfile(updatedProfile)
                _uiState.value = _uiState.value.copy(successMessage = "Đã cập nhật thông tin tài khoản!")
            } catch (e: Exception) {
                Log.w(TAG, "updateUserProfile error", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Không thể cập nhật thông tin.")
            }
        }
    }
}
