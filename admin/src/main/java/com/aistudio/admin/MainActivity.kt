package com.aistudio.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * QUAN TRI VIEN — APK rieng (`thanh.dev.app`), module `:admin`, cai song song
 * ben app chinh tren cung mot may.
 *
 * ============================================================================
 * BON THU APP NAY CO TINH KHONG LAM DUOC — VIET RA DAY DE KHONG AI PHAI HOI LAI
 * ============================================================================
 *
 * 1. **KHONG the hien mat khau cua nguoi dung.** Firebase Auth chi luu BAN HAM
 *    mot chieu (scrypt). Khong co bat ky API nao — client, Admin SDK, hay bang
 *    dieu khien — doc lai duoc chuoi mat khau. Muon "thay mat khau tat ca nguoi"
 *    thi app phai TU ghi mat khau nguyen van vao Firestore luc dang ky, tuc la
 *    chu dong tao ra mot danh sach mat khau that cua tre em nam hoac khong bao
 *    gio mat. Do la diem ban duoc trong mot bai thi ve bao mat, khong phai mot
 *    tinh nang. App nay khong lam vay va khong ai nen lam vay.
 *
 * 2. **KHONG the chan IP.** `firestore.rules` khong co bien nao ten la IP, va
 *    dien thoai di dong doi dia chi lien tuc qua CGNAT nha mang: chan mot IP
 *    hom nay thi hom sau trung nguoi khac, khong trung ke spam. Cach that la
 *    CAM THEO UID (nut "Cam" o day + collection `/bans/{uid}` + luat o may chu).
 *
 * 3. **KHONG the xoa tai khoan dang nhap.** Nut "Xóa tài khoản" o day don het
 *    moi document cua nguoi dung trong Firestore (ho so, vi tri, vet di chuyen,
 *    phieu SOS, noi quy + nhat ky, tin hieu cuoc goi) — nhung email + mat khau
 *    nam trong Firebase Authentication, ma app nay khong co Admin SDK. Dang nhap
 *    lai bang dung tai khoan ay se tao mot ho so TRONG. UI ghi ro dieu nay va
 *    in so document THUC XOA duoc chu khong noi "đã xóa tất cả".
 *
 * 4. **Cam khong phai nut bam la xong.** Lenh cam chi co hieu luc khi
 *    `firestore.rules` da duoc Publish trong bang dieu khien Firebase (ban 66.0
 *    them pham vi `scope` = ca app / chi hoi AI, va `expiresAt` = 7 ngay, 30 ngay
 *    hoac vinh vien). Chua Publish thi /bans chi la mot tep ghi chu.
 */

/**
 * Allowlist uid duoc vao man hinh quan tri. Day CHI LA CAN UI — quyen that nam
 * trong `firestore.rules` (ham `isAdmin()`), va hai ben phai trung nhau.
 *
 * `cSzzRXZzaVR83q2gQTnskhIxkzw2` = tai khoan email `khkt.admin@giadinhviet.vn`,
 * dang nhap bang "KHKT ADMIN" o man dang nhap, tao rieng cho muc dich nay.
 */
private val ADMIN_UIDS = setOf(
  "YU2BwDqvahSh1hUWx9NRAdOwvG33", // duongtrungthanh011020122@gmail.com (chu project)
  "cSzzRXZzaVR83q2gQTnskhIxkzw2"  // khkt.admin@giadinhviet.vn
)

/** Tai khoan email dung cho o "Ten dang nhap / Mat khau". */
private const val ADMIN_LOGIN_USER = "KHKT ADMIN"
private const val ADMIN_LOGIN_EMAIL = "khkt.admin@giadinhviet.vn"

private const val WEB_OAUTH_CLIENT =
  "931971500070-jfd9trfl7vpq14l82p2ro4vtvtoui03v.apps.googleusercontent.com"

private const val DATABASE_ID = "ai-studio-android-vespera-f0b8eaec-ca07-4549-bb58-a45233ead3c3"
private const val USERS = "users"
private const val BANS = "bans"
private const val LOCATIONS = "locations"
private const val HISTORY = "location_history"
private const val SOS = "sos_alerts"
private const val PAIRINGS = "pairings"
private const val CALLS = "calls"

/**
 * Bảy subcollection nằm DUOI một document ghép nối — đúng danh sách mà
 * `VesperaRepository.inventoryForDeletion` (module `:app`) quét. `:admin` không
 * dùng chung source với `:app`, nên thêm một loại dữ liệu mới là phải thêm ở
 * CẢ HAI chỗ, nếu không nút "xóa tài khoản" của hai phía sẽ cho hai kết quả
 * khác nhau trên cùng một tài khoản.
 */
private val PAIRING_SUBCOLLECTIONS = listOf(
  "schedules", "rule_audit", "agreements", "extra_time",
  "privacy_requests", "messages", "location_history"
)

/** Bon kieu lenh cam trong `firestore.rules` 66.0: `scope` x `expiresAt`. */
private const val SCOPE_ALL = "ALL"
private const val SCOPE_AI = "AI"
private const val DAY_MS = 24L * 60L * 60L * 1000L

/** Project KHKT khong co database `(default)` — phai lay instance theo ten. */
private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance(DATABASE_ID)

private val Night = Color(0xFF0A0A0F)
private val CardBg = Color(0xFF15151F)
private val CardBg2 = Color(0xFF1D1D2B)
private val Txt = Color(0xFFF2F2F7)
private val TxtDim = Color(0xFF9A9AB0)
private val Violet = Color(0xFF8B5CF6)
private val Rose = Color(0xFFFF4D6D)
private val Amber = Color(0xFFF5B942)
private val Mint = Color(0xFF3DDC97)

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme(
        colorScheme = darkColorScheme(
          background = Night,
          surface = CardBg,
          primary = Violet,
          onPrimary = Txt,
          onBackground = Txt,
          onSurface = Txt
        )
      ) { AdminApp() }
    }
  }
}

