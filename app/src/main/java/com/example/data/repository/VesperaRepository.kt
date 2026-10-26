package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.FirestoreDb
import com.example.data.model.ExtraTimeRequest
import com.example.data.model.FamilyMessage
import com.example.data.model.LocationData
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.Pairing
import com.example.data.model.PairingStatus
import com.example.data.model.PrivacyRequest
import com.example.data.model.RequestStatus
import com.example.data.model.RuleAuditEntry
import com.example.data.model.ScreenTimeAgreement
import com.example.data.model.UsageSchedule
import com.example.service.blocker.ConsentPolicy
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "VesperaRepository"

/**
 * Bang ky tu cua ma ghep noi: khong co 0/1/I/O vi nguoi dung phai chep tay tu
 * man hinh may nay sang may kia. `studentCodePattern` duoc lap trinh tu chinh
 * chuoi nay, nen muon doi bang ky tu thi chi can sua mot cho — cau lenh bao
 * "ma khong dung dinh dang" tren man phu huynh khong the lac loi khoi loi sinh
 * ma duoi may con.
 */
private const val STUDENT_CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
private const val STUDENT_CODE_LENGTH = 8

class VesperaRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth, private val context: Context? = null) {

    constructor(context: Context) : this(
        FirestoreDb.instance(),
        FirebaseAuth.getInstance(),
        context
    )

    /**
     * Observe or get UserProfile safely without throwing
     */
    fun observeUserProfile(userId: String): Flow<UserProfile?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val docRef = db.collection("users").document(userId)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observeUserProfile error: ${error.message}")
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun getUserProfile(userId: String): UserProfile? {
        return try {
            val snapshot = db.collection("users").document(userId).get().await()
            if (snapshot.exists()) snapshot.toObject(UserProfile::class.java) else null
        } catch (e: Exception) {
            Log.w(TAG, "getUserProfile error", e)
            null
        }
    }

    /**
     * Ghi ho so len may chu. NEM ngoai le khi ghi that bai: truoc day loi bi
     * `Log.w` an, nen man hinh van bao "Đã cập nhật thông tin tài khoản!" du
     * document chua bao gio duoc viet.
     */
    suspend fun saveUserProfile(profile: UserProfile) {
        // Khong tu sinh uid: `user_<dong ho>` tao ra mot document `users/` ma
        // khong co tai khoan Firebase nao dung ten, khong tim lai duoc.
        val uid = profile.id.ifEmpty { auth.currentUser?.uid.orEmpty() }
        if (uid.isEmpty()) {
            throw IllegalStateException("Máy chưa đăng nhập nên chưa lưu được hồ sơ lên máy chủ.")
        }
        var toSave = profile.copy(id = uid)
        if (toSave.activePairingIds.isEmpty()) {
            // BAN 67.0: hồ sơ trên máy có thể chưa nạp được mảng ghép nối (doc
            // cũ, hoặc luồng đăng ký mới). set() đầy đủ sẽ XOÁ MẢNG trên máy
            // chủ — mất xác nhận của những phụ huynh đã ghép. Nên giữ nguyên
            // mảng hiện có trên máy chủ khi máy gửi lên một mảng rỗng.
            val existing = runCatching {
                db.collection("users").document(uid).get().await()
            }.getOrNull()
            val ids = existing?.get("activePairingIds") as? List<*>
            if (!ids.isNullOrEmpty()) {
                toSave = toSave.copy(activePairingIds = ids.filterIsInstance<String>())
            }
        }
        db.collection("users").document(uid).set(toSave).await()
    }

    /**
     * Ma ghep noi cua may hoc sinh: `HS-` + [STUDENT_CODE_LENGTH] ky tu thuoc
     * [STUDENT_CODE_ALPHABET]. Do dai that la 8, KHONG phai 5 — chu giai o o
     * nhap cua phu huynh va `studentCodePattern` cung lay tu mot nguon.
     */
    fun generateStudentCode(): String {
        val randomPart = (1..STUDENT_CODE_LENGTH)
            .map { STUDENT_CODE_ALPHABET.random() }
            .joinToString("")
        return "HS-$randomPart"
    }

    /**
     * Dinh dang ma ghep noi. Doi tu thanh phan `STUDENT_CODE_ALPHABET` +
     * `STUDENT_CODE_LENGTH` (dau `$$` cuoi chuoi la `$` trong Kotlin), nen
     * khong the lac khoi loi sinh ma.
     */
    val studentCodePattern: Regex = Regex(
        "^HS-[$STUDENT_CODE_ALPHABET]{$STUDENT_CODE_LENGTH}$$"
    )

    /** Vi du dinh dang cho chu giai tren man hinh: `HS-XXXXXXXX`. */
    val studentCodeExample: String get() = "HS-" + "X".repeat(STUDENT_CODE_LENGTH)

    /**
     * Ma co ra duoc mot document hoc sinh tren may chu khong?
     *
     * Kiem tra ngay tren may truoc khi goi Firestore: mot lan nhap sai ma khong
     * co thiet bi nay nao dat mot lan doc tai lieu, va phu huynh nhan duoc cau
     * "ma khong dung dinh dang" hon la "Ghép nối thất bại: Không tìm thấy học
     * sinh với mã ABC" — hai loi khong gop lam mot, vi mot ben la phep thu tay
     * nguoi, mot ben la tai khoan that su khong ton tai.
     */
    fun isPlausibleStudentCode(raw: String): Boolean =
        studentCodePattern.matches(raw.trim().uppercase())


    /**
     * Unique persistent student code per device (mỗi người một mã khác nhau)
     */
    fun getOrGenerateDeviceStudentCode(): String {
        val prefs = context?.getSharedPreferences("vespera_device_prefs", android.content.Context.MODE_PRIVATE)
        var code = prefs?.getString("unique_student_code", "") ?: ""
        if (code.isBlank() || code == "HS-8F29K") {
            code = generateStudentCode()
            prefs?.edit()?.putString("unique_student_code", code)?.apply()
        }
        return code
    }

