package com.example.service.socket

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Socket.IO client manager for instant real-time chat and pairing telemetry
 * when internet/network is available.
 */
object SocketManager {
    private const val TAG = "SocketManager"
    // Default demo socket server (supports fallback and custom endpoints)
    private const val DEFAULT_SOCKET_URL = "https://vespera-chat.glitch.me"

    private var socket: Socket? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<JSONObject>(extraBufferCapacity = 50)
    val incomingMessages: SharedFlow<JSONObject> = _incomingMessages.asSharedFlow()

    private var currentRoom: String? = null

    /**
     * Connect to Socket.IO server
     */
    fun connect(customUrl: String? = null) {
        if (socket?.connected() == true) return

        val url = customUrl ?: DEFAULT_SOCKET_URL
        try {
            val options = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = 10
                reconnectionDelay = 1500
                timeout = 10000
                forceNew = true
            }

            socket = IO.socket(url, options).apply {
                on(Socket.EVENT_CONNECT) {
                    Log.i(TAG, "Socket.IO connected to $url")
                    _isConnected.value = true
                    currentRoom?.let { joinRoom(it) }
                }

                on(Socket.EVENT_DISCONNECT) {
                    Log.i(TAG, "Socket.IO disconnected")
                    _isConnected.value = false
                }

                on(Socket.EVENT_CONNECT_ERROR) { args ->
                    val err = args.firstOrNull()
                    Log.d(TAG, "Socket.IO connection status: $err (Offline/Mesh fallback active)")
                    _isConnected.value = false
                }

                on("chat_message") { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        Log.d(TAG, "Received Socket.IO chat_message: $data")
                        scope.launch {
                            _incomingMessages.emit(data)
                        }
                    }
                }

                on("new_message") { args ->
                    val data = args.firstOrNull() as? JSONObject
                    if (data != null) {
                        Log.d(TAG, "Received Socket.IO new_message: $data")
                        scope.launch {
                            _incomingMessages.emit(data)
                        }
                    }
                }
            }

            socket?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Socket.IO", e)
            _isConnected.value = false
        }
    }

    /**
     * Join room dedicated to this pairing or student code
     */
    fun joinRoom(roomId: String) {
        currentRoom = roomId
        if (socket?.connected() == true) {
            val json = JSONObject().apply {
                put("room", roomId)
            }
            socket?.emit("join_room", json)
            Log.d(TAG, "Joined Socket.IO room: $roomId")
        }
    }

    /**
     * Emit chat message via Socket.IO for instant real-time delivery
     */
    fun sendChatMessage(
        pairingId: String,
        senderId: String,
        senderName: String,
        text: String,
        emotionTag: String = "HAPPY",
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val payload = JSONObject().apply {
                put("id", "msg_${System.currentTimeMillis()}")
                put("pairingId", pairingId)
                put("senderId", senderId)
                put("senderName", senderName)
                put("text", text)
                put("emotionTag", emotionTag)
                put("timestamp", timestamp)
                put("room", currentRoom ?: pairingId)
            }

            if (socket?.connected() == true) {
                socket?.emit("chat_message", payload)
                Log.d(TAG, "Emitted Socket.IO message: $text")
                true
            } else {
                Log.d(TAG, "Socket not connected, message will sync via Cloud/Mesh")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Socket.IO message", e)
            false
        }
    }

    /**
     * Disconnect socket cleanly
     */
    fun disconnect() {
        try {
            socket?.disconnect()
            socket?.off()
            socket = null
            _isConnected.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting socket", e)
        }
    }
}