data class AdminRow(
  val uid: String,
  val displayName: String,
  val email: String,
  val role: String,
  val studentCode: String,
  val aiCalls: Long,
  // `aiToday` la so luot HOI CUA RIENG HOM NAY do may nguoi dung ghi len
  // (`aiUsageToday` + `aiUsageDay` trong firestore.rules, ban 64.0). -1 = tai
  // khoan chua bao gio duoc do, khong phai "hom nay 0 luot".
  val aiToday: Long,
  // `false` khi tai khoan dung tren hai may hoac may lech dong ho: duoc phep
  // nho lai so lieu cua ngay cu, vi du no thanh "hom nay im lang" sai.
  val aiTodayFresh: Boolean,
  val banned: Boolean,
  val banReason: String,
  /**
   * 'ALL' = cam dung app (nhu ban cu), 'AI' = chi cam hoi tro ly AI. Truong
   * `scope` trong `firestore.rules` 66.0; lenh cam cua ban 63.0 khong co truong
   * nay nen doc ra thanh 'ALL' — dung nghia cua no luc ghi.
   */
  val banScope: String,
  /** null = VINH VIEN; khong null = epoch millis cua `expiresAt` (7/30 ngày). */
  val banUntilMillis: Long?,
  /**
   * Document van con ma han da qua. Rules TU MO khi `expiresAt <= request.time`,
   * nen app phai in "đã hết hạn" chu khong duoc in "ĐANG BỊ CẤM" — neu khong
   * quan tri vien tuong minh dang cam mot nguoi trong khi may cua ho van chay
   * binh thuong, va document rac khong ai don.
   */
  val banExpired: Boolean
)

/**
 * Chu in len chip trang thai cam: pham vi + thoi han con lai. Noi ro "còn N
 * ngày" vi nguoi dung phai biet khi nao lenh tu het hieu luc, va phai biet lenh
 * do cat cai gi (ca app hay chi hoi AI).
 */
private fun banLabel(scope: String, until: Long?, expired: Boolean): String {
  val phamVi = if (scope == SCOPE_AI) "CẤM HỎI AI" else "CẤM APP"
  val thanHan = when {
    expired && until != null -> {
      val qua = kotlin.math.ceil((System.currentTimeMillis() - until) / DAY_MS.toDouble()).toLong()
      "ĐÃ HẾT HẠN ($qua ngày trước)"
    }
    expired -> "ĐÃ HẾT HẠN"
    until == null -> "· vĩnh viễn"
    else -> {
      val con = kotlin.math.ceil((until - System.currentTimeMillis()) / DAY_MS.toDouble()).toLong()
      if (con <= 1L) "· còn dưới 1 ngày" else "· còn $con ngày"
    }
  }
  return "$phamVi $thanHan"
}

/**
 * Bien nhan don tai khoan: X document THUC XOA / Y document KIEM DUOC, kem ghi
 * chu nhung nhom khong xoa duoc. In ca ba thu ra man hinh — noi "đã xóa tất cả
 * dữ liệu" khi rules con chan mot nhom la noi dua, va la ly do ben duoi phai
 * liet ke tung nhom thay vi xoa im lang.
 */
data class AdminWipeOutcome(
  val userId: String,
  val deleted: Int,
  val found: Int,
  val failed: List<String>
)

/**
 * Don mot tai khoan tu phia quan tri — moi document cua ho trong Firestore.
 *
 * THU TU trung het voi app nguoi dung (`VesperaRepository.deleteAccount`): ho
 * so truoc, roi vi tri → vet di chuyen → phieu SOS → tin hieu cuoc goi → noi
 * quy + nhat ky → document ghep noi cuoi cung (Firestore khong tu xoa
 * subcollection, va `isPairMember()` trong rules doc tren chinh document do).
 * Hai phia phai trung nhau vi nguoi dung co the tu xoa truoc khi quan tri kip
 * tay — hai nut "xóa tài khoản" cho hai ket qua khac nhau la bug, khong phai
 * tinh nang.
 *
 * CHI TIN DOCUMENT THUC SU TON TAI: xoa mot document khong co that duoc
 * Firestore coi la thanh cong (no-op), nen dem luon thi bien nhan se cao hon
 * thuc te — tinh huong rat thuong xay ra voi `locations/<uid>` cua may chua
 * tung bat dinh vi va `calls/room_<uid>` cua may chua tung goi.
 *
 * KHONG dong `bans/<uid>` theo. Lenh cam gan voi uid, ma uid do van con (app
 * nay khong xoa duoc tai khoan dang nhap). Don luon lenh cam thi mot tai khoan
 * bi cam chi can xoa roi dang nhap lai la het bi cam; muon mo thi phai bam
 * "Bỏ cấm" co chu dich.
 */
