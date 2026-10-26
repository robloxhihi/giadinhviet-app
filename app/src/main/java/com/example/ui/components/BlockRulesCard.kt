package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.RuleAuditEntry
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole
import com.example.data.model.parseWindowCode
import com.example.data.model.windowCode
import com.example.data.model.windowCodeFrom
import com.example.data.model.windowDisplay
import com.example.service.blocker.BlockPolicy
import com.example.service.blocker.ConsentPolicy
import com.example.ui.theme.Premium
import com.example.ui.viewmodel.BlockRulesSaveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Bang dieu khien che do chan ung dung.
 *
 * Ba trang thai can phan biet ro, vi chi mot trong so do la that su chan:
 *   1. chua bat Tro nang      -> service khong chay, co chan cung khong duoc
 *   2. bat Tro nang, chi nhac -> dem thoi gian roi bao, khong can app
 *   3. bat Tro nang, chan that-> day ve Home khi mo app bi chan
 *
 * Nguoi dung chon app vao `selected` (ban nhap), roi phai bam "Gửi đề xuất".
 * Truoc day tick duoc gan thang vao Firestore nen khi ghi that bai (hoa do
 * chua ghep noi) o tick quay ve khong checked va nguoi dung tuong rang man
 * hinh bi hong. Ban nhap giu local + co nut Luu + co tich xanh bao da luu
 * thanh cong.
 *
 * Tu ban nay them lop dong thuan: bam Luu KHONG con ap dung luat (trung hop
 * chua ghep noi), ma mo mot phieu de ben kia duyet. Phan "Cho duyet" ben
 * duoi chi hien voi ben KHONG phai la nguoi goi de xuat — me khong the tu
 * thong qua phep cua minh.
 */
