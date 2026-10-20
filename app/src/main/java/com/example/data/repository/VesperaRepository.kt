package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.model.ExtraTimeRequest
import com.example.data.model.FamilyMessage
import com.example.data.model.LocationData
import com.example.data.model.LocationHistoryEntry
import com.example.data.model.Pairing
import com.example.data.model.PairingRequest
import com.example.data.model.PairingStatus
import com.example.data.model.PrivacyRequest
import com.example.data.model.RequestStatus
import com.example.data.model.RuleAuditEntry
import com.example.data.model.ScreenTimeAgreement
import com.example.data.model.UsageSchedule
import com.example.data.model.UserProfile
import com.example.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

private const val TAG = "VesperaRepository"

class VesperaRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth, private val context: Context? = null) {

    constructor(context: Context) : this(
        try {
            val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
            if (resId != 0) {
                val dbId = context.getString(resId)
                if (dbId.isNotBlank()) FirebaseFirestore.getInstance(dbId) else FirebaseFirestore.getInstance()
            } else {
                FirebaseFirestore.getInstance()
            }
        } catch (e: Exception) {
            FirebaseFirestore.getInstance()
        },
        FirebaseAuth.getInstance(),
        context
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid ?: "guest_${System.currentTimeMillis()}"
    }

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

    suspend fun saveUserProfile(profile: UserProfile) {
        val uid = profile.id.ifEmpty { auth.currentUser?.uid ?: "user_${System.currentTimeMillis()}" }
        try {
            db.collection("users").document(uid).set(profile.copy(id = uid)).await()
        } catch (e: Exception) {
            Log.w(TAG, "saveUserProfile error", e)
        }
    }

    /**
     * Student Code generator (HS-XXXXX: Unique 5-char alphanumeric)
     */
    /**
     * Student Code generator (HS-XXXXXXXX: Unique 8-char alphanumeric)
     */
    fun generateStudentCode(): String {
        val allowedChars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        val randomPart = (1..8)
            .map { allowedChars.random() }
            .joinToString("")
        return "HS-$randomPart"
    }


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

    fun regenerateDeviceStudentCode(): String {
        val prefs = context?.getSharedPreferences("vespera_device_prefs", android.content.Context.MODE_PRIVATE)
        val code = generateStudentCode()
        prefs?.edit()?.putString("unique_student_code", code)?.apply()
        return code
    }

    fun getOrGenerateVerificationPin(): String {
        val prefs = context?.getSharedPreferences("vespera_device_prefs", android.content.Context.MODE_PRIVATE)
        var pin = prefs?.getString("unique_verification_pin", "") ?: ""
        if (pin.isBlank() || pin == "8888") {
            pin = (1000..9999).random().toString()
            prefs?.edit()?.putString("unique_verification_pin", pin)?.apply()
        }
        return pin
    }

