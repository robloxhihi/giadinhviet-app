package com.example.service.sos

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets

private const val TAG = "OfflineMeshManager"
private const val SERVICE_ID = "com.example.vespera.mesh"

data class MeshPeer(
    val endpointId: String,
    val deviceName: String,
    val isConnected: Boolean = false,
    val distanceEstimate: String = "< 50m"
)

data class MeshMessage(
    val id: String = "${System.currentTimeMillis()}",
    val senderEndpointId: String,
    val senderName: String,
    val content: String,
    val isSos: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Quản lý mạng cứu hộ P2P Mesh ngoại tuyến sử dụng Google Nearby Connections API.
 * Hoạt động hoàn toàn không cần Internet / 4G trong bán kính ~50m qua Wi-Fi Direct & Bluetooth.
 */
class OfflineMeshManager(private val context: Context) {

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<MeshPeer>> = _discoveredPeers.asStateFlow()

    private val _connectedPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    val connectedPeers: StateFlow<List<MeshPeer>> = _connectedPeers.asStateFlow()

    private val _incomingMessages = MutableStateFlow<List<MeshMessage>>(emptyList())
    val incomingMessages: StateFlow<List<MeshMessage>> = _incomingMessages.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val rawText = String(bytes, StandardCharsets.UTF_8)
                Log.d(TAG, "Nhận được gói tin Mesh từ $endpointId: $rawText")

                val senderName = _connectedPeers.value.find { it.endpointId == endpointId }?.deviceName
                    ?: _discoveredPeers.value.find { it.endpointId == endpointId }?.deviceName
                    ?: "Thiết bị gần ($endpointId)"

                val isSos = rawText.startsWith("SOS:")
                val displayContent = if (isSos) rawText.removePrefix("SOS:") else rawText

                val msg = MeshMessage(
                    senderEndpointId = endpointId,
                    senderName = senderName,
                    content = displayContent,
                    isSos = isSos
                )
                _incomingMessages.value = _incomingMessages.value + msg
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Theo dõi tiến trình truyền tải nếu cần
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Phát hiện yêu cầu kết nối Mesh từ: ${connectionInfo.endpointName} ($endpointId)")
            // Tự động chấp nhận kết nối trong mạng cứu hộ khẩn cấp
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Kết nối Mesh thành công tới: $endpointId")
                val existing = _discoveredPeers.value.find { it.endpointId == endpointId }
                val peer = existing?.copy(isConnected = true) ?: MeshPeer(
                    endpointId = endpointId,
                    deviceName = "Thiết bị lân cận",
                    isConnected = true
                )
                _connectedPeers.value = (_connectedPeers.value.filter { it.endpointId != endpointId } + peer)
            } else {
                Log.w(TAG, "Kết nối thất bại tới: $endpointId (${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Mất kết nối với thiết bị: $endpointId")
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Tìm thấy thiết bị trong bán kính 50m: ${info.endpointName} ($endpointId)")
            val newPeer = MeshPeer(
                endpointId = endpointId,
                deviceName = info.endpointName.ifEmpty { "Thiết bị ẩn danh" },
                isConnected = false
            )
            val current = _discoveredPeers.value.toMutableList()
            if (current.none { it.endpointId == endpointId }) {
                current.add(newPeer)
                _discoveredPeers.value = current
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Thiết bị đã rời vùng 50m: $endpointId")
            _discoveredPeers.value = _discoveredPeers.value.filter { it.endpointId != endpointId }
            _connectedPeers.value = _connectedPeers.value.filter { it.endpointId != endpointId }
        }
    }

    /**
     * Bắt đầu phát sóng nhận diện thiết bị (Advertising)
     */
    fun startAdvertising(localDeviceName: String) {
        val options = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            localDeviceName,
            SERVICE_ID,
            connectionLifecycleCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Bắt đầu phát sóng Mesh thành công: $localDeviceName")
            _isAdvertising.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Lỗi khi phát sóng Mesh: ${e.message}")
            _isAdvertising.value = false
        }
    }

    /**
     * Bắt đầu quét các thiết bị xung quanh trong bán kính 50m (Discovery)
     */
    fun startDiscovery() {
        val options = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            options
        ).addOnSuccessListener {
            Log.d(TAG, "Bắt đầu quét thiết bị Mesh trong bán kính 50m")
            _isDiscovering.value = true
        }.addOnFailureListener { e ->
            Log.e(TAG, "Lỗi khi quét thiết bị Mesh: ${e.message}")
            _isDiscovering.value = false
        }
    }

    /**
     * Gửi yêu cầu kết nối tới một thiết bị cụ thể
     */
    fun requestConnection(endpointId: String, localName: String) {
        connectionsClient.requestConnection(
            localName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Đã gửi yêu cầu kết nối tới $endpointId")
        }.addOnFailureListener { e ->
            Log.w(TAG, "Gửi yêu cầu kết nối thất bại: ${e.message}")
        }
    }

    /**
     * Gửi tin nhắn P2P qua Mesh ngoại tuyến.
     * Chỉ trả về true khi thiết bị đích THỰC SỰ đang kết nối — tránh báo "đã gửi" giả.
     */
    fun sendMessageToPeer(endpointId: String, text: String, senderName: String): Boolean {
        val isConnected = _connectedPeers.value.any { it.endpointId == endpointId }
        if (!isConnected) {
            Log.w(TAG, "Không gửi Mesh: thiết bị $endpointId chưa kết nối.")
            return false
        }
        return try {
            val bytes = text.toByteArray(StandardCharsets.UTF_8)
            val payload = Payload.fromBytes(bytes)
            connectionsClient.sendPayload(endpointId, payload)
                .addOnFailureListener { e ->
                    Log.e(TAG, "sendPayload tới $endpointId thất bại: ${e.message}")
                }
            // Ghi nhận tin nhắn đã gửi
            val localMsg = MeshMessage(
                senderEndpointId = "ME",
                senderName = senderName,
                content = text,
                isSos = false
            )
            _incomingMessages.value = _incomingMessages.value + localMsg
            true
        } catch (e: Exception) {
            Log.e(TAG, "Không thể gửi tin nhắn Mesh: ${e.message}")
            false
        }
    }

    /**
     * Phát tín hiệu cầu cứu SOS ngoại tuyến tới TẤT CẢ các thiết bị kết nối xung quanh (<50m)
     */
    fun broadcastSosBeacon(sosMessage: String, senderName: String): Int {
        val recipients = _connectedPeers.value.map { it.endpointId }
        if (recipients.isEmpty()) {
            Log.d(TAG, "Không có thiết bị Mesh nào đang kết nối để gửi SOS Beacon.")
            return 0
        }
        val bytes = "SOS:$sosMessage".toByteArray(StandardCharsets.UTF_8)
        val payload = Payload.fromBytes(bytes)
        connectionsClient.sendPayload(recipients, payload)

        val localMsg = MeshMessage(
            senderEndpointId = "ME",
            senderName = senderName,
            content = "🆘 $sosMessage",
            isSos = true
        )
        _incomingMessages.value = _incomingMessages.value + localMsg
        Log.d(TAG, "Đã phát sóng SOS Beacon tới ${recipients.size} thiết bị lân cận!")
        return recipients.size
    }

    /**
     * Dừng toàn bộ quét và phát sóng ngoại tuyến khi có mạng trở lại (để tiết kiệm pin)
     */
    fun stopAll() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi dừng Mesh: ${e.message}")
        }
        _isAdvertising.value = false
        _isDiscovering.value = false
        _discoveredPeers.value = emptyList()
        _connectedPeers.value = emptyList()
    }
}
