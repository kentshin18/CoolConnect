package com.example.coolconnect

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TechnicianViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val repository: CoolConnectRepository,
    private val aiRepository: AiAssistantRepository
) : ViewModel() {

    private val _techName = MutableStateFlow("Technician")
    val techName: StateFlow<String> = _techName.asStateFlow()

    private val _profilePicUrl = MutableStateFlow<String?>(null)
    val profilePicUrl: StateFlow<String?> = _profilePicUrl.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _aiFixResult = MutableStateFlow<String?>(null)
    val aiFixResult: StateFlow<String?> = _aiFixResult.asStateFlow()

    private val _isAiAnalyzing = MutableStateFlow(false)
    val isAiAnalyzing: StateFlow<Boolean> = _isAiAnalyzing.asStateFlow()

    private val _hasConsented = MutableStateFlow(true)
    val hasConsented: StateFlow<Boolean> = _hasConsented.asStateFlow()

    private val _verificationStatus = MutableStateFlow("Not Verified")
    val verificationStatus: StateFlow<String> = _verificationStatus.asStateFlow()

    private val _isKycUploading = MutableStateFlow(false)
    val isKycUploading: StateFlow<Boolean> = _isKycUploading.asStateFlow()

    // 🟢 Observable Online Status (null = loading / neutral state)
    private val _isOnline = MutableStateFlow<Boolean?>(null)
    val isOnline: StateFlow<Boolean?> = _isOnline.asStateFlow()

    // 🔵 Workload State Flows
    private val _activeJobs = MutableStateFlow<List<TechJobDetail>>(emptyList())
    val activeJobs: StateFlow<List<TechJobDetail>> = _activeJobs.asStateFlow()

    private val _primaryJob = MutableStateFlow<TechJobDetail?>(null)
    val primaryJob: StateFlow<TechJobDetail?> = _primaryJob.asStateFlow()

    private val _historyJobs = MutableStateFlow<List<ServiceHistoryItem>>(emptyList())
    val historyJobs: StateFlow<List<ServiceHistoryItem>> = _historyJobs.asStateFlow()

    private val _adminPhone = MutableStateFlow<String?>(null)
    val adminPhone: StateFlow<String?> = _adminPhone.asStateFlow()

    // 🗺️ Navigation & Route State
    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints: StateFlow<List<LatLng>> = _routePoints.asStateFlow()

    private val _routeDuration = MutableStateFlow<String?>(null)
    val routeDuration: StateFlow<String?> = _routeDuration.asStateFlow()

    private val _routeDistance = MutableStateFlow<String?>(null)
    val routeDistance: StateFlow<String?> = _routeDistance.asStateFlow()

    private val _assignedCustomerId = MutableStateFlow<String?>(null)
    val assignedCustomerId: StateFlow<String?> = _assignedCustomerId.asStateFlow()

    private val _assignedCustomerName = MutableStateFlow<String?>(null)
    val assignedCustomerName: StateFlow<String?> = _assignedCustomerName.asStateFlow()

    // Firestore Listener Registrations
    private var techDataListener: ListenerRegistration? = null
    private var isOnlineListener: ListenerRegistration? = null
    private var activeJobListener: ListenerRegistration? = null
    private var historyJobsListener1: ListenerRegistration? = null
    private var historyJobsListener2: ListenerRegistration? = null
    private var historyJobsListener3: ListenerRegistration? = null
    private var adminUserListener: ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                listenTechData(uid)
                listenOnlineStatus(uid)
            } else {
                cleanupListeners()
            }
        }
        auth.currentUser?.uid?.let { uid ->
            listenTechData(uid)
            listenOnlineStatus(uid)
        }
        listenAdminPhone()
    }

    fun isActiveStatus(status: String): Boolean {
        return !status.equals("Completed", ignoreCase = true) &&
                !status.equals("Denied", ignoreCase = true) &&
                !status.equals("Request Cancelled", ignoreCase = true) &&
                !status.equals("Cancelled", ignoreCase = true)
    }

    private fun listenOnlineStatus(uid: String) {
        isOnlineListener?.remove()
        isOnlineListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                _isOnline.value = doc.getBoolean("isOnline") ?: true
            } else {
                _isOnline.value = false
            }
        }
    }

    fun setOnlineStatus(online: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        _isOnline.value = online
        db.collection("users").document(uid).update("isOnline", online)
    }

    private fun listenTechData(uid: String) {
        techDataListener?.remove()
        techDataListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val name = doc.getString("name") ?: "Technician"
                _techName.value = name
                _verificationStatus.value = doc.getString("verificationStatus") ?: "Not Verified"
                if (!_isUploading.value) {
                    _profilePicUrl.value = doc.getString("profilePicUrl")
                }
                _hasConsented.value = doc.getBoolean("hasConsented") ?: false
                listenActiveJobs(uid, name)
                listenHistoryJobs(uid, name)
            } else {
                _hasConsented.value = false
            }
        }
    }

    private fun listenActiveJobs(techUid: String, techName: String) {
        activeJobListener?.remove()
        if (techUid.isBlank() && techName.isBlank()) return

        activeJobListener = db.collection("service_requests")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("TechnicianViewModel", "Error listening to active jobs: ${e.localizedMessage}", e)
                    return@addSnapshotListener
                }

                val jobs = mutableListOf<TechJobDetail>()
                val seenIds = mutableSetOf<String>()

                snapshot?.documents?.forEach { doc ->
                    try {
                        val docId = doc.id
                        val docTechUid = doc.getString("technicianUid")
                        val docTechName = doc.getString("technicianName") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: ""
                        
                        val isAssignedToThisTech = (docTechUid != null && docTechUid == techUid) ||
                                (techName.isNotBlank() && docTechName.equals(techName, ignoreCase = true))

                        if (!isAssignedToThisTech) return@forEach

                        val type = doc.getString("type") ?: "service"
                        val status = doc.getString("status") ?: "Assigned"
                        val customerId = doc.getString("customerUid") ?: doc.getString("customerId") ?: ""

                        val isApprovedPartsJob = type == "parts_installation" && status != "Pending Admin Approval"
                        val isServiceJob = type == "service" || type == "order_installation"

                        if (isActiveStatus(status) && (isServiceJob || isApprovedPartsJob)) {
                            if (!seenIds.contains(docId)) {
                                seenIds.add(docId)

                                jobs.add(
                                    TechJobDetail(
                                        id = docId,
                                        customerId = customerId,
                                        name = doc.getString("customerName") ?: doc.getString("chatName") ?: "Customer",
                                        phone = doc.getString("customerPhone") ?: "No Phone",
                                        address = doc.getString("customerAddress") ?: doc.getString("address") ?: "No Address",
                                        status = status,
                                        issue = doc.getString("issue") ?: doc.getString("lastRequest") ?: "HVAC Repair & Maintenance",
                                        type = type
                                    )
                                )
                            }
                        }
                    } catch (ex: Exception) {
                        Log.w("TechnicianViewModel", "Error parsing job document ${doc.id}: ${ex.localizedMessage}", ex)
                    }
                }
                
                _activeJobs.value = jobs
                val firstActive = jobs.find { it.status.equals("In Progress", ignoreCase = true) }
                    ?: jobs.find { it.status.equals("Accepted", ignoreCase = true) }
                    ?: jobs.firstOrNull()
                _primaryJob.value = firstActive

                if (firstActive != null) {
                    _assignedCustomerId.value = firstActive.customerId
                    _assignedCustomerName.value = firstActive.name
                } else {
                    _assignedCustomerId.value = null
                    _assignedCustomerName.value = null
                }
            }
    }

    private fun listenHistoryJobs(techUid: String, techName: String) {
        historyJobsListener1?.remove()
        historyJobsListener2?.remove()
        historyJobsListener3?.remove()
        if (techUid.isBlank() && techName.isBlank()) return

        historyJobsListener1 = db.collection("service_requests")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("TechnicianViewModel", "Error listening to history jobs: ${e.localizedMessage}", e)
                    return@addSnapshotListener
                }

                val historyList = mutableListOf<ServiceHistoryItem>()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val docTechUid = doc.getString("technicianUid")
                        val docTechName = doc.getString("technicianName") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: ""
                        val isAssignedToThisTech = (docTechUid != null && docTechUid == techUid) ||
                                (techName.isNotBlank() && docTechName.equals(techName, ignoreCase = true))

                        if (!isAssignedToThisTech) return@forEach

                        val status = doc.getString("status") ?: ""
                        if (status.equals("Completed", ignoreCase = true) || status.equals("Denied", ignoreCase = true)) {
                            val item = ServiceHistoryItem(
                                id = doc.id,
                                name = doc.getString("customerName") ?: doc.getString("chatName") ?: "Customer",
                                phone = doc.getString("customerPhone") ?: "No Phone",
                                address = doc.getString("customerAddress") ?: doc.getString("address") ?: "No Address",
                                status = status,
                                issue = doc.getString("issue") ?: doc.getString("lastRequest") ?: "HVAC Repair & Maintenance",
                                completionNotes = doc.getString("completionNotes") ?: doc.getString("notes") ?: "",
                                partsUsed = doc.getString("partsUsed") ?: "",
                                paymentMethod = doc.getString("paymentMethod") ?: "",
                                laborFee = (doc.get("laborFee") as? Number)?.toDouble() ?: 0.0,
                                partsFee = (doc.get("partsFee") as? Number)?.toDouble() ?: 0.0,
                                vatAmount = (doc.get("vatAmount") as? Number)?.toDouble() ?: 0.0,
                                totalAmount = (doc.get("totalAmount") as? Number)?.toDouble() ?: 0.0,
                                photoProofUrl = doc.getString("photoProofUrl") ?: "",
                                signatureUrl = doc.getString("signatureUrl") ?: "",
                                completedAt = doc.getTimestamp("completedAt"),
                                deniedAt = doc.getTimestamp("deniedAt"),
                                createdAt = doc.getTimestamp("createdAt"),
                                timestamp = doc.getTimestamp("timestamp") ?: doc.getTimestamp("createdAt")
                            )
                            historyList.add(item)
                        }
                    } catch (e: Exception) {
                        Log.w("TechnicianViewModel", "Error parsing history job document ${doc.id}: ${e.localizedMessage}", e)
                    }
                }
                _historyJobs.value = historyList.sortedByDescending { it.completedAt?.toDate()?.time ?: 0L }
            }
    }

    private fun listenAdminPhone() {
        adminUserListener?.remove()
        adminUserListener = db.collection("users")
            .whereEqualTo("role", "admin")
            .addSnapshotListener { snapshot, _ ->
                val adminDoc = snapshot?.documents?.firstOrNull()
                _adminPhone.value = adminDoc?.getString("phone")
            }
    }

    fun startJob(jobId: String) {
        val timestamp = Timestamp.now()
        val currentTech = techName.value
        val updates = mapOf(
            "status" to "In Progress",
            "startedAt" to timestamp,
            "updatedAt" to timestamp
        )

        // Update service request document
        db.collection("service_requests").document(jobId).update(updates)
            .addOnSuccessListener {
                db.collection("service_requests").document(jobId).get().addOnSuccessListener { doc ->
                    val customerUid = doc.getString("customerUid") ?: ""
                    val orderId = doc.getString("orderId")
                    if (!orderId.isNullOrBlank()) {
                        db.collection("orders").document(orderId).update(mapOf("status" to "In Progress"))
                    }
                    if (customerUid.isNotBlank()) {
                        val progressMsg = hashMapOf(
                            "senderId" to "tech",
                            "senderName" to currentTech,
                            "text" to "Technician $currentTech has started working on your request ($jobId).",
                            "timestamp" to timestamp
                        )
                        db.collection("chats_v2").document("admin_$customerUid").collection("messages").add(progressMsg)
                    }
                }
            }
        db.collection("service_history").document(jobId).set(updates, SetOptions.merge())
    }

    // Atomic Job Completion using Firestore WriteBatch
    fun completeJob(
        jobId: String,
        workNotes: String,
        partsUsed: String,
        laborFee: Double,
        partsFee: Double,
        vatAmount: Double,
        totalAmount: Double,
        paymentMethod: String,
        photoProofUrl: String,
        signatureUrl: String,
        techName: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val reqRef = db.collection("service_requests").document(jobId)
        reqRef.get().addOnSuccessListener { reqDoc ->
            val customerUid = reqDoc.getString("customerUid") ?: ""
            val customerName = reqDoc.getString("customerName") ?: "Customer"
            val customerPhone = reqDoc.getString("customerPhone") ?: "No Phone"
            val customerAddress = reqDoc.getString("customerAddress") ?: docAddressFallback(reqDoc)
            val issue = reqDoc.getString("issue") ?: "HVAC Repair & Maintenance"
            val orderId = reqDoc.getString("orderId")

            val batch = db.batch()
            val timestamp = Timestamp.now()
            val hasProof = photoProofUrl.isNotBlank()

            val serviceUpdates = hashMapOf(
                "status" to "Completed",
                "notes" to workNotes,
                "completionNotes" to workNotes,
                "partsUsed" to partsUsed.ifBlank { "Standard / None" },
                "paymentMethod" to paymentMethod,
                "laborFee" to laborFee,
                "partsFee" to partsFee,
                "vatAmount" to vatAmount,
                "totalAmount" to totalAmount,
                "hasPhotoProof" to hasProof,
                "photoProofUrl" to photoProofUrl,
                "signatureUrl" to signatureUrl,
                "completedAt" to timestamp,
                "updatedAt" to timestamp
            )
            batch.update(reqRef, serviceUpdates as Map<String, Any>)

            if (!orderId.isNullOrBlank()) {
                batch.update(db.collection("orders").document(orderId), mapOf("status" to "Completed", "completedAt" to timestamp))
            }

            val serviceHistoryRef = db.collection("service_history").document(jobId)
            val serviceHistoryDoc = hashMapOf(
                "serviceId" to jobId,
                "customerUid" to customerUid,
                "customerName" to customerName,
                "customerPhone" to customerPhone,
                "customerAddress" to customerAddress,
                "technicianName" to techName,
                "lastTechnician" to techName,
                "assignedTechnician" to techName,
                "status" to "Completed",
                "issue" to issue,
                "completionNotes" to workNotes,
                "partsUsed" to partsUsed.ifBlank { "Standard / None" },
                "paymentMethod" to paymentMethod,
                "laborFee" to laborFee,
                "partsFee" to partsFee,
                "vatAmount" to vatAmount,
                "totalAmount" to totalAmount,
                "hasPhotoProof" to hasProof,
                "photoProofUrl" to photoProofUrl,
                "signatureUrl" to signatureUrl,
                "completedAt" to timestamp,
                "timestamp" to timestamp
            )
            batch.set(serviceHistoryRef, serviceHistoryDoc, SetOptions.merge())

            if (customerUid.isNotBlank()) {
                val msgRef = db.collection("chats_v2").document("admin_$customerUid").collection("messages").document()
                val completionMsg = hashMapOf(
                    "senderId" to "tech",
                    "senderName" to techName,
                    "text" to "Job Completed! Total Amount: ₱${String.format(Locale.getDefault(), "%,.2f", totalAmount)} via $paymentMethod",
                    "timestamp" to timestamp
                )
                batch.set(msgRef, completionMsg)
            }

            batch.commit()
                .addOnSuccessListener { onComplete(true, null) }
                .addOnFailureListener { err -> onComplete(false, err.localizedMessage) }
        }.addOnFailureListener { err -> onComplete(false, err.localizedMessage) }
    }

    private fun docAddressFallback(doc: com.google.firebase.firestore.DocumentSnapshot): String {
        return doc.getString("address") ?: "No Address"
    }

    fun getAiFix(query: String) {
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            _aiFixResult.value = null
            try {
                val result = queryAiAssistant(query)
                _aiFixResult.value = result.getOrElse { e ->
                    "Diagnostic Error: ${e.localizedMessage ?: "Unknown error"}"
                }
            } finally {
                _isAiAnalyzing.value = false
            }
        }
    }

    suspend fun queryAiAssistant(
        question: String,
        customerId: String? = null,
        customerName: String? = null
    ): Result<String> {
        val targetCustomerId = customerId?.ifBlank { null }
            ?: _assignedCustomerId.value
            ?: ""
        val targetCustomerName = customerName?.ifBlank { null }
            ?: _assignedCustomerName.value
            ?: ""
        return aiRepository.getTechnicianAssistant(
            question = question,
            customerId = targetCustomerId,
            customerName = targetCustomerName
        )
    }

    fun queryAiAssistantStream(
        question: String,
        customerId: String? = null,
        customerName: String? = null
    ): Flow<String> {
        val targetCustomerId = customerId?.ifBlank { null }
            ?: _assignedCustomerId.value
            ?: ""
        val targetCustomerName = customerName?.ifBlank { null }
            ?: _assignedCustomerName.value
            ?: ""
        return aiRepository.getTechnicianAssistantStream(
            question = question,
            customerId = targetCustomerId,
            customerName = targetCustomerName
        )
    }

    fun fetchRoute(origin: LatLng, destination: LatLng, apiKey: String) {
        viewModelScope.launch {
            val routeResult = NavigationUtils.getRouteDetails(origin, destination, apiKey)
            _routePoints.value = routeResult.points
            _routeDuration.value = routeResult.durationText
            _routeDistance.value = routeResult.distanceText
        }
    }

    fun clearRoute() {
        _routePoints.value = emptyList()
        _routeDuration.value = null
        _routeDistance.value = null
    }

    fun uploadKycDoc(uri: Uri, context: Context) {
        viewModelScope.launch {
            when (val validation = ImageUtils.validateImageUri(context, uri)) {
                is ImageUtils.ValidationResult.Error -> {
                    Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    return@launch
                }
                is ImageUtils.ValidationResult.Success -> { /* proceed */ }
            }
            _isKycUploading.value = true
            try {
                repository.uploadKYCDocument(uri)
                Toast.makeText(context, "Verification document uploaded! Pending review.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isKycUploading.value = false
            }
        }
    }

    fun uploadProfilePic(uri: Uri, context: Context) {
        viewModelScope.launch {
            when (val validation = ImageUtils.validateImageUri(context, uri)) {
                is ImageUtils.ValidationResult.Error -> {
                    Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    return@launch
                }
                is ImageUtils.ValidationResult.Success -> { /* proceed */ }
            }
            _isUploading.value = true
            try {
                val newUrl = repository.uploadProfilePicture(uri)
                _profilePicUrl.value = newUrl
                Toast.makeText(context, "Profile picture updated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("TechnicianViewModel", "Upload failed", e)
                Toast.makeText(context, "Upload failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun uploadBitmapImage(bitmap: Bitmap, folder: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val url = repository.uploadBitmapImage(bitmap, folder)
                onResult(Result.success(url))
            } catch (e: Exception) {
                Log.e("TechnicianViewModel", "Bitmap upload failed", e)
                onResult(Result.failure(e))
            }
        }
    }

    fun updateConsent(consented: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update("hasConsented", consented)
    }

    fun updateLocation(latitude: Double, longitude: Double) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(
            mapOf("latitude" to latitude, "longitude" to longitude, "lastUpdated" to Timestamp.now())
        )
    }

    val aiMessages = mutableStateListOf<AiChatMessage>()
    private var isChatLoaded = false

    fun loadAiChatHistory(context: Context) {
        if (isChatLoaded) return
        isChatLoaded = true
        try {
            val prefs = context.getSharedPreferences("coolconnect_ai_chat", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("chat_history", null) ?: return
            val jsonArray = JSONArray(jsonStr)
            val loadedList = mutableListOf<AiChatMessage>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val senderStr = obj.optString("sender", "USER")
                val sender = if (senderStr == "AI") MessageSender.AI else MessageSender.USER
                loadedList.add(
                    AiChatMessage(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sender = sender,
                        text = obj.optString("text", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        userPrompt = if (obj.has("userPrompt") && !obj.isNull("userPrompt")) obj.optString("userPrompt") else null
                    )
                )
            }
            aiMessages.clear()
            aiMessages.addAll(loadedList)
        } catch (e: Exception) {
            Log.e("TechnicianViewModel", "Error loading AI chat history", e)
        }
    }

    fun saveAiChatHistory(context: Context) {
        try {
            val jsonArray = JSONArray()
            for (msg in aiMessages) {
                if (msg.isPending) continue
                val obj = JSONObject().apply {
                    put("id", msg.id)
                    put("sender", msg.sender.name)
                    put("text", msg.text)
                    put("timestamp", msg.timestamp)
                    if (msg.userPrompt != null) {
                        put("userPrompt", msg.userPrompt)
                    }
                }
                jsonArray.put(obj)
            }
            val prefs = context.getSharedPreferences("coolconnect_ai_chat", Context.MODE_PRIVATE)
            prefs.edit().putString("chat_history", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("TechnicianViewModel", "Error saving AI chat history", e)
        }
    }

    fun clearAiChatHistory(context: Context) {
        aiMessages.clear()
        try {
            val prefs = context.getSharedPreferences("coolconnect_ai_chat", Context.MODE_PRIVATE)
            prefs.edit().remove("chat_history").apply()
        } catch (e: Exception) {
            Log.e("TechnicianViewModel", "Error clearing AI chat history", e)
        }
    }

    private fun cleanupListeners() {
        techDataListener?.remove()
        techDataListener = null
        isOnlineListener?.remove()
        isOnlineListener = null
        activeJobListener?.remove()
        activeJobListener = null
        historyJobsListener1?.remove()
        historyJobsListener1 = null
        historyJobsListener2?.remove()
        historyJobsListener2 = null
        historyJobsListener3?.remove()
        historyJobsListener3 = null
        adminUserListener?.remove()
        adminUserListener = null
    }

    override fun onCleared() {
        super.onCleared()
        cleanupListeners()
    }
}
