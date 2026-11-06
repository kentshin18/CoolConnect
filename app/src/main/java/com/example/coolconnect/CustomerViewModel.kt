package com.example.coolconnect

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class CustomerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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
    private var activeUid: String? = null
    private var ordersJob: kotlinx.coroutines.Job? = null
    private var aiRecommendationJob: kotlinx.coroutines.Job? = null

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val pastSalesMap = MutableStateFlow<Map<String, Int>>(emptyMap())
    private var lastProductsSnapshot: com.google.firebase.firestore.QuerySnapshot? = null

    init {
        observeAllOrdersForSalesCount()
        observeProductsRealtime()
        auth.addAuthStateListener { firebaseAuth ->
            val uid = firebaseAuth.currentUser?.uid
            if (uid != null && uid != activeUid) {
                setupUserSession(uid)
            } else if (uid == null) {
                activeUid = null
                userDataListener?.remove()
                ordersJob?.cancel()
                aiRecommendationJob?.cancel()
            }
        }
    }

    private fun setupUserSession(uid: String) {
        activeUid = uid
        _cart.value = loadCartFromPrefs(uid)
        listenUserData(uid)
        observeOrdersForUser(uid)
        fetchAIRecommendationForUser(uid)
    }

    private fun observeAllOrdersForSalesCount() {
        db.collection("orders").addSnapshotListener { oSnap, _ ->
            val map = mutableMapOf<String, Int>()
            oSnap?.documents?.forEach { oDoc ->
                val itemsList = oDoc.get("items") as? List<*>
                itemsList?.forEach { itemObj ->
                    if (itemObj is Map<*, *>) {
                        val pName = itemObj["name"]?.toString() ?: ""
                        val qty = (itemObj["quantity"] as? Number)?.toInt() ?: (itemObj["qty"] as? Number)?.toInt() ?: 1
                        if (pName.isNotBlank()) {
                            val key = pName.trim().lowercase(java.util.Locale.getDefault())
                            map[key] = (map[key] ?: 0) + qty
                        }
                    }
                }
            }
            pastSalesMap.value = map
            refreshProductsFromSnapshot(lastProductsSnapshot)
        }
    }

    private fun observeProductsRealtime() {
        db.collection("products").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && !snapshot.isEmpty) {
                lastProductsSnapshot = snapshot
                refreshProductsFromSnapshot(snapshot)
            }
        }
    }

    private fun refreshProductsFromSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot?) {
        if (snapshot == null || snapshot.isEmpty) return
        val sales = pastSalesMap.value
        val list = snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            val rawPrice = (doc.get("price") as? Number)?.toDouble() ?: 0.0
            val priceStr = "₱${String.format(java.util.Locale.getDefault(), "%,.0f", rawPrice)}"
            val category = doc.getString("category") ?: "Compressors"
            val badge = doc.getString("badge") ?: "IN STOCK"
            val description = doc.getString("description") ?: "HVAC Hardware & Replacement Part"
            val specs = doc.getString("specs") ?: "Heavy-Duty HVAC Grade"
            val warranty = doc.getString("warranty") ?: "1 Year Warranty"
            val imageUrl = doc.getString("imageUrl") ?: ""
            val stock = (doc.get("stock") as? Number)?.toInt() ?: 10
            val rating = (doc.get("rating") as? Number)?.toDouble() ?: 5.0
            val reviewCount = (doc.get("reviewCount") as? Number)?.toInt() ?: 0
            val docSoldCount = (doc.get("soldCount") as? Number)?.toInt() ?: 0
            val historicalSoldCount = sales[name.trim().lowercase(java.util.Locale.getDefault())] ?: 0
            val soldCount = maxOf(docSoldCount, historicalSoldCount)

            val color = when (category) {
                "Compressors" -> androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                "Circuit Boards" -> androidx.compose.ui.graphics.Color(0xFFF1F8E9)
                "Motors & Fans" -> androidx.compose.ui.graphics.Color(0xFFFFFDE7)
                "Refrigerants" -> androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                else -> androidx.compose.ui.graphics.Color(0xFFFFF3E0)
            }

            Product(
                id = doc.id.hashCode(),
                name = name,
                price = priceStr,
                category = category,
                color = color,
                badge = badge,
                imageUrl = imageUrl,
                description = description,
                specs = specs,
                warranty = warranty,
                stock = stock,
                docId = doc.id,
                rating = rating,
                reviewCount = reviewCount,
                soldCount = soldCount
            )
        }
        _products.value = list
        auth.currentUser?.uid?.let { uid ->
            generateRecommendationsForUser(uid)
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
        aiRecommendationJob?.cancel()
        aiRecommendationJob = viewModelScope.launch {
            val result = aiRepository.getCustomerRecommendation(uid)
            result.onSuccess { recommendation ->
                _aiRecommendation.value = recommendation
            }.onFailure {
                _aiRecommendation.value = "Maintain your home's health with our smart AC cleaning & filter replacement packages."
            }
            generateRecommendationsForUser(uid)
        }
    }

    fun refreshAIRecommendation() {
        val uid = auth.currentUser?.uid ?: return
        _aiRecommendation.value = "Analyzing your service history and home AC system with CoolConnect AI..."
        fetchAIRecommendationForUser(uid)
    }

    private fun generateRecommendationsForUser(uid: String) {
        viewModelScope.launch {
            val engineRecs = _recommendationEngine.getRecommendations(
                customerId = uid,
                onboarding = OnboardingAnswers(propertyType = "Home", numUnits = 1),
                equipmentList = emptyList(),
                bookingHistory = emptyList(),
                shopOrders = _orders.value,
                productsCatalog = _products.value
            )
            _structuredRecommendations.value = engineRecs
        }
    }

    private fun observeOrdersForUser(uid: String) {
        ordersJob?.cancel()
        ordersJob = viewModelScope.launch {
            repository.getCustomerOrders(uid).collect {
                _orders.value = it
            }
        }
    }

    // Cart Persistence Logic
    private fun saveCartToPrefs(uid: String, cartList: List<CartItem>) {
        val targetUid = if (uid.isNotBlank()) uid else activeUid ?: auth.currentUser?.uid ?: ""
        if (targetUid.isBlank()) return
        try {
            val prefs = context.getSharedPreferences("coolconnect_cart_prefs", Context.MODE_PRIVATE)
            val jsonArray = org.json.JSONArray()
            for (item in cartList) {
                val obj = org.json.JSONObject().apply {
                    put("id", item.product.id)
                    put("name", item.product.name)
                    put("price", item.product.price)
                    put("category", item.product.category)
                    put("badge", item.product.badge)
                    put("imageUrl", item.product.imageUrl ?: "")
                    put("description", item.product.description)
                    put("specs", item.product.specs)
                    put("warranty", item.product.warranty)
                    put("stock", item.product.stock)
                    put("docId", item.product.docId)
                    put("rating", item.product.rating)
                    put("reviewCount", item.product.reviewCount)
                    put("soldCount", item.product.soldCount)
                    put("quantity", item.quantity)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("cart_$targetUid", jsonArray.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("CustomerViewModel", "Error saving cart to prefs", e)
        }
    }

    private fun loadCartFromPrefs(uid: String): List<CartItem> {
        val targetUid = if (uid.isNotBlank()) uid else activeUid ?: auth.currentUser?.uid ?: ""
        if (targetUid.isBlank()) return emptyList()
        val list = mutableListOf<CartItem>()
        try {
            val prefs = context.getSharedPreferences("coolconnect_cart_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("cart_$targetUid", null) ?: return emptyList()
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.optString("name", "")
                val category = obj.optString("category", "Compressors")
                val color = when (category) {
                    "Compressors" -> androidx.compose.ui.graphics.Color(0xFFE3F2FD)
                    "Circuit Boards" -> androidx.compose.ui.graphics.Color(0xFFF1F8E9)
                    "Motors & Fans" -> androidx.compose.ui.graphics.Color(0xFFFFFDE7)
                    "Refrigerants" -> androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                    else -> androidx.compose.ui.graphics.Color(0xFFFFF3E0)
                }
                val product = Product(
                    id = obj.optInt("id", name.hashCode()),
                    name = name,
                    price = obj.optString("price", "₱0"),
                    category = category,
                    color = color,
                    badge = obj.optString("badge", "IN STOCK"),
                    imageUrl = obj.optString("imageUrl", ""),
                    description = obj.optString("description", ""),
                    specs = obj.optString("specs", ""),
                    warranty = obj.optString("warranty", ""),
                    stock = obj.optInt("stock", 10),
                    docId = obj.optString("docId", ""),
                    rating = obj.optDouble("rating", 5.0),
                    reviewCount = obj.optInt("reviewCount", 0),
                    soldCount = obj.optInt("soldCount", 0)
                )
                val quantity = obj.optInt("quantity", 1)
                list.add(CartItem(product, quantity))
            }
        } catch (e: Exception) {
            android.util.Log.e("CustomerViewModel", "Error loading cart from prefs", e)
        }
        return list
    }

    // Cart Logic with Stock Limits & Persistence
    fun addToCart(product: Product): Boolean {
        val currentCart = _cart.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == product.id }
        val maxStock = if (product.stock > 0) product.stock else 10
        if (existingItem != null) {
            val index = currentCart.indexOf(existingItem)
            if (existingItem.quantity >= maxStock) {
                return false // Stock limit reached
            }
            currentCart[index] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            if (maxStock <= 0) return false
            currentCart.add(CartItem(product, 1))
        }
        _cart.value = currentCart
        saveCartToPrefs(activeUid ?: auth.currentUser?.uid ?: "", currentCart)
        return true
    }

    fun removeFromCart(productId: Int) {
        val updated = _cart.value.filter { it.product.id != productId }
        _cart.value = updated
        saveCartToPrefs(activeUid ?: auth.currentUser?.uid ?: "", updated)
    }

    fun updateQuantity(productId: Int, delta: Int): Boolean {
        val currentCart = _cart.value.toMutableList()
        val existingItem = currentCart.find { it.product.id == productId }
        if (existingItem != null) {
            val index = currentCart.indexOf(existingItem)
            val maxStock = if (existingItem.product.stock > 0) existingItem.product.stock else 10
            val newQuantity = existingItem.quantity + delta
            if (delta > 0 && newQuantity > maxStock) {
                return false // Exceeds available stock
            }
            if (newQuantity > 0) {
                currentCart[index] = existingItem.copy(quantity = newQuantity)
                _cart.value = currentCart
                saveCartToPrefs(activeUid ?: auth.currentUser?.uid ?: "", currentCart)
                return true
            } else if (newQuantity == 0) {
                removeFromCart(productId)
                return true
            }
        }
        return false
    }

    fun clearCart() {
        _cart.value = emptyList()
        saveCartToPrefs(activeUid ?: auth.currentUser?.uid ?: "", emptyList())
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
                            val currentSoldCount = (snapshot.get("soldCount") as? Number)?.toInt() ?: 0
                            val newStock = maxOf(0, currentStock - item.quantity)
                            val newSoldCount = currentSoldCount + item.quantity
                            val newBadge = if (newStock <= 0) "OUT OF STOCK" else if (newStock <= 5) "LIMITED STOCK" else (snapshot.getString("badge") ?: "IN STOCK")
                            transaction.update(productRef, mapOf(
                                "stock" to newStock,
                                "soldCount" to newSoldCount,
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
