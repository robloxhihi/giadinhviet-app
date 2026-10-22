package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ai.DailyUsageStore
import com.example.service.ai.AiKey
import com.example.service.ai.AiModel
import com.example.service.ai.AiModels
import com.example.service.ai.ChatHistoryStore
import com.example.service.ai.ChatTurn
import com.example.ui.components.GlassButton
import com.example.ui.components.GlassCard
import com.example.ui.components.GradientButton
import com.example.ui.components.PremiumTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.Premium
import com.example.ui.theme.SpaceCardBorder
import com.example.ui.theme.SpaceCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * "Trợ lý gia đình" — man hinh chat RIENG voi AI, mo tu the Tong quan.
 *
 * Khong dung `ChatScreen` (tab Chat): cho do noi voi Firestore va phai ghep
 * noi moi chay, con o day chi co may cua ba me va mot loi goi HTTP. Tam
 * so lieu cung cap cho AI lay tu `DailyUsageStore` — so do app TU DO tren
 * may, nen cau tra loi Khong the la mot loi khuyen chung chung ap cho moi
 * nha. Do la ly do nguoi dung yeu cau "khong demo".
 *
 * Khoa AI lay theo `AiKey` va theo tung provider: Gemini can khoa AI Studio,
 * ba model DeepSeek/GLM can khoa OpenRouter. Chon model o day quyet luon khoa
 * va client duoc dung; man hinh tu hoi khi chua co, khong gui request vo nghia.
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
    var modelId by remember { mutableStateOf(AiModels.selectedId(context)) }
    val model = AiModels.find(modelId)
    var key by remember(modelId) { mutableStateOf(AiModels.keyFor(context, model)) }
    var keyDraft by remember { mutableStateOf("") }
    var showStats by remember { mutableStateOf(false) }

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
            val answer = AiModels.chat(
                model = model,
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
            subtitle = if (hasKey) "Đang dùng ${model.label} · hỏi về số liệu hôm nay của máy"
            else "Cần nhập ${AiModels.keyLabel(model)} để bắt đầu",
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

        // ── Chon model ─────────────────────────────────────────────────────
        // Moi model mot kieu: Gemini can khoa AI Studio, ba model DeepSeek/GLM
        // goi qua OpenRouter can khoa OpenRouter. Chon o day quyet luon khung
        // nhap khoa ben duoi, de nguoi dung khong phai doan tai sao "luu khoa
        // roi van hoi".
        Text(
            text = "Model trả lời",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("ai_model_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiModels.all.forEach { candidate ->
                val selected = candidate.id == modelId
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) NeonCyan.copy(alpha = 0.18f) else SpaceCardElevated
                        )
                        .border(
                            1.2.dp,
                            if (selected) NeonCyan else SpaceCardBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable(enabled = !busy) {
                            modelId = candidate.id
                            AiModels.saveSelected(context, candidate.id)
                            key = AiModels.keyFor(context, candidate)
                            keyDraft = ""
                            error = null
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = candidate.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) NeonCyan else TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = model.note,
            style = MaterialTheme.typography.bodySmall,
            color = TextMuted
        )
        Spacer(modifier = Modifier.height(14.dp))

        if (!hasKey) {
            GlassCard(modifier = Modifier.fillMaxWidth(), glow = Premium.amber) {
                Column {
                    SectionHeader(
                        title = "Nhập ${AiModels.keyLabel(model)}",
                        subtitle = "Khoá lưu riêng trên máy này, không đi lên mạng và không nằm trong file cài đặt.",
                        icon = Icons.Default.AutoAwesome,
                        tint = Premium.amber
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it },
                        placeholder = {
                            Text(
                                if (model.provider == AiModel.Provider.OPENROUTER)
                                    "Dán khoá openrouter.ai vào đây" else "Dán khoá AI Studio vào đây",
                                color = TextMuted, fontSize = 13.sp
                            )
                        },
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
                                if (model.provider == AiModel.Provider.OPENROUTER)
                                    AiKey.saveOpenRouter(context, keyDraft)
                                else AiKey.save(context, keyDraft)
                                keyDraft = ""
                                key = AiModels.keyFor(context, model)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // ── So lieu that app do duoc — nguon cua moi cau tra loi ───────────
        // An theo mac dinh: bang so lieu chiem toi 1/3 man hinh nganh, day
        // doan chat xuong cot 3 dong va cau tra loi dai khong doc het duoc
        // (loc tren BlueStacks ban 22.0). So lieu van gui cho AI day du, chi
        // la khong chiem cho cua nguoi dung khi ho khong xem no.
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SectionHeader(
                    title = "Hôm nay máy đo được",
                    subtitle = if (showStats) "Bảng số liệu gửi kèm mỗi câu hỏi" else "Đang ẩn để dành chỗ cho đoạn chat",
                    tint = Premium.cyan
                )
                Spacer(modifier = Modifier.height(6.dp))
                GlassButton(
                    text = if (showStats) "Ẩn bảng số liệu" else "Xem bảng số liệu",
                    onClick = { showStats = !showStats },
                    modifier = Modifier.fillMaxWidth().testTag("ai_toggle_stats"),
                    tint = Premium.cyan,
                    height = 34.dp,
                    fontSize = 12
                )
                if (showStats) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Cung mot kieu danh cho nhu cau tra loi: gach dau dong thang
                    // phan loai, nhan dem mau de phat hien ngay thieu du lieu.
                    AiAnswerBody(text = stats, ink = TextSecondary, accent = Premium.cyan)
                }
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
                                // Cau tra loi dai 15-20 dong. Ghim bong o 320dp
                                // khiem khach o goi trai khien no trang mot cot
                                // doc dai khong doc duoc; chi bong cua nguoi dung
                                // giu ngan de lech phai.
                                .then(
                                    if (fromModel) Modifier.fillMaxWidth(0.97f)
                                    else Modifier.widthIn(max = 340.dp)
                                )
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
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            if (fromModel) {
                                AiAnswerBody(
                                    text = turn.text,
                                    ink = TextPrimary,
                                    accent = Premium.cyan
                                )
                            } else {
                                AiInlineText(
                                    text = turn.text,
                                    color = Color.White,
                                    boldColor = Color.White
                                )
                            }
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

/**
 * Mot bong bong chat cua tro ly, tach thanh tung khoai co nhan rieng.
 *
 * Model tra ve `1. **Kiểm tra ứng dụng:** Mở app…` — nem thang chuoi vao `Text`
 * thi ra mot dam chu cung mau, cung chen chuc, khong diem nao de mat dua vao
 * (nguoi dung ban 26.0: "noi dung duoc ma bo cuc khong muon doc"). O day moi
 * buoc co so tron danh o, nhan dam mau accent nam tren, noi dung nam duoi va
 * cac khoai cach nhau that.
 */
@Composable
private fun AiAnswerBody(text: String, ink: Color, accent: Color) {
    val blocks = remember(text) { AiMarkdown.blocks(text) }
    Column {
        blocks.forEachIndexed { i, block ->
            if (i > 0) {
                val prev = blocks[i - 1]
                // Nhan don ("**Nguyên nhân:**" khong co noi dung cung dong) phai dinh
                // vao y ben duoi; tach ra khoang cach lon la nhin nhu mot dong roi.
                Spacer(
                    modifier = Modifier.height(
                        if (prev.label.isNotEmpty() && prev.body.isEmpty()) 5.dp else 11.dp
                    )
                )
            }
            when (block.kind) {
                AiMarkdown.Kind.STEP -> Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = block.marker,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    AiBlockCopy(block, ink, accent, Modifier.weight(1f))
                }
                AiMarkdown.Kind.BULLET -> Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = "•",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        modifier = Modifier.width(16.dp)
                    )
                    AiBlockCopy(block, ink, accent, Modifier.weight(1f))
                }
                AiMarkdown.Kind.PARAGRAPH -> AiBlockCopy(block, ink, accent, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AiBlockCopy(
    block: AiMarkdown.Block,
    ink: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        if (block.label.isNotEmpty()) {
            Text(
                text = block.label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = accent,
                lineHeight = 18.sp
            )
            if (block.body.isNotEmpty()) Spacer(modifier = Modifier.height(2.dp))
        }
        if (block.body.isNotEmpty()) {
            AiInlineText(
                text = block.body,
                color = if (block.label.isEmpty()) ink else TextSecondary,
                boldColor = ink
            )
        }
    }
}

/** Doan van ngan, giu `**...` cua model thanh chu dam thay vi hien nguyen van. */
@Composable
private fun AiInlineText(text: String, color: Color, boldColor: Color) {
    val body = remember(text) {
        val parsed = AiMarkdown.segments(AiMarkdown.normalize(text))
        buildAnnotatedString {
            parsed.forEach { seg ->
                if (seg.bold) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                        append(seg.text)
                    }
                } else {
                    append(seg.text)
                }
            }
        }
    }
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        lineHeight = 21.sp
    )
}
