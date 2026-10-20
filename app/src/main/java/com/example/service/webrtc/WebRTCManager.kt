package com.example.service.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRTCManager(
    private val context: Context,
    val eglBaseContext: EglBase.Context = EglBase.create().eglBaseContext
) {
    private val TAG = "WebRTCManager"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null
        private set

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    var onIceCandidateGenerated: ((IceCandidate) -> Unit)? = null
    var onRemoteStreamAdded: ((MediaStream) -> Unit)? = null
    var onConnectionStateChanged: ((PeerConnection.PeerConnectionState) -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
    }

    fun initPeerConnection(
        localRenderer: SurfaceViewRenderer? = null,
        remoteRenderer: SurfaceViewRenderer? = null,
        enableVideo: Boolean = true
    ) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pcObserver = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { onIceCandidateGenerated?.invoke(it) }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                newState?.let { onConnectionStateChanged?.invoke(it) }
            }

            override fun onAddStream(stream: MediaStream?) {
                stream?.let {
                    if (remoteRenderer != null && it.videoTracks.isNotEmpty()) {
                        it.videoTracks[0].addSink(remoteRenderer)
                    }
                    onRemoteStreamAdded?.invoke(it)
                }
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack && remoteRenderer != null) {
                    track.addSink(remoteRenderer)
                }
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {}
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, pcObserver)

        // Setup Audio Track
        val audioConstraints = MediaConstraints()
        audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("101", audioSource)
        localAudioTrack?.let {
            peerConnection?.addTrack(it, listOf("ARDAMS"))
        }

        // Setup Video Track if requested
        if (enableVideo) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            val capturer = createCameraCapturer()
            if (capturer != null) {
                videoCapturer = capturer
                videoSource = peerConnectionFactory?.createVideoSource(capturer.isScreencast)
                capturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
                capturer.startCapture(640, 480, 30)

                localVideoTrack = peerConnectionFactory?.createVideoTrack("102", videoSource)
                if (localRenderer != null) {
                    localVideoTrack?.addSink(localRenderer)
                }
                localVideoTrack?.let {
                    peerConnection?.addTrack(it, listOf("ARDAMS"))
                }
            }
        }

        // Configure audio for calls
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = enableVideo // Mặc định bật loa ngoài khi gọi video
    }

    private fun createCameraCapturer(): VideoCapturer? {
        val enumerator: CameraEnumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        // Ưu tiên camera trước (front-facing)
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Nếu không có camera trước, dùng camera sau
        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }
        return null
    }

    fun createOffer(callback: (SessionDescription) -> Unit) {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let {
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            callback(it)
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(p0: String?) {
                            Log.e(TAG, "setLocalDescription failure: $p0")
                        }
                    }, it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "createOffer failure: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, sdpConstraints)
    }

    fun createAnswer(remoteOfferSdp: String, callback: (SessionDescription) -> Unit) {
        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, remoteOfferSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                val sdpConstraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                }
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answerDesc: SessionDescription?) {
                        answerDesc?.let {
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    callback(it)
                                }
                                override fun onCreateFailure(p0: String?) {}
                                override fun onSetFailure(p0: String?) {}
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, sdpConstraints)
            }
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteDescription failure: $error")
            }
        }, remoteDesc)
    }

    fun setRemoteAnswer(remoteAnswerSdp: String) {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, remoteAnswerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "setRemoteAnswer success")
            }
            override fun onCreateSuccess(desc: SessionDescription?) {}
            override fun onCreateFailure(error: String?) {}
            override fun onSetFailure(error: String?) {
                Log.e(TAG, "setRemoteAnswer failure: $error")
            }
        }, remoteDesc)
    }

    fun addIceCandidate(candidate: IceCandidate) {
        peerConnection?.addIceCandidate(candidate)
    }

    fun toggleMicrophone(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleCamera(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        val cameraCapturer = videoCapturer as? CameraVideoCapturer
        cameraCapturer?.switchCamera(null)
    }

    fun toggleSpeaker(isSpeakerOn: Boolean) {
        audioManager?.isSpeakerphoneOn = isSpeakerOn
    }

    fun cleanup() {
        try {
            videoCapturer?.stopCapture()
        } catch (e: Exception) {
            Log.d(TAG, "videoCapturer stop error: ${e.message}")
        }
        videoCapturer?.dispose()
        videoCapturer = null

        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null

        localVideoTrack?.dispose()
        localAudioTrack?.dispose()
        videoSource?.dispose()
        audioSource?.dispose()

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        audioManager?.mode = AudioManager.MODE_NORMAL
    }
}