@Composable
fun BlockRulesCard(
    schedule: UsageSchedule?,
    blockerServiceEnabled: Boolean,
    saveState: BlockRulesSaveState = BlockRulesSaveState.Idle,
    savedAtMillis: Long = 0L,
    audit: List<RuleAuditEntry> = emptyList(),
    /** Loi gan nhat tu ViewModel — dung de banner "chưa lưu được" noi DUNG ly do. */
    errorText: String? = null,
    /**
     * Ten con do phu huynh nhin thay. 69.0: ca the nay la man DAT LUAT cho may
     * KHAC, nen phai ghi ro luat ap len may nao thay vi de nguoi dung doan.
     */
    targetChildName: String = "",
    onUpdate: (
        blockedPackages: List<String>,
        reminderOnly: Boolean,
        allowedApps: List<String>,
        dailyTargetMinutes: Int,
        /** `"HH:mm-HH:mm"` = dat khung gio; `""` = khong chan theo gio. */
        windowCode: String
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val serverDraft = remember(schedule) {
        RuleDraft(
            blocked = schedule?.blockedPackages?.toSet() ?: emptySet(),
            reminderOnly = schedule?.isReminderOnly ?: true,
            allowed = schedule?.allowedApps?.toSet() ?: emptySet(),
            minutes = schedule?.dailyTargetMinutes ?: 0,
            window = schedule?.windowCode().orEmpty()
        )
    }
    // Bien dung hoa ten cu cho de doc cac phan ben duoi.
    val serverBlocked = serverDraft.blocked
    val serverReminderOnly = serverDraft.reminderOnly

    // Ban nhap: tick giu nguyen cho den khi luu xong hoac nguoi dung bo thay doi.
    var selected by remember { mutableStateOf(serverBlocked) }
    var reminderOnly by remember { mutableStateOf(serverReminderOnly) }
    var allowedSelected by remember { mutableStateOf(serverDraft.allowed) }
    var targetMinutes by remember { mutableStateOf(serverDraft.minutes) }
    /**
     * Hai moc cua khung gio duoc dung máy, tính bang so phut tu 00:00.
     * `null` = chua chon. CHUA CO mac dinh nao o day: 07:00–21:30 tung la gia
     * tri cu cua `UsageSchedule` nen moi gia dinh chua chon gi cung bi hien
     * nhu ho da tu dat. Gio thiet ke la phai chon ca hai moc thi lop chan
     * theo gio moi ton tai.
     */
    var windowStart by remember { mutableStateOf(parseWindowCode(serverDraft.window)?.first) }
    var windowEnd by remember { mutableStateOf(parseWindowCode(serverDraft.window)?.second) }
    val draftWindowCode = if (windowStart != null && windowEnd != null)
        windowCodeFrom(windowStart ?: 0, windowEnd ?: 0) else ""
    var dirty by remember { mutableStateOf(false) }

    /**
     * Da bam "Gửi đề xuất" va dang cho ket qua tu Firestore. `dirty` chi duoc
     * tat khi that su Saved — xem LaunchedEffect(saveState) ben duoi.
     */
    var saveInFlight by remember { mutableStateOf(false) }

    /**
     * Ban vua gui. "Da luu" chi duoc xac nhan khi may chủ thực sự trả về đúng
     * ban nay — neu không, o tick mat bay giay lat (nhin cu lieu cu) trong luc
     * snapshot mới chưa kip ve, va nguoi dung lai tuong bo chan bi hong.
     * So sanh CA NAM thu (app chan / che do / app mien tran / han muc / khung
     * gio), vi truoc day chi so sanh app + che do nen doi han muc phut that
     * bai ma van bam "✓ Đã lưu".
     */
    var lastSubmitted by remember { mutableStateOf<RuleDraft?>(null) }

    /**
     * Vua bam "Bỏ các thay đổi chưa lưu": banner "✓ Đã lưu lúc …" den tu
     * `saveState` cua ViewModel va bi thieu `resetBlockRulesSaveState()` nen
     * no khong bao gio tu ve Idle — tich xanh do van treo o tren the sau khi
     * bo thay doi, khien nguoi dung tuong cai vua bo van duoc luu. Day la
     * "muc thoi da xac nhan" cua chinh the nay: an banner khi dung bang
     * `savedAtMillis`, khong cho phep ViewModel moi den nhung lan dat lai sau.
     */
    // -1 (khong phai 0) vi `savedAtMillis` mac dinh la 0L khi chua co lan luu
    // nao: 0 == 0 se an nham banner cua lan luu dau tien.
    var dismissedSavedStamp by remember { mutableStateOf(-1L) }
    val visibleSaveState =
        if (saveState == BlockRulesSaveState.Saved && savedAtMillis == dismissedSavedStamp)
            BlockRulesSaveState.Idle else saveState

    // Trang thai vung chon app dat o day (KHONG phai ben trong `if (showPicker)`)
    // de khi dong roi mo lai, tu khoa tim va vi tri cuon van con nguyen.
    var showPicker by remember { mutableStateOf(false) }
    var pickerQuery by remember { mutableStateOf("") }
    val pickerScrollState = rememberScrollState()

    // Vung chon "ung dung luon duoc dung" (mien chan ngoai gio). Nam ngoai
    // `if (showAllowPicker)` giong het showPicker de tu khoa tim khong mat khi
    // dong roi mo lai.
    var showAllowPicker by remember { mutableStateOf(false) }
    var allowQuery by remember { mutableStateOf("") }
    val allowScrollState = rememberScrollState()

    // Khi Firestore tra ve lieu moi thi dong bo ban nhap, nhung KHONG dong bo
    // luc nguoi dung dang soan dang ho (dirty) — neu khong tick bi an mat.
    LaunchedEffect(schedule, dirty) {
        if (!dirty) {
            selected = serverDraft.blocked
            reminderOnly = serverDraft.reminderOnly
            allowedSelected = serverDraft.allowed
            targetMinutes = serverDraft.minutes
            windowStart = parseWindowCode(serverDraft.window)?.first
            windowEnd = parseWindowCode(serverDraft.window)?.second
        }
    }

    /**
     * Khi ghi that bai (Failed) thi ViewModel tra `usageSchedule` ve gia tri cu.
     * Truoc day nut Luu tat `dirty` ngay khi bam, nen effect dong bo ben
     * tren lap tuc nap lai cu lieu cu va o tick trang bong — nguoi dung tuong
     * man hinh bi hong. Chi xac nhan "da luu" khi state la Saved; Failed thi
     * giu nguyen ban nhap de nguoi dung con thay minh da chon gi va bam gui
     * lai.
     */
    LaunchedEffect(saveState, serverDraft) {
        val submitted = lastSubmitted
        when (saveState) {
            BlockRulesSaveState.Saved -> if (saveInFlight && submitted != null &&
                serverDraft == submitted
            ) {
                saveInFlight = false
                dirty = false
            }
            BlockRulesSaveState.Failed -> saveInFlight = false
            else -> Unit
        }
    }

    // Trang thai Tro nang phai duoc doc LAI luc quay ve tu Cai dat. Neu chi tin
    // gia tri luc lan hop nay thi nguoi dung bat Tro nang xong mo app ra van
    // thay "chua bat", va nghi bo chan bi hong.
    var serviceEnabled by remember { mutableStateOf(blockerServiceEnabled) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = isAppBlockerEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(blockerServiceEnabled, showPicker) {
        serviceEnabled = isAppBlockerEnabled(context)
    }

    // Danh sach ung dung phai doc tren nền IO: may co 200+ app thi doc tren
    // main thread lam giay khi mo man hinh Cai dat.
    var installedApps by remember { mutableStateOf<List<LaunchableApp>>(emptyList()) }
    var appsLoadFailed by remember { mutableStateOf(false) }
    LaunchedEffect(showPicker, showAllowPicker, serverDraft) {
        if ((showPicker || showAllowPicker) && installedApps.isEmpty()) {
            val loaded = withContext(Dispatchers.IO) {
                loadLaunchableApps(context, serverDraft.blocked + serverDraft.allowed)
            }
            if (loaded.isEmpty()) appsLoadFailed = true else installedApps = loaded
        }
    }

    // App da nam trong luat (hoac dang duoc chon) ma danh sach vua doc khong
    // co — van them vao de o tick cua nguoi dung khong bien mat khong ly do.
    val pickerApps = remember(installedApps, serverDraft, selected, allowedSelected) {
        val known = installedApps.mapTo(mutableSetOf()) { it.packageName }
        val missing = (serverDraft.blocked + serverDraft.allowed + selected + allowedSelected)
            .filterNot { it in known }.distinct()
        installedApps + missing.map { pkg ->
            LaunchableApp(
                packageName = pkg,
                label = pkg,
                blockNote = "Máy không đọc lại được thông tin app này (đã gỡ?) — vẫn bỏ tích được."
            )
        }
    }

    val newlyBlocked = selected.size - serverBlocked.size

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        // KIN HOAN TOAN: 0.35 alpha khien ca the noi quy trong suot, hoa lan
        // + noi dung ben duoi boi xuyen qua chu (cam giac "chong nen").
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Chặn ứng dụng theo lịch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                "Cần bật Trợ năng (Accessibility) để chặn thật. " +
                    "Ứng dụng không đọc nội dung màn hình, chỉ xem app nào đang mở.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))


            // Trang thai 1: chua bat Tro nang
            if (!serviceEnabled) {
                StatusBanner(
                    text = "Chưa bật Trợ năng — hiện tại chưa chặn được app nào.",
                    tone = Tone.Warn
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Mở Cài đặt Trợ năng")
                }
                Spacer(Modifier.height(12.dp))
            } else {
                StatusBanner(
                    text = "Trợ năng đang bật — bộ chặn hoạt động.",
                    tone = Tone.Ok
                )
                Spacer(Modifier.height(12.dp))
            }

            // 69.0 — "cái này phụ huynh chọn thì áp dụng lên máy nào?" Ghi ngay
            // dau the, vi day la man DAT LUAT cho mot cai khac: nguoi dung dung
            // may cua minh ma so luat day ap len may con. Dong thu hai chan
            // nan mot noi lo that: ban 64.0 tung dem may cua ME ve Home bang
            // chinh han muc phut cua con (xem AppBlockerService.listenSchedule).
            StatusBanner(
                text = (
                    if (targetChildName.isNotBlank()) "Mọi lựa chọn dưới đây áp dụng lên MÁY CỦA CON — «$targetChildName». "
                    else "Mọi lựa chọn dưới đây áp dụng lên MÁY CỦA CON đang ghép nối với bạn. "
                    ) +
                    "Máy bạn đang cầm chỉ để đặt luật, không bị chính luật này chặn.",
                tone = Tone.Info
            )
            Spacer(Modifier.height(12.dp))

            // Chon che do. The nay tung ghi nhan la "Chi nhac nho" trong khi
            // Switch duoc dat `checked = !reminderOnly` — tuc GAT la "chan
            // that". Nhan nguoc chieu nhu vay khien nguoi dung tick app xong
            // tuong rang may da chan, con luat van o che do nhac (mac dinh
            // `isReminderOnly = true`). Dat ten theo dung cai Switch bat.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Chặn thật", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (reminderOnly) "Đang tắt — máy con chỉ đếm thời gian, không chặn app nào"
                        else "Đang bật — đẩy về màn hình Home khi mở app bị chặn",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (reminderOnly) Premium.amber
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = !reminderOnly,
                    onCheckedChange = {
                        reminderOnly = !it
                        dirty = true
                    }
                )
            }

            // Canh bao "chon roi ma chua chan that" dat XUONG DUOI danh sach
            // o cuoi: o vi tri cu (ngay duoi cong tac) no chen vao truong khi
            // tick con app dau tien, day ca danh sach tui xuong duoi ngon tay
            // va nhip tiep theo trung mot app khac — cam giac "tich app chan
            // bi loi". Noi dung giu nguyen, chi khong con chen vao giua.

            Spacer(Modifier.height(12.dp))

            // Danh sach app bi chan
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Ứng dụng bị chặn: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (selected.isEmpty()) "chưa chọn" else "${selected.size} app",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (dirty) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (newlyBlocked > 0) "• thêm $newlyBlocked app" else "• đã thay đổi",
                        style = MaterialTheme.typography.bodySmall,
                        color = Premium.amber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { showPicker = !showPicker }) {
                Text(if (showPicker) "Đóng danh sách" else "Chọn ứng dụng cần chặn")
            }

            if (showPicker) {
                AppPicker(
                    apps = pickerApps,
                    query = pickerQuery,
                    onQueryChange = { pickerQuery = it },
                    scrollState = pickerScrollState,
                    selected = selected,
                    loading = installedApps.isEmpty() && !appsLoadFailed,
                    loadFailed = appsLoadFailed && installedApps.isEmpty(),
                    onToggle = { pkg ->
                        selected = if (pkg in selected) selected - pkg else selected + pkg
                        dirty = true
                    },
                    onClearAll = {
                        selected = emptySet()
                        dirty = true
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            if (reminderOnly && selected.isNotEmpty()) {
                StatusBanner(
                    text = "${selected.size} app đã chọn nhưng “Chặn thật” đang tắt — " +
                        "máy sẽ chỉ nhắc khi quá giờ, không đẩy app về Home. " +
                        "Bật công tắc trên rồi gửi đề xuất để chặn thật.",
                    tone = Tone.Warn
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Hai lop chan ──────────────────────────────────────────────
            // Day la cai khien nguoi dung "tich 3 app ma may chan het moi thu":
            // BlockPolicy khong chi chan danh sach da tick, ma ngoai khung gio
            // cho phep va khi vuot han muc ngay thi chan TAT CA app khong duoc
            // mien tran. Doan van nay phai mo ta BAN NHAP (windowCode cua card),
            // khong mo ta luat dang chay — neu khong nguoi dung vua dat xong
            // khung gio van thay chu "may KHÔNG chặn theo giờ".
            Spacer(Modifier.height(14.dp))
            val hasWindow = draftWindowCode.isNotBlank()
            val windowText = windowDisplay(draftWindowCode) ?: "chưa đặt"
            val minutesNote = if (targetMinutes > 0)
                "Chỉ chặn khi vượt $targetMinutes phút/ngày (app ngoài danh sách “luôn được dùng”)."
            else "Và chưa đặt hạn mức phút, nên lớp chặn này đang không có tác dụng."
            Text(
                "Hai lớp chặn hoạt động độc lập",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "1) App đã tích ở trên: bị chặn mọi lúc.\n" +
                    "2) " + when {
                        hasWindow ->
                            "Ngoài khung giờ $windowText" +
                                (if (targetMinutes > 0) " hoặc khi vượt $targetMinutes phút/ngày" else "") +
                                ": máy chặn TẤT CẢ app chưa được cho phép. Danh sách “luôn được dùng” đang " +
                                if (allowedSelected.isEmpty())
                                    "trống — nghĩa là ngoài giờ, con gần như không mở được app nào."
                                else
                                    "có ${allowedSelected.size} app — chỉ những app đó chạy được ngoài giờ."

                        windowStart != null || windowEnd != null ->
                            "Khung giờ mới chọn được " +
                                (if (windowStart != null) "mốc bắt đầu" else "mốc kết thúc") +
                                " — chưa đủ hai mốc nên máy KHÔNG chặn theo giờ. " + minutesNote

                        else -> "Khung giờ cho phép: chưa đặt, nên máy KHÔNG chặn theo giờ. " + minutesNote
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))
            WindowEditor(
                startMinutes = windowStart,
                endMinutes = windowEnd,
                onStartChange = {
                    windowStart = it
                    dirty = true
                },
                onEndChange = {
                    windowEnd = it
                    dirty = true
                },
                onClear = {
                    windowStart = null
                    windowEnd = null
                    dirty = true
                }
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Luôn được dùng: ",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    if (allowedSelected.isEmpty()) "chưa có" else "${allowedSelected.size} app",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Premium.emerald
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showAllowPicker = !showAllowPicker }) {
                    Text(if (showAllowPicker) "Đóng" else "Chọn app miễn chặn")
                }
            }

            // May con danh gia danh sach BI CHAN truoc (BlockPolicy.blockReason),
            // nen mot app nam o ca hai cho thi van bi chan. Truoc day UI khong
            // noi gi, nguoi dung tuong "mien chan" la thang — do la mot trong
            // nhung ly do "chuc nang khong hoat dong dung nhu ghi".
            val bothBlockedAndExempt = selected.intersect(allowedSelected)
            if (bothBlockedAndExempt.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                StatusBanner(
                    text = "${bothBlockedAndExempt.size} app đang nằm ở cả hai danh sách — " +
                        "máy con sẽ CHẶN, vì danh sách bị chặn được xét trước danh sách miễn chặn.",
                    tone = Tone.Warn
                )
            }

            if (showAllowPicker) {
                AppPicker(
                    apps = pickerApps,
                    query = allowQuery,
                    onQueryChange = { allowQuery = it },
                    scrollState = allowScrollState,
                    selected = allowedSelected,
                    loading = installedApps.isEmpty() && !appsLoadFailed,
                    loadFailed = appsLoadFailed && installedApps.isEmpty(),
                    titleIfEmpty = "Chưa chọn app nào được miễn chặn",
                    onToggle = { pkg ->
                        allowedSelected =
                            if (pkg in allowedSelected) allowedSelected - pkg else allowedSelected + pkg
                        dirty = true
                    },
                    onClearAll = {
                        allowedSelected = emptySet()
                        dirty = true
                    }
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Han muc phut/ngay (nam o day vi chi gui duoc khi DA ghep noi) ──
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (targetMinutes > 0) "Hạn mức mỗi ngày: $targetMinutes phút"
                else "Hạn mức mỗi ngày: chưa đặt — máy không chặn theo số phút",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            // Han muc phut song duoc nua nho quyen "Truy cập thông tin sử dụng"
            // o may CON: khong co quyen thi dem ra 0 phut va bo chan that bai
            // im lang. Noi dieu kien truoc khi nguoi dung bam gui.
            if (targetMinutes > 0) {
                Text(
                    text = "Cần máy con đã bật quyền \"Truy cập thông tin sử dụng\" — chưa có quyền thì " +
                        "không đếm được phút, hạn mức không chặn được.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Premium.amber
                )
                Spacer(Modifier.height(6.dp))
            }
            // Slider cua Material3 NEM loi neu `value` nam ngoai `valueRange`,
            // ma han muc gio day co the bang 0 (chua dat) → an nut moi bat len
            // 30 phut la min cua thanh keo. khong co con so nao xuat hien khi
            // chua ai chon.
            if (targetMinutes <= 0) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = {
                    targetMinutes = 30
                    dirty = true
                }) { Text("Đặt hạn mức phút/ngày") }
            } else {
                Slider(
                    value = targetMinutes.toFloat().coerceIn(30f, 360f),
                    onValueChange = {
                        targetMinutes = it.toInt()
                        dirty = true
                    },
                    valueRange = 30f..360f,
                    steps = 21,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text(
                "30–360 phút. Kéo thanh này KHÔNG gửi gì cả — phải bấm nút bên dưới, " +
                    "và máy con chạy theo ngay khi lưu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── Luu ban nhap ──────────────────────────────────────────────
            if (dirty || visibleSaveState != BlockRulesSaveState.Idle) {
                Spacer(Modifier.height(12.dp))
                SaveRow(
                    dirty = dirty,
                    saveState = visibleSaveState,
                    savedAtMillis = savedAtMillis,
                    savedIsReminderOnly = reminderOnly,
                    savedBlockedCount = selected.size,
                    countSelected = selected.size,
                    targetMinutes = targetMinutes,
                    windowSummary = if (hasWindow) "khung giờ $windowText"
                    else "không chặn theo giờ",
                    errorText = errorText,
                    onSave = {
                        // KHONG tat dirty o day: that bai duoc thi phai giu ban nhap.
                        lastSubmitted = RuleDraft(
                            selected, reminderOnly, allowedSelected, targetMinutes, draftWindowCode
                        )
                        saveInFlight = true
                        onUpdate(
                            selected.toList(),
                            reminderOnly,
                            allowedSelected.toList(),
                            targetMinutes,
                            draftWindowCode
                        )
                    },
                    onDiscard = {
                        selected = serverDraft.blocked
                        reminderOnly = serverDraft.reminderOnly
                        allowedSelected = serverDraft.allowed
                        targetMinutes = serverDraft.minutes
                        windowStart = parseWindowCode(serverDraft.window)?.first
                        windowEnd = parseWindowCode(serverDraft.window)?.second
                        dirty = false
                        saveInFlight = false
                        lastSubmitted = null
                        dismissedSavedStamp = savedAtMillis
                    }
                )
            }

            // ── Nhat ky noi quy (phan "minh bach") ────────────────────────
            if (audit.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Nhật ký nội quy",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                audit.take(5).forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            formatTime(entry.createdAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.detail.ifBlank { entry.action },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    "Hai bên cùng đọc được nhật ký này — ai đổi gì, lúc nào, bị duyệt hay từ chối.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Toan bo noi quy ma the nay soan thao, gom CA NAM thu: app bi chan, che do
 * chan/nhac, app mien tran, han muc phut va khung gio duoc dung may. Dung lam
 * ban in cua `lastSubmitted` de so voi snapshot tro ve tu may chủ — chi khi
 * khop het nam thu moi dam bao "✓ Đã lưu". So sanh theo gia tri (data class)
 * nen `Set<String>` doi chieu duoc du cho thu tu khong giong nhau.
 */
private data class RuleDraft(
    val blocked: Set<String>,
    val reminderOnly: Boolean,
    val allowed: Set<String>,
    val minutes: Int,
    /** `"HH:mm-HH:mm"` hoac rong khi chua dat khung gio. */
    val window: String
)

/**
 * Hang duoi cung cua the: nut gui de xuat (hoac nut luu khi chua ghep noi) +
 * tich xanh bao da ghi. Tich chi xuat hien khi Firestore ghi xong
 * (saveState == Saved), khong phai khi vua bam.
 *
 * Chu tren nut phai noi DUNG bat dung: co ben kia thi day la "gui de xuat",
 * chua phai "luu va ap dung". Noi sai chu nay la danh mat cai nghia dong
 * thuan cua toan bo tinh nang.
 */
@Composable
private fun SaveRow(
    dirty: Boolean,
    saveState: BlockRulesSaveState,
    savedAtMillis: Long,
    /** Che do cua BAN NHAP — dung de banner "Da luu" khong noi tran. */
    savedIsReminderOnly: Boolean,
    savedBlockedCount: Int,
    countSelected: Int,
    /** Han muc phut dang soan — de banner "Da luu" noi du ca chinh sach ngay. */
    targetMinutes: Int,
    /** Tom tat lop chan theo gio cua ban vua luu, de banner khong noi tran. */
    windowSummary: String,
    /** Loi gan nhat tu ViewModel: banner "Chưa lưu được" phai noi DUNG ly do. */
    errorText: String?,
    onSave: () -> Unit,
    onDiscard: () -> Unit
) {
    val reason = errorText?.takeIf { it.isNotBlank() }
    if (dirty) {
        // Lan gui tru do that bai thi van giu ban nhap, nen loi phai hien NGAY
        // canh nut — neu khong nguoi dung chi thay nut "Gửi đề xuất" muon man.
        if (saveState == BlockRulesSaveState.Failed) {
            StatusBanner(
                text = "Lần gửi trước chưa xuống được máy chủ" +
                    (reason?.let { " ($it)" } ?: "") +
                    ". Lựa chọn của bạn vẫn được giữ nguyên — sửa theo hướng dẫn rồi bấm lại.",
                tone = Tone.Warn
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = onSave,
            enabled = saveState != BlockRulesSaveState.Saving,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Premium.emerald)
        ) {
            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (saveState == BlockRulesSaveState.Saving) {
                    "Đang gửi…"
                } else {
                    // Chu tren nut phai noi DUNG bat dung, va phai nom ca lop
                    // chan theo gio: cung "3 app" ma "chi nhac" hoac "ngoai
                    // gio" thi may lam hai viec hoan toan khac nhau.
                    val mode = when {
                        countSelected == 0 && savedIsReminderOnly -> "không chặn app nào, chỉ nhắc"
                        countSelected == 0 -> "không chặn app nào"
                        savedIsReminderOnly -> "nhắc $countSelected app (chưa chặn thật)"
                        else -> "chặn thật $countSelected app"
                    }
                    buildString {
                        append("Gửi xuống máy của con")
                        append(" • ").append(mode)
                        append(" • ").append(windowSummary)
                    }
                },
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = onDiscard,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Bỏ các thay đổi chưa lưu", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        when (saveState) {
            BlockRulesSaveState.Saving -> StatusBanner(
                text = "Đang ghi xuống máy chủ…",
                tone = Tone.Info
            )
            BlockRulesSaveState.Saved -> when {
                // "Áp dụng ngay" la sai khi may dang o che do nhac nho hoac
                // chua bat Tro nang — day chinh la ly do nguoi dung bam tich
                // xong thay app van chay binh thuong va tuong bo chan bi hong.
                savedIsReminderOnly -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — máy đang ở chế độ nhắc nhở, " +
                        "chưa chặn app nào (hạn mức $targetMinutes phút/ngày, $windowSummary). Bật “Chặn thật” ở trên " +
                        "nếu muốn đẩy về Home.",
                    tone = Tone.Info
                )
                savedBlockedCount == 0 -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — hiện không có app nào bị chặn, " +
                        "hạn mức $targetMinutes phút/ngày, $windowSummary.",
                    tone = Tone.Ok
                )
                else -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — chặn thật $savedBlockedCount app, " +
                        "hạn mức $targetMinutes phút/ngày, $windowSummary, áp dụng khi Trợ năng đang bật.",
                    tone = Tone.Ok
                )
            }
            BlockRulesSaveState.Failed -> StatusBanner(
                text = "Chưa lưu được" + (reason?.let { ": $it" } ?: " — mở lại mục này sau khi đã ghép nối và có mạng.") +
                    " Lựa chọn trên màn hình vẫn được giữ.",
                tone = Tone.Warn
            )
            BlockRulesSaveState.Idle -> Unit
        }
    }
}

private fun formatTime(millis: Long): String =
    if (millis <= 0L) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(Date(millis))

private enum class Tone { Ok, Warn, Info }

@Composable
private fun WindowEditor(
    startMinutes: Int?,
    endMinutes: Int?,
    onStartChange: (Int?) -> Unit,
    onEndChange: (Int?) -> Unit,
    onClear: () -> Unit
) {
    val both = startMinutes != null && endMinutes != null
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Khung giờ được dùng máy: ", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = when {
                    both -> "%02d:%02d → %02d:%02d".format(
                        (startMinutes ?: 0) / 60 % 24, (startMinutes ?: 0) % 60,
                        (endMinutes ?: 0) / 60 % 24, (endMinutes ?: 0) % 60
                    )

                    startMinutes != null -> "thiếu mốc kết thúc"
                    endMinutes != null -> "thiếu mốc bắt đầu"
                    else -> "chưa đặt"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (both) Premium.emerald else Premium.amber
            )
            Spacer(Modifier.weight(1f))
            if (startMinutes != null || endMinutes != null) {
                TextButton(onClick = onClear) {
                    Text("Xoá", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        TimeField(
            tag = "block_window_start",
            label = "Bắt đầu",
            minutes = startMinutes,
            onSelect = { onStartChange(it) }
        )
        Spacer(Modifier.height(6.dp))
        TimeField(
            tag = "block_window_end",
            label = "Kết thúc",
            minutes = endMinutes,
            onSelect = { onEndChange(it) }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Khung qua nửa đêm (ví dụ 21:00 → 06:00) được hiểu là từ tối hôm " +
                "trước đến sáng hôm sau. Chọn ở đây mới là bản nháp trên máy này — " +
                "chưa đổi nội quy nào cho tới khi bấm nút gửi bên dưới.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Mot moc cua khung gio, nhap kieu "21:00" (ung "2100" cung duoc). Chu dung
 * chip liet ke 0..23 vi nguoi dung bao nhin loan va kho tim. O nay khong goi
 * onSelect khi chuoi chua phai gio that, nen khong the vo sinh moc 00:00 nhu
 * hang phut cu (bam ":30" khi chua chon gio tung bi hieu thanh 00:30).
 */
@Composable
private fun TimeField(
    label: String,
    tag: String,
    minutes: Int?,
    onSelect: (Int) -> Unit
) {
    var text by remember(minutes) {
        mutableStateOf(minutes?.let { "%02d:%02d".format(it / 60 % 24, it % 60) } ?: "")
    }
    var invalid by remember(minutes) { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(4)
                text = if (digits.length > 2) "${digits.take(2)}:${digits.drop(2)}" else digits
                val h = digits.take(2).toIntOrNull()
                val m = digits.drop(2).toIntOrNull()
                val ok = digits.length == 4 && h != null && m != null && h in 0..23 && m in 0..59
                invalid = digits.isNotEmpty() && !ok
                if (ok) onSelect(h * 60 + m)
            },
            label = { Text(label) },
            placeholder = { Text("21:00") },
            singleLine = true,
            isError = invalid,
            supportingText = if (invalid) {
                { Text("Phải là giờ thật, kiểu 21:00 hoặc 06:30") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().testTag(tag)
        )
    }
}

/**
 * Tro nang tiep can da bat that chua. Service chi chay khi nguoi dung tu bat
 * trong Cai dat -> Tro nang tiep can, nen UI phai bao dung trang thai nay.
 */
fun isAppBlockerEnabled(context: Context): Boolean {
    val enabled = runCatching {
        Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
    }.getOrNull() ?: return false

    return enabled.split(':').any { it.contains("AppBlockerService") }
}

@Composable
private fun StatusBanner(text: String, tone: Tone) {
    val color = when (tone) {
        Tone.Ok -> Premium.emerald
        Tone.Warn -> MaterialTheme.colorScheme.error
        Tone.Info -> MaterialTheme.colorScheme.primary
    }
    val icon: ImageVector = when (tone) {
        Tone.Ok -> Icons.Default.CheckCircle
        Tone.Warn -> Icons.Default.NotificationsActive
        Tone.Info -> Icons.Default.NotificationsActive
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

/**
 * So dong toi da render mot luc. May cua con co the co 300 app; ve het vao
 * Column cung luc thi gianh khi mo vung chon, nen phan con lai chi hien ra
 * khi nguoi dung go ten de tim.
 */
private const val PICKER_RENDER_LIMIT = 80

/**
 * Vung chon app: tim kiem + danh sach co o tich.
 *
 * Hai dieu khac biet so voi ban cu:
 *  1. CA HANG la vung bam (`selectable`), o Checkbox chi hien trang thai
 *     (`onCheckedChange = null`). Truoc day chi o tich moi bam duoc nen cham
 *     vao ten app thi khong xay ra gi — dung cai nguoi dung bao.
 *  2. Danh sach dung `verticalScroll` voi ScrollState da duoc day len the
 *     cha, khong dung `LazyColumn` nua — LazyColumn nam trong Column co
 *     verticalScroll cua man hinh Cai dat la hai container cuong doan nhau.
 *     Column + verticalScroll thi cuon duoc noi tiep (het danh sach thi lan
 *     ra ngoai) va vi tri cuon khong mat khi dong mo vung chon.
 */
@Composable
private fun AppPicker(
    apps: List<LaunchableApp>,
    query: String,
    onQueryChange: (String) -> Unit,
    scrollState: ScrollState,
    selected: Set<String>,
    loading: Boolean,
    loadFailed: Boolean,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit,
    /** Chu dem khi chua chon gi — vung nay dung chung cho ca "chan" lan "mien chan". */
    titleIfEmpty: String = "Chưa chọn app nào"
) {
    val keyword = query.trim()

    // DAN SACH CO DINH — day la cai phua that su. Truoc day app vua tick bi
    // nhay len nhom "Dang chọn" o dau danh sach, nen toan bo phi ben duoi
    // tran len mot hang: bam tiep o thu 2 thi danh sach da doi, va nguoi dung
    // tick TRONG khi muon tick app khac (mo tawtgs sai, tick lai mat). Gio
    // thu tu la theo dung thu tu doc duoc (A-Z, app khong chan duoc xuong
    // cuoi) va KHONG doi khi chon; app da chon van nam yen cho cua no, chi
    // doi mau + co tich. Duoi dau danh sach co dai chip liet ke nhung app
    // dang chon de luc nao cung bo tich duoc, ke ca khi no nam ngoai 150 dong
    // dau tien.
    val filtered = remember(apps, keyword) {
        apps.filter { app ->
            keyword.isEmpty() ||
                app.label.contains(keyword, ignoreCase = true) ||
                app.packageName.contains(keyword, ignoreCase = true)
        }
    }
    val shown = remember(filtered) { filtered.take(PICKER_RENDER_LIMIT) }
    val chosenApps = remember(apps, selected) { apps.filter { it.packageName in selected } }

    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("Tìm ứng dụng") },
        placeholder = { Text("theo tên app hoặc tên gói") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Xoá từ khoá tìm",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onQueryChange("") }
                        .padding(4.dp)
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    )

    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (selected.isEmpty()) titleIfEmpty else "Đã chọn ${selected.size} app",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else Premium.emerald
        )
        Spacer(Modifier.weight(1f))
        if (selected.isNotEmpty()) {
            TextButton(onClick = onClearAll) {
                Text("Bỏ chọn tất cả", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(12.dp),
        // Hen (well) dung opaqueSurface: 0.30 alpha khiem nen hoa lan ben
        // duoi day len ngay giua cac o tich — danh sach "bi lot" khi cuon.
        color = Premium.opaqueSurface
    ) {
        if (loading) {
            Text(
                "Đang đọc danh sách ứng dụng…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else if (loadFailed) {
            Text(
                "Máy không trả về danh sách ứng dụng nào. Đóng mục này rồi mở lại để đọc lại.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (shown.isEmpty() && chosenApps.isEmpty()) {
            Text(
                text = "Không có ứng dụng nào khớp từ khoá \"$keyword\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (chosenApps.isNotEmpty()) {
                    // Day noi liet ke cac dang chon: bo tich duoc moi app ngay
                    // ca khi no nam ngoai 80 dong dang hien ra.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        chosenApps.forEach { app ->
                            SelectedAppChip(label = app.label, onRemove = { onToggle(app.packageName) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                PickerSectionLabel(
                    if (keyword.isEmpty()) "Ứng dụng trên máy (${filtered.size})"
                    else "Kết quả tìm kiếm (${filtered.size})"
                )
                shown.forEach { app ->
                    AppPickRow(
                        app = app,
                        checked = app.packageName in selected,
                        onToggle = { onToggle(app.packageName) }
                    )
                }
                if (filtered.size > shown.size) {
                    Text(
                        text = "Còn ${filtered.size - shown.size} ứng dụng nữa — gõ tên để tìm nhanh hơn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** O chon dang app da tick, nam thanh ngang dau danh sach. Bam X = bo tich. */
@Composable
private fun SelectedAppChip(label: String, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Premium.emerald.copy(alpha = 0.20f))
            .border(1.dp, Premium.emerald.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
            .clickable(onClick = onRemove)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Premium.emerald,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Bỏ chọn $label",
            tint = Premium.emerald,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
    )
}

/**
 * Mot dong chon app. Hang la vung bam; Checkbox ben trong chi ve trang thai.
 *
 * `blockNote != null` = ung dung nay bi [BlockPolicy] loai tep (he thong,
 * launcher, ban phim, chinh app nay). Van liet ke + ghi ro ly do va vo hieu
 * hoa co tich CO CHU DIC, thay vi im lang loai khoi danh sach khien nguoi dung
 * nghi bo chan bi hong. Ngoai le: neu no DANG duoc chon (luu cu con giu) thi
 * phai bam duoc de bo tich.
 */
@Composable
private fun AppPickRow(
    app: LaunchableApp,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val toggleable = app.blockNote == null || checked
    val rowShape = RoundedCornerShape(10.dp)
    val uncheckedBorder = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.70f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(
                when {
                    checked -> Premium.emerald.copy(alpha = 0.18f)
                    app.blockNote != null -> Color.Transparent
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                }
            )
            .border(
                width = 1.dp,
                color = if (checked) Premium.emerald.copy(alpha = 0.55f) else Color.Transparent,
                shape = rowShape
            )
            .clickable(
                enabled = toggleable,
                role = Role.Checkbox,
                onClick = onToggle
            )
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = toggleable,
            colors = CheckboxDefaults.colors(
                checkedColor = Premium.emerald,
                uncheckedColor = uncheckedBorder,
                checkmarkColor = Color.White,
                disabledUncheckedColor = uncheckedBorder.copy(alpha = 0.25f)
            )
        )
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    checked -> Premium.emerald
                    app.blockNote != null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
            Text(
                text = app.blockNote ?: app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = if (app.blockNote != null) Premium.amber
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (checked) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Đã chọn",
                tint = Premium.emerald,
                modifier = Modifier.size(20.dp)
            )
        } else if (app.blockNote != null) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Không chặn được",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Mot dong trong vung chon.
 *
 * `blockNote` khac null nghia la app nay KIEN TRIEN khong chan duoc (dung
 * chung ly do voi [BlockPolicy.isNeverBlocked]) va phan do la chu giai hien
 * duoi ten app.
 */
private data class LaunchableApp(
    val packageName: String,
    val label: String,
    val blockNote: String? = null
)

/**
 * Lay danh sach app co the mo duoc va ca nhung goi tuc da nam trong luat.
 * App he thong / launcher / ban phim / chinh app nay KHONG bi loai khoi danh
 * sach nua — chung co `blockNote` de nguoi dung nhin thay ly do.
 *
 * Thu tu: chan duoc len truoc, app "khong chan duoc" day xuong cuoi, moi nhom
 * theo ten A-Z.
 */
private fun loadLaunchableApps(
    context: Context,
    extraPackages: Set<String> = emptySet()
): List<LaunchableApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    // `map` o day tung NPE lay het ca danh sach (runCatching bat ca vòng lặp)
    // khi may co mot intent resolver thieu activityInfo — vi vay dung
    // mapNotNull de bo qua dung phan tu do, khong mat ca danh sach.
    val launchable = runCatching {
        pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }
    }.getOrDefault(emptyList()).distinct()

    val packages = (launchable + extraPackages).distinct()

    return packages
        .map { pkg ->
            LaunchableApp(
                packageName = pkg,
                label = runCatching {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg),
                blockNote = unblockableReason(pkg, context.packageName)
            )
        }
        .sortedWith(
            compareBy<LaunchableApp> { it.blockNote != null }
                .thenBy { it.label.lowercase() }
        )
}

/**
 * Ly do ngan bang tieng Viet vi sao mot app khong the chan, hoac null neu
 * chan duoc. Dinh luat that nam trong [BlockPolicy] — day chi la phien ban
 * "nghi" cua no cho giao dien, khong duoc tu them/bot app nao.
 */
private fun unblockableReason(pkg: String, ownPackage: String): String? {
    if (pkg == ownPackage) {
        return "Chính ứng dụng này — chặn nó là mất luôn chỗ tắt luật."
    }
    if (!BlockPolicy.isNeverBlocked(pkg)) return null
    return when {
        pkg.contains("inputmethod", ignoreCase = true) ->
            "Bàn phím — chặn là không gõ được chữ nào."
        pkg.contains("launcher", ignoreCase = true) || pkg.endsWith("android.launcher") ->
            "Màn hình chính — chặn launcher sẽ bị đẩy về Home liên tục."
        pkg.contains("dialer", ignoreCase = true) || pkg.contains("incallui", ignoreCase = true) ||
            pkg.contains("telecom", ignoreCase = true) || pkg.contains("emergency", ignoreCase = true) ||
            pkg.contains("phone", ignoreCase = true) ->
            "Điện thoại và số khẩn cấp — luôn phải mở được."
        pkg.contains("settings", ignoreCase = true) ->
            "Cài đặt — cần mở được để tắt Trợ năng khi cần."
        pkg == "com.android.vending" || pkg.contains("permissioncontroller", ignoreCase = true) ||
            pkg.startsWith("com.google.android.gms") || pkg.startsWith("com.google.android.gsf") ->
            "Dịch vụ hệ thống — chặn là không đăng nhập, không cấp quyền được."
        pkg == "com.android.chrome" ->
            "Trình duyệt — để còn tra được hướng dẫn gỡ chặn."
        else -> "Ứng dụng hệ thống Android — không nằm trong nhóm được chặn."
    }
}

