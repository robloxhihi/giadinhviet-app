package com.example.ui.call

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.service.webrtc.SignalingClient
import com.example.service.webrtc.SignalingEvent
import com.example.service.webrtc.WebRTCManager
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonRose
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.DialogSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer

class CallActivity : ComponentActivity() {

    private lateinit var roomId: String
    private lateinit var targetName: String
    private var isCaller: Boolean = true
    private var isVideo: Boolean = false
    private var isSos: Boolean = false

    private var webRtcManager: WebRTCManager? = null
    private var signalingClient: SignalingClient? = null

    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null

    private var isMicMuted by mutableStateOf(false)
    private var isSpeakerOn by mutableStateOf(false)
    private var callStatusText by mutableStateOf("Đang kết nối...")
    private var isCallConnected by mutableStateOf(false)
    private var hasAnswered by mutableStateOf(false)
    private var incomingOfferSdp: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (audioGranted) {
            setupCall()
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        roomId = intent.getStringExtra(EXTRA_ROOM_ID).orEmpty().ifEmpty { "default_room" }
        targetName = intent.getStringExtra(EXTRA_TARGET_NAME).orEmpty().ifEmpty { "Gia đình" }
        isCaller = intent.getBooleanExtra(EXTRA_IS_CALLER, true)
        isVideo = intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        isSos = intent.getBooleanExtra(EXTRA_IS_SOS, false)

        isSpeakerOn = isVideo // Default speaker for video calls

        setContent {
            CallScreen(
                targetName = targetName,
                isVideo = isVideo,
                isSos = isSos,
                isCaller = isCaller,
                hasAnswered = hasAnswered,
                callStatusText = callStatusText,
                isCallConnected = isCallConnected,
                isMicMuted = isMicMuted,
                isSpeakerOn = isSpeakerOn,
                onAnswerCall = { answerIncomingCall() },
                onEndCall = { endAndFinishCall() },
                onToggleMic = { toggleMic() },
                onToggleSpeaker = { toggleSpeaker() },
                onSwitchCamera = { webRtcManager?.switchCamera() },
                localRendererView = localRenderer,
                remoteRendererView = remoteRenderer
            )
        }

        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (isVideo) {
            permissions.add(Manifest.permission.CAMERA)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            setupCall()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun setupCall() {
        webRtcManager = WebRTCManager(applicationContext)
        signalingClient = SignalingClient(roomId, isCaller)

        if (isVideo) {
            localRenderer = SurfaceViewRenderer(this).apply {
                init(webRtcManager?.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setMirror(true)
                setZOrderMediaOverlay(true)
            }
            remoteRenderer = SurfaceViewRenderer(this).apply {
                init(webRtcManager?.eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
            }
        }

        webRtcManager?.initPeerConnection(
            localRenderer = localRenderer,
            remoteRenderer = remoteRenderer,
            enableVideo = isVideo
        )

        webRtcManager?.onIceCandidateGenerated = { candidate ->
            signalingClient?.sendIceCandidate(candidate)
        }

        webRtcManager?.onConnectionStateChanged = { state ->
            runOnUiThread {
                when (state) {
                    org.webrtc.PeerConnection.PeerConnectionState.CONNECTED -> {
                        isCallConnected = true
                        callStatusText = "Đang trong cuộc gọi"
                    }
                    org.webrtc.PeerConnection.PeerConnectionState.DISCONNECTED,
                    org.webrtc.PeerConnection.PeerConnectionState.FAILED -> {
                        callStatusText = "Cuộc gọi bị gián đoạn"
                    }
                    org.webrtc.PeerConnection.PeerConnectionState.CLOSED -> {
                        endAndFinishCall()
                    }
                    else -> {}
                }
            }
        }

        // Listen to signaling events
        CoroutineScope(Dispatchers.Main).launch {
            signalingClient?.events?.collect { event ->
                when (event) {
                    is SignalingEvent.OfferReceived -> {
                        incomingOfferSdp = event.sdp
                        if (!isCaller && !hasAnswered) {
                            callStatusText = if (event.isSos) "🚨 CUỘC GỌI SOS KHẨN CẤP ĐẾN!" else "Cuộc gọi đến..."
                        }
                    }
                    is SignalingEvent.AnswerReceived -> {
                        webRtcManager?.setRemoteAnswer(event.sdp)
                        isCallConnected = true
                        callStatusText = "Đã kết nối"
                    }
                    is SignalingEvent.IceCandidateReceived -> {
                        webRtcManager?.addIceCandidate(event.candidate)
                    }
                    is SignalingEvent.CallStateChanged -> {
                        if (event.state == "connected") {
                            isCallConnected = true
                            callStatusText = "Đã kết nối"
                        }
                    }
                    is SignalingEvent.CallEnded -> {
                        runOnUiThread {
                            callStatusText = "Cuộc gọi đã kết thúc"
                            Handler(Looper.getMainLooper()).postDelayed({ finish() }, 800)
                        }
                    }
                }
            }
        }

        if (isCaller) {
            callStatusText = if (isSos) "🚨 Đang gọi SOS khẩn cấp..." else "Đang đổ chuông..."
            webRtcManager?.createOffer { offerDesc ->
                signalingClient?.sendOffer(
                    description = offerDesc,
                    isVideo = isVideo,
                    callerName = targetName,
                    isSos = isSos
                )
            }
        } else {
            callStatusText = if (isSos) "🚨 Cuộc gọi SOS khẩn cấp đến" else "Chuông đang reo..."
        }
    }

    private fun answerIncomingCall() {
        val sdp = incomingOfferSdp ?: return
        hasAnswered = true
        callStatusText = "Đang kết nối tín hiệu..."
        webRtcManager?.createAnswer(sdp) { answerDesc ->
            signalingClient?.sendAnswer(answerDesc)
            isCallConnected = true
            callStatusText = "Đã kết nối"
        }
    }

    private fun toggleMic() {
        isMicMuted = !isMicMuted
        webRtcManager?.toggleMicrophone(!isMicMuted)
    }

    private fun toggleSpeaker() {
        isSpeakerOn = !isSpeakerOn
        webRtcManager?.toggleSpeaker(isSpeakerOn)
    }

    private fun endAndFinishCall() {
        signalingClient?.endCall()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        webRtcManager?.cleanup()
        signalingClient?.cleanup()
        localRenderer?.release()
        remoteRenderer?.release()
    }

    companion object {
        const val EXTRA_ROOM_ID = "EXTRA_ROOM_ID"
        const val EXTRA_TARGET_NAME = "EXTRA_TARGET_NAME"
        const val EXTRA_IS_CALLER = "EXTRA_IS_CALLER"
        const val EXTRA_IS_VIDEO = "EXTRA_IS_VIDEO"
        const val EXTRA_IS_SOS = "EXTRA_IS_SOS"

        fun startCall(
            context: Context,
            roomId: String,
            targetName: String,
            isCaller: Boolean,
            isVideo: Boolean,
            isSos: Boolean = false
        ) {
            val intent = Intent(context, CallActivity::class.java).apply {
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_TARGET_NAME, targetName)
                putExtra(EXTRA_IS_CALLER, isCaller)
                putExtra(EXTRA_IS_VIDEO, isVideo)
                putExtra(EXTRA_IS_SOS, isSos)
                if (context !is ComponentActivity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun CallScreen(
    targetName: String,
    isVideo: Boolean,
    isSos: Boolean,
    isCaller: Boolean,
    hasAnswered: Boolean,
    callStatusText: String,
    isCallConnected: Boolean,
    isMicMuted: Boolean,
    isSpeakerOn: Boolean,
    onAnswerCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSwitchCamera: () -> Unit,
    localRendererView: SurfaceViewRenderer?,
    remoteRendererView: SurfaceViewRenderer?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DialogSurface)
    ) {
        if (isVideo) {
            // Remote Video Fullscreen
            if (remoteRendererView != null) {
                AndroidView(
                    factory = {
                        remoteRendererView.apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Local Video Preview (Picture in Picture)
            if (localRendererView != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 16.dp, end = 16.dp)
                        .size(width = 110.dp, height = 160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(2.dp, NeonCyan, RoundedCornerShape(14.dp))
                ) {
                    AndroidView(
                        factory = {
                            localRendererView.apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // Top Info Overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSos) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeonRose.copy(alpha = 0.25f))
                        .border(1.dp, NeonRose, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = NeonRose,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "CUỘC GỌI SOS KHẨN CẤP",
                            color = NeonRose,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!isVideo) {
                // Pulsating Avatar for Audio Call
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(if (!isCallConnected) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(if (isSos) NeonRose else NeonCyan, Color(0xFF1877F2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = targetName.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 42.sp
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            Text(
                text = targetName,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = callStatusText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCallConnected) NeonEmerald else NeonAmber
            )
        }

        // Bottom Controls Overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 36.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute Mic Button
                IconButton(
                    onClick = onToggleMic,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isMicMuted) NeonRose.copy(alpha = 0.3f) else SpaceCardElevated)
                        .border(1.dp, if (isMicMuted) NeonRose else SpaceCardBorder, CircleShape)
                        .testTag("mute_mic_btn")
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = "Microphone",
                        tint = if (isMicMuted) NeonRose else TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Speaker Toggle Button
                IconButton(
                    onClick = onToggleSpeaker,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (isSpeakerOn) NeonCyan.copy(alpha = 0.3f) else SpaceCardElevated)
                        .border(1.dp, if (isSpeakerOn) NeonCyan else SpaceCardBorder, CircleShape)
                        .testTag("speaker_btn")
                ) {
                    Icon(
                        imageVector = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                        contentDescription = "Loa ngoài",
                        tint = if (isSpeakerOn) NeonCyan else TextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Switch Camera Button (if Video)
                if (isVideo) {
                    IconButton(
                        onClick = onSwitchCamera,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SpaceCardElevated)
                            .border(1.dp, SpaceCardBorder, CircleShape)
                            .testTag("switch_camera_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Đổi Camera",
                            tint = NeonCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Answer Button (If incoming and not yet answered)
                if (!isCaller && !hasAnswered) {
                    IconButton(
                        onClick = onAnswerCall,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NeonEmerald)
                            .testTag("answer_call_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Nghe máy",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // End Call Button
                IconButton(
                    onClick = onEndCall,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(NeonRose)
                        .testTag("end_call_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Tắt máy",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