    /**
     * Find student user by student code
     */
    suspend fun findStudentByCode(studentCode: String): UserProfile? {
        return try {
            val query = db.collection("users")
                .whereEqualTo("studentCode", studentCode.trim().uppercase())
                .whereEqualTo("role", UserRole.CHILD.name)
                .limit(1)
                .get()
                .await()
            query.documents.firstOrNull()?.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "findStudentByCode error", e)
            null
        }
    }

    /**
     * Doi ma hoc sinh SAU khi mot cap da ghep noi thanh cong.
     *
     * Ly do: ma dung de ghep la ma mot lan — de no luon luon nhap duoc thi
     * bat ky phu huynh nao biet no cung vao duoc. May con tu goi ham nay trong
     * `watchPairingArrival`, nen ma in ra man hinh chi con hieu luc cho den
     * luc phep ghep dau tien xuong may chu.
     */
    suspend fun rotateStudentCode(childId: String) {
        val newCode = generateStudentCode()
        db.collection("users").document(childId).update("studentCode", newCode).await()
    }

    /**
     * Huy ghep noi tren may chu TRUOC, roi moi xoa trang thai local. Neu ghi
     * that bai thi hai ben van con noi voi nhau, hon la mot ben nghi "đã hủy"
     * trong khi ben kia con thay "đã ghép nối".
     *
     * Chi document ghep noi va ho so CUA MINH bi dong: ben kia se tu thay
     * `status = UNPAIRED` qua listener cua no va tu xoa lien ket cua no
     * ([observePairingsFor] chi tra ve cap dang PAIRED).
     */
    suspend fun unpair(pairingId: String, myUserId: String) {
        db.collection("pairings").document(pairingId).update(
            "status", PairingStatus.UNPAIRED.name,
            "updatedAt", System.currentTimeMillis()
        ).await()
        db.collection("users").document(myUserId).update(
            "activePairingId", "",
            "pairedWith", ""
        ).await()
    }

    // ── TỰ XÓA TÀI KHOẢN (bản 66.0) ─────────────────────────────────────────
    //
    // `firestore.rules` từng chặn `allow delete: if false` ở /users vì sợ người
    // dùng bấm nhầm. Bản 66.0 mở đường đó cho CHÍNH CHỦ, và Nut "Xóa tài khoản"
    // phải giao đủ thứ mà nó hứa — nếu chỉ xóa `users/<uid>` thì GPS, vết di
    // chuyển, phiếu SOS và nhật ký nội quy vẫn nằm lại trên máy chủ dưới tên
    // một người không còn hồ sơ.
    //
    // THỨ TỰ Ở ĐÂY KHÔNG PHẢI TÙY CHỌN: rules chỉ cho xóa locations /
    // location_history / sos_alerts / rule_audit khi document `users/<uid>`
    // ĐÃ KHÔNG CÒN (`accountIsBeingDeleted`). Xóa ngược lại thì mọi lệnh sau
    // đều 403. Vì vậy `users/<uid>` luôn là phát đạn đầu tiên.

    /** Những gì một tài khoản đang giữ trên máy chủ, đếm được trước khi xóa. */
    data class AccountInventory(
        val userId: String,
        val locationDocs: List<DocumentReference> = emptyList(),
        val historyDocs: List<DocumentReference> = emptyList(),
        val sosDocs: List<DocumentReference> = emptyList(),
        val callDocs: List<DocumentReference> = emptyList(),
        val pairingDocs: List<DocumentReference> = emptyList(),
        val pairingChildDocs: List<DocumentReference> = emptyList(),
    ) {
        /** Tổng số document ngoài hồ sơ — con số app in ra để người dùng biết thật. */
        val total: Int
            get() = locationDocs.size + historyDocs.size + sosDocs.size + callDocs.size +
                pairingDocs.size + pairingChildDocs.size
    }

    /** @param deleted số document đã xóa, @param failed nhãn nhóm KHÔNG xóa được. */
    data class WipeOutcome(val deleted: Int, val failed: List<String>)

    /**
     * Liệt kê toàn bộ document thuộc về [userId]. Chạy TRƯỚC khi xóa hồ sơ, vì
     * hết hồ sơ thì máy khác (và cả rules) mất luôn mốc để đoán tài liệu nào là
     * của ai. Mot query KHONG duoc lam loi toan bo luong: tai khoan chua bao gio
     * dung tinh nao (chua co `locations`, chua lap phieu SOS nao) thi read bi
     * tu choi la chuyen thuong, con cac thu khac van phai don duoc.
     */
    suspend fun inventoryForDeletion(userId: String): AccountInventory {
        if (userId.isEmpty()) return AccountInventory(userId)

        suspend fun refs(build: suspend () -> List<DocumentReference>, why: String) =
            runCatching { build() }.getOrElse {
                Log.w(TAG, "inventoryForDeletion: $why", it)
                emptyList()
            }

        val locationDocs = listOf(db.collection("locations").document(userId))
        val historyDocs = refs(
            { db.collection("location_history").whereEqualTo("userId", userId).get().await()
                .documents.map { it.reference } },
            "location_history"
        )
        val sosDocs = refs(
            { db.collection("sos_alerts").whereEqualTo("userId", userId).get().await()
                .documents.map { it.reference } },
            "sos_alerts"
        )

        // Ghép nối: máy này có thể là parentId hoặc childId. Firestore không có
        // query OR nên phải chạy hai lần rồi tự loại trùng.
        val pairingDocs = mutableListOf<DocumentReference>()
        for (field in listOf("parentId", "childId")) {
            refs(
                { db.collection("pairings").whereEqualTo(field, userId).get().await()
                    .documents.map { it.reference } },
                "pairings.$field"
            ).forEach { if (it !in pairingDocs) pairingDocs.add(it) }
        }

        // Subcollection của từng ghép nối. `rule_audit` chỉ xóa được khi hồ sơ
        // đã mất (accountIsBeingDeleted trong rules) — thu vao nay va thu tu
        // xoa o `deleteAccount` bao dam dieu do.
        val childDocs = mutableListOf<DocumentReference>()
        for (pairRef in pairingDocs) {
            for (sub in listOf(
                "schedules", "rule_audit", "agreements", "extra_time",
                "privacy_requests", "messages", "location_history"
            )) {
                childDocs.addAll(
                    refs(
                        { pairRef.collection(sub).get().await().documents.map { it.reference } },
                        "${pairRef.id}/$sub"
                    )
                )
            }
        }

        // `calls/<pairingId>` la tin hieu WebRTC cua ghep noi, `calls/room_<uid>`
        // la phong rieng khi chua ghep noi. Khong doc duoc danh sach nay sau khi
        // document ghep noi bi xoa, nen lay id tu danh sach da liet ke.
        val callDocs = (pairingDocs.map { db.collection("calls").document(it.id) } +
            db.collection("calls").document("room_$userId")).distinct()

        return AccountInventory(
            userId = userId,
            locationDocs = locationDocs,
            historyDocs = historyDocs,
            sosDocs = sosDocs,
            callDocs = callDocs,
            pairingDocs = pairingDocs,
            pairingChildDocs = childDocs,
        )
    }

    /**
     * Xóa sạch dấu vết của một tài khoản trên máy chủ. Trả về số document thực
     * xóa được và danh sách chỗ thất bại — app phải nói đủ hai con số đó, không
     * được hứa "đã xóa toàn bộ" khi một nửa still bị rules chặn.
     *
     * Không có transaction: lệnh xóa chỉ chạy được theo đúng THỨ TỰ (hồ sơ
     * trước, phần còn lại sau) và Firestore transaction không cho phép đọc lại
     * rules ở giữa. Mỗi phát là một lệnh độc lập, lỗi ở bước sau không hoàn tác
     * bước trước — đó chính là lý do app phải giữ lại biên nhận để in ra.
     */
    suspend fun deleteAccount(userId: String, inventory: AccountInventory): WipeOutcome {
        if (userId.isEmpty()) return WipeOutcome(0, listOf("máy này chưa đăng nhập"))
        var done = 0
        val failed = mutableListOf<String>()

        // BUOC 1 — ho so. That bai (thuong la tai khoan dang bi cam) thi dung
        // lai: phia sau chac chan bi rules chan vi ho so van con.
        try {
            db.collection("users").document(userId).delete().await()
            done++
        } catch (e: Exception) {
            Log.w(TAG, "deleteAccount: khong xoa duoc ho so", e)
            return WipeOutcome(0, listOf("Hồ sơ trên máy chủ vẫn còn (bị chặn hoặc mất mạng)"))
        }

        val batches = listOf(
            "vị trí" to inventory.locationDocs,
            "lịch sử vị trí" to inventory.historyDocs,
            "phiếu SOS" to inventory.sosDocs,
            "tin hiệu cuộc gọi" to inventory.callDocs,
            "nội quy & nhật ký ghép nối" to inventory.pairingChildDocs,
            "ghép nối" to inventory.pairingDocs,
        )
        for ((label, refs) in batches) {
            for (ref in refs) {
                try {
                    ref.delete().await()
                    done++
                } catch (e: Exception) {
                    Log.w(TAG, "deleteAccount: bo qua ${ref.path}", e)
                    if (label !in failed) failed.add(label)
                }
            }
        }
        return WipeOutcome(done, failed)
    }

    /**
     * MOI document ghep noi dang PAIRED ma tai khoan nay la mot ben — theo doi
     * truc tiep, khong hoai phat.
     *
     * BAN 68.0 — hai ly do doi hinh, ca hai deu do truc tiep tren production:
     *  1. Ban cu la `findPairingFor` (one-shot, `maxByOrNull`) duoc goi trong
     *     vong lap 20 giay, va ViewModel chi chay vong lap do khi
     *     `activePairingId` CON TRONG. May con da ghep voi ME vi vay khong bao
     *     gio nhin ra BO vua ghep them: "nhieu nguoi ghep noi chung mot ho so"
     *     chet o buoc do, khong phai o rules.
     *  2. Mot listener theo query cung la MOT QUERY nhu `.get()`, nen no phai
     *     di qua dung canh `allow list` cua /pairings (68.0). Doi lai thi Firestore
     *     tu day ket qua ngay khi co document moi khop dieu kien — phep ghep thu
     *     hai ve may con trong vai giay thay vi toi 20 giay, va khot 90 lan doc.
     *
     * Khong `orderBy`: loc `parentId` roi sap theo `createdAt` la query composite,
     * can chi muc phu do Firestore tao; hai canh `whereEqualTo` (toan bo la
     * equality) thi khong can. Sap xep lam o day, du bang ket qua nho.
     */
    fun observePairingsFor(profile: UserProfile): Flow<List<Pairing>> = callbackFlow {
        if (profile.id.isEmpty()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val roleField = if (profile.role == UserRole.PARENT) "parentId" else "childId"
        val listener = db.collection("pairings")
            .whereEqualTo(roleField, profile.id)
            .whereEqualTo("status", PairingStatus.PAIRED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // That bai (403, mat mang) thi giu danh sach lan cu: ho so
                    // cua may nay van con nguyen, app khong tu "gỡ" minh khoi
                    // gia dinh chi vi mot lan doc loi.
                    Log.w(TAG, "observePairingsFor error", error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(Pairing::class.java) }
                    .sortedBy { it.createdAt }
                trySend(docs)
            }
        awaitClose { listener.remove() }
    }

    /**
     * May nay tu ghi ho so cua chinh no: "toi dang ghep voi ai, theo document
     * nao". Day la buoc ma truoc day app tuong la ben kia lam ho duoc.
     *
     * BAN 67.0 — NHIỀU người ghép chung một hồ sơ: id mới được THÊM vào
     * `activePairingIds` (không thay thế), để mẹ, bố, ông bà… mỗi người có
     * document ghép nối riêng cùng thấy máy con. `activePairingId` (một) vẫn
     * ghi = ghép nối mới nhất, cho APK cũ và cho các màn hình đang dùng một
     * ghép nối "hiện tại".
     */
    suspend fun linkSelfToPairing(
        userId: String,
        pairingId: String,
        partnerId: String
    ) {
        if (userId.isEmpty()) return
        val docRef = db.collection("users").document(userId)
        val current = runCatching { docRef.get().await() }.getOrNull()
        val existing = current?.get("activePairingIds") as? List<*>
        val merged = (existing?.filterIsInstance<String>().orEmpty() + pairingId).distinct()
        docRef.update(
            mapOf(
                "pairedWith" to partnerId,
                "activePairingId" to pairingId,
                "activePairingIds" to merged
            )
        ).await()
    }

    /** Xoa lien ket cua may nay khi document ghep noi khong con PAIRED nua. */
    suspend fun unlinkSelf(userId: String) {
        if (userId.isEmpty()) return
        val docRef = db.collection("users").document(userId)
        // Gỡ khỏi mảng GHÉP NỐI HIỆN TẬT; các ghép nối khác của máy (đa ghép)
        // vẫn giữ xác nhận trong activePairingIds — chúng tự mất hiệu lực khi
        // document ghép nối tương ứng rời trạng thái PAIRED (attestedPair kiểm
        // tra status trên máy chủ).
        val current = runCatching { docRef.get().await() }.getOrNull()
        val activeId = current?.getString("activePairingId").orEmpty()
        val ids = (current?.get("activePairingIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        val remaining = ids - activeId
        docRef.update(
            mapOf(
                "pairedWith" to "",
                "activePairingId" to "",
                "activePairingIds" to remaining
            )
        ).await()
    }

    fun observePairing(pairingId: String): Flow<Pairing?> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observePairing error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toObject(Pairing::class.java))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * May co duong ra Internet khong?
     *
     * Xet theo KHA NANG cua mang dang hoat dong, khong liet ke kieu ket noi.
     * Ban cu chi nhan WIFI/CELLULAR/ETHERNET nen may bat VPN (activeNetwork la
     * mang VPN) bi ket luan "offline" -> `updateLocation` am tham cat vi tri
     * vao bo nho may va phu huynh khong bao gio thay con o dau. Con bat VPN de
     * lot luoi la tinh huong that, khong phai canh bien.
     */
    fun isWifiOrInternetConnected(): Boolean {
        val ctx = context ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
            val capabilities = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return false
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    suspend fun updateLocation(location: LocationData) {
        try {
            if (!isWifiOrInternetConnected() || auth.currentUser == null) {
                saveOfflineLocationLocally(location)
                Log.d(TAG, "Offline or unauthenticated: Saved GPS location locally.")
                return
            }

            flushOfflineLocations()
            db.collection("locations").document(location.userId).set(location).await()
        } catch (e: Exception) {
            Log.d(TAG, "updateLocation offline/permission fallback: ${e.message}")
            saveOfflineLocationLocally(location)
        }
    }

    /**
     * Kết quả thực của một lần đẩy SOS lên đám mây.
     *  • [CloudDelivery.SENT]          — đã ghi thành công lên Firestore.
     *  • [CloudDelivery.QUEUED_OFFLINE] — chưa gửi được, đã xếp hàng chờ đồng bộ sau.
     *  • [CloudDelivery.FAILED]        — thất bại hoàn toàn (kèm lý do).
     */
    enum class CloudDelivery { SENT, QUEUED_OFFLINE, FAILED }

    data class CloudSyncResult(
        val delivery: CloudDelivery,
        val detail: String? = null
    )

    /**
     * Gửi cảnh báo SOS. Trả về [CloudSyncResult] phản ánh ĐÚNG việc đã lên được
     * máy chủ hay chỉ đang nằm trong hàng đợi ngoại tuyến — không báo thành công giả.
     */
    suspend fun sendSosAlert(location: LocationData): CloudSyncResult {
        val alert = location.copy(isSos = true, updatedAt = System.currentTimeMillis())
        if (auth.currentUser == null) {
            saveOfflineLocationLocally(alert)
            return CloudSyncResult(
                CloudDelivery.QUEUED_OFFLINE,
                "Chưa đăng nhập tài khoản — cảnh báo đã lưu và sẽ tự đồng bộ sau khi đăng nhập."
            )
        }
        if (!isWifiOrInternetConnected()) {
            saveOfflineLocationLocally(alert)
            return CloudSyncResult(
                CloudDelivery.QUEUED_OFFLINE,
                "Thiết bị đang ngoại tuyến — cảnh báo đã lưu và sẽ tự đồng bộ khi có mạng."
            )
        }
        return try {
            db.collection("sos_alerts").document("${alert.userId}_${alert.updatedAt}").set(alert).await()
            db.collection("locations").document(alert.userId).set(alert).await()
            CloudSyncResult(CloudDelivery.SENT)
        } catch (e: Exception) {
            Log.d(TAG, "sendSosAlert cloud write failed: ${e.message}")
            saveOfflineLocationLocally(alert)
            CloudSyncResult(
                CloudDelivery.QUEUED_OFFLINE,
                "Không kết nối được máy chủ (${e.localizedMessage ?: "lỗi mạng"}) — đã lưu và sẽ tự đồng bộ sau."
            )
        }
    }

    private fun saveOfflineLocationLocally(location: LocationData) {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences("vespera_offline_gps", Context.MODE_PRIVATE)
            val jsonList = prefs.getString("queue", "[]") ?: "[]"
            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, LocationData::class.java)
            val adapter = moshi.adapter<List<LocationData>>(type)
            val list = try { adapter.fromJson(jsonList) ?: emptyList() } catch (e: Exception) { emptyList() }

            val updated = list + location
            prefs.edit().putString("queue", adapter.toJson(updated)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveOfflineLocationLocally error", e)
        }
    }

    /**
     * BAN 67.0 — hàng đợi LỊCH SỬ di chuyển khi offline. Bản cũ bỏ lặng entry
     * lịch sử khi chưa có mạng (`saveLocationHistory` return sớm) nên đường đi
     * trong lúc mất mạng mất hẳn, máy mẹ không bao giờ thấy. Nay xếp hàng ở
     * "vespera_offline_gps"/"history_queue" và [flushOfflineLocations] sẽ ghi
     * lại cả hai nơi (location_history + pairings/{id}/location_history) khi
     * mạng trở lại — lộ trình không còn bị khuyết.
     *
     * Trần 250 điểm: queue là biện pháp tạm, không phải kho dữ liệu; quá giới
     * hạn thì bỏ điểm CŨ NHẤT (vẫn giữ nghĩa "mất ít nhất").
     */
    private fun saveOfflineHistoryLocally(entry: LocationHistoryEntry) {
        val ctx = context ?: return
        try {
            val prefs = ctx.getSharedPreferences("vespera_offline_gps", Context.MODE_PRIVATE)
            val jsonList = prefs.getString("history_queue", "[]") ?: "[]"
            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, LocationHistoryEntry::class.java)
            val adapter = moshi.adapter<List<LocationHistoryEntry>>(type)
            val list = try { adapter.fromJson(jsonList) ?: emptyList() } catch (e: Exception) { emptyList() }

            val capped = if (list.size >= 250) list.takeLast(249) else list
            prefs.edit().putString("history_queue", adapter.toJson(capped + entry)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "saveOfflineHistoryLocally error", e)
        }
    }

    suspend fun flushOfflineLocations() {
        val ctx = context ?: return
        try {
            if (!isWifiOrInternetConnected()) return
            val prefs = ctx.getSharedPreferences("vespera_offline_gps", Context.MODE_PRIVATE)
            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()

            // ── 1. Hàng đợi vị trí hiện tại/SOS ──
            val jsonList = prefs.getString("queue", "[]") ?: "[]"
            if (jsonList != "[]" && jsonList.isNotBlank()) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, LocationData::class.java)
                val adapter = moshi.adapter<List<LocationData>>(type)
                val list = try { adapter.fromJson(jsonList) ?: emptyList() } catch (e: Exception) { emptyList() }

                if (list.isNotEmpty()) {
                    for (loc in list) {
                        try {
                            db.collection("locations").document(loc.userId).set(loc).await()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to flush offline location", e)
                        }
                    }
                    prefs.edit().remove("queue").apply()
                    Log.d(TAG, "Đã có WiFi: Trả lại ${list.size} vị trí GPS đã lưu offline lên máy chủ thành công.")
                }
            }

            // ── 2. BAN 67.0: hàng đợi LỊCH SỬ di chuyển ──
            // Ghi lại qua đúng đường saveLocationHistory (merge bán kính 5 m +
            // ghi cả root lẫn subcollection của ghep noi), XÓA KHỎI QUEUE TỪNG
            // ĐIỂM THÀNH CÔNG — điểm còn lỗi thì chờ lần flush sau, không mất.
            val jsonHist = prefs.getString("history_queue", "[]") ?: "[]"
            if (jsonHist != "[]" && jsonHist.isNotBlank()) {
                val type = com.squareup.moshi.Types.newParameterizedType(List::class.java, LocationHistoryEntry::class.java)
                val adapter = moshi.adapter<List<LocationHistoryEntry>>(type)
                val list = try { adapter.fromJson(jsonHist) ?: emptyList() } catch (e: Exception) { emptyList() }

                if (list.isNotEmpty()) {
                    val remaining = mutableListOf<LocationHistoryEntry>()
                    for (entry in list) {
                        val ok = runCatching { writeLocationHistoryNow(entry) }
                            .onFailure { Log.w(TAG, "Failed to flush offline history ${entry.id}", it) }
                            .isSuccess
                        if (!ok) remaining.add(entry)
                    }
                    if (remaining.isEmpty()) {
                        prefs.edit().remove("history_queue").apply()
                    } else {
                        val type2 = com.squareup.moshi.Types.newParameterizedType(List::class.java, LocationHistoryEntry::class.java)
                        prefs.edit().putString("history_queue", moshi.adapter<List<LocationHistoryEntry>>(type2).toJson(remaining)).apply()
                    }
                    Log.d(TAG, "Đã đồng bộ lịch sử offline: ${list.size - remaining.size}/${list.size} điểm lên máy chủ.")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "flushOfflineLocations error", e)
        }
    }

    suspend fun getLocation(userId: String): LocationData? {
        if (userId.isEmpty()) return null
        return try {
            val doc = db.collection("locations").document(userId).get().await()
            doc.toObject(LocationData::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "getLocation error", e)
            null
        }
    }

    fun observeLocation(userId: String): Flow<LocationData?> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = db.collection("locations").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeLocation error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(snapshot.toObject(LocationData::class.java))
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Ghi một điểm lịch sử di chuyển. BAN 67.0: offline / chưa đăng nhập thì
     * XẾP HÀNG (saveOfflineHistoryLocally) thay vì bỏ lặng — khi có mạng,
     * [flushOfflineLocations] sẽ đẩy cả queue lên để máy mẹ thấy đủ lộ trình.
     */
    suspend fun saveLocationHistory(entry: LocationHistoryEntry) {
        try {
            if (auth.currentUser == null || !isWifiOrInternetConnected()) {
                saveOfflineHistoryLocally(entry)
                Log.d(TAG, "Offline: xếp hàng lịch sử di chuyển ${entry.id} (${if (entry.pairingId.isNotEmpty()) "có" else "không có"} ghép nối).")
                return
            }
            writeLocationHistoryNow(entry)
        } catch (e: Exception) {
            Log.d(TAG, "saveLocationHistory lỗi (${e.message}) — xếp hàng offline để thử lại khi có mạng.")
            saveOfflineHistoryLocally(entry)
        }
    }

    /**
     * Ghi THẬT một điểm lịch sử lên cả hai nơi (root + subcollection của ghép
     * nối), gộp điểm trùng bán kính 5 m như cũ. KHÔNG bắt lỗi ở đây để
     * [flushOfflineLocations] biết điểm nào flush thất bại và giữ lại queue.
     */
    private suspend fun writeLocationHistoryNow(entry: LocationHistoryEntry) {
        // Try to find the last entry for this user
        val lastEntrySnapshot = db.collection("location_history")
            .whereEqualTo("userId", entry.userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get().await()

        val lastEntry = lastEntrySnapshot.documents.firstOrNull()?.toObject(LocationHistoryEntry::class.java)

        // Check distance
        val isWithinRadius = if (lastEntry != null) {
            val distance = calculateDistance(entry.latitude, entry.longitude, lastEntry.latitude, lastEntry.longitude)
            distance < 5.0
        } else {
            false
        }

        if (isWithinRadius && lastEntry != null) {
            // Update existing
            val newDuration = (entry.timestamp - lastEntry.timestamp) + lastEntry.duration

            val updateMap = mapOf(
                "timestamp" to entry.timestamp,
                "duration" to newDuration,
                "batteryLevel" to entry.batteryLevel,
                "isCharging" to entry.isCharging
            )
            db.collection("location_history").document(lastEntry.id).update(updateMap).await()

            for (pairingId in pairingIdsToMirror(entry)) {
                db.collection("pairings").document(pairingId)
                    .collection("location_history").document(lastEntry.id).update(updateMap).await()
            }
        } else {
            // Create new
            val docId = entry.id.ifEmpty { "${entry.userId}_${entry.timestamp}" }
            val dataMap = mapOf(
                "id" to docId,
                "pairingId" to entry.pairingId,
                "userId" to entry.userId,
                "userName" to entry.userName,
                "latitude" to entry.latitude,
                "longitude" to entry.longitude,
                "placeName" to entry.placeName,
                "speed" to entry.speed,
                "batteryLevel" to entry.batteryLevel,
                "isCharging" to entry.isCharging,
                "duration" to 0L,
                "timestamp" to entry.timestamp,
                "serverTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "dateStr" to entry.dateStr,
                "timeStr" to entry.timeStr,
                "year" to entry.year,
                "month" to entry.month,
                "day" to entry.day,
                "hour" to entry.hour,
                "minute" to entry.minute,
                "second" to entry.second
            )
            db.collection("location_history").document(docId).set(dataMap).await()
            for (pairingId in pairingIdsToMirror(entry)) {
                db.collection("pairings").document(pairingId)
                    .collection("location_history").document(docId).set(dataMap).await()
            }
        }
    }

    /**
     * BAN 67.0 — máy con MIRROR lộ trình vào GHÉP NỐI CỦA TẤT CẢ những người
     * đã ghép (mẹ, bố, ông bà…), không chỉ ghép nối "mới nhất". Danh sách lấy
     * từ hồ sơ của chính máy ghi (activePairingIds + activePairingId + id gắn
     * trên entry). Rules cho phép vì máy con là childId của TỪNG document đó
     * (isPairMember). Một document lỗi mirror thì các nơi khác vẫn được ghi —
     * lỗi từng nơi đã được bắt ở caller khi offline; ở đây dùng runCatching
     * để một ghép nối hỏng không làm treo vòng mirror.
     */
    private suspend fun pairingIdsToMirror(entry: LocationHistoryEntry): List<String> {
        val ids = mutableSetOf(entry.pairingId)
        if (entry.userId.isNotEmpty()) {
            runCatching {
                val doc = db.collection("users").document(entry.userId).get().await()
                (doc.get("activePairingIds") as? List<*>)?.filterIsInstance<String>()?.let(ids::addAll)
                doc.getString("activePairingId")?.takeIf { it.isNotEmpty() }?.let(ids::add)
            }
        }
        return ids.filter { it.isNotEmpty() }
    }

    /** Bien nhan don dep dinh ky: X document thuc xoa + nhom khong xoa duoc. */
    data class RetentionOutcome(val deleted: Int, val failed: List<String>)

    /**
     * BAN 67.0 — DỌN ĐỊNH KÝ 30 NGÀY: xóa LỊCH SỬ DI CHUYỂN và TIN NHẮN cũ
     * hơn [cutoffMillis], giữ lại TIN ĐƯỢC GHIM (`isStarred`). Chạy mỗi lần
     * app mở (ViewModel gọi đúng một lần mỗi phiên) — dự án không chạy server
     * cron nên "định kỳ" ở đây là "dọn khi thiết bị nào đó của gia đình bật
     * app": thiết bị nào mở trước sẽ dọn trước, thiết bị sau chạy lại thấy
     * trống và bỏ qua.
     *
     * Dọn ĐỦ BA NƠI: lịch sử gốc `/location_history` (chỉ của chính máy này —
     * rules 67.0 mở quyền xóa cho chủ), và theo TỪNG ghép nối mà máy này là
     * thành viên: tin nhắn trong từng ghép nối (pairings/<id>/messages —
     * subcollection cho phép thành viên viết = xóa được) và lịch sử mirror
     * Tin ghim được giữ ở cả hai thiết bị vì ai ghim thì cũng nằm trong cặp
     * này. Lỗi một ghép nối không làm hỏng ghép nối khác — mỗi chỗ đều bắt
     * lỗi riêng và ghi vào `failed` để app nói thật đã xóa được bao nhiêu.
     */
    suspend fun runRetentionCleanup(
        cutoffMillis: Long = System.currentTimeMillis() - RETENTION_WINDOW_MS
    ): RetentionOutcome {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) return RetentionOutcome(0, listOf("chưa đăng nhập"))
        var deleted = 0
        val failed = mutableListOf<String>()

        // 1) Lịch sử gốc của chính máy. Query theo userId rồi lọc timestamp
        //    phía client — tránh composite index (userId + timestamp) mà dự án
        //    chưa tạo. Document KHÔNG CÓ timestamp (0/null) là tuổi không rõ:
        //    GIỮ, không xóa mò.
        try {
            val snap = db.collection("location_history")
                .whereEqualTo("userId", uid).get().await()
            val old = snap.documents.filter { (it.getLong("timestamp") ?: 0L) in 1..cutoffMillis }
            deleted += deleteInChunks(old.map { it.reference })
        } catch (e: Exception) {
            Log.w(TAG, "runRetentionCleanup: location_history", e)
            failed.add("lịch sử vị trí (${e.message})")
        }

        // 2) Theo từng ghép nối mà máy này là parentId hoặc childId.
        val pairRefs = mutableListOf<DocumentReference>()
        for (field in listOf("parentId", "childId")) {
            runCatching {
                db.collection("pairings").whereEqualTo(field, uid).get().await()
                    .documents.map { it.reference }.forEach { if (it !in pairRefs) pairRefs.add(it) }
            }.onFailure { failed.add("ghép nối (${it.message})") }
        }

        for (pair in pairRefs) {
            // 2a) Tin nhắn cũ hơn hạn, TRỪ tin ghim. `isStarred` là tên khóa
            //     thật trong Firestore (xem FamilyMessage @get:PropertyName).
            try {
                val msgs = pair.collection("messages")
                    .whereLessThan("createdAt", cutoffMillis).get().await()
                val toDelete = msgs.documents
                    .filter { it.getBoolean("isStarred") != true }
                    .map { it.reference }
                deleted += deleteInChunks(toDelete)
            } catch (e: Exception) {
                Log.w(TAG, "runRetentionCleanup: ${pair.id}/messages", e)
                failed.add("tin nhắn của ${pair.id} (${e.message})")
            }
            // 2b) Lịch sử di chuyển mirror trong ghép nối.
            try {
                val mirrored = pair.collection("location_history")
                    .whereLessThan("timestamp", cutoffMillis).get().await()
                deleted += deleteInChunks(mirrored.documents.map { it.reference })
            } catch (e: Exception) {
                Log.w(TAG, "runRetentionCleanup: ${pair.id}/location_history", e)
                failed.add("lịch sử của ${pair.id} (${e.message})")
            }
        }

        Log.d(TAG, "runRetentionCleanup: đã xóa $deleted document, còn tồn: $failed")
        return RetentionOutcome(deleted, failed)
    }

    /** Cửa sổ giữ dữ liệu mặc định: 30 ngày. */
    companion object {
        const val RETENTION_WINDOW_MS: Long = 30L * 24L * 60L * 60L * 1000L
    }

    /**
     * Xóa danh sách document theo lô (Firestore giới hạn 500 lệnh/batch; lấy
     * 400 cho an toàn). Trả về số lệnh THẬT SỰ thành công — lệnh xóa document
     * không tồn tại vẫn được tính thành công bởi Firestore, nhưng với dữ liệu
     * vừa query ra thì chúng luôn tồn tại tại thời điểm query.
     */
    private suspend fun deleteInChunks(refs: List<DocumentReference>): Int {
        var done = 0
        for (chunk in refs.chunked(400)) {
            try {
                val batch = db.batch()
                chunk.forEach { batch.delete(it) }
                batch.commit().await()
                done += chunk.size
            } catch (e: Exception) {
                Log.w(TAG, "deleteInChunks: ${e.message} (${chunk.size} mục)", e)
            }
        }
        return done
    }

    fun observeLocationHistory(pairingId: String): Flow<List<LocationHistoryEntry>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("location_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeLocationHistory error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(LocationHistoryEntry::class.java) }
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeLocationHistoryForUser(userId: String): Flow<List<LocationHistoryEntry>> = callbackFlow {
        if (userId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("location_history")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeLocationHistoryForUser error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.toObject(LocationHistoryEntry::class.java) }
                    trySend(list)
                } else {
                    trySend(emptyList())
                }
            }
        awaitClose { listener.remove() }
    }

    fun observeSchedule(pairingId: String): Flow<UsageSchedule?> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(null)
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("schedules").document("default_sched")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeSchedule error", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    trySend(
                        snapshot.toObject(UsageSchedule::class.java)
                            ?.let { ConsentPolicy.healLegacyKeys(it, snapshot.data) }
                            ?.let(ConsentPolicy::normalizePending)
                    )
                } else {
                    trySend(null)
                }
            }
        awaitClose { listener.remove() }
    }

    /**
     * Ghi lich sang Firestore. VI LOAI exception co tinh y: noi dung goi
     * `updateBlockRules` trong ViewModel can biet that bai de hoi lai gia tri cu.
     * Neu an loi o day thi UI bao "da luu" trong khi luat chan khong bao gio
     * duoc ghi — nguoi dung tuong la app dang chan trong khi khong.
     */
    suspend fun saveSchedule(schedule: UsageSchedule) {
        db.collection("pairings").document(schedule.pairingId)
            .collection("schedules").document(schedule.id.ifEmpty { "default_sched" })
            .set(schedule).await()
    }

    /**
     * Them mot dong vao nhat ky noi quy (`pairings/{id}/rule_audit`).
     *
     * Khong try/catch: nhat ky la bang chung "ai da doi luat" phuc vu muc
     * tieu minh bach cua tinh nang, ghi that thi UI phai biet. Nhung loi o
     * day khong duoc lam hu viec luu luat -> ViewModel goi no trong mot
     * `runCatching` rieng.
     */
    suspend fun appendRuleAudit(entry: RuleAuditEntry) {
        val docId = entry.id.ifEmpty { UUID.randomUUID().toString() }
        db.collection("pairings").document(entry.pairingId)
            .collection("rule_audit").document(docId)
            .set(entry.copy(id = docId)).await()
    }

    fun observeRuleAudit(pairingId: String): Flow<List<RuleAuditEntry>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("rule_audit")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeRuleAudit error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(RuleAuditEntry::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Ghi trang thai "co luu diem di chuyen khong" len document ghep noi de
     * CA HAI MAY cung thay. Truoc day no nam trong SharedPreferences cua
     * tung may, nghia la tat di thi ben kia khong he biet.
     *
     * De ngoai exception: nguoi dung phai nhin thay "khong luu duoc" chu
     * khong dung tuong la ben kia cung da biet minh tat dinh vi.
     */
    suspend fun updateHistoryConsent(
        pairingId: String,
        enabled: Boolean,
        role: UserRole,
        actorName: String
    ) {
        db.collection("pairings").document(pairingId)
            .update(
                mapOf(
                    "locationHistoryEnabled" to enabled,
                    "historyChangedByRole" to role.name,
                    "historyChangedByName" to actorName,
                    "historyChangedAt" to System.currentTimeMillis(),
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()
    }

    /** Khong an loi: `update()` len document khong ton tai phai cho UI biet. */
    suspend fun updateDailyTargetMinutes(pairingId: String, newTargetMinutes: Int) =
        db.collection("pairings").document(pairingId)
            .collection("schedules").document("default_sched")
            .update("dailyTargetMinutes", newTargetMinutes).await()


    suspend fun requestExtraTime(request: ExtraTimeRequest) {
        val reqId = request.id.ifEmpty { UUID.randomUUID().toString() }
        db.collection("pairings").document(request.pairingId)
            .collection("extra_time").document(reqId)
            .set(request.copy(id = reqId)).await()
    }

    /**
     * Ghi mot "chạm tay" (Zenly Bump) len document ghep noi. KHONG chan
     * exception: neu khong ghi duoc thi nguoi dung phai thay "chua gui duoc",
     * khong duoc rung may minh roi bao la ben kia da rung.
     */
    suspend fun sendBump(pairingId: String, atMillis: Long, role: UserRole, actorName: String) =
        db.collection("pairings").document(pairingId)
            .update(
                mapOf(
                    "lastBumpAt" to atMillis,
                    "lastBumpByRole" to role.name,
                    "lastBumpByName" to actorName,
                    "updatedAt" to System.currentTimeMillis()
                )
            ).await()

    /**
     * Ba me bat mot phien "hoc tap trung" tren may con.
     *
     * Ghi thang vao `pairings/{id}` — KHONG đi qua phieu dong thuan nhu
     * UsageSchedule, vi bam nut thi phai co hieu luc ngay. Cung vi vay no nam
     * o document ma rules production da cho thanh vien cap nhat, nen ship
     * duoc ma khong can deploy rules lai (khac voi `agreements` /
     * `rule_audit` dang bi 403).
     *
     * Khong bat loi: that bai ghi phai den duoc mat nguoi dung, de ho khong
     * tuong rang may con dang bi chan trong khi that ra khong.
     */
    suspend fun startStudySession(pairingId: String, minutes: Int, actorName: String) {
        if (pairingId.isEmpty()) error("Chưa ghép nối nên không gửi được phiên học.")
        val now = System.currentTimeMillis()
        val safeMinutes = minutes.coerceIn(1, 240)
        db.collection("pairings").document(pairingId)
            .update(
                mapOf(
                    "studyUntil" to (now + safeMinutes * 60_000L),
                    "studyByName" to actorName,
                    "studyStartedAt" to now
                )
            ).await()
    }

    /** May nao cung co dung duoc — con noi quy thi duoc quyet dinh lai. */
    suspend fun stopStudySession(pairingId: String) {
        if (pairingId.isEmpty()) error("Chưa ghép nối nên không dừng được phiên học.")
        db.collection("pairings").document(pairingId)
            .update(
                mapOf(
                    "studyUntil" to 0L,
                    "studyByName" to "",
                    "studyStartedAt" to 0L
                )
            ).await()
    }



    fun observeExtraTimeRequests(pairingId: String): Flow<List<ExtraTimeRequest>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("extra_time")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeExtraTimeRequests error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ExtraTimeRequest::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Duyet phieu xin them phut. Phieu duoc ghi TRUOC va loi ghi phieu phai
     * nem ra ngoai (truoc day `Log.w` an het nen "Đã duyệt" hiện ra du khong
     * co gi len may chu). Cong them phut vao han muc la buoc thu hai: neu no
     * that bai thi bao ro rang phe duyet da ghi nhung han muc chua doi, de
     * nguoi dung khong tuong "con 30 phut nua" trong khi may van chan.
     */
    suspend fun respondToExtraTime(pairingId: String, requestId: String, approved: Boolean, minutesToAdd: Int) {
        db.collection("pairings").document(pairingId)
            .collection("extra_time").document(requestId)
            .update(
                mapOf(
                    "status" to if (approved) RequestStatus.APPROVED.name else RequestStatus.REJECTED.name,
                    "respondedAt" to System.currentTimeMillis()
                )
            ).await()

        if (!approved || minutesToAdd <= 0) return

        val schedRef = db.collection("pairings").document(pairingId)
            .collection("schedules").document("default_sched")
        val failure = runCatching {
            val snap = schedRef.get().await()
            val data = snap.data.orEmpty()
            if (!snap.exists()) {
                throw IllegalStateException("chưa có lịch")
            }
            // `getLong()` nem exception neu Firestore luu truong nay
            // kieu Double/Int khac — doc qua map de ep kieu an toan.
            // Thieu truong = CHUA dat han muc (0), khong phai "120 phut".
            val currentTarget = (data["dailyTargetMinutes"] as? Number)?.toLong() ?: 0L
            val updates = mutableMapOf<String, Any>(
                "dailyTargetMinutes" to currentTarget + minutesToAdd
            )
            schedRef.update(updates).await()
        }.exceptionOrNull()
        if (failure != null) {
            throw IllegalStateException(
                "Đã ghi phiếu duyệt, nhưng CHƯA cộng thêm $minutesToAdd phút vào hạn mức " +
                    "(gia đình chưa có lịch hoặc máy chủ từ chối ghi). Tạo lịch ở màn Nội quy rồi thử lại.",
                failure
            )
        }
    }

    suspend fun requestPrivacyHours(request: PrivacyRequest) {
        val reqId = request.id.ifEmpty { UUID.randomUUID().toString() }
        db.collection("pairings").document(request.pairingId)
            .collection("privacy_requests").document(reqId)
            .set(request.copy(id = reqId)).await()
    }

    fun observePrivacyRequests(pairingId: String): Flow<List<PrivacyRequest>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("privacy_requests")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observePrivacyRequests error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(PrivacyRequest::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Duyet phieu "gio trong quyen rieng". Khong an loi nua. Ghi `isPrivacyActive`
     * bang merge: truoc day `update()` len document vi tri chua ton tai nem
     * NOT_FOUND bi bat, nen phieu duoc duyet ma may con van bi theo doi.
     */
    suspend fun respondToPrivacyRequest(pairingId: String, requestId: String, childId: String, approved: Boolean) {
        db.collection("pairings").document(pairingId)
            .collection("privacy_requests").document(requestId)
            .update(
                mapOf(
                    "status" to if (approved) RequestStatus.APPROVED.name else RequestStatus.REJECTED.name,
                    "respondedAt" to System.currentTimeMillis()
                )
            ).await()

        if (approved && childId.isNotEmpty()) {
            db.collection("locations").document(childId).set(
                mapOf(
                    "isPrivacyActive" to true,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }
    }

    suspend fun saveAgreement(agreement: ScreenTimeAgreement) {
        db.collection("pairings").document(agreement.pairingId)
            .collection("agreements").document(agreement.id)
            .set(agreement).await()
    }

    fun observeAgreements(pairingId: String): Flow<List<ScreenTimeAgreement>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("agreements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeAgreements error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.toObjects(ScreenTimeAgreement::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Gui tinn nhan gia dinh. Loi PHAI nem ra ngoai: thu vien `try/catch` +
     * `Log.w` khien hop thoai mat tin nhan sau khi bam Gui ma khong bao gio
     * noi la chua gui duoc (listener echo chi phat lai khi ghi thanh cong).
     */
    suspend fun sendMessage(message: FamilyMessage) {
        val msgId = message.id.ifEmpty { UUID.randomUUID().toString() }
        db.collection("pairings").document(message.pairingId)
            .collection("messages").document(msgId)
            .set(message.copy(id = msgId)).await()
    }

    suspend fun toggleMessageStar(pairingId: String, messageId: String, isStarred: Boolean) =
        db.collection("pairings").document(pairingId)
            .collection("messages").document(messageId)
            .update("isStarred", isStarred).await()

    fun observeMessages(pairingId: String): Flow<List<FamilyMessage>> = callbackFlow {
        if (pairingId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("pairings").document(pairingId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "observeMessages error", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val msg = doc.toObject(FamilyMessage::class.java) ?: return@mapNotNull null
                    // `toggleMessageStar` ghi ro khoa "isStarred", nhung ban cu
                    // ghi ca object nen tin cu con mang khoa "starred" (duoc cat
                    // chu "is"). Model khai bao ten day du thi khoa cu bi bo qua
                    // -> dau sao bien mat. Lay khoa moi neu co, khong thi ve.
                    if (msg.isStarred || doc.contains("isStarred")) msg
                    else msg.copy(isStarred = doc.getBoolean("starred") == true)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
    /**
     * Ghep noi tuc thi tu phia phu huynh. Chi ghi duoc hai thu: document ghep
     * noi (moi tai khoan da dang nhap duoc tao) va ho so CUA MINH. Ho so cua
     * con van nguyen ven vi `firestore.rules` chi cho chinh no ghi — may con
     * se tu thay document mang `childId` cua no va tu gan lay
     * ([observePairingsFor]), nen "Ghép nối thành công" khong con la mot loi bao
     * mot chieu.
     */
    suspend fun pairDirectly(studentCode: String, parentProfile: UserProfile): Pairing {
        val student = findStudentByCode(studentCode)
            ?: throw IllegalArgumentException("Không tìm thấy học sinh với mã $studentCode")
        if (student.id == parentProfile.id) {
            throw IllegalArgumentException("Mã này chính là máy đang đăng nhập — không thể tự ghép với mình.")
        }

        val pairingId = "pair_${parentProfile.id}_${student.id}"
        val pairing = Pairing(
            id = pairingId,
            parentId = parentProfile.id,
            childId = student.id,
            studentCode = studentCode.trim().uppercase(),
            parentName = parentProfile.displayName.ifEmpty { "Phụ huynh" },
            childName = student.displayName.ifEmpty { "Học sinh" },
            status = PairingStatus.PAIRED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        db.collection("pairings").document(pairingId).set(pairing).await()
        val ownRef = db.collection("users").document(parentProfile.id)
        val own = runCatching { ownRef.get().await() }.getOrNull()
        // 68.0: THEM vao MANG, khong chi ghi truong "mot". Mot may phu huynh co
        // the tham gia nhieu ho so (hai con, ho so chung cua vo chong); rules
        // `attestedPair` doc `activePairingIds` cua ca hai ben, nen ben nay cung
        // can thay id nay nam trong danh sach cua minh.
        val existingOwn = (own?.get("activePairingIds") as? List<*>)
            ?.filterIsInstance<String>().orEmpty()
        ownRef.update(
            mapOf(
                "pairedWith" to student.id,
                "activePairingId" to pairingId,
                "activePairingIds" to (existingOwn + pairingId).distinct()
            )
        ).await()

        return pairing
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371e3 // metres
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lon2 - lon1)

        val a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
                Math.cos(φ1) * Math.cos(φ2) *
                Math.sin(Δλ / 2) * Math.sin(Δλ / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return R * c
    }
}