private suspend fun wipeAccountFromAdmin(uid: String): AdminWipeOutcome {
  var deleted = 0
  var found = 0
  val failed = mutableListOf<String>()
  val hoSo = db().collection(USERS).document(uid)

  // BUOC 1 — ho so. Dem ho so vao `found` chi khi no THUC SU ton tai: bam "Thử
  // lại" sau mot lan don thanh cong mot nua thi `users/<uid>` da mat, ma lenh
  // xoa document khong ton tai van duoc Firestore coi la thanh cong — tin vao
  // do la bien nhan tang them mot muc khong co that.
  // Doc khong duoc (rules chua Publish / mat mang) thi GIA SU con, de an toan
  // van thu xoa va bao loi that.
  val hoSoCon = try {
    hoSo.get().await().exists()
  } catch (e: Exception) {
    true
  }
  if (hoSoCon) {
    found++
    // Khong xoa duoc ho so thi dung lai het: quan tri xoa duoc tung thu con lai
    // bat ke ho so, nen tiep tuc se de lai tinh trang "nua nha da mat, nua nha
    // con nguyen" ma khong ai biet chuyen gi da xay ra giua chung.
    try {
      hoSo.delete().await()
      deleted++
    } catch (e: Exception) {
      return AdminWipeOutcome(uid, 0, found, listOf("hồ sơ /users/$uid (không xóa được: ${e.message})"))
    }
  }

  // BUOC 2 — liet ke. Day la ly do `firestore.rules` 66.0 mo `isAdmin()` cho
  // duong DOC o /locations, /location_history, /sos_alerts va sau (6) subcollection
  // duoi /pairings: id cua vet di chuyen va phieu SOS la ngau nhien, khong doc
  // duoc danh sach thi khong the xoa tung cai — app quan tri khong co Admin SDK,
  // va Firestore khong co lenh xoa theo truong.
  val pairRefs = mutableListOf<DocumentReference>()
  for (field in listOf("parentId", "childId")) {
    try {
      db().collection(PAIRINGS).whereEqualTo(field, uid).get().await().documents.forEach { d ->
        if (pairRefs.none { it.id == d.id }) pairRefs.add(d.reference)
      }
    } catch (e: Exception) {
      if ("ghép nối" !in failed) failed.add("ghép nối (không liệt kê được)")
    }
  }

  val groups = mutableListOf<Pair<String, List<DocumentReference>>>()

  suspend fun scan(label: String, block: suspend () -> List<DocumentReference>) {
    val list = try {
      block()
    } catch (e: Exception) {
      if (label !in failed) failed.add("$label (không đọc được danh sách)")
      emptyList()
    }
    found += list.size
    if (list.isNotEmpty()) groups.add(label to list)
  }

  scan("vị trí") {
    val ref = db().collection(LOCATIONS).document(uid)
    // `delete()` lenh document khong ton tai van "thanh cong" → phai loc truoc.
    if (ref.get().await().exists()) listOf(ref) else emptyList()
  }
  scan("lịch sử vị trí") {
    db().collection(HISTORY).whereEqualTo("userId", uid).get().await().documents
      .map { it.reference }
  }
  scan("phiếu SOS") {
    db().collection(SOS).whereEqualTo("userId", uid).get().await().documents.map { it.reference }
  }
  scan("tin hiệu cuộc gọi") {
    val ids = (pairRefs.map { it.id } + "room_$uid").distinct()
    ids.mapNotNull { id ->
      val call = db().collection(CALLS).document(id)
      if (call.get().await().exists()) call else null
    }
  }
  scan("nội quy & nhật ký ghép nối") {
    val out = mutableListOf<DocumentReference>()
    for (pair in pairRefs) {
      for (sub in PAIRING_SUBCOLLECTIONS) {
        out += pair.collection(sub).get().await().documents.map { it.reference }
      }
    }
    out
  }
  if ("ghép nối" !in failed) {
    found += pairRefs.size
    if (pairRefs.isNotEmpty()) groups.add("ghép nối" to pairRefs)
  }

  // BUOC 3 — xoa theo tung nhom. Mot document bi tu choi KHONG duoc lam dung ca
  // ke hoach: ho so da mat roi, nen phai don den cai cuoi cung roi moi bao cao.
  // Dem so loi trong nhom thay vi nuot loi, de bien nhan ghi ro "3 muc khong xoa
  // duoc" chu khong noi "da xoa het".
  for ((label, refs) in groups) {
    var errors = 0
    for (ref in refs) {
      try {
        ref.delete().await()
        deleted++
      } catch (e: Exception) {
        errors++
      }
    }
    if (errors > 0) failed.add("$label ($errors mục không xóa được)")
  }
  return AdminWipeOutcome(uid, deleted, found, failed)
}

/**
 * Muc "7 ngay / 30 ngay / vinh vien" gan duoc chon san khi mo hop thoai doi lenh
 * cam, de thao "Doi lenh cam" khong vo tinh doi luon thoi han dang chay. Lenh cu
 * het han roi thi chon lai tu dau (vinh vien) la hop ly nhat: document van nam
 * trong /bans nhung rules da mo.
 */
private fun presetDaysFor(until: Long?): Long {
  if (until == null) return 0L
  val con = until - System.currentTimeMillis()
  if (con <= 0L) return 0L
  return if (con <= 7L * DAY_MS) 7L else 30L
}

/** Han cua lenh cam, tinh bang giay de in ra mot dong "het han luc ...". */
private fun formatMillis(ms: Long): String =
  java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.US).format(java.util.Date(ms))

/**
 * Nguong trong app nguoi dung (AiUsageCounter.WARN_AT / DAILY_LIMIT). Vi module
 * `:admin` khong dung chung source voi `:app` nen phai ghi lai hai hang so nay,
 * va do la ly do chung co chu thich o ca hai cho: doi nguong o day ma khong doi
 * o ben kia la dua so lieu mau lech nhau.
 */
private const val AI_WARN_AT = 50L
private const val AI_DAILY_LIMIT = 100L

/** "yyyy-MM-dd" theo gio VN — trung cach tinh ngay cua AiUsageCounter. */
private fun todayKeyVn(): String {
  val calendar = java.util.Calendar.getInstance(
    java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
  )
  return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(calendar.time)
}

