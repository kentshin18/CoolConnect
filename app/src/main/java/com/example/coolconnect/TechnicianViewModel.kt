package com.example.coolconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TechnicianViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val repository: CoolConnectRepository,
    private val aiManager: CoolConnectAIManager
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

    private val _routePoints = MutableStateFlow<List<com.google.android.gms.maps.model.LatLng>>(emptyList())
    val routePoints: StateFlow<List<com.google.android.gms.maps.model.LatLng>> = _routePoints.asStateFlow()

    init {
        fetchTechData()
    }

    fun getAiFix(query: String) {
        viewModelScope.launch {
            _isAiAnalyzing.value = true
            _aiFixResult.value = null
            try {
                _aiFixResult.value = aiManager.getTechnicianFix(query)
            } finally {
                _isAiAnalyzing.value = false
            }
        }
    }

    fun fetchRoute(origin: com.google.android.gms.maps.model.LatLng, destination: com.google.android.gms.maps.model.LatLng, apiKey: String) {
        viewModelScope.launch {
            val points = NavigationUtils.getRoutePoints(origin, destination, apiKey)
            _routePoints.value = points
        }
    }

    fun clearRoute() {
        _routePoints.value = emptyList()
    }

    private fun fetchTechData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                _techName.value = doc.getString("name") ?: "Technician"
                _verificationStatus.value = doc.getString("verificationStatus") ?: "Not Verified"
                if (!_isUploading.value) {
                    _profilePicUrl.value = doc.getString("profilePicUrl")
                }
                _hasConsented.value = doc.getBoolean("hasConsented") ?: false
            } else {
                _hasConsented.value = false
            }
        }
    }

    fun uploadKycDoc(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isKycUploading.value = true
            try {
                repository.uploadKYCDocument(uri)
                android.widget.Toast.makeText(context, "Verification document uploaded! Pending review.", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Upload failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isKycUploading.value = false
            }
        }
    }

    fun uploadProfilePic(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isUploading.value = true
            try {
                val newUrl = repository.uploadProfilePicture(uri)
                _profilePicUrl.value = newUrl
                android.widget.Toast.makeText(context, "Profile picture updated!", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.util.Log.e("TechnicianViewModel", "Upload failed", e)
                android.widget.Toast.makeText(context, "Upload failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                _isUploading.value = false
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
            mapOf("latitude" to latitude, "longitude" to longitude, "lastUpdated" to com.google.firebase.Timestamp.now())
        )
    }
}
