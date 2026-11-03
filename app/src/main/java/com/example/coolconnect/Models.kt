package com.example.coolconnect

import androidx.compose.ui.graphics.Color

data class Product(
    val id: Int,
    val name: String,
    val price: String,
    val category: String,
    val color: Color,
    val badge: String = "IN STOCK",
    val imageUrl: String? = null,
    val description: String = "High-quality Smart HVAC & Home Automation hardware designed for energy efficiency and seamless integration.",
    val warranty: String = "1 Year Official Warranty & Tech Support",
    val specs: String = "Wi-Fi Enabled • Low Power Draw • App Controlled",
    val stock: Int = 10,
    val docId: String = ""
)

data class ChatMessage(
    val id: String = "",
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now(),
    val isAdmin: Boolean = false,
    val imageUrl: String? = null,
    val senderProfilePic: String? = null,
    val status: String = "read"
)

data class CartItem(
    val product: Product,
    val quantity: Int
)

data class ShopOrder(
    val orderId: String = "",
    val customerUid: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val items: List<Map<String, Any>> = emptyList(),
    val itemsSummary: String = "",
    val subtotal: Double = 0.0,
    val vat: Double = 0.0,
    val total: Double = 0.0,
    val status: String = "Pending Dispatch",
    val shippingAddress: String = "",
    val paymentMethod: String = "",
    val assignedTechnician: String = "",
    val timestamp: com.google.firebase.Timestamp = com.google.firebase.Timestamp.now()
)

enum class MessageSender { USER, AI }

data class AiChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isPending: Boolean = false,
    val isError: Boolean = false,
    val userPrompt: String? = null
)



