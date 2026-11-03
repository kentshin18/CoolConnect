package com.example.coolconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: CoolConnectRepository
) : ViewModel() {

    private val _technicians = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val technicians: StateFlow<List<Map<String, Any>>> = _technicians.asStateFlow()

    private val _products = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val products: StateFlow<List<Map<String, Any>>> = _products.asStateFlow()

    val completedJobs: StateFlow<List<Map<String, Any>>> = repository.completedJobs.asStateFlow(emptyList())

    // Grouping pending requests by area
    private val _neighborhoodClusters = MutableStateFlow<Map<String, List<Map<String, Any>>>>(emptyMap())
    val neighborhoodClusters: StateFlow<Map<String, List<Map<String, Any>>>> = _neighborhoodClusters.asStateFlow()

    private val _pendingServices = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingServices: StateFlow<List<Map<String, Any>>> = _pendingServices.asStateFlow()

    private val _orders = MutableStateFlow<List<ShopOrder>>(emptyList())
    val orders: StateFlow<List<ShopOrder>> = _orders.asStateFlow()

    private val _adminProfilePicUrl = MutableStateFlow<String?>(null)
    val adminProfilePicUrl: StateFlow<String?> = _adminProfilePicUrl.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private var adminDataListenerRegistration: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        refreshData()
        com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            if (uid != null) {
                listenAdminData(uid)
            }
        }
        observePendingServices()
        observeNeighborhoodClusters()
        observeTechniciansRealtime()
        observeProductsRealtime()
        observeOrdersRealtime()
    }

    private fun listenAdminData(uid: String) {
        adminDataListenerRegistration?.remove()
        adminDataListenerRegistration = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .addSnapshotListener { doc, _ ->
                if (!_isUploading.value) {
                    _adminProfilePicUrl.value = doc?.getString("profilePicUrl")
                }
            }
    }

    private fun observeOrdersRealtime() {
        viewModelScope.launch {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("orders")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("AdminViewModel", "Error observing orders", error)
                        return@addSnapshotListener
                    }
                    @Suppress("UNCHECKED_CAST")
                    val list = snapshot?.documents?.mapNotNull { doc ->
                        val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                        ShopOrder(
                            orderId = doc.getString("orderId") ?: doc.id,
                            customerUid = doc.getString("customerUid") ?: "",
                            customerName = doc.getString("customerName") ?: "Customer",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            items = rawItems,
                            itemsSummary = doc.getString("itemsSummary") ?: "",
                            subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                            vat = (doc.get("vat") as? Number)?.toDouble() ?: 0.0,
                            total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                            status = doc.getString("status") ?: "Pending Dispatch",
                            shippingAddress = doc.getString("shippingAddress") ?: "",
                            paymentMethod = doc.getString("paymentMethod") ?: "",
                            assignedTechnician = doc.getString("assignedTechnician") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now()
                        )
                    } ?: emptyList()
                    _orders.value = list
                }
        }
    }

    fun assignTechnicianToOrder(
        orderId: String,
        customerUid: String,
        technicianName: String,
        technicianUid: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                
                val orderUpdates = mutableMapOf<String, Any>(
                    "assignedTechnician" to technicianName,
                    "status" to "Dispatched to $technicianName"
                )
                if (!technicianUid.isNullOrBlank()) {
                    orderUpdates["technicianUid"] = technicianUid
                }

                // Update Order document
                db.collection("orders").document(orderId).update(orderUpdates).await()

                // Update underlying Service Requests for the hardware order
                val reqsSnap = db.collection("service_requests")
                    .whereEqualTo("orderId", orderId)
                    .get().await()

                for (doc in reqsSnap.documents) {
                    val reqUpdates = mutableMapOf<String, Any>(
                        "technicianName" to technicianName,
                        "technician" to technicianName,
                        "assignedTechnician" to technicianName,
                        "status" to "Assigned",
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )
                    if (!technicianUid.isNullOrBlank()) {
                        reqUpdates["technicianUid"] = technicianUid
                    }
                    doc.reference.update(reqUpdates).await()
                }

                // Append automated chat notification
                if (customerUid.isNotBlank()) {
                    val autoMsg = hashMapOf(
                        "senderId" to "admin",
                        "senderName" to "Admin Dispatch",
                        "text" to "🚚 Field Technician $technicianName has been assigned to deliver & install your hardware order ($orderId).",
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "isAdmin" to true
                    )
                    db.collection("chats_v2").document("admin_$customerUid").collection("messages").add(autoMsg).await()
                }
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Failed to assign technician to order", e)
                onResult(false)
            }
        }
    }

    fun assignTechnicianToRequest(
        requestId: String,
        customerUid: String,
        technicianName: String,
        technicianUid: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val updates = mutableMapOf<String, Any>(
                    "status" to "Assigned",
                    "technicianName" to technicianName,
                    "technician" to technicianName,
                    "assignedTechnician" to technicianName,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
                if (!technicianUid.isNullOrBlank()) {
                    updates["technicianUid"] = technicianUid
                }

                db.collection("service_requests").document(requestId).update(updates).await()
                db.collection("service_history").document(requestId).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()

                if (customerUid.isNotBlank()) {
                    val autoMsg = hashMapOf(
                        "senderId" to "admin",
                        "senderName" to "Admin Dispatch",
                        "text" to "Field Technician $technicianName has been assigned to your service request ($requestId).",
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "isAdmin" to true
                    )
                    db.collection("chats_v2").document("admin_$customerUid").collection("messages").add(autoMsg).await()
                }
                onResult(true)
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Failed to assign technician to request $requestId", e)
                onResult(false)
            }
        }
    }

    fun approvePartsInstallation(
        requestId: String,
        technicianName: String,
        technicianUid: String? = null,
        onResult: (Boolean) -> Unit = {}
    ) {
        assignTechnicianToRequest(requestId, "", technicianName, technicianUid, onResult)
    }

    private fun observeProductsRealtime() {
        viewModelScope.launch {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("products")
                .addSnapshotListener { snapshot, _ ->
                    val list = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["id"] = doc.id
                        data
                    } ?: emptyList()
                    _products.value = list
                }
        }
    }

    private fun observeTechniciansRealtime() {
        viewModelScope.launch {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("role", "technician")
                .addSnapshotListener { snapshot, _ ->
                    val techs = snapshot?.documents?.map { doc ->
                        val data = doc.data?.toMutableMap() ?: mutableMapOf()
                        data["uid"] = doc.id
                        data
                    } ?: emptyList()
                    _technicians.value = techs
                }
        }
    }

    private fun observeNeighborhoodClusters() {
        viewModelScope.launch {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("service_requests")
                .whereIn("status", listOf("pending", "Pending", "Pending Dispatch", "Assigned", "Accepted", "In Progress", "Inquiry Sent"))
                .addSnapshotListener { snapshot, _ ->
                    val grouped = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data?.toMutableMap() ?: return@mapNotNull null
                        data["requestId"] = doc.id
                        data["uid"] = doc.id
                        data
                    }?.groupBy { it["regionCity"]?.toString() ?: it["customerAddress"]?.toString()?.split(",")?.lastOrNull()?.trim() ?: "Unknown Area" } ?: emptyMap()
                    
                    _neighborhoodClusters.value = grouped
                }
        }
    }

    private fun observePendingServices() {
        viewModelScope.launch {
            repository.getAllPendingServices().collect {
                _pendingServices.value = it
            }
        }
    }



    fun uploadProfilePic(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            when (val validation = ImageUtils.validateImageUri(context, uri)) {
                is ImageUtils.ValidationResult.Error -> {
                    android.widget.Toast.makeText(context, validation.message, android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                is ImageUtils.ValidationResult.Success -> { /* proceed */ }
            }
            _isUploading.value = true
            try {
                val newUrl = repository.uploadProfilePicture(uri)
                _adminProfilePicUrl.value = newUrl
                android.widget.Toast.makeText(context, "Profile picture updated!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("AdminViewModel", "Upload failed", e)
                android.widget.Toast.makeText(context, "Upload failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            repository.refreshTechnicians()
            repository.refreshProducts()
            repository.refreshCompletedJobs()
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asStateFlow(initialValue: T): StateFlow<T> {
        val flow = MutableStateFlow(initialValue)
        viewModelScope.launch {
            this@asStateFlow.collect { flow.value = it }
        }
        return flow.asStateFlow()
    }
}
