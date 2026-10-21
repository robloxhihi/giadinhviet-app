package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.DailyUsageStore
import com.example.service.ai.AiKey
import com.example.service.ai.ChatHistoryStore
import com.example.service.ai.ChatTurn
import com.example.service.ai.GeminiClient
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PremiumTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Premium
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * "Trợ lý gia đình" — man hinh chat RIENG voi Gemini, mo tu the Tong quan.
 *
 * Khong dung `ChatScreen` (tab Chat): cho do noi voi Firestore va phai ghep
 * noi moi chay, con o day chi co may cua ba me va mot loi goi HTTP. Tam
 * so lieu cung cap cho AI lay tu `DailyUsageStore` — so do app TU DO tren
 * may, nen cau tra loi Khong the la mot loi khuyen chung chung ap cho moi
 * nha. Do la ly do nguoi dung yeu cau "khong demo".
 *
 * Khoa Gemini lay theo `AiKey`: nhap tay tren may (uu tien) hoac tiem luc
 * build tu CI secret. Man hinh tu hoi khoa khi chua co, khong gui request
 * vo nghia.
 */
@Composable
fun AiChatScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val history = remember { ChatHistoryStore(context) }
    val usage = remember { DailyUsageStore(context) }
    val scroll = rememberScrollState()

    var turns by remember { mutableStateOf(history.load()) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var key by remember { mutableStateOf(AiKey.effective(context)) }
    var keyDraft by remember { mutableStateOf("") }

    val stats = remember(turns.size, busy) { usage.summaryForAi() }
    val hasKey = AiKey.isUsable(key)

    LaunchedEffect(turns.size, busy) {
        if (turns.isNotEmpty()) scroll.animateScrollTo(scroll.maxValue)
    }

    fun send(question: String) {
        val q = question.trim()
        if (q.isEmpty() || busy || !AiKey.isUsable(key)) return
        val next = turns + ChatTurn("user", q)
        turns = next
        draft = ""
        busy = true
        error = null
        scope.launch {
            val answer = GeminiClient.chat(
                apiKey = key,
                history = next.dropLast(1),
                question = q,
                stats = usage.summaryForAi()
            )
            busy = false
            answer.onSuccess { text ->
                turns = turns + ChatTurn("model", text)
                history.save(turns)
            }.onFailure { t ->
                error = t.message ?: "Không nhận được câu trả lời."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("ai_chat_screen")
    ) {
        PremiumTopBar(
            title = "Trợ lý gia đình",
            subtitle = if (hasKey) "Hỏi về số liệu hôm nay của con" else "Cần nhập khoá Gemini để bắt đầu",
            onBack = onBack,
            trailing = {
                if (turns.isNotEmpty()) {
                    IconButton(onClick = {
                        turns = emptyList()
                        history.clear()
                        error = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Xoá nội dung chat",
                            tint = TextSecondary
                        )
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (!hasKey) {
            GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.amber) {
                Column {
                    SectionHeader(
                        title = "Nhập khoá Gemini",
                        subtitle = "Khoá lưu riêng trên máy này, không đi lên mạng và không nằm trong file cài đặt.",
                        icon = Icons.Default.AutoAwesome,
                        tint = Premium.amber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        placeholder = { Text("Dán khoá AI Studio vào đây", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("ai_key_field"),
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SpaceCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = NeonCyan
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    GradientButton(
                        text = "Lưu khoá",
                        icon = Icons.Default.AutoAwesome,
                        onClick = {
                            if (keyDraft.isNotBlank()) {
                                AiKey.save(context, keyDraft)
                                key = AiKey.effective(context)
                                keyDraft = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── So lieu that app do duoc — nguon cua moi cau tra loi ───────────
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Hôm nay máy đo được",
                    subtitle = "Bảng số liệu gửi kèm mỗi câu hỏi",
                    tint = Premium.cyan
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stats,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Phân tích hôm nay",
                "Xin giải pháp",
                "Nói gì với con?"
            ).forEachIndexed { index, label ->
                val question = when (index) {
                    0 -> "Dựa trên số liệu trên, hôm nay của con thế nào?"
                    1 -> "Hãy đưa giải pháp cụ thể từng bước cho tuần này."
                    else -> "Tôi nên nói gì với con về thời gian dùng máy?"
                }
                GlassButton(
                    text = label,
                    onClick = { send(question) },
                    modifier = Modifier.weight(1f).testTag("ai_suggestion"),
                    tint = Premium.violet,
                    height = 38.dp,
                    fontSize = 11
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Doan chat ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (turns.isEmpty()) {
                    Text(
                        text = "Chào bạn. Tôi xem được số liệu sử dụng máy mà app đang đo trên thiết bị này: " +
                            "số phút hôm nay, hạn mức, số lần bộ chặn giữ lại và những ứng dụng dùng nhiều nhất. " +
                            "Hỏi tôi điều gì đó, hoặc bấm nút bên dưới.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                turns.forEach { turn ->
                    val fromModel = turn.role == "model"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (fromModel) Arrangement.Start else Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                // CA hai bong deu OPAQUE. nen trong suot de len
                                // anh trang dem se tao cam giac "chong nen" ma
                                // nguoi dung da phe phan o cac ban truoc.
                                .background(
                                    color = if (fromModel) Premium.opaqueSurface else Premium.primary,
                                    shape = RoundedCornerShape(
                                        topStart = if (fromModel) 6.dp else 20.dp,
                                        topEnd = if (fromModel) 20.dp else 6.dp,
                                        bottomStart = 20.dp,
                                        bottomEnd = 20.dp
                                    )
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = turn.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fromModel) TextPrimary else Color.White,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                if (busy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp).width(16.dp),
                            strokeWidth = 2.dp,
                            color = Premium.cyan
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Trợ lý đang đọc số liệu…", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                }
            }
        }

        error?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.rose, contentPadding = 12.dp) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        GradientButton(
            text = "Xin AI phân tích hôm nay",
            icon = Icons.Default.AutoAwesome,
            enabled = hasKey && !busy,
            onClick = { send("Dựa trên số liệu trên, hãy phân tích hôm nay của con và đưa giải pháp.") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = { Text("Nhập câu hỏi…", color = TextMuted, fontSize = 14.sp) },
                modifier = Modifier.weight(1f).testTag("ai_input_field"),
                shape = RoundedCornerShape(20.dp),
                maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = SpaceCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = NeonCyan
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { send(draft) },
                enabled = hasKey && !busy && draft.isNotBlank(),
                modifier = Modifier
                    .height(48.dp)
                    .width(48.dp)
                    .background(
                        color = if (hasKey && !busy && draft.isNotBlank()) Premium.primaryBright else SpaceCardBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("ai_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Gửi câu hỏi",
                    tint = TextPrimary
                )
            }
        }
    }
}
