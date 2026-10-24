package com.aistudio.admin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * QUAN TRI VIEN — app rieng, APK rieng (`thanh.dev.app`), de chu tai khoan
 * duoc lieut ke trong Firebase va cam/Go xoa ho so ma khong phai mo bang
 * dieu khien Firebase tren may tinh.
 *
 * BA THU APP NAY KHONG LAM DUOC, va noi thang ra man hinh chu khong gia gio:
 *
 * 1. **Khong hien duoc mat khau.** Firebase Auth chi luu BAN HAM (scrypt/Bcrypt),
 *    khong co cach nao doc lai thanh chuoi. Bat ky app nao hien duoc "mat khau
 *    cua moi nguoi" la app tu che mat khau hoac danh cap tu noi khac — khong
 *    phai day la tinh nang quan tri.
 * 2. **Khong chan duoc IP.** `firestore.rules` khong co bien nao ten la IP, va
 *    dien thoai di dong doi dia chi lien tuc qua CGNAT: chan mot IP hom nay la
 *    hom sau trung nguoi khac. Chan that = cam theo `uid` (nut "Cam" o day),
 *    va luat o may chu moi la noi thuc hien lenh cam do.
 * 3. **Khong xoa duoc tai khoan Google/Auth tu client.** Nut "Xoa ho so" chi xoa
 *    document trong `/users`. Dong tai khoan trong Authentication van phai
 *    xoa bang bang dieu khien Firebase (hoac Cloud Functions).
 */

/**
 * Cho phep dung tren may cua chu tai khoan. Day la allowlist tinh — doi lai
 * `firestore.rules` cung phai ghi dung nhung uid nay, vi o day la UI con
 * duoi may chu moi la quyen that.
 */
private val ADMIN_UIDS = setOf(
  "YU2BwDqvahSh1hUWx9NRAdOwvG33", // duongtrungthanh011020122@gmail.com
)

/** Collection `/bans/{uid}` — `firestore.rules` chan doc/ghi theo nay. */
private const val BANS = "bans"
private const val USERS = "users"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (FirebaseFirestore.getInstance().app.name.isNotEmpty()) {
      // Firestore cua project KHKT nam trong database CO TEN, khong phai "default".
      // Goi db() o ben duoi luon dung ten nay; dong nay chi de ro rang.
    }
    setContent { MaterialTheme { AdminApp() } }
  }
}

private const val DATABASE_ID = "ai-studio-android-vespera-f0b8eaec-ca07-4549-bb58-a45233ead3c3"

/** Project KHKT KHONG co database `(default)` — phai lay instance theo ten. */
private fun db(): FirebaseFirestore = FirebaseFirestore.getInstance(DATABASE_ID)

