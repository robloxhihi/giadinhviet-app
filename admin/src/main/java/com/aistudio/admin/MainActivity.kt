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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * QUAN TRI VIEN — APK rieng (`thanh.dev.app`), module `:admin`, cai song song
 * ben app chinh tren cung mot may.
 *
 * ============================================================================
 * BA THU APP NAY CO TINH KHONG LAM DUOC — VIET RA DAY DE KHONG AI PHAI HOI LAI
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
 * 3. **Nut "Xoa ho so" o day chi xoa document `/users/{uid}`.** Tai khoan trong
 *    Authentication van con, muon xoa han phai xoa bang bang dieu khien Firebase
 *    hoac Cloud Functions. UI ghi ro dieu nay, khong noi "xoa tai khoan".
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
  val banned: Boolean,
  val banReason: String
)

@Composable
private fun Chip(text: String, color: Color) {
  Box(
    Modifier
      .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
      .padding(horizontal = 8.dp, vertical = 3.dp)
  ) { Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
  var banTarget by remember { mutableStateOf<AdminRow?>(null) }

  val isAdmin = user?.uid?.let { it in ADMIN_UIDS } == true

  fun refresh() {
    busy = true
    status = "Dang doc..."
    scope.launch {
      try {
        val banDoc = mutableMapOf<String, String>()
        try {
          db().collection(BANS).get().await().documents.forEach {
            banDoc[it.id] = it.getString("reason") ?: ""
          }
        } catch (e: Exception) {
          status = "Doc /bans bi tu choi (firestore.rules chua Publish?): ${e.message}"
        }
        val users = db().collection(USERS).get().await()
        val out = users.documents.map { d ->
          AdminRow(
            uid = d.id,
            displayName = d.getString("displayName") ?: "(khong co ten)",
            email = d.getString("email") ?: "",
            role = d.getString("role") ?: "?",
            studentCode = d.getString("studentCode") ?: "",
            aiCalls = (d.get("aiCallCount") as? Number)?.toLong() ?: -1L,
            banned = banDoc.containsKey(d.id),
            banReason = banDoc[d.id] ?: ""
          )
        }
        rows.clear()
        rows += out
        status = "${out.size} tai khoan  ·  ${out.count { it.banned }} dang bi cam  ·  " +
          "${out.sumOf { if (it.aiCalls > 0) it.aiCalls else 0L }} luot goi AI da ghi nhan"
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
          Text(if (sortByAi) "Xếp: AI" else "Xếp: tên", color = TxtDim, fontSize = 12.sp)
        }
      }

      if (status.isNotEmpty()) Text(status, color = Amber, fontSize = 12.sp)

      // So sanh viet bang Comparator thu cong. Chuoi `compareBy<AdminRow, String>
      // { it.displayName }` tung lam `:admin:compileDebugKotlin` chet ngay tren CI
      // (Unresolved reference 'it' — buoc 63.0), nen khong dung chuoi suy kieu o
      // day nua; thu do dung tren may nguoi viet nhung khong on dinh khi build.
      val comparator = Comparator<AdminRow> { a, b ->
        if (sortByAi) {
          // Tai khoan chua duoc do (-1) luon ep xuong cuoi, de "Xep: AI" khong
          // tao ra hang dau gia cho nguoi chua tung goi AI.
          val ca = if (a.aiCalls < 0) -1L else a.aiCalls
          val cb = if (b.aiCalls < 0) -1L else b.aiCalls
          when {
            cb > ca -> 1
            cb < ca -> -1
            else -> a.displayName.compareTo(b.displayName, ignoreCase = true)
          }
        } else {
          val byName = a.displayName.compareTo(b.displayName, ignoreCase = true)
          if (byName != 0) byName else a.uid.compareTo(b.uid)
        }
      }

      val shown = rows
        .filter {
          query.isBlank() ||
            it.displayName.contains(query, true) ||
            it.email.contains(query, true) ||
            it.uid.contains(query, true) ||
            it.studentCode.contains(query, true)
        }
        .sortedWith(comparator)
      val ranked: List<AdminRow> = shown.filter { it.aiCalls > 0 }.sortedByDescending { it.aiCalls }

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
                  Chip(
                    if (row.aiCalls < 0) "AI: chưa có số liệu" else "AI: ${row.aiCalls} lượt",
                    if (row.aiCalls < 0) TxtDim else if (rank in 1..3) Amber else Mint
                  )
                  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(if (row.role == "PARENT") "PHỤ HUYNH" else "HỌC SINH", if (row.role == "PARENT") Violet else Mint)
                    if (row.banned) Chip("ĐANG BỊ CẤM", Rose)
                  }
                }
              }
              Text("uid ${row.uid}", color = TxtDim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
              if (row.studentCode.isNotEmpty()) {
                Text("mã ghép nối: ${row.studentCode}", color = TxtDim, fontSize = 11.sp)
              }
              if (row.banned && row.banReason.isNotEmpty()) {
                Text("lý do cấm: ${row.banReason}", color = Rose, fontSize = 11.sp)
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.banned) {
                  OutlinedButton(onClick = {
                    scope.launch {
                      try {
                        db().collection(BANS).document(row.uid).delete().await()
                        status = "Đã bỏ cấm ${row.displayName}."
                        refresh()
                      } catch (e: Exception) { status = "Bỏ cấm thất bại: ${e.message}" }
                    }
                  }) { Text("Bỏ cấm", color = Mint) }
                } else {
                  Button(
                    onClick = { banTarget = row },
                    colors = ButtonDefaults.buttonColors(containerColor = Rose)
                  ) { Text("Cấm") }
                }
                OutlinedButton(onClick = {
                  scope.launch {
                    try {
                      db().collection(USERS).document(row.uid).delete().await()
                      status = "Đã xóa HỒ SƠ ${row.displayName} khỏi /users. Tài khoản " +
                        "trong Authentication VẪN CÒN — muốn xóa hẳn phải xóa ở bảng " +
                        "điều khiển Firebase."
                      refresh()
                    } catch (e: Exception) { status = "Xóa thất bại: ${e.message}" }
                  }
                }) { Text("Xóa hồ sơ", color = Rose) }
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
    var reason by remember { mutableStateOf("spam / lạm dụng AI") }
    AlertDialog(
      onDismissRequest = { banTarget = null },
      containerColor = CardBg,
      title = { Text("Cấm ${target.displayName}?", color = Txt) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            "Ghi vào /bans/${target.uid}. Máy chủ sẽ từ chối đọc và ghi dữ liệu gia đình " +
              "của uid này — nhưng chỉ sau khi bạn bấm Publish cho firestore.rules trong " +
              "bảng điều khiển Firebase.",
            color = TxtDim, fontSize = 12.sp
          )
          OutlinedTextField(
            value = reason, onValueChange = { reason = it },
            label = { Text("Lý do") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Txt)
          )
        }
      },
      confirmButton = {
        TextButton(onClick = {
          scope.launch {
            try {
              db().collection(BANS).document(target.uid).set(
                mapOf(
                  "uid" to target.uid,
                  "displayName" to target.displayName,
                  "email" to target.email,
                  "reason" to reason,
                  "by" to (user?.uid ?: ""),
                  "at" to FieldValue.serverTimestamp()
                )
              ).await()
              status = "Đã cấm ${target.displayName}."
              refresh()
            } catch (e: Exception) {
              status = "Cấm thất bại: ${e.message}. " +
                "Nếu là PERMISSION_DENIED thì firestore.rules chưa được Publish."
            }
          }
          banTarget = null
        }) { Text("Cấm", color = Rose, fontWeight = FontWeight.Bold) }
      },
      dismissButton = {
        TextButton(onClick = { banTarget = null }) { Text("Quay lại", color = TxtDim) }
      }
    )
  }
}
