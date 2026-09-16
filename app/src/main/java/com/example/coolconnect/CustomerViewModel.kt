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
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val repository: CoolConnectRepository,
    private val aiManager: CoolConnectAIManager
) : ViewModel() {

    private val _customerName = MutableStateFlow("Customer")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _subscriptionStatus = MutableStateFlow("Free")
    val subscriptionStatus: StateFlow<String> = _subscriptionStatus.asStateFlow()

    private val _profilePicUrl = MutableStateFlow<String?>(null)
    val profilePicUrl: StateFlow<String?> = _profilePicUrl.asStateFlow()

    private val _aiRecommendation = MutableStateFlow("Analyzing your history for personalized suggestions...")
    val aiRecommendation: StateFlow<String> = _aiRecommendation.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _hasConsented = MutableStateFlow(true)
    val hasConsented: StateFlow<Boolean> = _hasConsented.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart: StateFlow<List<CartItem>> = _cart.asStateFlow()

    private val _orders = MutableStateFlow<List<ShopOrder>>(emptyList())
    val orders: StateFlow<List<ShopOrder>> = _orders.asStateFlow()

    init {
        fetchUserData()
        observeOrders()
        fetchAIRecommendation()
    }

    private fun fetchAIRecommendation() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("chats_v2").document("admin_$uid").collection("services")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                val history = snapshot.documents.mapNotNull { it.getString("issue") }
                viewModelScope.launch {
                    _aiRecommendation.value = aiManager.getCustomerRecommendation(history)
                }
            }
    }

    private fun observeOrders() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            repository.getCustomerOrders(uid).collect {
                _orders.value = it
            }
        }
    }

    // Cart Logic
    fun addToCart(product: Product) {
        val currentCart = _cart.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == product.id }
        if (existingItem != null) {
            val index = currentCart.indexOf(existingItem)
            currentCart[index] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            currentCart.add(CartItem(product, 1))
        }
        _cart.value = currentCart
    }

    fun removeFromCart(productId: Int) {
        _cart.value = _cart.value.filter { it.product.id != productId }
    }

    fun updateQuantity(productId: Int, delta: Int) {
        val currentCart = _cart.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == productId }
        if (existingItem != null) {
            val index = currentCart.indexOf(existingItem)
            val newQuantity = existingItem.quantity + delta
            if (newQuantity > 0) {
                currentCart[index] = existingItem.copy(quantity = newQuantity)
                _cart.value = currentCart
            } else if (newQuantity == 0) {
                removeFromCart(productId)
            }
        }
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun checkout(address: String, paymentMethod: String, onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: throw Exception("User not logged in")
                val cartItems = _cart.value
                val subtotal = cartItems.sumOf { it.product.price.replace("₱", "").replace(",", "").toDouble() * it.quantity }
                val vat = subtotal * 0.12
                val total = subtotal + vat

                val orderId = "ORD-${System.currentTimeMillis()}"
                
                // Calculate Warranty Expiry for each item
                val itemsWithWarranty = cartItems.map { item ->
                    val cal = java.util.Calendar.getInstance()
                    // Default to 1 year if not specified, 2 years for hubs/locks
                    val years = if (item.product.name.contains("Hub") || item.product.name.contains("Lock")) 2 else 1
                    cal.add(java.util.Calendar.YEAR, years)
                    
                    mapOf(
                        "name" to item.product.name,
                        "quantity" to item.quantity,
                        "price" to item.product.price,
                        "warrantyExpiry" to com.google.firebase.Timestamp(cal.time)
                    )
                }

                val orderData = mapOf(
                    "orderId" to orderId,
                    "customerUid" to uid,
                    "customerName" to _customerName.value,
                    "items" to itemsWithWarranty,
                    "subtotal" to subtotal,
                    "vat" to vat,
                    "total" to total,
                    "shippingAddress" to address,
                    "paymentMethod" to paymentMethod,
                    "status" to "Processing",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )

                db.collection("orders").document(orderId).set(orderData).await()
                
                // Also create a service request for installation if products need it
                val serviceRequest = mapOf(
                    "technician" to "Hardware Team",
                    "status" to "pending",
                    "timestamp" to com.google.firebase.Timestamp.now(),
                    "issue" to "Hardware Order: $orderId (Installation Requested)"
                )
                db.collection("chats_v2").document("admin_$uid").collection("services").add(serviceRequest)

                clearCart()
                onResult(Result.success(orderId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    private fun fetchUserData() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                _customerName.value = doc.getString("name") ?: "Customer"
                _subscriptionStatus.value = doc.getString("subscription") ?: "Free"
                // Only update from Firestore if we aren't currently in the middle of an upload
                if (!_isUploading.value) {
                    _profilePicUrl.value = doc.getString("profilePicUrl")
                }
                _hasConsented.value = doc.getBoolean("hasConsented") ?: false
            } else {
                _hasConsented.value = false
            }
        }
    }

    fun upgradeSubscription(planName: String, onResult: (Result<Unit>) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                db.collection("users").document(uid).update("subscription", planName).await()
                _subscriptionStatus.value = planName
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
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
                android.util.Log.e("CustomerViewModel", "Upload failed", e)
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
            mapOf("latitude" to latitude, "longitude" to longitude)
        )
    }
}
