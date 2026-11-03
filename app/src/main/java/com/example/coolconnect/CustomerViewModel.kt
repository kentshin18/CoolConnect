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
    private val aiRepository: AiAssistantRepository
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

    private var userDataListener: com.google.firebase.firestore.ListenerRegistration? = null

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null) {
                listenUserData(uid)
                observeOrdersForUser(uid)
                fetchAIRecommendationForUser(uid)
            }
        }
        auth.currentUser?.uid?.let { uid ->
            listenUserData(uid)
            observeOrdersForUser(uid)
            fetchAIRecommendationForUser(uid)
        }
    }

    private fun listenUserData(uid: String) {
        userDataListener?.remove()
        userDataListener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                _customerName.value = doc.getString("name") ?: "Customer"
                _subscriptionStatus.value = doc.getString("subscription") ?: "Free"
                if (!_isUploading.value) {
                    _profilePicUrl.value = doc.getString("profilePicUrl")
                }
                _hasConsented.value = doc.getBoolean("hasConsented") ?: false
            } else {
                _hasConsented.value = false
            }
        }
    }

    private val _recommendationEngine = RecommendationEngine()
    private val _structuredRecommendations = MutableStateFlow<List<Recommendation>>(emptyList())
    val structuredRecommendations: StateFlow<List<Recommendation>> = _structuredRecommendations.asStateFlow()

    private fun fetchAIRecommendationForUser(uid: String) {
        viewModelScope.launch {
            val result = aiRepository.getCustomerRecommendation(uid)
            result.onSuccess { recommendation ->
                _aiRecommendation.value = recommendation
            }.onFailure {
                _aiRecommendation.value = "Maintain your home's health with our smart AC cleaning & filter replacement packages."
            }

            // Load rule-based structured recommendations
            val engineRecs = _recommendationEngine.getRecommendations(
                customerId = uid,
                onboarding = OnboardingAnswers(propertyType = "Home", numUnits = 1),
                equipmentList = emptyList(),
                bookingHistory = emptyList(),
                shopOrders = _orders.value,
                productsCatalog = emptyList()
            )
            _structuredRecommendations.value = engineRecs
        }
    }

    private fun observeOrdersForUser(uid: String) {
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
                if (cartItems.isEmpty()) {
                    throw Exception("Cart is empty")
                }

                // 1. Update Inventory Stocks in Firestore
                for (item in cartItems) {
                    val productRef = if (item.product.docId.isNotBlank()) {
                        db.collection("products").document(item.product.docId)
                    } else {
                        val querySnap = db.collection("products").whereEqualTo("name", item.product.name).get().await()
                        querySnap.documents.firstOrNull()?.reference
                    }

                    if (productRef != null) {
                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(productRef)
                            val currentStock = (snapshot.get("stock") as? Number)?.toInt() ?: 10
                            val newStock = maxOf(0, currentStock - item.quantity)
                            val newBadge = if (newStock <= 0) "OUT OF STOCK" else if (newStock <= 5) "LIMITED STOCK" else (snapshot.getString("badge") ?: "IN STOCK")
                            transaction.update(productRef, mapOf(
                                "stock" to newStock,
                                "badge" to newBadge
                            ))
                        }.await()
                    }
                }

                // Refresh local database cache
                try {
                    repository.refreshProducts()
                } catch (e: Exception) {
                    android.util.Log.e("CustomerViewModel", "Failed to refresh products local DB", e)
                }

                // 2. Calculate totals and details
                val subtotal = cartItems.sumOf { it.product.price.replace("₱", "").replace(",", "").toDouble() * it.quantity }
                val vat = subtotal * 0.12
                val total = subtotal + vat

                val orderId = "ORD-${System.currentTimeMillis()}"
                val itemsSummary = cartItems.joinToString(", ") { "${it.product.name} x${it.quantity}" }

                // Fetch customer phone
                val userDoc = db.collection("users").document(uid).get().await()
                val customerPhone = userDoc.getString("phone") ?: ""

                // Calculate Warranty Expiry for each item
                val itemsWithWarranty = cartItems.map { item ->
                    val cal = java.util.Calendar.getInstance()
                    val years = if (item.product.name.contains("Hub") || item.product.name.contains("Lock")) 2 else 1
                    cal.add(java.util.Calendar.YEAR, years)

                    mapOf(
                        "name" to item.product.name,
                        "quantity" to item.quantity,
                        "price" to item.product.price,
                        "category" to item.product.category,
                        "warrantyExpiry" to com.google.firebase.Timestamp(cal.time)
                    )
                }

                // 3. Write Order Document to 'orders' collection
                val orderData = mapOf(
                    "orderId" to orderId,
                    "customerUid" to uid,
                    "customerName" to _customerName.value,
                    "customerPhone" to customerPhone,
                    "items" to itemsWithWarranty,
                    "itemsSummary" to itemsSummary,
                    "subtotal" to subtotal,
                    "vat" to vat,
                    "total" to total,
                    "shippingAddress" to address,
                    "paymentMethod" to paymentMethod,
                    "status" to "Pending Dispatch",
                    "assignedTechnician" to "Unassigned",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                db.collection("orders").document(orderId).set(orderData).await()

                // 4. Create Service Request for Delivery & Installation in top-level 'service_requests'
                val serviceReqId = db.collection("service_requests").document().id
                val nowTimestamp = com.google.firebase.Timestamp.now()
                val serviceRequest = mapOf(
                    "requestId" to serviceReqId,
                    "type" to "order_installation",
                    "orderId" to orderId,
                    "customerUid" to uid,
                    "customerName" to _customerName.value,
                    "customerPhone" to customerPhone,
                    "customerAddress" to address,
                    "technicianUid" to null,
                    "technicianName" to "Unassigned",
                    "status" to "Pending Dispatch",
                    "issue" to "Hardware Order: $orderId ($itemsSummary)",
                    "itemsSummary" to itemsSummary,
                    "totalAmount" to total,
                    "createdAt" to nowTimestamp,
                    "updatedAt" to nowTimestamp
                )
                db.collection("service_requests").document(serviceReqId).set(serviceRequest).await()
                db.collection("service_history").document(serviceReqId).set(serviceRequest, com.google.firebase.firestore.SetOptions.merge()).await()

                // 5. Send Automated Chat Notification
                val autoMsg = hashMapOf(
                    "senderId" to uid,
                    "senderName" to _customerName.value,
                    "text" to "📦 Order Placed: $orderId\nItems: $itemsSummary\nTotal: ₱${String.format(java.util.Locale.getDefault(), "%,.2f", total)}\nDelivery & Install Address: $address",
                    "timestamp" to nowTimestamp
                )
                db.collection("chats_v2").document("admin_$uid").collection("messages").add(autoMsg).await()

                clearCart()
                onResult(Result.success(orderId))
            } catch (e: Exception) {
                onResult(Result.failure(e))
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
