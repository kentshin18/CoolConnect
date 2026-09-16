package com.example.coolconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: CoolConnectRepository
) : ViewModel() {

    val technicians: StateFlow<List<Map<String, Any>>> = repository.technicians.asStateFlow(emptyList())
    val products: StateFlow<List<Map<String, Any>>> = repository.products.asStateFlow(emptyList())
    val completedJobs: StateFlow<List<Map<String, Any>>> = repository.completedJobs.asStateFlow(emptyList())

    // Grouping pending requests by area
    private val _neighborhoodClusters = MutableStateFlow<Map<String, List<Map<String, Any>>>>(emptyMap())
    val neighborhoodClusters: StateFlow<Map<String, List<Map<String, Any>>>> = _neighborhoodClusters.asStateFlow()

    private val _pendingServices = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val pendingServices: StateFlow<List<Map<String, Any>>> = _pendingServices.asStateFlow()

    private val _adminProfilePicUrl = MutableStateFlow<String?>(null)
    val adminProfilePicUrl: StateFlow<String?> = _adminProfilePicUrl.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    init {
        refreshData()
        fetchAdminData()
        observePendingServices()
        observeNeighborhoodClusters()
    }

    private fun observeNeighborhoodClusters() {
        // We'll use the chats_v2 collection to find active requests and their locations
        viewModelScope.launch {
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("chats_v2")
                .whereIn("status", listOf("pending", "Accepted", "In Progress", "🚨 URGENT SOS DISPATCH", "Inquiry Sent"))
                .addSnapshotListener { snapshot, _ ->
                    val grouped = snapshot?.documents?.mapNotNull { doc ->
                        val data = doc.data?.toMutableMap() ?: return@mapNotNull null
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

    private fun fetchAdminData() {
        // Since it's a singleton admin in the user's logic, we just fetch the current user's profilePicUrl
        // Assuming admin account is also in 'users' collection
        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid)
                .addSnapshotListener { doc, _ ->
                    if (!_isUploading.value) {
                        _adminProfilePicUrl.value = doc?.getString("profilePicUrl")
                    }
                }
        }
    }

    fun uploadProfilePic(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
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
