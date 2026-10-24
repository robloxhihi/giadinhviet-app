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
        db.collection("users").document(uid).set(profile.copy(id = uid)).await()
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
     * ([findPairingFor] chi tra ve cap dang PAIRED).
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

    /**
     * Tim document ghep noi ma tai khoan nay la mot ben, dung cho lan hien
     * dau tien: may con ghi `activePairingId` rong vi phu huynh khong co quyen
     * ghi ho so cua no. Tra ve null khi may nay that su chua duoc ghep voi ai.
     */
    suspend fun findPairingFor(profile: UserProfile): Pairing? {
        if (profile.id.isEmpty()) return null
        val roleField = if (profile.role == UserRole.PARENT) "parentId" else "childId"
        return try {
            // Khong `orderBy`: loc bang `parentId` + sap theo `createdAt` la mot
            // cau truy van composite, can thiet biFirestore tao chi muc phu —
            // khong co no thi query loi va may con im lang "chua ghep noi".
            // Hai bang can don muc do du, nen lay ve roi chuc lan cuoi cung.
            db.collection("pairings")
                .whereEqualTo(roleField, profile.id)
                .whereEqualTo("status", PairingStatus.PAIRED.name)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Pairing::class.java) }
                .maxByOrNull { it.createdAt }
        } catch (e: Exception) {
            Log.w(TAG, "findPairingFor error", e)
            null
        }
    }

    /**
     * May nay tu ghi ho so cua chinh no: "toi dang ghep voi ai, theo document
     * nao". Day la buoc ma truoc day app tuong la ben kia lam ho duoc.
     */
    suspend fun linkSelfToPairing(
        userId: String,
        pairingId: String,
        partnerId: String
    ) {
        if (userId.isEmpty()) return
        db.collection("users").document(userId).update(
            mapOf(
                "pairedWith" to partnerId,
                "activePairingId" to pairingId
            )
        ).await()
    }

    /** Xoa lien ket cua may nay khi document ghep noi khong con PAIRED nua. */
    suspend fun unlinkSelf(userId: String) {
        if (userId.isEmpty()) return
        db.collection("users").document(userId).update(
            mapOf(
                "pairedWith" to "",
                "activePairingId" to ""
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

    suspend fun flushOfflineLocations() {
        val ctx = context ?: return
        try {
            if (!isWifiOrInternetConnected()) return
            val prefs = ctx.getSharedPreferences("vespera_offline_gps", Context.MODE_PRIVATE)
            val jsonList = prefs.getString("queue", "[]") ?: "[]"
            if (jsonList == "[]" || jsonList.isBlank()) return

            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
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

    suspend fun saveLocationHistory(entry: LocationHistoryEntry) {
        try {
            if (auth.currentUser == null || !isWifiOrInternetConnected()) return
            
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
                
                if (entry.pairingId.isNotEmpty()) {
                    db.collection("pairings").document(entry.pairingId)
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
                if (entry.pairingId.isNotEmpty()) {
                    db.collection("pairings").document(entry.pairingId)
                        .collection("location_history").document(docId).set(dataMap).await()
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "saveLocationHistory offline/permission bypass: ${e.message}")
        }
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
     * ([findPairingFor]), nen "Ghép nối thành công" khong con la mot loi bao
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
        db.collection("users").document(parentProfile.id).update(
            mapOf(
                "pairedWith" to student.id,
                "activePairingId" to pairingId
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