@Composable
private fun Chip(text: String, color: Color) {
  Box(
    Modifier
      .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/**
 * Nut chon mot gia tri trong hai ba lua chon (pham vi lenh cam / thoi han).
 * Dung Button Thuong vs OutlinedButton lam trang thai "duoc chon" — khong can
 * them RadioButton/Checkbox vao imports va cung khong dem chu "chuyen muc"
 * len man cam, vi muc tieu o day la bam mot cai la xong.
 */
@Composable
private fun ChoiceButton(
  text: String,
  selected: Boolean,
  selectedColor: Color = Violet,
  onClick: () -> Unit
) {
  if (selected) {
    Button(
      onClick = onClick,
      colors = ButtonDefaults.buttonColors(containerColor = selectedColor)
    ) { Text(text, color = Txt, fontWeight = FontWeight.Bold) }
  } else {
    OutlinedButton(onClick = onClick) { Text(text, color = TxtDim) }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminApp() {
  val auth = remember { FirebaseAuth.getInstance() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val rows = remember { mutableStateListOf<AdminRow>() }

  var user by remember { mutableStateOf(auth.currentUser) }
  var status by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var login by remember { mutableStateOf("") }
  var pass by remember { mutableStateOf("") }
  var query by remember { mutableStateOf("") }
  var sortByAi by remember { mutableStateOf(true) }
  // Loc "chi ai vuot nguong hom nay" — day la con duong de NGUOI QUET dinh ai
  // dang bi spam, con viec CAM van la mot nut bam cua ho. Ban dau ke ho tu dong
  // cam khi du 100 luot, da doi: may cham may con tre, khong nen co chuyen do
  // xay ra ma khong co ai xem lai.
  var onlyOverQuota by remember { mutableStateOf(false) }
  var banTarget by remember { mutableStateOf<AdminRow?>(null) }
  // Nut "Xóa tài khoản" (66.0) co hai trang thai trong CUNG mot hop thoai:
  // xac nhan (chua chay) va bien nhan (da chay) — de nguoi bam nhin thay ngay
  // bao nhieu muc bi xoa va nhom nao rules con chan, thay vi chi thay dong
  // "dang lam..." roi mat hut.
  var deleteTarget by remember { mutableStateOf<AdminRow?>(null) }
  var wipeRunning by remember { mutableStateOf(false) }
  var wipeOutcome by remember { mutableStateOf<AdminWipeOutcome?>(null) }

  val isAdmin = user?.uid?.let { it in ADMIN_UIDS } == true

  fun refresh() {
    busy = true
    status = "Dang doc..."
    scope.launch {
      try {
        val banDoc = mutableMapOf<String, Map<String, Any?>>()
        try {
          db().collection(BANS).get().await().documents.forEach {
            banDoc[it.id] = it.data ?: emptyMap()
          }
        } catch (e: Exception) {
          status = "Doc /bans bi tu choi (firestore.rules chua Publish?): ${e.message}"
        }
        val users = db().collection(USERS).get().await()
        val today = todayKeyVn()
        val now = System.currentTimeMillis()
        val out = users.documents.map { d ->
          val ban = banDoc[d.id]
          // `expiresAt` do chinh app quan tri ghi len bang Timestamp, nen doc ve
          // phai la `Timestamp` — dung `as? Number` vi Firestore tra timestamp
          // duoi dang object, khong phai millis.
          val until = (ban?.get("expiresAt") as? com.google.firebase.firestore.Timestamp)?.toMillis()
          AdminRow(
            uid = d.id,
            displayName = d.getString("displayName") ?: "(khong co ten)",
            email = d.getString("email") ?: "",
            role = d.getString("role") ?: "?",
            studentCode = d.getString("studentCode") ?: "",
            aiCalls = (d.get("aiCallCount") as? Number)?.toLong() ?: -1L,
            aiToday = (d.get("aiUsageToday") as? Number)?.toLong() ?: -1L,
            aiTodayFresh = d.getString("aiUsageDay") == today,
            banned = ban != null && (until == null || until > now),
            banExpired = ban != null && until != null && until <= now,
            banReason = (ban?.get("reason") as? String) ?: "",
            // Lenh cam cua ban 63.0 khong co `scope`: doc thanh 'ALL' dung nhu
            // luc no duoc ghi (cam dung app), dung like rules `isBannedFor()`.
            banScope = (ban?.get("scope") as? String) ?: SCOPE_ALL,
            banUntilMillis = until
          )
        }
        rows.clear()
        rows += out
        val touched = out.count { it.aiTodayFresh && it.aiToday >= AI_WARN_AT }
        val hetHan = out.count { it.banExpired }
        status = "${out.size} tai khoan  ·  ${out.count { it.banned }} dang bi cam  ·  " +
          (if (hetHan > 0) "$hetHan lenh cam DA HET HAN (van nam trong /bans, bam 'Bỏ cấm' de don)  ·  " else "") +
          "${out.sumOf { if (it.aiCalls > 0) it.aiCalls else 0L }} luot goi AI da ghi nhan  ·  " +
          "$touched tai khoan dat $AI_WARN_AT luot hom nay"
      } catch (e: Exception) {
        status = "Khong doc duoc: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  fun signInEmail() {
    busy = true
    status = "Dang dang nhap..."
    scope.launch {
      try {
        val email =
          if (login.trim().equals(ADMIN_LOGIN_USER, ignoreCase = true)) ADMIN_LOGIN_EMAIL
          else login.trim()
        val r = auth.signInWithEmailAndPassword(email, pass).await()
        user = r.user
        status = ""
      } catch (e: Exception) {
        status = "Dang nhap that bai: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  fun signInGoogle() {
    busy = true
    status = "Dang mo chon tai khoan..."
    scope.launch {
      try {
        val req = GetCredentialRequest.Builder()
          .addCredentialOption(
            GetGoogleIdOption.Builder()
              .setServerClientId(WEB_OAUTH_CLIENT)
              .setFilterByAuthorizedAccounts(false)
              .build()
          )
          .build()
        val res = CredentialManager.create(context).getCredential(context, req)
        val cred = res.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
          val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
          val r = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
          user = r.user
          status = ""
        } else {
          status = "Khong nhan duoc Google id token."
        }
      } catch (e: Exception) {
        status = "Dang nhap Google that bai: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  // Vao duoc dashboard la doc ngay — nguoi dung khong phai tim nut "Lam moi"
  // chi de thay danh sach dau tien.
  LaunchedEffect(user?.uid, isAdmin) {
    if (isAdmin && rows.isEmpty()) refresh()
  }

  Scaffold(containerColor = Night) { pad ->
    Column(
      Modifier
        .fillMaxSize()
        .padding(pad)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Spacer(Modifier.height(6.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("QUẢN TRỊ VIÊN", color = Violet, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
        Spacer(Modifier.width(10.dp))
        Text("Gia Đình Việt", color = TxtDim, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (user != null) {
          TextButton(onClick = {
            auth.signOut(); user = null; rows.clear(); status = ""
          }) { Text("Đăng xuất", color = TxtDim) }
        }
      }

      if (user == null) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
          Column(Modifier.padding(18.dp), Arrangement.spacedBy(10.dp)) {
            Text("ĐĂNG NHẬP QUẢN TRỊ", color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            OutlinedTextField(
              value = login, onValueChange = { login = it },
              label = { Text("Tên đăng nhập") },
              placeholder = { Text(ADMIN_LOGIN_USER) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth(),
              textStyle = MaterialTheme.typography.bodyLarge.copy(color = Txt)
            )
            OutlinedTextField(
              value = pass, onValueChange = { pass = it },
              label = { Text("Mật khẩu") },
              singleLine = true,
              visualTransformation = PasswordVisualTransformation(),
              modifier = Modifier.fillMaxWidth(),
              textStyle = MaterialTheme.typography.bodyLarge.copy(color = Txt)
            )
            Button(
              onClick = { signInEmail() },
              enabled = !busy && login.isNotBlank() && pass.isNotBlank(),
              colors = ButtonDefaults.buttonColors(containerColor = Violet),
              modifier = Modifier.fillMaxWidth()
            ) { Text("Đăng nhập", fontWeight = FontWeight.Bold) }
            OutlinedButton(onClick = { signInGoogle() }, modifier = Modifier.fillMaxWidth()) {
              Text("Hoặc đăng nhập bằng Gmail chủ dự án", color = TxtDim)
            }
            Text(
              "Đây là tài khoản thật trong Firebase; uid của nó nằm trong allowlist ở " +
                "firestore.rules. Mật khẩu chỉ dùng để ĐĂNG NHẬP — app không đọc được " +
                "mật khẩu của bất kỳ tài khoản nào khác.",
              color = TxtDim, fontSize = 11.sp
            )
          }
        }
        if (status.isNotEmpty()) Text(status, color = Rose, fontSize = 12.sp)
        return@Column
      }

      if (!isAdmin) {
        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
          Column(Modifier.padding(18.dp), Arrangement.spacedBy(8.dp)) {
            Text("TÀI KHOẢN NÀY KHÔNG PHẢI QUẢN TRỊ", color = Rose, fontWeight = FontWeight.Black)
            Text("uid: ${user?.uid}", color = Txt, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text("email: ${user?.email ?: "?"}", color = TxtDim, fontSize = 12.sp)
            Text(
              "Muốn cấp quyền: thêm đúng uid này vào ADMIN_UIDS (file này) VÀ vào hàm " +
                "isAdmin() trong firestore.rules, rồi build lại và Publish rules. Chỉ sửa " +
                "một trong hai chỗ thì vẫn không dùng được — đó là chủ ý.",
              color = TxtDim, fontSize = 12.sp
            )
          }
        }
        return@Column
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
          onClick = { refresh() },
          enabled = !busy,
          colors = ButtonDefaults.buttonColors(containerColor = Violet)
        ) { Text("Làm mới") }
        OutlinedTextField(
          value = query, onValueChange = { query = it },
          placeholder = { Text("Tìm tên / email / uid", color = TxtDim) },
          singleLine = true,
          modifier = Modifier.weight(1f),
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = Txt)
        )
        OutlinedButton(onClick = { sortByAi = !sortByAi }) {
          Text(if (sortByAi) "Xếp: hôm nay" else "Xếp: tên", color = TxtDim, fontSize = 12.sp)
        }
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { onlyOverQuota = !onlyOverQuota }) {
          Text(
            if (onlyOverQuota) "Đang lọc: ≥ $AI_WARN_AT lượt hôm nay" else "Lọc: ≥ $AI_WARN_AT lượt hôm nay",
            color = if (onlyOverQuota) Amber else TxtDim,
            fontSize = 12.sp
          )
        }
        Text(
          "Chạm 100 lượt là app phía người dùng tự ngừng cho hỏi; ở đây không ai bị " +
            "cấm tự động — muốn cấm thì bấm nút CẤM.",
          color = TxtDim, fontSize = 11.sp, modifier = Modifier.weight(1f)
        )
      }

      if (status.isNotEmpty()) Text(status, color = Amber, fontSize = 12.sp)

      // So sanh viet bang Comparator thu cong. Chuoi `compareBy<AdminRow, String>
      // { it.displayName }` tung lam `:admin:compileDebugKotlin` chet ngay tren CI
      // (Unresolved reference 'it' — buoc 63.0), nen khong dung chuoi suy kieu o
      // day nua; thu do dung tren may nguoi viet nhung khong on dinh khi build.
      val comparator = Comparator<AdminRow> { a, b ->
        if (sortByAi) {
          // Tie thu 1 la LUOT HOM NAY, tie thu 2 moi den tong ca doi. Y cua
          // nguoi dung la "thang nao dang spam hom nay" — sap xep theo tong so
          // thi mot tai khoan im lang nam nay lai ngot hang dau.
          val ta = if (a.aiTodayFresh && a.aiToday >= 0) a.aiToday else -1L
          val tb = if (b.aiTodayFresh && b.aiToday >= 0) b.aiToday else -1L
          when {
            tb > ta -> 1
            tb < ta -> -1
            else -> {
              // Tai khoan chua duoc do (-1) luon ep xuong cuoi, de "Xep" khong
              // tao ra hang dau gia cho nguoi chua tung goi AI.
              val ca = if (a.aiCalls < 0) -1L else a.aiCalls
              val cb = if (b.aiCalls < 0) -1L else b.aiCalls
              when {
                cb > ca -> 1
                cb < ca -> -1
                else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
              }
            }
          }
        } else {
          val byName = a.displayName.compareTo(b.displayName, ignoreCase = true)
          if (byName != 0) byName else a.uid.compareTo(b.uid)
        }
      }

      val shown = rows
        .filter {
          (!onlyOverQuota || (it.aiTodayFresh && it.aiToday >= AI_WARN_AT)) &&
            (
              query.isBlank() ||
                it.displayName.contains(query, true) ||
                it.email.contains(query, true) ||
                it.uid.contains(query, true) ||
                it.studentCode.contains(query, true)
              )
        }
        .sortedWith(comparator)
      val ranked: List<AdminRow> = shown.filter {
        (it.aiTodayFresh && it.aiToday > 0) || it.aiCalls > 0
      }

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(shown, key = { it.uid }) { row ->
          val rank = if (row.aiCalls > 0) ranked.indexOfFirst { it.uid == row.uid } + 1 else -1
          Card(colors = CardDefaults.cardColors(containerColor = if (row.banned) Color(0xFF2A1220) else CardBg)) {
            Column(Modifier.padding(14.dp), Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = if (rank > 0) "#$rank" else "–",
                  color = if (rank in 1..3) Amber else TxtDim,
                  fontWeight = FontWeight.Black, fontSize = 18.sp,
                  modifier = Modifier.width(38.dp)
                )
                Column(Modifier.weight(1f)) {
                  Text(row.displayName, color = Txt, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                  Text(row.email.ifEmpty { "(khong co email)" }, color = TxtDim, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  // Cong them chuot duoi: 100 la do CHINH APP cat duong hoi,
                  // mau hong la "nguoi dung nay hom nay khong goi AI duoc nua",
                  // khong phai "da bi cam". Hai chuyen khong giong nhau.
                  Chip(
                    when {
                      !row.aiTodayFresh || row.aiToday < 0 -> "Hôm nay: chưa đo"
                      row.aiToday >= AI_DAILY_LIMIT -> "Hôm nay ${row.aiToday}/$AI_DAILY_LIMIT · đã chặn hỏi"
                      else -> "Hôm nay ${row.aiToday}/$AI_DAILY_LIMIT"
                    },
                    when {
                      !row.aiTodayFresh || row.aiToday < 0 -> TxtDim
                      row.aiToday >= AI_DAILY_LIMIT -> Rose
                      row.aiToday >= AI_WARN_AT -> Amber
                      else -> Mint
                    }
                  )
                  Chip(
                    if (row.aiCalls < 0) "AI: chưa có số liệu" else "AI: ${row.aiCalls} lượt",
                    if (row.aiCalls < 0) TxtDim else if (rank in 1..3) Amber else Mint
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(if (row.role == "PARENT") "PHỤ HUYNH" else "HỌC SINH", if (row.role == "PARENT") Violet else Mint)
                    // Chip noi RO pham vi + han len, vi "CẤM" mot chuong thi
                    // khong biet la cam ca app hay chi hoi AI, va con bao lau.
                    if (row.banned || row.banExpired) {
                      Chip(
                        banLabel(row.banScope, row.banUntilMillis, row.banExpired),
                        if (row.banExpired) TxtDim else Rose
                      )
                    }
                  }
                }
              }
              Text("uid ${row.uid}", color = TxtDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
              if (row.studentCode.isNotEmpty()) {
                Text("mã ghép nối: ${row.studentCode}", color = TxtDim, fontSize = 11.sp)
              }
              if ((row.banned || row.banExpired) && row.banReason.isNotEmpty()) {
                Text("lý do cấm: ${row.banReason}", color = Rose, fontSize = 11.sp)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.banned || row.banExpired) {
                  OutlinedButton(onClick = {
                    scope.launch {
                      try {
                        db().collection(BANS).document(row.uid).delete().await()
                        status = "Đã bỏ cấm ${row.displayName} (xóa /bans/${row.uid})."
                        refresh()
                      } catch (e: Exception) { status = "Bỏ cấm thất bại: ${e.message}" }
                    }
                  }) { Text("Bỏ cấm", color = Mint) }
                }
                Button(
                  // Ban 66.0: mot nut cho bon lenh (7 ngay / 30 ngay / vinh vien
                  // / chi cam hoi AI), nen mo hop thoai chu khong ghi ngay. Da
                  // co lenh roi thi nut doi thanh "Đổi lệnh cấm" de khoi ghi de
                  // document cu.
                  onClick = { banTarget = row },
                  colors = ButtonDefaults.buttonColors(containerColor = Rose)
                ) { Text(if (row.banned || row.banExpired) "Đổi lệnh cấm" else "Cấm") }
                OutlinedButton(onClick = { deleteTarget = row }) {
                  Text("Xóa tài khoản", color = Rose)
                }
                Spacer(Modifier.weight(1f))
                Text("mật khẩu: không xem được", color = TxtDim, fontSize = 10.sp)
              }
            }
          }
        }

        item {
          Card(colors = CardDefaults.cardColors(containerColor = CardBg2)) {
            Column(Modifier.padding(14.dp), Arrangement.spacedBy(6.dp)) {
              Text(
                "TẠI SAO KHÔNG CÓ CỘT MẬT KHẨU",
                color = Violet, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp
              )
              Text(
                "Firebase chỉ lưu bản băm một chiều của mật khẩu, không có API nào đọc lại " +
                  "thành chữ — kể cả tài khoản quản trị hay bảng điều khiển. App nào hiện " +
                  "được mật khẩu của mọi người là app đã tự ghi mật khẩu nguyên văn lên máy " +
                  "chủ, tức tự tạo ra một danh sách mật khẩu thật của trẻ em. Đó là lỗ hổng, " +
                  "không phải tính năng.",
                color = TxtDim, fontSize = 12.sp
              )
              Text(
                "Tương tự, không chặn được theo IP: quy tắc bảo mật Firestore không có biến " +
                  "IP, và điện thoại đổi IP liên tục qua mạng di động nên chặn IP vừa không " +
                  "làm được vừa không trúng ai. Nút CẤM ở trên chặn theo uid — đó là cách có " +
                  "thật, và phải Publish firestore.rules thì lệnh cấm mới thực sự hiệu lực.",
                color = TxtDim, fontSize = 12.sp
              )
            }
          }
          Spacer(Modifier.height(24.dp))
        }
      }
    }
  }

  banTarget?.let { target ->
    // Nho lenh cu khi mo hop thoai "Đổi lệnh cấm": neu khong dung `remember`
    // theo khoa uid thi lan mo tiep theo van giu nguyen gia tri lan truoc —
    // doi sang cam mot nguoi khac ma ly do van la cua nguoi truoc.
    var reason by remember(target.uid) {
      mutableStateOf(target.banReason.ifEmpty { "spam / lạm dụng AI" })
    }
    // 'ALL' = cam dung app (may chu tu choi moi lan doc/ghi du lieu gia dinh),
    // 'AI' = chi cat duong GHI BO DEM luot hoi AI tren /users. Hai thu khong
    // giong nhau ve hieu luc, nen hop thoai phai noi ro chu khong de quan tri
    // tu doan rang minh vua "tat" duoc cuoc hoi AI.
    var phamVi by remember(target.uid) { mutableStateOf(target.banScope) }
    // 0 = VINH VIEN (rules: khong ghi `expiresAt`); 7 / 30 = tu mo sau han.
    // `banWriteIsValid` tu choi `expiresAt <= request.time` nen khong the ghi
    // qua khu, va mac dinh bat theo lenh cu de "doi lenh" khong vo tinh "doi han".
    var soNgay by remember(target.uid) { mutableStateOf(presetDaysFor(target.banUntilMillis)) }
    val hetLuc = if (soNgay > 0L) System.currentTimeMillis() + soNgay * DAY_MS else null
    val dangCoLenh = target.banned || target.banExpired

    AlertDialog(
      onDismissRequest = { banTarget = null },
      containerColor = CardBg,
      title = {
        Text(
          if (dangCoLenh) "Đổi lệnh cấm ${target.displayName}?" else "Cấm ${target.displayName}?",
          color = Txt
        )
      },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (dangCoLenh) {
            Text(
              "Lệnh đang áp: ${banLabel(target.banScope, target.banUntilMillis, target.banExpired)}" +
                (if (target.banUntilMillis == null) "" else " · đến ${formatMillis(target.banUntilMillis)}"),
              color = Amber, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
          }
          OutlinedTextField(
            value = reason, onValueChange = { reason = it },
            label = { Text("Lý do (in ra trong /bans)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Txt)
          )
          Text("PHẠM VI", color = Violet, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceButton("Cả ứng dụng", phamVi == SCOPE_ALL, Rose) { phamVi = SCOPE_ALL }
            ChoiceButton("Chỉ hỏi AI", phamVi == SCOPE_AI, Rose) { phamVi = SCOPE_AI }
          }
          Text("THỜI HẠN", color = Violet, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChoiceButton("7 ngày", soNgay == 7L) { soNgay = 7L }
            ChoiceButton("30 ngày", soNgay == 30L) { soNgay = 30L }
            ChoiceButton("Vĩnh viễn", soNgay == 0L) { soNgay = 0L }
          }
          Text(
            if (hetLuc == null) {
              "Không tự hết hạn: máy đó chỉ mở lại được khi bạn bấm “Bỏ cấm”."
            } else {
              "Tự mở lại sau ${soNgay} ngày — hết hạn lúc ${formatMillis(hetLuc)}. " +
                "Document van nam trong /bans cho toi khi ban don no, nen danh sach " +
                "se con ghi " + banLabel(phamVi, hetLuc, false) + "."
            },
            color = TxtDim, fontSize = 12.sp
          )
          // Mot hop thoai "Cam" ma noi giong het cho ca hai pham vi la noi dua:
          // lenh 'AI' KHONG the chan cau hoi that, vi yeu cau AI di thang tu may
          // len nha cung cap bang khoa nam trong APK (task #45). Rules chi cat
          // duong bo dem, de so lieu hom nay khong con do client tu cat guc.
          Text(
            if (phamVi == SCOPE_AI) {
              "'Chỉ hỏi AI' cắt đường ghi BỘ ĐẾM lượt trên máy chủ, nên máy đó không " +
                "còn tự nâng giới hạn của mình được nữa. Nó KHÔNG cắt được chính cuộc gọi " +
                "tới trợ lý AI — câu hỏi đi thẳng từ điện thoại lên nhà cung cấp bằng " +
                "khóa có sẵn trong APK. Chặn tận gốc cần một proxy đứng giữa (chưa làm)."
            } else {
              "'Cả ứng dụng' làm máy chủ từ chối mọi lần đọc và ghi dữ liệu gia đình " +
                "của uid này: bản đồ, lịch sử vị trí, nội quy, tin nhắn, phiếu SOS và " +
                "cả bộ đếm lượt AI."
            },
            color = TxtDim, fontSize = 12.sp
          )
          Text(
            "Ghi vào /bans/${target.uid}. Lệnh chỉ có hiệu lực sau khi bạn bấm Publish " +
              "cho firestore.rules trong bảng điều khiển Firebase — chưa Publish thì " +
              "/bans mới chỉ là một tệp ghi chú.",
            color = Amber, fontSize = 12.sp
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            val payload = mutableMapOf<String, Any>(
              // `uid` KHONG phai thu tua: rules doi request.resource.data.uid ==
              // uid document, va `scope` phai mot trong ['ALL','AI']. Thieu mot
              // trong hai thu la PERMISSION_DENIED — ghi lenh that bai vi ly do
              // khong lien quan gi den bao mat.
              "uid" to target.uid,
              "displayName" to target.displayName,
              "email" to target.email,
              "reason" to reason.trim(),
              "scope" to phamVi,
              "by" to (user?.uid ?: ""),
              "at" to FieldValue.serverTimestamp()
            )
            if (hetLuc != null) {
              payload["expiresAt"] =
                com.google.firebase.firestore.Timestamp(java.util.Date(hetLuc))
            }
            try {
              db().collection(BANS).document(target.uid).set(payload).await()
              status = "Đã ghi lệnh ${if (phamVi == SCOPE_AI) "cấm hỏi AI" else "cấm dùng app"} cho " +
                "${target.displayName} · " +
                (if (hetLuc == null) "vĩnh viễn (tự mở khi bạn bấm “Bỏ cấm”)" else "hết hạn ${formatMillis(hetLuc)}") +
                ". Nhớ Publish firestore.rules nếu chưa."
              refresh()
            } catch (e: Exception) {
              status = "Cấm thất bại: ${e.message}. " +
                "Nếu là PERMISSION_DENIED thì firestore.rules chưa được Publish."
            }
          }
          banTarget = null
        }) {
          Text(
            if (dangCoLenh) "Ghi lệnh mới" else "Cấm",
            color = Rose, fontWeight = FontWeight.Bold
          )
        }
      },
      dismissButton = {
        TextButton(onClick = { banTarget = null }) { Text("Quay lại", color = TxtDim) }
      }
    )
  }

  // "Xóa tài khoản" phia quan tri (66.0): XOA THAT, va in bien nhan that.
  //
  // Ba cai ma nut nay KHONG lam duoc — viet ca vao hop thoai, vi do la thu
  // nguoi van hanh phai biet truoc khi bam chu khong phai sau khi bam:
  //  1. Email + mat khau nam trong Firebase Authentication; app khong co Admin
  //     SDK nen khong xoa duoc. Dang nhap lai bang tai khoan ay se tao mot ho so
  //     TRONG, khong phai "tai khoan da bi xoa han".
  //  2. Document ghep noi la cua CHUNG hai may — xoa la may ben kia mat luon
  //     lien ket va phai ghep lai bang ma.
  //  3. Lenh cam CO Y giu lai: neu don ca /bans thi mot tai khoan bi cam chi
  //     can "xóa tài khoản" roi dang nhap lai la het bi cam.
  deleteTarget?.let { target ->
    val receipt = wipeOutcome
    AlertDialog(
      onDismissRequest = {
        if (!wipeRunning) {
          deleteTarget = null
          wipeOutcome = null
        }
      },
      containerColor = CardBg,
      title = {
        Text(
          if (receipt == null) "Xóa hẳn ${target.displayName}?" else "Biên nhận xóa cho ${target.displayName}",
          color = Txt
        )
      },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          if (receipt == null) {
            Text(
              "Liệt kê và xóa từng document của uid ${target.uid}: hồ sơ /users, " +
                "vị trí, lịch sử vị trí, phiếu SOS, tin hiệu cuộc gọi, nội quy + " +
                "nhật ký và các bộ sưu tập con, rồi document ghép nối cuối cùng.",
              color = TxtDim, fontSize = 12.sp
            )
            Text(
              "Xong là KHÔNG hoàn tác được — ứng dụng không giữ bản sao lưu nào.",
              color = Rose, fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            Text(
              "Sẽ CÒN sau khi xóa:\n" +
                "· Email + mật khẩu trong Firebase Authentication (app này không có " +
                "Admin SDK) — người đó đăng nhập lại sẽ tạo một hồ sơ TRỐNG.\n" +
                "· Lệnh cấm trong /bans/${target.uid}, cố ý giữ lại để \"xóa rồi đăng " +
                "nhập lại\" không thành một đường thoát án.\n" +
                "· Máy bên kia của cùng ghép nối: document ghép nối là của hai máy, " +
                "xóa là máy còn lại MẤT liên kết và phải ghép lại bằng mã.",
              color = TxtDim, fontSize = 12.sp
            )
          } else {
            Text(
              "Đã xóa ${receipt.deleted}/${receipt.found} mục trên máy chủ (uid ${receipt.userId}).",
              color = if (receipt.failed.isEmpty()) Mint else Amber,
              fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            if (receipt.failed.isEmpty()) {
              Text(
                "Không có mục nào bị rules chặn — toàn bộ những gì liệt kê được đã mất.",
                color = Mint, fontSize = 12.sp
              )
            } else {
              Text(
                "KHÔNG xóa được: ${receipt.failed.joinToString(" · ")}.\n" +
                  "Thường là vì firestore.rules chưa Publish (PERMISSION_DENIED) hoặc " +
                  "mất mạng. Bấm “Thử lại” sau khi Publish, hoặc dọn tay từng document " +
                  "trong bảng điều khiển Firebase.",
                color = Rose, fontSize = 12.sp
              )
            }
            Text(
              "VẪN CÒN: email + mật khẩu trong Firebase Authentication, và lệnh cấm " +
                "(nếu có) trong /bans/${receipt.userId}. Đăng nhập lại bằng tài khoản đó " +
                "sẽ tạo một hồ sơ trống.",
              color = TxtDim, fontSize = 12.sp
            )
          }
        }
      },
      confirmButton = {
        if (receipt == null) {
          TextButton(onClick = {
            wipeRunning = true
            scope.launch {
              try {
                wipeOutcome = wipeAccountFromAdmin(target.uid)
                status = "Đã xóa ${wipeOutcome?.deleted}/${wipeOutcome?.found} mục của " +
                  "${target.displayName} — xem biên nhận trong hộp thoại."
              } catch (e: Exception) {
                wipeOutcome = AdminWipeOutcome(target.uid, 0, 0, listOf("không chạy được: ${e.message}"))
                status = "Xóa tài khoản thất bại: ${e.message}"
              } finally {
                wipeRunning = false
                refresh()
              }
            }
          }, enabled = !wipeRunning) {
            Text(
              if (wipeRunning) "Đang xóa…" else "Xóa trên máy chủ",
              color = Rose, fontWeight = FontWeight.Bold
            )
          }
        } else {
          // "Thử lại" la cach duy nhat de don tiep mot lan da giua chung: thu tu
          // xoa khong co transaction, nen lan chay lai se bo qua ho so da mat va
          // don dung nhung nhom con lai.
          TextButton(onClick = {
            wipeRunning = true
            scope.launch {
              try {
                wipeOutcome = wipeAccountFromAdmin(target.uid)
              } catch (e: Exception) {
                wipeOutcome = AdminWipeOutcome(target.uid, 0, 0, listOf("không chạy được: ${e.message}"))
              } finally {
                wipeRunning = false
                refresh()
              }
            }
          }, enabled = !wipeRunning) {
            Text(if (wipeRunning) "Đang thử lại…" else "Thử lại", color = Amber, fontWeight = FontWeight.Bold)
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            deleteTarget = null
            wipeOutcome = null
          },
          enabled = !wipeRunning
        ) { Text(if (receipt == null) "Quay lại" else "Đóng", color = TxtDim) }
      }
    )
  }
}

