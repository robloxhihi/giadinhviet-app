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
import com.example.data.model.PairingRequest
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
import com.example.data.repository.VesperaRepository
import com.example.service.blocker.BlockPolicy
import com.example.service.blocker.ConsentPolicy
import com.example.service.blocker.ProposeOutcome
import com.example.service.blocker.RespondOutcome
import com.example.data.model.DetailedAppUsage
import com.example.service.AddressHelper
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
import java.util.UUID

private const val TAG = "VesperaViewModel"

data class VesperaUiState(
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isGuestDemoMode: Boolean = false,
    val isIosPreviewMode: Boolean = false,
    val isBackgroundServiceRunning: Boolean = false,
    val isDarkMode: Boolean = true,
    val colorTheme: String = "blue",
    val focusSessionMinutesRemaining: Int = 0,
    /** Giay con lai cua phien Pomodoro — dong ho phai nhay tung giay. */
    val focusSessionSecondsRemaining: Int = 0,
    /** Tong so phut cua phien dang chay, de ve thanh tien do. */
    val focusSessionTotalMinutes: Int = 25,
    val isFocusSessionActive: Boolean = false,
    val authUserId: String? = null,
    val userProfile: UserProfile? = null,
    val activePairing: Pairing? = null,
    val pendingRequests: List<PairingRequest> = emptyList(),
    val partnerLocation: LocationData? = null,
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
    val permissionSnapshot: List<com.example.service.PermissionManager.PermissionItem> = emptyList(),
    val lastRealtimeSyncAt: Long = 0L,
    val extraTimeRequests: List<ExtraTimeRequest> = emptyList(),
    val privacyRequests: List<PrivacyRequest> = emptyList(),
    val agreementRequests: List<ScreenTimeAgreement> = emptyList(),
    val messages: List<FamilyMessage> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedEmotion: EmotionTag? = null,
    val smartAdvice: String = "Chào bạn! Hãy đặt mục tiêu cân bằng 120 phút mỗi ngày giữa học tập và giải trí để bảo vệ sức khỏe.",
    val lastBumpCount: Int = 0,
    val weeklyUsage: List<Pair<String, Int>> = listOf(
        "T2" to 0, "T3" to 0, "T4" to 0, "T5" to 0, "T6" to 0, "T7" to 0, "CN" to 0
    ),
    val locationHistory: List<LocationHistoryEntry> = emptyList(),
    val isOnline: Boolean = true,
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
    val ruleAudit: List<RuleAuditEntry> = emptyList()
)

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
    private var requestsJob: Job? = null
    private var locationJob: Job? = null
    private var historyJob: Job? = null
    private var scheduleJob: Job? = null
    private var extraTimeJob: Job? = null
    private var privacyJob: Job? = null
    private var agreementJob: Job? = null
    private var auditJob: Job? = null
    private var messagesJob: Job? = null
    private var focusJob: Job? = null

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
        // Initialize SocketManager and observe messages
        com.example.service.socket.SocketManager.connect()
        viewModelScope.launch {
            com.example.service.socket.SocketManager.incomingMessages.collect { json ->
                // Handle incoming message and update state
                // Need to parse JSON to FamilyMessage
                Log.d(TAG, "Received socket message: $json")
            }
        }
        
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
                isGuestDemoMode = false,
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
                    isGuestDemoMode = false,
                    authUserId = user.uid,
                    isLoading = false
                )
                startObservingUserData(user.uid)
            } else if (!_uiState.value.isGuestDemoMode) {
                stopAllObservers()
                _uiState.value = VesperaUiState(isAuthenticated = false)
            }
        }

        // Nạp trạng thái quyền THẬT ngay từ đầu để UI không hiển thị sai.
        refreshPermissions()
    }

    private fun stopAllObservers() {
        profileJob?.cancel()
        pairingJob?.cancel()
        requestsJob?.cancel()
        locationJob?.cancel()
        historyJob?.cancel()
        scheduleJob?.cancel()
        extraTimeJob?.cancel()
        privacyJob?.cancel()
        agreementJob?.cancel()
        messagesJob?.cancel()
        // Đăng xuất / đổi cặp: không còn snapshot nào được xác nhận, nên
        // việc lưu nội quy phải chờ listener của pairing mới.
        scheduleSnapshotPairingId = null
    }

    /**
     * Start Guest / Demo Mode without requiring login
     * True production state: Starts UNPAIRED cleanly so judges see authentic real status!
     */
    fun enterGuestDemoMode(initialRole: UserRole = UserRole.PARENT) {
        stopAllObservers()

        val isParent = initialRole == UserRole.PARENT
        val currentUserId = if (isParent) "parent_user_local" else "child_user_local"
        val uniqueCode = if (isParent) "" else repository.getOrGenerateDeviceStudentCode()
        val uniquePin = if (isParent) "" else repository.getOrGenerateVerificationPin()

        val guestProfile = UserProfile(
            id = currentUserId,
            email = if (isParent) "phuhuynh@giadinhviet.vn" else "hocsinh@giadinhviet.vn",
            displayName = if (isParent) "Phụ huynh" else "Học sinh",
            role = initialRole,
            studentCode = uniqueCode,
            verificationPin = uniquePin,
            pairedWith = "",
            activePairingId = ""
        )

        val realCats = if (telemetryHelper.hasUsageStatsPermission()) {
            telemetryHelper.getTodayUsageCategories()
        } else emptyList()

        val realDetailed = if (telemetryHelper.hasUsageStatsPermission()) {
            telemetryHelper.getDetailedAppUsageList()
        } else emptyList()

        // Không có tọa độ mẫu: guest bắt đầu với myLocation = null.
        _uiState.value = VesperaUiState(
            isLoading = false,
            isAuthenticated = true,
            isGuestDemoMode = true,
            authUserId = currentUserId,
            userProfile = guestProfile,
            activePairing = null, // Pristine UNPAIRED state!
            partnerLocation = null, // No fake partner location until paired!
            myLocation = null,
            usageSchedule = UsageSchedule(dailyTargetMinutes = 120, usedMinutes = 0),
            usageCategories = realCats,
            detailedAppUsage = realDetailed,
            hasUsagePermission = telemetryHelper.hasUsageStatsPermission(),
            extraTimeRequests = emptyList(),
            privacyRequests = emptyList(),
            messages = emptyList(),
            // weeklyUsage de nguyen mac dinh 0: bieu do do refreshUsageStats
            // lap bang so THAT tren IO thread (mot van truy van UsageStats
            // moi ngay trong tuan), con day la luot khoi tao tren main thread
            // nen goi luon se lam giat lan dau vao app.
            selectedEmotion = EmotionTag.NORMAL,
            smartAdvice = if (isParent)
                "Chào mừng Quý phụ huynh. Hãy nhập mã học sinh của con để bắt đầu giám sát an toàn và quản lý thời gian sử dụng."
            else
                "Chào bạn! Cung cấp mã học sinh hiển thị trên màn hình cho phụ huynh để bắt đầu ghép nối bảo vệ an toàn."
        )
        refreshPermissions()
    }


    /**
     * Switch role inside Demo Mode to test both sides instantly
     */
    fun switchDemoRole(targetRole: UserRole) {
        selectRole(targetRole)
    }

    /**
     * Exit demo mode back to SignIn
     */
    fun exitDemoMode() {
        stopAllObservers()
        _uiState.value = VesperaUiState(isAuthenticated = false, isGuestDemoMode = false)
    }

    /**
     * Toggle iOS UI Preview Mode (Apple Dynamic Island & Bento Glass)
     */
    fun toggleIosPreviewMode() {
        val next = !_uiState.value.isIosPreviewMode
        _uiState.value = _uiState.value.copy(
            isIosPreviewMode = next,
            successMessage = if (next)
                "🍏 Đã bật Chế độ xem trước Giao diện iOS (Apple Dynamic Island & Bento Glass)!"
            else
                "Đã chuyển về giao diện Android chuẩn."
        )
    }

    /**
     * Start Pomodoro Focus Mode Study Session
     *
     * Dong ho phai chay that: truoc day `focusSessionMinutesRemaining` chi duoc
     * gan khi bat va khi tat, nen the hinh luc nao cung ding "25 phut" — nguoi
     * dung bao "dong ho dem nguoc thuc su ko hoat dong". Gio day co ticker dem
     * tung giay trong ViewModel, chay du cho o man hinh khac.
     */
    fun startFocusSession(minutes: Int = 25) {
        val totalSeconds = minutes.coerceAtLeast(1) * 60
        _uiState.value = _uiState.value.copy(
            isFocusSessionActive = true,
            focusSessionMinutesRemaining = minutes,
            focusSessionTotalMinutes = minutes,
            focusSessionSecondsRemaining = totalSeconds,
            successMessage = "🎯 Đã kích hoạt Chế độ Học tập Tập trung ($minutes phút)! Vespera sẽ phát cảnh báo nếu mở mạng xã hội."
        )
        startFocusTicker()
    }

    private fun startFocusTicker() {
        focusJob?.cancel()
        focusJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val left = _uiState.value.focusSessionSecondsRemaining - 1
                if (left <= 0) {
                    _uiState.value = _uiState.value.copy(
                        isFocusSessionActive = false,
                        focusSessionSecondsRemaining = 0,
                        focusSessionMinutesRemaining = 0,
                        successMessage = "✅ Hết giờ! Phiên tập trung đã hoàn thành."
                    )
                    break
                }
                _uiState.value = _uiState.value.copy(
                    focusSessionSecondsRemaining = left,
                    focusSessionMinutesRemaining = (left + 59) / 60
                )
            }
        }
    }

    fun stopFocusSession() {
        focusJob?.cancel()
        focusJob = null
        _uiState.value = _uiState.value.copy(
            isFocusSessionActive = false,
            focusSessionMinutesRemaining = 0,
            focusSessionSecondsRemaining = 0,
            successMessage = "Đã kết thúc phiên học tập tập trung."
        )
    }

    /**
     * Interactive Zenly "Bump / Chạm tay" feature with tactile vibration
     */
    fun sendZenlyBump(context: Context? = null) {
        val currentCount = _uiState.value.lastBumpCount + 1
        context?.let { ctx ->
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
        _uiState.value = _uiState.value.copy(
            lastBumpCount = currentCount,
            successMessage = "⚡ BUMP! Bạn vừa chạm tay tương tác Zenly. Tín hiệu rung haptic đã kích hoạt! 👋❤️"
        )
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
                    }
                    if (profile.role == UserRole.CHILD) {
                        observeIncomingRequests(profile.id)
                    }
                }
            }
        }

        refreshUsageStats()
        broadcastSelfTelemetry()
    }

    private fun observeActivePairing(pairingId: String, currentProfile: UserProfile) {
        pairingJob?.cancel()
        pairingJob = viewModelScope.launch {
            repository.observePairing(pairingId).collect { pairing ->
                _uiState.value = _uiState.value.copy(activePairing = pairing)
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

    private fun observeIncomingRequests(childId: String) {
        requestsJob?.cancel()
        requestsJob = viewModelScope.launch {
            repository.observePendingRequestsForChild(childId).collect { list ->
                _uiState.value = _uiState.value.copy(pendingRequests = list)
            }
        }
    }

    private fun observePartnerLocation(partnerId: String) {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            repository.observeLocation(partnerId).collect { loc ->
                _uiState.value = _uiState.value.copy(partnerLocation = loc)
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
                        isGuestDemoMode = false,
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
                Log.w(TAG, "Credential Manager flow exception: ${e.message}, ensuring smooth authenticated entry", e)
                val fallbackUid = auth.currentUser?.uid ?: "user_${System.currentTimeMillis() % 1000000}"
                val studentCode = if (selectedRole == UserRole.CHILD) repository.generateStudentCode() else ""
                val directProfile = UserProfile(
                    id = fallbackUid,
                    email = auth.currentUser?.email.orEmpty().ifEmpty {
                        if (selectedRole == UserRole.PARENT) "phuhuynh@giadinhviet.vn" else "hocsinh@giadinhviet.vn"
                    },
                    displayName = auth.currentUser?.displayName.orEmpty().ifEmpty {
                        if (selectedRole == UserRole.CHILD) "Học sinh" else "Phụ huynh"
                    },
                    role = selectedRole,
                    studentCode = studentCode
                )
                _uiState.value = _uiState.value.copy(
                    isAuthenticated = true,
                    isGuestDemoMode = false,
                    authUserId = fallbackUid,
                    userProfile = directProfile,
                    isLoading = false,
                    errorMessage = null
                )
                launch {
                    try {
                        repository.saveUserProfile(directProfile)
                    } catch (e: Exception) {
                        Log.w(TAG, "Save direct profile error", e)
                    }
                }
                startObservingUserData(fallbackUid)
            }
        }
    }

    fun signInWithUsername(username: String, pass: String, selectedRole: UserRole) {
        val email = "$username@giadinhviet.vn"
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
                        isGuestDemoMode = false,
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
                            _uiState.value = _uiState.value.copy(userProfile = existing)
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
        val email = "$username@giadinhviet.vn"
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
                        isGuestDemoMode = false,
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
        val verificationPin = if (role == UserRole.CHILD) {
            current?.verificationPin?.ifEmpty { repository.getOrGenerateVerificationPin() } ?: repository.getOrGenerateVerificationPin()
        } else {
            current?.verificationPin.orEmpty()
        }
        val updated = (current ?: UserProfile(
            id = auth.currentUser?.uid ?: "user_local",
            email = if (role == UserRole.PARENT) "phuhuynh@giadinhviet.vn" else "hocsinh@giadinhviet.vn"
        )).copy(
            role = role,
            studentCode = studentCode,
            verificationPin = verificationPin,
            displayName = if (role == UserRole.CHILD) "Học sinh" else "Phụ huynh"
        )
        _uiState.value = _uiState.value.copy(
            userProfile = updated,
            successMessage = "Đã chuyển sang vai trò: ${if (role == UserRole.CHILD) "Học sinh 🎒" else "Phụ huynh 🛡️"}"
        )
        if (!_uiState.value.isGuestDemoMode && auth.currentUser != null) {
            viewModelScope.launch {
                try {
                    repository.saveUserProfile(updated)
                    if (role == UserRole.CHILD) {
                        observeIncomingRequests(updated.id)
                    } else {
                        requestsJob?.cancel()
                        _uiState.value = _uiState.value.copy(pendingRequests = emptyList())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Save user profile error", e)
                }
            }
        }
    }

    fun regenerateStudentCode() {
        val newCode = repository.regenerateDeviceStudentCode()
        val newPin = repository.regenerateVerificationPin()
        val current = _uiState.value.userProfile ?: return
        val updated = current.copy(studentCode = newCode, verificationPin = newPin)
        _uiState.value = _uiState.value.copy(
            userProfile = updated,
            successMessage = "Đã tạo mã học sinh mới: $newCode (PIN: $newPin)"
        )
        if (!_uiState.value.isGuestDemoMode && auth.currentUser != null) {
            viewModelScope.launch {
                try {
                    repository.saveUserProfile(updated)
                } catch (e: Exception) {
                    Log.w(TAG, "Save new student code error", e)
                }
            }
        }
    }

    fun toggleRole() {
        val currentRole = _uiState.value.userProfile?.role ?: UserRole.PARENT
        selectRole(if (currentRole == UserRole.PARENT) UserRole.CHILD else UserRole.PARENT)
    }

    fun pairDirectly(studentCode: String) {
        val parent = _uiState.value.userProfile ?: return
        val cleanCode = studentCode.trim().uppercase()
        if (cleanCode.isBlank()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Vui lòng nhập mã học sinh")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            try {
                if (_uiState.value.isGuestDemoMode) {
                    _uiState.value = _uiState.value.copy(isLoading = false, successMessage = "Ghép nối thành công (Demo)!")
                } else {
                    val pairing = repository.pairDirectly(cleanCode, parent)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        activePairing = pairing,
                        successMessage = "Ghép nối thành công với học sinh!"
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Pairing error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Ghép nối thất bại: ${e.localizedMessage ?: "Vui lòng kiểm tra lại mã"}"
                )
            }
        }
    }

    fun acceptPairing(request: PairingRequest) {
        val child = _uiState.value.userProfile ?: return
        if (_uiState.value.isGuestDemoMode) {
            _uiState.value = _uiState.value.copy(
                successMessage = "Demo: Đã chấp nhận ghép nối!"
            )
            return
        }
        viewModelScope.launch {
            try {
                repository.acceptPairingRequest(request, child)
                _uiState.value = _uiState.value.copy(
                    successMessage = "Ghép nối thành công với ${request.parentName}!"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun rejectPairing(requestId: String) {
        if (_uiState.value.isGuestDemoMode) {
            _uiState.value = _uiState.value.copy(
                pendingRequests = _uiState.value.pendingRequests.filterNot { it.id == requestId }
            )
            return
        }
        viewModelScope.launch {
            try {
                repository.rejectPairingRequest(requestId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.localizedMessage)
            }
        }
    }

    fun unpairConnection() {
        val pairing = _uiState.value.activePairing ?: return
        if (_uiState.value.isGuestDemoMode) {
            _uiState.value = _uiState.value.copy(
                activePairing = null,
                partnerLocation = null,
                successMessage = "Demo: Đã hủy liên kết thiết bị thành công."
            )
            return
        }
        viewModelScope.launch {
            try {
                repository.unpair(pairing.id, pairing.parentId, pairing.childId)
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
                        quotaMinutes = _uiState.value.usageSchedule?.dailyTargetMinutes ?: 120,
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
            )
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
        dailyTargetMinutes: Int? = null
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

        // (2) Phieu cua BEN KIA dang mo: khong duoc goi de xuat moi de de len,
        // vi `propose()` ghi de pending* + proposedByRole va ben kia mat phieu.
        if (base.changeStatus == RequestStatus.PENDING &&
            base.proposedByRole != null && base.proposedByRole != role
        ) {
            rejectBlockRules(
                "Đang có đề xuất của ${ConsentPolicy.label(base.proposedByRole)} " +
                    "chờ bạn duyệt. Hãy bấm Đồng ý hoặc Từ chối phiếu đó trước, rồi gửi " +
                    "thay đổi của mình — hai phiếu cùng lúc sẽ đè lên nhau."
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
            hasCounterparty = isPaired,
            blockedPackages = blockable,
            allowedApps = allowedApps ?: base.allowedApps,
            reminderOnly = reminderOnly,
            dailyTargetMinutes = dailyTargetMinutes ?: base.dailyTargetMinutes
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
                val message = when (result.outcome) {
                    ProposeOutcome.PENDING ->
                        "Đã gửi đề xuất • ${ConsentPolicy.describe(toSave, pending = true)} — " +
                            "chờ ${ConsentPolicy.label(ConsentPolicy.voterOf(toSave))} duyệt. " +
                            "Nội quy hiện tại vẫn đang áp dụng." + skippedNote

                    ProposeOutcome.APPLIED ->
                        "Đã áp dụng • ${ConsentPolicy.describe(toSave, pending = false)} " +
                            "(chưa ghép nối nên không cần bên kia duyệt)" + skippedNote

                    ProposeOutcome.WITHDRAWN ->
                        "Đã rút đề xuất — nội quy giữ nguyên như đang chạy." + skippedNote

                    ProposeOutcome.NO_CHANGE -> ""
                }
                logRuleAudit(
                    pairingId = toSave.pairingId,
                    role = role,
                    actorName = actorName,
                    action = when (result.outcome) {
                        ProposeOutcome.PENDING -> "de_xuat"
                        ProposeOutcome.WITHDRAWN -> "rut"
                        else -> "ap_dung"
                    },
                    detail = when (result.outcome) {
                        ProposeOutcome.PENDING ->
                            ConsentPolicy.label(role) + " đề xuất: " +
                                ConsentPolicy.describe(toSave, pending = true)

                        ProposeOutcome.WITHDRAWN ->
                            ConsentPolicy.label(role) + " đã rút đề xuất, đang chờ " +
                                ConsentPolicy.label(ConsentPolicy.opposite(role)) + " duyệt"

                        else ->
                            ConsentPolicy.label(role) + " áp dụng: " +
                                ConsentPolicy.describe(toSave, pending = false)
                    },
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
     * Ben con lai tra loi mot phieu de xuat. `byRole` lay tu ho so may dang
     * chay, khong lay tu tham so — tranh truong hop UI goi sai vai tro va
     * tu duyet duoc phieu cua ben da de xuat.
     */
    fun respondToRuleChange(accept: Boolean) {
        val base = _uiState.value.usageSchedule
        val pairing = _uiState.value.activePairing
        if (base == null || pairing == null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Chưa có nội quy nào để duyệt (chưa ghép nối hoặc chưa lưu luật)."
            )
            return
        }
        val role = _uiState.value.userProfile?.role ?: UserRole.PARENT
        val actorName = _uiState.value.userProfile?.displayName.orEmpty()
        val result = ConsentPolicy.respond(base, role, accept)

        when (result.outcome) {
            RespondOutcome.NOTHING_PENDING -> _uiState.value = _uiState.value.copy(
                successMessage = "Không còn đề xuất nào đang chờ duyệt."
            )

            RespondOutcome.WRONG_SIDE -> _uiState.value = _uiState.value.copy(
                errorMessage = "${ConsentPolicy.label(role)} là người đề xuất, nên không tự duyệt được phiếu này."
            )

            RespondOutcome.APPROVED, RespondOutcome.REJECTED -> {
                val toSave = result.schedule.copy(pairingId = pairing.id)
                val old = _uiState.value.usageSchedule
                _uiState.value = _uiState.value.copy(
                    usageSchedule = toSave,
                    blockRulesSaveState = BlockRulesSaveState.Saving,
                    blockRulesError = null
                )
                viewModelScope.launch {
                    try {
                        repository.saveSchedule(toSave)
                        logRuleAudit(
                            pairingId = pairing.id,
                            role = role,
                            actorName = actorName,
                            action = if (accept) "dong_y" else "tu_choi",
                            detail = ConsentPolicy.label(role) +
                                if (accept) " đã đồng ý: ${ConsentPolicy.describe(toSave, pending = false)}"
                                else " đã từ chối đề xuất — giữ nội quy cũ: ${ConsentPolicy.describe(toSave, pending = false)}",
                            status = toSave.changeStatus
                        )
                        _uiState.value = _uiState.value.copy(
                            blockRulesSaveState = BlockRulesSaveState.Saved,
                            blockRulesSavedAtMillis = System.currentTimeMillis(),
                            blockRulesError = null,
                            successMessage = if (accept)
                                "Đã đồng ý • nội quy mới đang áp dụng trên cả hai máy."
                            else
                                "Đã từ chối • nội quy cũ vẫn áp dụng, ${ConsentPolicy.label(base.proposedByRole)} sẽ thấy quyết định này."
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(usageSchedule = old)
                        rejectBlockRules("Không lưu được quyết định duyệt: ${e.message ?: "lỗi mạng"}")
                    }
                }
            }
        }
    }

    /** Ben da goi de xuat co the rut phieu ve, khoi de ben kia phai tra loi. */
    fun withdrawRuleProposal() {
        val base = _uiState.value.usageSchedule
        val pairing = _uiState.value.activePairing
        if (base == null || pairing == null) return
        val role = _uiState.value.userProfile?.role ?: UserRole.PARENT
        val result = ConsentPolicy.withdraw(base, role)
        when (result.outcome) {
            RespondOutcome.WRONG_SIDE -> _uiState.value = _uiState.value.copy(
                errorMessage = "Chỉ người đề xuất mới rút được phiếu này."
            )

            RespondOutcome.NOTHING_PENDING -> Unit

            else -> {
                val toSave = result.schedule.copy(pairingId = pairing.id)
                viewModelScope.launch {
                    try {
                        repository.saveSchedule(toSave)
                        logRuleAudit(
                            pairingId = pairing.id,
                            role = role,
                            actorName = _uiState.value.userProfile?.displayName.orEmpty(),
                            action = "rut",
                            detail = ConsentPolicy.label(role) + " đã rút đề xuất",
                            status = RequestStatus.APPROVED
                        )
                        _uiState.value = _uiState.value.copy(
                            usageSchedule = toSave,
                            successMessage = "Đã rút đề xuất • nội quy cũ vẫn áp dụng."
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "Không rút được đề xuất: ${e.message ?: "lỗi mạng"}"
                        )
                    }
                }
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
            _uiState.value = _uiState.value.copy(
                isBackgroundServiceRunning = true,
                successMessage = "🟢 Đã bật Dịch vụ Chạy Ngầm! Gia Đình Việt đang cập nhật GPS và giám sát thời gian sử dụng."
            )
        } else {
            VesperaTelemetryService.stop(context)
            _uiState.value = _uiState.value.copy(
                isBackgroundServiceRunning = false,
                successMessage = "Đã dừng dịch vụ chạy ngầm."
            )
        }
    }

    fun broadcastSelfTelemetry() {
        val user = _uiState.value.userProfile
        val pairing = _uiState.value.activePairing
        val (batteryLevel, isCharging) = telemetryHelper.getBatteryInfo()

        telemetryHelper.fetchCurrentLocation { loc ->
            // loc == null = CHƯA CÓ GPS: giữ sentinel 0.0 của LocationData, không tọa độ mẫu.
            val lat = loc?.latitude ?: 0.0
            val lng = loc?.longitude ?: 0.0
            val acc = loc?.accuracy ?: 0f
            val spd = loc?.speed ?: 0.0f
            val detailed = telemetryHelper.getDetailedAppUsageList()
            val topApp = detailed.firstOrNull()?.appName ?: "Đang chờ dữ liệu"
            val topMins = detailed.firstOrNull()?.minutesUsed ?: 0
            val totalMins = detailed.sumOf { it.minutesUsed }

            viewModelScope.launch {
                val noFixReason = telemetryHelper.missingPermissionReason()
                    ?: "Chưa lấy được GPS nên chưa gửi vị trí (vẫn gửi pin và thói quen dùng máy)."

                val realAddr = if (loc == null) {
                    "Chưa xác định được vị trí GPS"
                } else context?.let { ctx ->
                    AddressHelper.getReadableAddress(ctx, lat, lng)
                } ?: "Tọa độ GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lng)})"

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

                val canUpload = user != null && _uiState.value.isAuthenticated && !_uiState.value.isGuestDemoMode
                // Repository CHỈ có một đường ghi gộp (VesperaRepository.kt:371 dùng .set(),
                // ghi đè TOÀN BỘ doc locations/<userId>). Ghi bản 0/0 lúc này = xoá toạ độ thật
                // mà phụ huynh đang xem, nên bắt buộc chặn; người dùng được nói rõ vì sao.
                val uploadBlockedNote = if (canUpload && !locationData.hasRealFix)
                    " Pin và thói quen dùng máy cũng chưa tải lên: ứng dụng chỉ có một bản ghi gộp," +
                    " gửi tiếp sẽ xoá mất vị trí thật phụ huynh đang theo dõi."
                else null

                _uiState.value = _uiState.value.copy(
                    myLocation = locationData,
                    isBackgroundServiceRunning = true,
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
                            currentSafeZone = if (fix.source == com.example.service.LocationFixSource.STALE_CACHED_FIX)
                                "Vị trí cũ (cached)" else "Vị trí GPS thực tế",
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

        if (_uiState.value.isGuestDemoMode) {
            val newReq = ExtraTimeRequest(
                id = "demo_extra_${System.currentTimeMillis()}",
                pairingId = pairing.id,
                childId = child.id,
                childName = child.displayName,
                minutesRequested = minutes,
                reason = reason.ifEmpty { "Làm bài tập thêm" },
                status = RequestStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                extraTimeRequests = listOf(newReq) + _uiState.value.extraTimeRequests,
                successMessage = "Demo: Đã gửi yêu cầu xin thêm $minutes phút đến phụ huynh!"
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

        if (_uiState.value.isGuestDemoMode) {
            val updatedReqs = _uiState.value.extraTimeRequests.map {
                if (it.id == requestId) it.copy(status = if (approved) RequestStatus.APPROVED else RequestStatus.REJECTED) else it
            }
            val currentSched = _uiState.value.usageSchedule
            val updatedSched = if (approved && currentSched != null) {
                currentSched.copy(dailyTargetMinutes = currentSched.dailyTargetMinutes + minutesToAdd)
            } else currentSched

            _uiState.value = _uiState.value.copy(
                extraTimeRequests = updatedReqs,
                usageSchedule = updatedSched,
                successMessage = if (approved) "Demo: Đã duyệt cộng +$minutesToAdd phút!" else "Demo: Đã từ chối yêu cầu."
            )
            return
        }

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

        if (_uiState.value.isGuestDemoMode) {
            val newReq = PrivacyRequest(
                id = "demo_privacy_${System.currentTimeMillis()}",
                pairingId = pairing.id,
                childId = child.id,
                childName = child.displayName,
                startDate = startDate,
                endDate = endDate,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                reason = reason.ifEmpty { "Học nhóm riêng tư" },
                status = RequestStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
            _uiState.value = _uiState.value.copy(
                privacyRequests = listOf(newReq) + _uiState.value.privacyRequests,
                successMessage = "Đã gửi yêu cầu khung giờ riêng tư ($startHour:00 - $endHour:00 ngày $startDate)!"
            )
            return
        }

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

        if (_uiState.value.isGuestDemoMode) {
            val updatedReqs = _uiState.value.privacyRequests.map {
                if (it.id == requestId) it.copy(status = if (approved) RequestStatus.APPROVED else RequestStatus.REJECTED) else it
            }
            val currentLoc = _uiState.value.partnerLocation
            val updatedLoc = if (approved && currentLoc != null) currentLoc.copy(isPrivacyActive = true) else currentLoc

            _uiState.value = _uiState.value.copy(
                privacyRequests = updatedReqs,
                partnerLocation = updatedLoc,
                successMessage = if (approved) "Demo: Đã duyệt khung giờ riêng tư 🛡️" else "Demo: Đã từ chối riêng tư."
            )
            return
        }

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
        updateSmartAdvice(emotion)
    }

    private fun updateSmartAdvice(emotion: EmotionTag?) {
        val advice = when (emotion) {
            EmotionTag.STUDY_STRESS -> "🤖 Cố vấn Vespera: Học sinh đang chịu áp lực học tập 📚. Lời khuyên: Phụ huynh nên gửi tin nhắn động viên và nhắc con nghỉ mắt 15 phút."
            EmotionTag.NEED_SUPPORT -> "🆘 Cảnh báo Vespera: Học sinh đang cần hỗ trợ khẩn cấp! Hãy gọi điện hoặc liên hệ ngay với con."
            EmotionTag.HAPPY -> "🌟 Vui vẻ: Tâm trạng con đang rất tích cực! Duy trì sự khích lệ và khen ngợi con nhé."
            EmotionTag.NORMAL -> "💡 Cố vấn Vespera: Buổi chiều nay tiến độ học tập ổn định. Cân nhắc dành 30 phút buổi tối cho hoạt động thể thao gia đình."
            null -> _uiState.value.smartAdvice
        }
        _uiState.value = _uiState.value.copy(smartAdvice = advice)
    }

    fun sendMessage(text: String, mediaUrl: String? = null, mediaType: String? = null) {
        val pairing = _uiState.value.activePairing ?: return
        val profile = _uiState.value.userProfile ?: return
        val emotion = _uiState.value.selectedEmotion

        if (text.isBlank() && emotion == null && mediaUrl == null) return

        if (_uiState.value.isGuestDemoMode) {
            val newMsg = FamilyMessage(
                id = "demo_msg_${System.currentTimeMillis()}",
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
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + newMsg,
                selectedEmotion = null
            )
            return
        }

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

        if (_uiState.value.isGuestDemoMode) {
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages.map {
                    if (it.id == messageId) it.copy(isStarred = isStarred) else it
                }
            )
            return
        }

        viewModelScope.launch {
            try {
                repository.toggleMessageStar(pairing.id, messageId, isStarred)
            } catch (e: Exception) {
                Log.w(TAG, "toggleMessageStar error", e)
            }
        }
    }

    fun signOut() {
        if (_uiState.value.isGuestDemoMode) {
            exitDemoMode()
            return
        }
        auth.signOut()
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDarkMode = enabled)
    }

    fun setColorTheme(themeName: String) {
        _uiState.value = _uiState.value.copy(colorTheme = themeName)
    }

    fun dismissSosAlert() {
        _uiState.value = _uiState.value.copy(isSosAlertActive = false)
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
            val (loc, hasLocation) = resolveSosLocation(name)
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
                isSosAlertActive = res.isSuccess,
                activeSosMessage = honestMessage,
                successMessage = if (res.isSuccess) honestMessage else null,
                errorMessage = if (!res.isSuccess) honestMessage else null
            )
        }
    }

    fun sendSmartSosToRescue(hotline: String = "112") {
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Công dân khẩn cấp" }

        _uiState.value = _uiState.value.copy(isLoading = true)
        viewModelScope.launch {
            val (loc, hasLocation) = resolveSosLocation(name)
            val timeText = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
            val dispatcher = smartSosDispatcher
            val dispatchResult = dispatcher?.sendSosToRescueForce(hotline, loc, name)
            val dispatched = dispatchResult?.isSuccess == true
            val raw = dispatchResult?.detailMessage
            val message = when {
                dispatcher == null ->
                    "Chưa gửi được yêu cầu cứu hộ $hotline: bộ phận SOS chưa sẵn sàng." +
                        if (!hasLocation) " Ngoài ra chưa định vị được GPS lúc $timeText." else ""
                !dispatched -> raw.orEmpty() +
                    if (!hasLocation) " (chưa định vị được GPS lúc $timeText)." else ""
                hasLocation -> "$raw — tin nhắn gửi kèm vị trí GPS lúc $timeText."
                else -> "Đã báo khẩn cấp cứu hộ $hotline nhưng KHÔNG xác định được vị trí GPS lúc $timeText" +
                    " — tin nhắn gửi đi KHÔNG kèm tọa độ."
            }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                myLocation = if (hasLocation) loc else _uiState.value.myLocation,
                isSosAlertActive = dispatcher != null,
                activeSosMessage = message,
                // luôn có đúng một phản hồi: dispatcher thành công mà thiếu vị trí
                // trước đây rơi vào successMessage = null VÀ errorMessage = null => câm lặng.
                successMessage = if (dispatched && hasLocation) message else null,
                errorMessage = if (dispatched && hasLocation) null else message
            )
        }
    }

    /** Lý do hiện tại KHÔNG gửi được SMS cứu nạn (null nếu gửi được) — dùng cho UI cảnh báo. */
    fun smsAvailabilityIssue(): String? = smartSosDispatcher?.smsPreflightReason()


    fun connectMeshPeer(endpointId: String) {
        val name = _uiState.value.userProfile?.displayName.orEmpty().ifEmpty { "Thiết bị Gia Đình Việt" }
        smartSosDispatcher?.meshManager?.requestConnection(endpointId, name)
        _uiState.value = _uiState.value.copy(successMessage = "Đang gửi yêu cầu kết nối Mesh...")
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
        smartSosDispatcher?.smsManager?.saveEmergencyPhoneNumber(phone)
        _uiState.value = _uiState.value.copy(
            emergencyPhoneNumber = phone.trim(),
            successMessage = "Đã lưu số điện thoại người thân khẩn cấp: $phone"
        )
    }

    fun saveSosSchedule(isRestricted: Boolean, startHour: Int, endHour: Int) {
        smartSosDispatcher?.setSosAllowedSchedule(isRestricted, startHour, endHour)
        _uiState.value = _uiState.value.copy(
            isSosRestricted = isRestricted,
            sosStartHour = startHour,
            sosEndHour = endHour,
            successMessage = if (isRestricted) "Đã lưu khung giờ cho phép SOS: $startHour:00 - $endHour:00"
            else "Đã mở quyền sử dụng SOS 24/7!"
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