data class AdminRow(
  val uid: String,
  val displayName: String,
  val email: String,
  val role: String,
  val studentCode: String,
  val aiCalls: Long,
  val banned: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminApp() {
  val auth = remember { FirebaseAuth.getInstance() }
  val scope = rememberCoroutineScope()
  val context = androidx.compose.ui.platform.LocalContext.current
  val rows = remember { mutableStateListOf<AdminRow>() }

  var user by remember { mutableStateOf(auth.currentUser) }
  var status by remember { mutableStateOf("Dang kiem tra dang nhap...") }
  var busy by remember { mutableStateOf(false) }

  val isAdmin = user?.uid?.let { it in ADMIN_UIDS } == true

  fun refresh() {
    busy = true
    status = "Dang doc /users..."
    scope.launch {
      try {
        val bans = mutableSetOf<String>()
        db().collection(BANS).get().await().documents.forEach { bans += it.id }
        val users = db().collection(USERS).orderBy("displayName", Query.Direction.ASCENDING).limit(500).get().await()
        val out = users.documents.map { d ->
          AdminRow(
            uid = d.id,
            displayName = d.getString("displayName") ?: "(khong co ten)",
            email = d.getString("email") ?: "",
            role = d.getString("role") ?: "",
            studentCode = d.getString("studentCode") ?: "",
            aiCalls = (d.get("aiCallCount") as? Number)?.toLong() ?: -1L,
            banned = bans.contains(d.id)
          )
        }
        rows.clear()
        rows += out
        status = "${out.size} ho so  |  ${out.count { it.banned }} dang bi cam"
      } catch (e: Exception) {
        status = "Khong doc duoc: ${e.message}"
      } finally {
        busy = false
      }
    }
  }

  // CredentialManager tu mo trang thai chọn tài khoản Google — khong can
  // ActivityResultLauncher rieng.

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Quản Trị Viên — Gia Đình Việt") },
        actions = {
          if (user != null) {
            TextButton(onClick = { auth.signOut(); user = null; rows.clear(); status = "Da dang xuat." }) { Text("Đăng xuất") }
          }
        }
      )
    }
  ) { pad ->
    Column(
      modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      if (user == null) {
        Text("Chưa đăng nhập.", fontWeight = FontWeight.Bold)
        Text("Chỉ tài khoản có uid nằm trong ADMIN_UIDS mới xem được danh sách này.")
        Button(
          onClick = {
            busy = true
            status = "Dang mo chon tai khoan Google..."
            scope.launch {
              try {
                val cm = CredentialManager.create(context)
                val req = GetCredentialRequest.Builder()
                  .addCredentialOption(
                    GetGoogleIdOption.Builder()
                      .setServerClientId("931971500070-jfd9trfl7vpq14l82p2ro4vtvtoui03v.apps.googleusercontent.com")
                      .setFilterByAuthorizedAccounts(false)
                      .build()
                  )
                  .build()
                val res = cm.getCredential(context, req)
                val cred = res.credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                  val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                  val r = auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                  user = r.user
                  status = "Da dang nhap: ${r.user?.email}"
                } else {
                  status = "Khong nhan duoc Google id token."
                  busy = false
                }
              } catch (e: Exception) {
                status = "Dang nhap that bai: ${e.message}"
                busy = false
              }
            }
          }
        ) { Text("Đăng nhập Google") }
        return@Column
      }

      if (!isAdmin) {
        Card(Modifier.fillMaxWidth()) {
          Column(Modifier.padding(14.dp)) {
            Text("Tài khoản này KHÔNG phải quản trị", fontWeight = FontWeight.Bold, color = Color(0xFFB00020))
            Spacer(Modifier.height(6.dp))
            Text("uid: ${user?.uid}", fontFamily = FontFamily.Monospace)
            Text("email: ${user?.email ?: "?"}")
            Spacer(Modifier.height(6.dp))
            Text(
              "Muốn dùng, thêm đúng uid này vào ADMIN_UIDS trong admin/.../MainActivity.kt " +
                "VÀ vào allowlist trong firestore.rules, rồi build lại. " +
                "Chỉ sửa trong app thì vẫn bị máy chủ chặn — đó là điểm chính của thiết kế này."
            )
          }
        }
        return@Column
      }

      Text(status)
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { refresh() }, enabled = !busy) { Text("Làm mới") }
        OutlinedButton(
          onClick = {
            scope.launch {
              try {
                val r = db().collection(USERS).limit(1).get().await()
                status = "Tham chuc /users OK (${r.documents.size} document dau)"
              } catch (e: Exception) {
                status = "Tu choi: ${e.message}"
              }
            }
          }
        ) { Text("Thử quyền đọc") }
      }

      Text(
        "Cột AI: -1 = tai khoan chua co so lieu. App chinh chua ghi dem goi AI len may chu, " +
          "nen xep hang dung AI that su chi co nghia sau khi ban do chay ban co bo dem.",
        color = Color(0xFF8A6D00)
      )
      HorizontalDivider()

      LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        items(rows, key = { it.uid }) { row ->
          Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
              Text(row.displayName, fontWeight = FontWeight.Bold)
              Text(row.email.ifEmpty { "(khong co email)" })
              Text("role=${row.role}  ma=${row.studentCode.ifEmpty { "-" }}  AI=${if (row.aiCalls < 0) "chưa có số liệu" else row.aiCalls}")
              Text("uid=${row.uid}", fontFamily = FontFamily.Monospace, color = Color.Gray)
              Spacer(Modifier.height(6.dp))
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (row.banned) {
                  Text("ĐANG BỊ CẤM", color = Color(0xFFB00020), fontWeight = FontWeight.Bold)
                  OutlinedButton(onClick = {
                    scope.launch {
                      try { db().collection(BANS).document(row.uid).delete().await(); refresh() }
                      catch (e: Exception) { status = "Go cam that bai: ${e.message}" }
                    }
                  }) { Text("Bỏ cấm") }
                } else {
                  Button(onClick = {
                    scope.launch {
                      try {
                        db().collection(BANS).document(row.uid).set(
                          mapOf(
                            "uid" to row.uid,
                            "reason" to "cam tu app quan tri",
                            "at" to System.currentTimeMillis()
                          )
                        ).await()
                        refresh()
                      } catch (e: Exception) { status = "Cam that bai: ${e.message}" }
                    }
                  }) { Text("Cấm") }
                }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(onClick = {
                  scope.launch {
                    try {
                      db().collection(USERS).document(row.uid).delete().await()
                      status = "Da xoa ho so ${row.displayName} khoi /users (van con tai khoan Auth)."
                      refresh()
                    } catch (e: Exception) { status = "Xoa that bai: ${e.message}" }
                  }
                }) { Text("Xóa hồ sơ") }
              }
            }
          }
        }
      }
    }
  }
}
