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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.RequestStatus
import com.example.data.model.RuleAuditEntry
import com.example.data.model.UsageSchedule
import com.example.data.model.UserRole
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
    /** Vai tro cua nguoi dang nhin the nay: PARENT | CHILD. */
    myRole: UserRole? = null,
    /** Da ghep noi va PAIRED -> moi thay doi can ben kia duyet. */
    hasCounterparty: Boolean = false,
    audit: List<RuleAuditEntry> = emptyList(),
    /** Loi gan nhat tu ViewModel — dung de banner "chưa lưu được" noi DUNG ly do. */
    errorText: String? = null,
    onUpdate: (
        blockedPackages: List<String>,
        reminderOnly: Boolean,
        allowedApps: List<String>,
        dailyTargetMinutes: Int
    ) -> Unit,
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Gia tri dang bi (hoặc se bi) ap dung — dung de doi chieu voi ban nhap.
    // Khi co phieu mo thi ban nhap phai hien theo PHIEU, khong theo luat cu,
    // neu khong nguoi dung mo lai the thay tick trang trong khi de xuat van
    // con treo.
    val pendingOpen = schedule?.changeStatus == RequestStatus.PENDING
    val serverDraft = remember(schedule, pendingOpen) {
        RuleDraft(
            blocked = (if (pendingOpen) schedule?.pendingBlockedPackages else schedule?.blockedPackages)
                ?.toSet() ?: emptySet(),
            reminderOnly = if (pendingOpen)
                schedule?.pendingIsReminderOnly ?: (schedule?.isReminderOnly ?: true)
            else schedule?.isReminderOnly ?: true,
            allowed = (if (pendingOpen) schedule?.pendingAllowedApps else schedule?.allowedApps)
                ?.toSet() ?: emptySet(),
            minutes = if (pendingOpen)
                schedule?.pendingDailyTargetMinutes ?: (schedule?.dailyTargetMinutes ?: 120)
            else schedule?.dailyTargetMinutes ?: 120
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
     * So sanh CA BON thu (app chan / che do / app mien tran / han muc), vi truoc
     * day chi so sanh app + che do nen doi han muc phut that bai ma van bam
     * "✓ Đã lưu".
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

            // ── Phieu dong thuan ──────────────────────────────────────────
            val consentLine = ConsentPolicy.summary(schedule, myRole)
            if (consentLine != null) {
                StatusBanner(
                    text = consentLine,
                    tone = if (pendingOpen) Tone.Info else Tone.Warn
                )
                Spacer(Modifier.height(8.dp))
            }

            if (schedule != null && ConsentPolicy.canVote(schedule, myRole)) {
                // Chi ben KHONG goi de xuat thay nut nay. Me da de xuat thi
                // me chi thay chu "cho Con duyet", khong co nut Dong y.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Premium.amber.copy(alpha = 0.12f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Phiếu chờ bạn duyệt",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Premium.amber
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            ConsentPolicy.describe(schedule, pending = true),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Đồng ý thì nội quy này mới chạy. Từ chối thì giữ nguyên nội quy cũ — " +
                                "hai bên đều thấy quyết định trong nhật ký.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onApprove,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Premium.emerald),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Đồng ý", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onReject,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Close, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Từ chối")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            } else if (schedule != null && ConsentPolicy.canWithdraw(schedule, myRole)) {
                TextButton(
                    onClick = onWithdraw,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Rút đề xuất đang chờ ${ConsentPolicy.label(ConsentPolicy.voterOf(schedule))}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

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
                        text = if (reminderOnly) "Đang tắt — máy chỉ đếm thời gian rồi báo, chưa chặn app"
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
            // mien tran. Truoc day `allowedApps` khong co lay nao trong giao
            // dien (va bi updateBlockRules xoa im lang), nen mien tran = rong =
            // may con bi khoa hoan toan ngoai gio ma me khong hieu vi sao.
            Spacer(Modifier.height(14.dp))
            val s = schedule
            val windowText = "%02d:%02d–%02d:%02d".format(
                s?.allowedStartHour ?: 7, s?.allowedStartMinute ?: 0,
                s?.allowedEndHour ?: 21, s?.allowedEndMinute ?: 30
            )
            Text(
                "Hai lớp chặn hoạt động độc lập",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "1) App đã tích ở trên: bị chặn mọi lúc.\n" +
                    "2) Ngoài khung giờ $windowText hoặc khi vượt $targetMinutes phút/ngày: " +
                    "máy chặn TẤT CẢ app chưa được cho phép. Danh sách “luôn được dùng” đang " +
                    if (allowedSelected.isEmpty()) "trống — nghĩa là ngoài giờ, con gần như không mở được app nào."
                    else "có ${allowedSelected.size} app — chỉ những app đó chạy được ngoài giờ.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                text = "Hạn mức mỗi ngày: $targetMinutes phút",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            Slider(
                value = targetMinutes.toFloat(),
                onValueChange = {
                    targetMinutes = it.toInt()
                    dirty = true
                },
                valueRange = 30f..360f,
                steps = 21,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "30–360 phút. Kéo thanh này KHÔNG gửi gì cả — phải bấm nút bên dưới, " +
                    "và thay đổi chỉ chạy khi bên kia đồng ý.",
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
                    hasCounterparty = hasCounterparty,
                    votePending = pendingOpen,
                    voterLabel = ConsentPolicy.label(voteOwnerRole(schedule, myRole, pendingOpen)),
                    errorText = errorText,
                    onSave = {
                        // KHONG tat dirty o day: that bai duoc thi phai giu ban nhap.
                        lastSubmitted = RuleDraft(selected, reminderOnly, allowedSelected, targetMinutes)
                        saveInFlight = true
                        onUpdate(
                            selected.toList(),
                            reminderOnly,
                            allowedSelected.toList(),
                            targetMinutes
                        )
                    },
                    onDiscard = {
                        selected = serverDraft.blocked
                        reminderOnly = serverDraft.reminderOnly
                        allowedSelected = serverDraft.allowed
                        targetMinutes = serverDraft.minutes
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
 * Toan bo noi quy ma the nay soan thao, gom CA BON thu: app bi chan, che do
 * chan/nhac, app mien tran va han muc phut. Dung lam ban in cua `lastSubmitted`
 * de so voi snapshot tro ve tu may chủ — chi khi khop het bon thu moi dam bao
 * "✓ Đã lưu". So sanh theo gia tri (data class) nen `Set<String>` doi chieu
 * duoc du cho thu tu khong giong nhau.
 */
private data class RuleDraft(
    val blocked: Set<String>,
    val reminderOnly: Boolean,
    val allowed: Set<String>,
    val minutes: Int
)

/** Ai la nguoi can tra loi phieu dang mo (hoac se can, neu minh goi de xuat). */
private fun voteOwnerRole(s: UsageSchedule?, myRole: UserRole?, pending: Boolean): UserRole? = when {
    s != null && pending -> ConsentPolicy.voterOf(s)
    myRole != null -> ConsentPolicy.opposite(myRole)
    else -> null
}

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
    hasCounterparty: Boolean,
    votePending: Boolean,
    voterLabel: String,
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
                text = when {
                    saveState == BlockRulesSaveState.Saving -> "Đang gửi…"
                    hasCounterparty && countSelected == 0 ->
                        "Gửi đề xuất • không chặn app nào • chờ $voterLabel duyệt"
                    // Nguoi dung phai thay CHE DO ngay tren nut bam: cung
                    // "chan 5 app" ma "chi nhac" thi may khong chan gi ca.
                    hasCounterparty && savedIsReminderOnly && countSelected > 0 ->
                        "Gửi đề xuất • nhắc $countSelected app (chưa chặn thật) • chờ $voterLabel duyệt"
                    hasCounterparty ->
                        "Gửi đề xuất • chặn thật $countSelected app • chờ $voterLabel duyệt"
                    savedIsReminderOnly && countSelected > 0 ->
                        "Lưu • chỉ nhắc $countSelected app, chưa chặn"
                    countSelected == 0 -> "Lưu luật chặn (không chặn app nào)"
                    else -> "Lưu luật chặn • chặn thật $countSelected app"
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
                votePending -> StatusBanner(
                    text = "\u2713 Đã gửi đề xuất lúc ${formatTime(savedAtMillis)} — nội quy cũ vẫn đang chạy, " +
                        "chưa chặn gì thêm cho tới khi $voterLabel duyệt.",
                    tone = Tone.Ok
                )
                // "Áp dụng ngay" la sai khi may dang o che do nhac nho hoac
                // chua bat Tro nang — day chinh la ly do nguoi dung bam tich
                // xong thay app van chay binh thuong va tuong bo chan bi hong.
                savedIsReminderOnly -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — máy đang ở chế độ nhắc nhở, " +
                        "chưa chặn app nào (hạn mức $targetMinutes phút/ngày). Bật “Chặn thật” ở trên " +
                        "nếu muốn đẩy về Home.",
                    tone = Tone.Info
                )
                savedBlockedCount == 0 -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — hiện không có app nào bị chặn, " +
                        "hạn mức $targetMinutes phút/ngày.",
                    tone = Tone.Ok
                )
                else -> StatusBanner(
                    text = "\u2713 Đã lưu lúc ${formatTime(savedAtMillis)} — chặn thật $savedBlockedCount app, " +
                        "hạn mức $targetMinutes phút/ngày, áp dụng khi Trợ năng đang bật.",
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