    fun regenerateVerificationPin(): String {
        val prefs = context?.getSharedPreferences("vespera_device_prefs", android.content.Context.MODE_PRIVATE)
        val pin = (1000..9999).random().toString()
        prefs?.edit()?.putString("unique_verification_pin", pin)?.apply()
        return pin
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
     * Send pairing request from Parent to Child
     */
    suspend fun createPairingRequest(studentCode: String, parentProfile: UserProfile): PairingRequest {
        val student = findStudentByCode(studentCode)
            ?: throw IllegalArgumentException("Không tìm thấy học sinh với mã $studentCode")

        val requestId = UUID.randomUUID().toString()
        val request = PairingRequest(
            id = requestId,
            studentCode = studentCode.trim().uppercase(),
            parentId = parentProfile.id,
            parentName = parentProfile.displayName.ifEmpty { "Phụ huynh" },
            parentEmail = parentProfile.email,
            childId = student.id,
            status = RequestStatus.PENDING,
            createdAt = System.currentTimeMillis()
        )

        db.collection("pairing_requests").document(requestId).set(request).await()
        return request
    }

    /**
     * Observe incoming pairing requests for a child
     */
    fun observePendingRequestsForChild(childId: String): Flow<List<PairingRequest>> = callbackFlow {
        if (childId.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }
        val query = db.collection("pairing_requests")
            .whereEqualTo("childId", childId)
            .whereEqualTo("status", RequestStatus.PENDING.name)

        val listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w(TAG, "observePendingRequestsForChild error", error)
                return@addSnapshotListener
            }
            val requests = snapshot?.toObjects(PairingRequest::class.java) ?: emptyList()
            trySend(requests)
        }
        awaitClose { listener.remove() }
    }

    /**
     * Rotate student code for a child after pairing
     */
    suspend fun rotateStudentCode(childId: String) {
        val newCode = generateStudentCode()
        db.collection("users").document(childId).update("studentCode", newCode).await()
    }

    /**
     * Accept pairing request and create permanent Pairing
     */
    suspend fun acceptPairingRequest(request: PairingRequest, childProfile: UserProfile) {
        val pairingId = "pair_${request.parentId}_${request.childId}"
        val pairing = Pairing(
            id = pairingId,
            parentId = request.parentId,
            childId = request.childId,
            studentCode = request.studentCode,
            parentName = request.parentName,
            childName = childProfile.displayName.ifEmpty { "Học sinh" },
            status = PairingStatus.PAIRED,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        try {
            db.collection("pairings").document(pairingId).set(pairing).await()
            db.collection("pairing_requests").document(request.id).update(
                "status", RequestStatus.APPROVED.name
            ).await()
            db.collection("users").document(request.childId).update(
                mapOf(
                    "pairedWith" to request.parentId,
                    "activePairingId" to pairingId
                )
            ).await()
            db.collection("users").document(request.parentId).update(
                mapOf(
                    "pairedWith" to request.childId,
                    "activePairingId" to pairingId
                )
            ).await()

            // Rotate child code to invalidate old one
            rotateStudentCode(request.childId)

            val defaultSchedule = UsageSchedule(
                id = "default_sched",
                pairingId = pairingId,
                title = "Lịch sinh hoạt chuẩn",
                dailyTargetMinutes = 120,
                // 0 chu khong phai 45: khong co duong nao ghi truong nay ve
                // sau, nen con so 45 se nam lai trong tai lieu va duoc hien
                // len nhu "hom nay da dung 45 phut" — du hom do may chua mo.
                usedMinutes = 0,
                allowedStartHour = 7,
                allowedStartMinute = 0,
                allowedEndHour = 21,
                allowedEndMinute = 30,
                daysOfWeek = "T2, T3, T4, T5, T6, T7, CN",
                isReminderOnly = true,
                isActive = true
            )
            db.collection("pairings").document(pairingId)
                .collection("schedules").document("default_sched").set(defaultSchedule).await()
        } catch (e: Exception) {
            Log.w(TAG, "acceptPairingRequest error", e)
        }
    }

    suspend fun savePairing(pairing: Pairing) {
        try {
            db.collection("pairings").document(pairing.id).set(pairing).await()
            db.collection("users").document(pairing.childId).update(
                mapOf(
                    "pairedWith" to pairing.parentId,
                    "activePairingId" to pairing.id
                )
            ).await()
            db.collection("users").document(pairing.parentId).update(
                mapOf(
                    "pairedWith" to pairing.childId,
                    "activePairingId" to pairing.id
                )
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "savePairing error", e)
        }
    }

    suspend fun rejectPairingRequest(requestId: String) {
        try {
            db.collection("pairing_requests").document(requestId).update(
                "status", RequestStatus.REJECTED.name
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "rejectPairingRequest error", e)
        }
    }

    suspend fun unpair(pairingId: String, parentId: String, childId: String) {
        try {
            db.collection("pairings").document(pairingId).update(
                "status", PairingStatus.UNPAIRED.name,
                "updatedAt", System.currentTimeMillis()
            ).await()
            db.collection("users").document(parentId).update(
                "activePairingId", "",
                "pairedWith", ""
            ).await()
            db.collection("users").document(childId).update(
                "activePairingId", "",
                "pairedWith", ""
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "unpair error", e)
        }
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

    fun isWifiOrInternetConnected(): Boolean {
        val ctx = context ?: return true
        return try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return true
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
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
                    trySend(snapshot.toObject(UsageSchedule::class.java))
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

    suspend fun updateDailyTargetMinutes(pairingId: String, newTargetMinutes: Int) {
        try {
            db.collection("pairings").document(pairingId)
                .collection("schedules").document("default_sched")
                .update("dailyTargetMinutes", newTargetMinutes).await()
        } catch (e: Exception) {
            Log.w(TAG, "updateDailyTargetMinutes error", e)
        }
    }


    suspend fun requestExtraTime(request: ExtraTimeRequest) {
        try {
            val reqId = request.id.ifEmpty { UUID.randomUUID().toString() }
            db.collection("pairings").document(request.pairingId)
                .collection("extra_time").document(reqId)
                .set(request.copy(id = reqId)).await()
        } catch (e: Exception) {
            Log.w(TAG, "requestExtraTime error", e)
        }
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

    suspend fun respondToExtraTime(pairingId: String, requestId: String, approved: Boolean, minutesToAdd: Int) {
        try {
            db.collection("pairings").document(pairingId)
                .collection("extra_time").document(requestId)
                .update(
                    mapOf(
                        "status" to if (approved) RequestStatus.APPROVED.name else RequestStatus.REJECTED.name,
                        "respondedAt" to System.currentTimeMillis()
                    )
                ).await()

            if (approved && minutesToAdd > 0) {
                val schedRef = db.collection("pairings").document(pairingId)
                    .collection("schedules").document("default_sched")
                // Doc that bai thi cung khong duoc lam mat ket qua da duyet o
                // tren: phieu extra_time da mang tinh APPROVED.
                val snap = runCatching { schedRef.get().await() }.getOrNull()
                if (snap != null && snap.exists()) {
                    val data = snap.data.orEmpty()
                    // `getLong()` nem exception neu Firestore luu truong nay
                    // kieu Double/Int khac — doc qua map de ep kieu an toan.
                    val currentTarget = (data["dailyTargetMinutes"] as? Number)?.toLong() ?: 120L
                    val updates = mutableMapOf<String, Any>(
                        "dailyTargetMinutes" to currentTarget + minutesToAdd
                    )
                    // Neu dang co phieu noi quy mo (ben kia chua duyet) va phieu
                    // do cung doi han muc: cong them vao CA phieu. Neu khong, khi
                    // bam Dong y, `pendingDailyTargetMinutes` de len ban vua
                    // tang — so phut xin them bi xoa im lang.
                    val pendingTarget = (data["pendingDailyTargetMinutes"] as? Number)?.toLong()
                    if (data["changeStatus"] == RequestStatus.PENDING.name && pendingTarget != null) {
                        updates["pendingDailyTargetMinutes"] = pendingTarget + minutesToAdd
                    }
                    schedRef.update(updates).await()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "respondToExtraTime error", e)
        }
    }

    suspend fun requestPrivacyHours(request: PrivacyRequest) {
        try {
            val reqId = request.id.ifEmpty { UUID.randomUUID().toString() }
            db.collection("pairings").document(request.pairingId)
                .collection("privacy_requests").document(reqId)
                .set(request.copy(id = reqId)).await()
        } catch (e: Exception) {
            Log.w(TAG, "requestPrivacyHours error", e)
        }
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

    suspend fun respondToPrivacyRequest(pairingId: String, requestId: String, childId: String, approved: Boolean) {
        try {
            db.collection("pairings").document(pairingId)
                .collection("privacy_requests").document(requestId)
                .update(
                    mapOf(
                        "status" to if (approved) RequestStatus.APPROVED.name else RequestStatus.REJECTED.name,
                        "respondedAt" to System.currentTimeMillis()
                    )
                ).await()

            if (approved) {
                db.collection("locations").document(childId).update(
                    "isPrivacyActive", true,
                    "updatedAt", System.currentTimeMillis()
                ).await()
            }
        } catch (e: Exception) {
            Log.w(TAG, "respondToPrivacyRequest error", e)
        }
    }

    suspend fun saveAgreement(agreement: ScreenTimeAgreement) {
        try {
            db.collection("pairings").document(agreement.pairingId)
                .collection("agreements").document(agreement.id)
                .set(agreement).await()
        } catch (e: Exception) {
            Log.w(TAG, "saveAgreement error", e)
        }
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

    suspend fun sendMessage(message: FamilyMessage) {
        try {
            val msgId = message.id.ifEmpty { UUID.randomUUID().toString() }
            db.collection("pairings").document(message.pairingId)
                .collection("messages").document(msgId)
                .set(message.copy(id = msgId)).await()
        } catch (e: Exception) {
            Log.w(TAG, "sendMessage error", e)
        }
    }

    suspend fun toggleMessageStar(pairingId: String, messageId: String, isStarred: Boolean) {
        try {
            db.collection("pairings").document(pairingId)
                .collection("messages").document(messageId)
                .update("isStarred", isStarred).await()
        } catch (e: Exception) {
            Log.w(TAG, "toggleMessageStar error", e)
        }
    }

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
                val list = snapshot?.toObjects(FamilyMessage::class.java) ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }
    suspend fun pairDirectly(studentCode: String, parentProfile: UserProfile): Pairing {
        val student = findStudentByCode(studentCode)
            ?: throw IllegalArgumentException("Không tìm thấy học sinh với mã $studentCode")

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
        db.collection("users").document(student.id).update(
            mapOf(
                "pairedWith" to parentProfile.id,
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
