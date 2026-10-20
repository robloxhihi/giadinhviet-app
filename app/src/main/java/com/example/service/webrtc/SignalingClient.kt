package com.example.service.webrtc

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription

sealed class SignalingEvent {
    data class OfferReceived(val sdp: String, val isVideo: Boolean, val callerName: String, val isSos: Boolean) : SignalingEvent()
    data class AnswerReceived(val sdp: String) : SignalingEvent()
    data class IceCandidateReceived(val candidate: IceCandidate) : SignalingEvent()
    data class CallStateChanged(val state: String) : SignalingEvent()
    object CallEnded : SignalingEvent()
}

class SignalingClient(
    private val roomId: String,
    private val isCaller: Boolean
) {
    private val TAG = "SignalingClient"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    private var rtdbRef: DatabaseReference? = null
    private var rtdbListener: ValueEventListener? = null
    private var firestoreListener: ListenerRegistration? = null

    init {
        initSignaling()
    }

    private fun initSignaling() {
        try {
            val db = try {
                FirebaseDatabase.getInstance()
            } catch (e: Exception) {
                FirebaseDatabase.getInstance("https://gen-lang-client-0995774334-default-rtdb.firebaseio.com/")
            }
            rtdbRef = db.getReference("calls").child(roomId)
            listenToRtdb()
        } catch (e: Exception) {
            Log.w(TAG, "RTDB initialization error, using Firestore fallback: ${e.message}")
        }
        listenToFirestoreFallback()
    }

    private fun listenToRtdb() {
        val ref = rtdbRef ?: return
        rtdbListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val status = snapshot.child("status").getValue(String::class.java)
                    if (status != null) {
                        scope.launch { _events.emit(SignalingEvent.CallStateChanged(status)) }
                        if (status == "ended") {
                            scope.launch { _events.emit(SignalingEvent.CallEnded) }
                            return
                        }
                    }

                    if (!isCaller) {
                        // Callee listens for Offer
                        val offerSdp = snapshot.child("offer").child("sdp").getValue(String::class.java)
                        val isVideo = snapshot.child("isVideo").getValue(Boolean::class.java) ?: false
                        val callerName = snapshot.child("callerName").getValue(String::class.java).orEmpty()
                        val isSos = snapshot.child("isSos").getValue(Boolean::class.java) ?: false

                        if (!offerSdp.isNullOrEmpty()) {
                            scope.launch {
                                _events.emit(SignalingEvent.OfferReceived(offerSdp, isVideo, callerName, isSos))
                            }
                        }

                        // Callee listens for Caller Candidates
                        val callerCandidates = snapshot.child("callerCandidates")
                        for (child in callerCandidates.children) {
                            val sdp = child.child("sdp").getValue(String::class.java)
                            val sdpMid = child.child("sdpMid").getValue(String::class.java)
                            val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                            if (sdp != null && sdpMid != null) {
                                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                scope.launch { _events.emit(SignalingEvent.IceCandidateReceived(candidate)) }
                            }
                        }
                    } else {
                        // Caller listens for Answer
                        val answerSdp = snapshot.child("answer").child("sdp").getValue(String::class.java)
                        if (!answerSdp.isNullOrEmpty()) {
                            scope.launch { _events.emit(SignalingEvent.AnswerReceived(answerSdp)) }
                        }

                        // Caller listens for Callee Candidates
                        val calleeCandidates = snapshot.child("calleeCandidates")
                        for (child in calleeCandidates.children) {
                            val sdp = child.child("sdp").getValue(String::class.java)
                            val sdpMid = child.child("sdpMid").getValue(String::class.java)
                            val sdpMLineIndex = child.child("sdpMLineIndex").getValue(Int::class.java) ?: 0
                            if (sdp != null && sdpMid != null) {
                                val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
                                scope.launch { _events.emit(SignalingEvent.IceCandidateReceived(candidate)) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing RTDB signaling data", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "RTDB onCancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(rtdbListener!!)
    }

    private fun listenToFirestoreFallback() {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestoreListener = firestore.collection("calls").document(roomId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    try {
                        val status = snapshot.getString("status")
                        if (status == "ended") {
                            scope.launch { _events.emit(SignalingEvent.CallEnded) }
                        } else if (!isCaller) {
                            val offerMap = snapshot.get("offer") as? Map<*, *>
                            val offerSdp = offerMap?.get("sdp") as? String
                            val isVideo = snapshot.getBoolean("isVideo") ?: false
                            val callerName = snapshot.getString("callerName").orEmpty()
                            val isSos = snapshot.getBoolean("isSos") ?: false
                            if (!offerSdp.isNullOrEmpty()) {
                                scope.launch {
                                    _events.emit(SignalingEvent.OfferReceived(offerSdp, isVideo, callerName, isSos))
                                }
                            }
                        } else {
                            val answerMap = snapshot.get("answer") as? Map<*, *>
                            val answerSdp = answerMap?.get("sdp") as? String
                            if (!answerSdp.isNullOrEmpty()) {
                                scope.launch { _events.emit(SignalingEvent.AnswerReceived(answerSdp)) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Firestore fallback listener error: ${e.message}")
                    }
                }
        } catch (e: Exception) {
            Log.d(TAG, "Firestore signaling listener setup failed: ${e.message}")
        }
    }

    fun sendOffer(description: SessionDescription, isVideo: Boolean, callerName: String, isSos: Boolean = false) {
        val offerData = mapOf(
            "type" to "offer",
            "sdp" to description.description
        )
        val callPayload = mapOf(
            "status" to "calling",
            "offer" to offerData,
            "isVideo" to isVideo,
            "callerName" to callerName,
            "isSos" to isSos,
            "timestamp" to System.currentTimeMillis()
        )

        rtdbRef?.updateChildren(callPayload)
        try {
            FirebaseFirestore.getInstance().collection("calls").document(roomId).set(callPayload)
        } catch (e: Exception) {
            Log.d(TAG, "Firestore sendOffer error: ${e.message}")
        }
    }

    fun sendAnswer(description: SessionDescription) {
        val answerData = mapOf(
            "type" to "answer",
            "sdp" to description.description
        )
        val updatePayload = mapOf(
            "status" to "connected",
            "answer" to answerData
        )

        rtdbRef?.updateChildren(updatePayload)
        try {
            FirebaseFirestore.getInstance().collection("calls").document(roomId).update(updatePayload)
        } catch (e: Exception) {
            Log.d(TAG, "Firestore sendAnswer error: ${e.message}")
        }
    }

    fun sendIceCandidate(candidate: IceCandidate) {
        val candidateData = mapOf(
            "sdp" to candidate.sdp,
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex
        )
        val targetNode = if (isCaller) "callerCandidates" else "calleeCandidates"
        rtdbRef?.child(targetNode)?.push()?.setValue(candidateData)

        try {
            FirebaseFirestore.getInstance().collection("calls").document(roomId)
                .collection(targetNode).add(candidateData)
        } catch (e: Exception) {
            Log.d(TAG, "Firestore candidate error: ${e.message}")
        }
    }

    fun endCall() {
        val endPayload = mapOf("status" to "ended")
        rtdbRef?.updateChildren(endPayload)
        try {
            FirebaseFirestore.getInstance().collection("calls").document(roomId).update(endPayload)
        } catch (e: Exception) {
            Log.d(TAG, "Firestore endCall error: ${e.message}")
        }
    }

    fun cleanup() {
        rtdbListener?.let { rtdbRef?.removeEventListener(it) }
        firestoreListener?.remove()
    }
}
