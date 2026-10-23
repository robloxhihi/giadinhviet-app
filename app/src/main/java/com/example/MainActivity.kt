package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.model.UserRole
import com.example.data.repository.VesperaRepository
import com.example.service.PermissionManager
import com.example.service.TelemetryAndUsageHelper
import com.example.ui.screens.MainAppScreen
import com.example.ui.screens.SignInScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.VesperaViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private lateinit var telemetryHelper: TelemetryAndUsageHelper
    private lateinit var repository: VesperaRepository

    private val viewModel: VesperaViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VesperaViewModel(repository, telemetryHelper, applicationContext) as T
            }
        }
    }

    // Launcher xin quyen runtime (dung cho ca luong onboarding, "Cap tat ca" lan
    // Trung tam quyen). Dang ky o cap activity de openSpecialPermission cung goi duoc.
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Phan biet "tu choi" voi "tu choi vinh vien" (Don't ask again) — chi Android
        // moi tra loi duoc dieu nay, qua shouldShowRequestPermissionRationale.
        PermissionManager.recordRequestOutcome(results) { shouldShowRequestPermissionRationale(it) }
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            com.example.service.VesperaTelemetryService.start(applicationContext)
            viewModel.broadcastSelfTelemetry()
        }
        // Đọc lại trạng thái THẬT ngay sau khi người dùng trả lời hộp thoại hệ thống.
        viewModel.refreshPermissions()
        viewModel.refreshUsageStats()
    }

    // ACCESS_BACKGROUND_LOCATION phai xin RIENG, chi sau khi foreground location
    // da duoc chap thuan (Android 10+); xin chung lo voi foreground bi fail tham.
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionManager.recordRequestOutcome(
            mapOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION to granted)
        ) { shouldShowRequestPermissionRationale(it) }
        if (granted) {
            com.example.service.VesperaTelemetryService.start(applicationContext)
            viewModel.broadcastSelfTelemetry()
        }
        viewModel.refreshPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        telemetryHelper = TelemetryAndUsageHelper(applicationContext)
        repository = VesperaRepository(applicationContext)

        // OsmDroid bat buoc co User-Agent; chuoi nay da do voi nen Google (200) va
        // Esri (200), con OSM thi bi chan tu phía host.
        try {
            org.osmdroid.config.Configuration.getInstance().load(
                applicationContext,
                android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext)
            )
            org.osmdroid.config.Configuration.getInstance().userAgentValue = "VesperaFamilySafe/${packageName}"
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "OsmDroid init error", e)
        }

        if (intent?.getBooleanExtra("TRIGGER_SOS", false) == true || intent?.action == "ACTION_SOS") {
            viewModel.sendSosAlert()
            // Không hứa trước kết quả — chỉ báo đang phát tín hiệu, kết quả thật hiện ở hộp thoại SOS.
            Toast.makeText(this, "🚨 Đang phát tín hiệu SOS khẩn cấp…", Toast.LENGTH_LONG).show()
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            MyApplicationTheme(
                darkTheme = uiState.isDarkMode
            ) {
                val context = LocalContext.current
                var selectedRoleForSignUp by remember { mutableStateOf(UserRole.PARENT) }

                // Xin tất cả quyền runtime còn thiếu ở đúng thời điểm người dùng vào app.
                // ACCESS_BACKGROUND_LOCATION được cố tình giữ NGOÀI lô này: xin chung
                // hộp thoại với foreground location sẽ fail thầm trên API 29+.
                LaunchedEffect(Unit) {
                    val missing = PermissionManager.missingRuntimePermissions(applicationContext)
                    if (missing.isNotEmpty()) permissionsLauncher.launch(missing)
                    if (telemetryHelper.hasLocationPermission()) {
                        com.example.service.VesperaTelemetryService.start(applicationContext)
                    }
                    viewModel.refreshPermissions()
                }

                // Đồng bộ realtime khi app đang hiển thị: quyền, giờ dùng, vị trí — không cần mở lại app
                val lifecycleOwner = LocalLifecycleOwner.current
                LaunchedEffect(lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        var tick = 0
                        while (true) {
                            viewModel.refreshPermissions()
                            viewModel.refreshUsageStats()
                            if (tick % 3 == 0) viewModel.broadcastSelfTelemetry()
                            tick++
                            delay(5_000L)
                        }
                    }
                }

                if (!uiState.isAuthenticated) {
                    SignInScreen(
                        isLoading = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        onSignInClick = {
                            viewModel.signInWithGoogle(this@MainActivity, selectedRoleForSignUp)
                        },
                        onSignIn = { username, pass ->
                            viewModel.signInWithUsername(username, pass, selectedRoleForSignUp)
                        },
                        onSignUp = { username, pass ->
                            viewModel.signUpWithUsername(username, pass, selectedRoleForSignUp)
                        },
                        selectedRole = selectedRoleForSignUp,
                        onRoleSelected = { role ->
                            selectedRoleForSignUp = role
                        }
                    )
                } else {
                    MainAppScreen(
                        viewModel = viewModel,
                        uiState = uiState,
                        onOpenUsageSettings = {
                            try {
                                startActivity(telemetryHelper.getUsageAccessSettingsIntent())
                            } catch (e: Exception) {
                                Toast.makeText(context, "Không thể mở cài đặt: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRequestRuntimePermissions = { perms -> permissionsLauncher.launch(perms) },
                        onOpenSpecialPermission = { item -> openSpecialPermission(item) },
                        onRecheckPermissions = {
                            viewModel.refreshPermissions()
                            viewModel.refreshUsageStats()
                        }
                    )
                }
            }
        }
    }

    private fun openSpecialPermission(item: PermissionManager.PermissionItem) {
        // Quyen runtime bi "Don't ask again": hop thoai he thong se khong hien nua,
        // loi duy nhat con lai la man hinh Thong tin ung dung cua chinh app.
        if (item.permanentlyDenied) {
            try {
                startActivity(PermissionManager.appDetailsIntent(this))
            } catch (e: Exception) {
                Toast.makeText(this, "Không thể mở cài đặt quyền: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            return
        }
        // Vi tri nen: tu Android 10 chi cap duoc qua hop thoai runtime, va chi sau khi
        // foreground location da duoc chap thuan. Mo trang "Thong tin ung dung" la
        // ngua tro tren nhieu OEM — khong di duong do nua.
        if (item.id == "background_location") {
            requestBackgroundLocation()
            return
        }
        val intent = when (item.id) {
            "usage" -> PermissionManager.usageAccessIntent()
            "overlay" -> PermissionManager.overlayIntent(this)
            "notifications" -> PermissionManager.notificationSettingsIntent(this)
            // Android cam code tu bat AccessibilityService — day la huong duy nhat hop le.
            "accessibility" -> PermissionManager.accessibilitySettingsIntent()
            "battery" -> PermissionManager.batteryOptimizationIntent(this)
            else -> null
        }
        try {
            if (intent != null) startActivity(intent) else Toast.makeText(
                this,
                "Quyền này cấp bằng hộp thoại hệ thống khi cần dùng.",
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở cài đặt quyền: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Xin ACCESS_BACKGROUND_LOCATION dung cach: rieng mot hop thoai, chi khi
     * foreground location da duoc cap. Neu chua co foreground thi xin no truoc.
     */
    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return // < 10: khong ton tai quyen nen rieng
        if (PermissionManager.hasBackgroundLocation(this)) return
        if (PermissionManager.hasForegroundLocation(this)) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            Toast.makeText(
                this,
                "Cần cấp quyền Vị trí (GPS) trước — Android chỉ cho bật vị trí nền sau đó.",
                Toast.LENGTH_LONG
            ).show()
            permissionsLauncher.launch(PermissionManager.runtimePermissionsFor("location"))
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("TRIGGER_SOS", false) == true || intent.action == "ACTION_SOS") {
            viewModel.sendSosAlert()
            Toast.makeText(this, "🚨 Đang phát tín hiệu SOS khẩn cấp…", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::telemetryHelper.isInitialized) {
            // Đọc lại trạng thái quyền + dữ liệu thật ngay khi quay về app
            viewModel.refreshPermissions()
            viewModel.refreshUsageStats()
            viewModel.broadcastSelfTelemetry()
        }
    }
}
