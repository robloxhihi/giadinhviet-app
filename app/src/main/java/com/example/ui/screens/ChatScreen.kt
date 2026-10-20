package com.example.ui.screens

import android.Manifest
import android.media.MediaRecorder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Videocam
import com.example.ui.call.CallActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.EmotionTag
import com.example.data.model.FamilyMessage
import com.example.data.model.UserRole
import com.example.ui.components.EmotionSelectorRow
import com.example.ui.theme.NeonAmber
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonEmerald
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.SpaceCardSurface
import com.example.ui.theme.SpaceDarkBackground
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.VesperaUiState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    uiState: VesperaUiState,
    onSendMessage: (String, String?, String?) -> Unit,
    onToggleStar: (String, Boolean) -> Unit,
    onSelectEmotion: (EmotionTag) -> Unit,
    onConnectMeshPeer: (String) -> Unit = {},
    onSendMeshMessage: (String, String) -> Unit = { _, _ -> },
    onSendFallbackSms: (String) -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var filterStarredOnly by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Audio recording states
    var isRecording by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedAudioFile by remember { mutableStateOf<File?>(null) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSendMessage("🖼️ [Hình ảnh đính kèm]", uri.toString(), "image")
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            onSendMessage("📎 [Tệp đính kèm]", uri.toString(), "file")
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try {
                val audioFile = File(context.cacheDir, "audio_${System.currentTimeMillis()}.3gp")
                val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                    setOutputFile(audioFile.absolutePath)
                    prepare()
                    start()
                }
                mediaRecorder = recorder
                recordedAudioFile = audioFile
                isRecording = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecordingAndSend() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        isRecording = false
        recordedAudioFile?.let { file ->
            if (file.exists()) {
                onSendMessage("🎤 [Tin nhắn thoại]", Uri.fromFile(file).toString(), "audio")
            }
        }
        recordedAudioFile = null
    }

    val currentUserId = uiState.userProfile?.id.orEmpty()
    val partnerName = if (uiState.userProfile?.role == UserRole.CHILD) {
        uiState.activePairing?.parentName.orEmpty().ifEmpty { "Phụ huynh" }
    } else {
        uiState.activePairing?.childName.orEmpty().ifEmpty { "Học sinh" }
    }

    val displayMessages = if (filterStarredOnly) {
        uiState.messages.filter { it.isStarred }
    } else {
        uiState.messages
    }

    LaunchedEffect(displayMessages.size) {
        if (displayMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayMessages.size - 1)
        }
    }

    val quickPresets = listOf(
        "Con đã về đến nhà an toàn!",
        "Bố mẹ sắp qua đón con nhé",
        "Con xin phép học bài thêm 30 phút",
        "Nhớ uống nước và nghỉ ngơi nhé ❤️",
        "Con đang ở trường học"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            // Hoa lan + cua so kinh cua shell (PremiumBackground) da nam sau
            // tab; mot nen opacity 255 o day se an het nen theo thiet ke mau.
            .background(Color.Transparent)
    ) {
        // Chat Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)) {
                Text(
                    text = "GIA ĐÌNH: $partnerName",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (uiState.isOnline) NeonEmerald else NeonAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isOnline) "Đang hoạt động • Cloud Firestore"
                        else "Ngoại tuyến (P2P Mesh / SMS)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (uiState.isOnline) NeonEmerald else NeonAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Facebook Messenger Uniform Circular Action Buttons (No collision, exactly 38dp each!)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Voice Call Button (Facebook Messenger Call Style)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SpaceCardElevated)
                        .border(1.dp, SpaceCardBorder, CircleShape)
                        .clickable {
                            val roomId = uiState.activePairing?.id.orEmpty().ifEmpty { "room_${uiState.userProfile?.id}" }
                            CallActivity.startCall(
                                context = context,
                                roomId = roomId,
                                targetName = partnerName,
                                isCaller = true,
                                isVideo = false,
                                isSos = false
                            )
                        }
                        .testTag("voice_call_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Gọi thoại",
                        tint = NeonEmerald,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // 2. Video Call Button (Facebook Messenger Video Style)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(SpaceCardElevated)
                        .border(1.dp, SpaceCardBorder, CircleShape)
                        .clickable {
                            val roomId = uiState.activePairing?.id.orEmpty().ifEmpty { "room_${uiState.userProfile?.id}" }
                            CallActivity.startCall(
                                context = context,
                                roomId = roomId,
                                targetName = partnerName,
                                isCaller = true,
                                isVideo = true,
                                isSos = false
                            )
                        }
                        .testTag("video_call_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Gọi video",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Star Filter Button (Uniform 38dp Circle!)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (filterStarredOnly) NeonAmber.copy(alpha = 0.2f) else SpaceCardElevated)
                        .border(1.dp, if (filterStarredOnly) NeonAmber else SpaceCardBorder, CircleShape)
                        .clickable { filterStarredOnly = !filterStarredOnly },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (filterStarredOnly) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Lọc tin quan trọng",
                        tint = if (filterStarredOnly) NeonAmber else TextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // BĂNG THÔNG BÁO MẠNG & KÊNH NGOẠI TUYẾN THÔNG MINH
        if (!uiState.isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeonAmber.copy(alpha = 0.12f))
                    .border(1.dp, NeonAmber.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonAmber)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MẤT MẠNG INTERNET • ĐÃ TỰ ĐỘNG BẬT QUÉT MESH P2P (< 50m)",
                            style = MaterialTheme.typography.labelSmall,
                            color = NeonAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (uiState.discoveredMeshPeers.isNotEmpty()) {
                        Text(
                            text = "Tìm thấy ${uiState.discoveredMeshPeers.size} thiết bị trong bán kính 50m:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.discoveredMeshPeers.forEach { peer ->
                                val isConnected = uiState.connectedMeshPeers.any { it.endpointId == peer.endpointId }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isConnected) NeonEmerald.copy(alpha = 0.2f) else SpaceCardElevated)
                                        .border(1.dp, if (isConnected) NeonEmerald else SpaceCardBorder, RoundedCornerShape(10.dp))
                                        .clickable { if (!isConnected) onConnectMeshPeer(peer.endpointId) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "📱 ${peer.deviceName}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isConnected) "Đã nối" else "Kết nối",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isConnected) NeonEmerald else NeonCyan,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Không có thiết bị Mesh lân cận. Hệ thống sẽ tự động chuyển Fallback SMS khi gửi tin khẩn cấp!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Quick Presets Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickPresets.forEach { preset ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpaceCardElevated)
                        .border(1.dp, SpaceCardBorder, RoundedCornerShape(16.dp))
                        .clickable { onSendMessage(preset, null, null) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = preset,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (displayMessages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (filterStarredOnly) "Chưa có tin nhắn nào được lưu sao." else "Bắt đầu cuộc trò chuyện gia đình!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }

            items(displayMessages, key = { it.id }) { msg ->
                val isMe = msg.senderId == currentUserId
                MessageBubble(
                    message = msg,
                    isMe = isMe,
                    onToggleStar = { onToggleStar(msg.id, !msg.isStarred) }
                )
            }
        }

        // Emotion selector bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            EmotionSelectorRow(
                selectedEmotion = uiState.selectedEmotion,
                onSelectEmotion = onSelectEmotion
            )
        }

        // Recording indicator banner if recording
        if (isRecording) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(NeonAmber.copy(alpha = 0.2f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🔴 Đang ghi âm giọng nói...",
                    color = NeonAmber,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SpaceCardElevated)
                            .clickable {
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                } catch (_: Exception) {}
                                mediaRecorder = null
                                isRecording = false
                                recordedAudioFile = null
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Hủy", color = TextSecondary, fontSize = 12.sp)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(NeonCyan)
                            .clickable { stopRecordingAndSend() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Gửi", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // Input Area with Image, File, Voice Record buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SpaceCardSurface)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image Upload Button
            IconButton(
                onClick = {
                    imagePickerLauncher.launch("image/*")
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text("🖼️", fontSize = 18.sp)
            }

            // File Upload Button
            IconButton(
                onClick = { filePickerLauncher.launch("*/*") },
                modifier = Modifier.size(36.dp)
            ) {
                Text("📎", fontSize = 18.sp)
            }

            // Voice Record Button
            IconButton(
                onClick = {
                    if (isRecording) {
                        stopRecordingAndSend()
                    } else {
                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Text(if (isRecording) "⏹️" else "🎤", fontSize = 18.sp)
            }

            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                placeholder = { Text("Nhập tin nhắn...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SpaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeonCyan
                ),
                maxLines = 3,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSend = {
                    if (messageText.isNotBlank() || uiState.selectedEmotion != null) {
                        onSendMessage(messageText, null, null)
                        messageText = ""
                    }
                })
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (messageText.isNotBlank() || uiState.selectedEmotion != null) {
                        onSendMessage(messageText, null, null)
                        messageText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NeonCyan)
                    .testTag("send_chat_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Gửi",
                    tint = Color.Black
                )
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: FamilyMessage,
    isMe: Boolean,
    onToggleStar: () -> Unit
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = timeFormat.format(Date(message.createdAt))

    val emotionEmoji = when (message.emotion) {
        EmotionTag.HAPPY.name -> "😊"
        EmotionTag.NORMAL.name -> "😐"
        EmotionTag.STUDY_STRESS.name -> "📚"
        EmotionTag.NEED_SUPPORT.name -> "🆘"
        else -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMe) 16.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 16.dp
                    )
                )
                .background(if (isMe) NeonViolet.copy(alpha = 0.35f) else SpaceCardElevated)
                .border(
                    1.dp,
                    if (isMe) NeonViolet.copy(alpha = 0.6f) else SpaceCardBorder,
                    RoundedCornerShape(16.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isMe) "Bạn" else message.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) NeonCyan else NeonViolet,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(
                        onClick = onToggleStar,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (message.isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Star message",
                            tint = if (message.isStarred) NeonAmber else TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (emotionEmoji != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "Tâm trạng: $emotionEmoji", fontSize = 16.sp)
                }

                // Render Media Attachment if present
                if (!message.mediaUrl.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    when (message.mediaType) {
                        "image" -> {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Hình ảnh đính kèm",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        "audio" -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceCardSurface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("▶️", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Tin nhắn thoại", style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.Bold)
                                    Text("Phát âm thanh giọng nói", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                }
                            }
                        }
                        else -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpaceCardSurface)
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📎", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tệp đính kèm tài liệu", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            }
                        }
                    }
                }

                if (message.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
