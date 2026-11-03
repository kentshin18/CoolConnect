package com.example.coolconnect

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val ENDPOINT = "https://field-ai.coldovertake.workers.dev"

@Singleton
class AiAssistantRepository @Inject constructor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    @param:ApplicationContext private val context: Context? = null,
) {
    // Ultra-fast client for worker endpoints
    private val fastWorkerClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    // Resilient client for live AI completions
    private val liveAiClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(4, TimeUnit.SECONDS)
        .build()

    var customApiKey: String = ""

    private fun getEffectiveApiKey(): String {
        if (customApiKey.isNotBlank()) return customApiKey
        val resKey = context?.getString(R.string.ai_secret_key)
        if (!resKey.isNullOrBlank() && resKey != "YOUR_AI_SECRET_KEY_HERE") {
            return resKey
        }
        return ""
    }

    private suspend fun fetchCustomerHistoryContext(customerId: String, customerName: String): String = withContext(Dispatchers.IO) {
        if (customerId.isBlank() && customerName.isBlank()) return@withContext ""

        try {
            val historyBuilder = StringBuilder()
            val targetChatId = if (customerId.startsWith("admin_")) customerId else "admin_$customerId"
            val rawId = customerId.replace("admin_", "").replace("tech_", "")

            // 1. Fetch main chat document
            val chatDocSnap = try {
                db.collection("chats_v2").document(targetChatId).get().await()
            } catch (_: Exception) {
                null
            }

            val docCustomerName = chatDocSnap?.getString("customerName") ?: chatDocSnap?.getString("chatName") ?: customerName
            val currentStatus = chatDocSnap?.getString("status") ?: "Active"
            val lastRequest = chatDocSnap?.getString("lastRequest") ?: ""

            if (docCustomerName.isNotBlank() || rawId.isNotBlank()) {
                historyBuilder.append("Customer Name: ${docCustomerName.ifBlank { rawId }}\n")
                historyBuilder.append("Current Booking Status: $currentStatus\n")
                if (lastRequest.isNotBlank()) {
                    historyBuilder.append("Current Request: $lastRequest\n")
                }
            }

            // 2. Fetch from top-level 'service_history' collection first
            val serviceDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()

            if (rawId.isNotBlank()) {
                try {
                    val shSnap = db.collection("service_history")
                        .whereEqualTo("customerUid", rawId)
                        .get()
                        .await()
                    serviceDocs.addAll(shSnap.documents)
                } catch (_: Exception) {}
            }

            if (serviceDocs.isEmpty() && docCustomerName.isNotBlank()) {
                try {
                    val shSnapName = db.collection("service_history")
                        .whereEqualTo("customerName", docCustomerName)
                        .get()
                        .await()
                    serviceDocs.addAll(shSnapName.documents)
                } catch (_: Exception) {}
            }

            // Fallback: Fetch services sub-collection under chats_v2/targetChatId/services
            if (serviceDocs.isEmpty()) {
                try {
                    val servicesSnap = db.collection("chats_v2")
                        .document(targetChatId)
                        .collection("services")
                        .get()
                        .await()
                    serviceDocs.addAll(servicesSnap.documents)
                } catch (_: Exception) {}
            }

            // Also check rawId if targetChatId didn't return services
            if (serviceDocs.isEmpty() && targetChatId != rawId && rawId.isNotBlank()) {
                try {
                    val rawServicesSnap = db.collection("chats_v2")
                        .document(rawId)
                        .collection("services")
                        .get()
                        .await()
                    serviceDocs.addAll(rawServicesSnap.documents)
                } catch (_: Exception) {}
            }

            // Fallback: Query collection group if still empty
            if (serviceDocs.isEmpty() && rawId.isNotBlank()) {
                try {
                    val cgSnap = db.collectionGroup("services")
                        .whereEqualTo("customerUid", rawId)
                        .get()
                        .await()
                    serviceDocs.addAll(cgSnap.documents)
                } catch (_: Exception) {}
            }

            if (serviceDocs.isNotEmpty()) {
                historyBuilder.append("Past Service Entries (${serviceDocs.size} total):\n")

                val sortedDocs = serviceDocs.sortedByDescending { doc ->
                    doc.getTimestamp("completedAt")?.toDate()?.time
                        ?: doc.getTimestamp("timestamp")?.toDate()?.time
                        ?: doc.getTimestamp("scheduledTimestamp")?.toDate()?.time
                        ?: 0L
                }

                for ((index, doc) in sortedDocs.withIndex()) {
                    val status = doc.getString("status") ?: "Unknown"
                    val issue = doc.getString("issue") ?: doc.getString("lastRequest") ?: doc.getString("notes") ?: "HVAC Service"
                    val notes = doc.getString("completionNotes") ?: doc.getString("notes") ?: "N/A"
                    val partsUsed = doc.getString("partsUsed") ?: "None"
                    val totalAmount = (doc.get("totalAmount") as? Number)?.toDouble()
                    val tech = doc.getString("lastTechnician") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: "N/A"
                    val dateObj = doc.getTimestamp("completedAt")?.toDate()
                        ?: doc.getTimestamp("timestamp")?.toDate()
                        ?: doc.getTimestamp("scheduledTimestamp")?.toDate()
                    val dateStr = if (dateObj != null) {
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(dateObj)
                    } else "N/A"

                    historyBuilder.append("  ${index + 1}. [$dateStr] Status: $status | Service: $issue | Work Done/Notes: $notes | Parts Used: $partsUsed | Tech: $tech")
                    if (totalAmount != null && totalAmount > 0) {
                        historyBuilder.append(" | Total: ₱${String.format(java.util.Locale.getDefault(), "%.2f", totalAmount)}")
                    }
                    historyBuilder.append("\n")
                }
            } else {
                val mainNotes = chatDocSnap?.getString("completionNotes")
                val mainParts = chatDocSnap?.getString("partsUsed")
                val mainTech = chatDocSnap?.getString("lastTechnician")
                val mainTotal = (chatDocSnap?.get("totalAmount") as? Number)?.toDouble()

                if (!mainNotes.isNullOrBlank() || !mainParts.isNullOrBlank()) {
                    historyBuilder.append("Last Completed Service:\n")
                    if (!mainNotes.isNullOrBlank()) historyBuilder.append("  • Notes: $mainNotes\n")
                    if (!mainParts.isNullOrBlank()) historyBuilder.append("  • Parts Used: $mainParts\n")
                    if (!mainTech.isNullOrBlank()) historyBuilder.append("  • Technician: $mainTech\n")
                    if (mainTotal != null && mainTotal > 0) historyBuilder.append("  • Amount: ₱${String.format(java.util.Locale.getDefault(), "%.2f", mainTotal)}\n")
                } else {
                    historyBuilder.append("No prior completed service history found in database for this customer.\n")
                }
            }

            // 3. Fetch orders / parts purchased by customer from top-level 'orders' collection
            val orderDocs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            if (rawId.isNotBlank()) {
                try {
                    val ordersSnap = db.collection("orders")
                        .whereEqualTo("customerUid", rawId)
                        .get()
                        .await()
                    orderDocs.addAll(ordersSnap.documents)
                } catch (_: Exception) {}
            }

            if (orderDocs.isNotEmpty()) {
                historyBuilder.append("\nParts & Products Purchased (${orderDocs.size} order(s)):\n")
                val sortedOrders = orderDocs.sortedByDescending { it.getTimestamp("timestamp")?.toDate()?.time ?: 0L }
                for ((index, orderDoc) in sortedOrders.withIndex()) {
                    val orderId = orderDoc.getString("orderId") ?: orderDoc.id
                    val status = orderDoc.getString("status") ?: "Completed"
                    val itemsSummary = orderDoc.getString("itemsSummary") ?: "Hardware order"
                    val total = (orderDoc.get("total") as? Number)?.toDouble() ?: 0.0
                    val dateObj = orderDoc.getTimestamp("timestamp")?.toDate()
                    val dateStr = if (dateObj != null) {
                        java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault()).format(dateObj)
                    } else "N/A"

                    historyBuilder.append("  ${index + 1}. [$dateStr] Order ID: $orderId | Status: $status | Items: $itemsSummary | Total: ₱${String.format(java.util.Locale.getDefault(), "%.2f", total)}\n")
                }
            }

            historyBuilder.toString().trim()
        } catch (e: Exception) {
            android.util.Log.w("AiAssistantRepository", "Error fetching customer history for AI: ${e.localizedMessage}")
            ""
        }
    }

    private suspend fun fetchAllCustomersDirectoryContext(): String = withContext(Dispatchers.IO) {
        try {
            val directoryBuilder = StringBuilder()
            val customerEntries = mutableListOf<String>()
            val seenUids = mutableSetOf<String>()
            val currentTechUid = auth.currentUser?.uid ?: ""

            // 1. Fetch active/recent customer chats from 'chats_v2'
            try {
                val chatsSnap = db.collection("chats_v2").get().await()
                chatsSnap.documents.forEach { doc ->
                    val cUid = doc.getString("customerUid")
                        ?: doc.id.replace("admin_", "").replace("tech_", "")
                    val cName = doc.getString("customerName") ?: doc.getString("chatName") ?: ""
                    val cPhone = doc.getString("customerPhone") ?: "N/A"
                    val cAddress = doc.getString("customerAddress") ?: doc.getString("address") ?: "N/A"
                    val status = doc.getString("status") ?: "Active"
                    val issue = doc.getString("lastRequest") ?: doc.getString("issue") ?: ""
                    val tech = doc.getString("assignedTechnician") ?: doc.getString("lastTechnician") ?: "Unassigned"

                    if (cUid.isNotBlank() && cUid != currentTechUid && !seenUids.contains(cUid)) {
                        seenUids.add(cUid)
                        val nameStr = if (cName.isNotBlank()) cName else "Customer"
                        val entry = StringBuilder("• Customer Name: $nameStr | Customer UID: $cUid | Phone: $cPhone | Address: $cAddress | Booking Status: $status")
                        if (issue.isNotBlank()) entry.append(" | Request: $issue")
                        if (tech.isNotBlank()) entry.append(" | Tech: $tech")
                        customerEntries.add(entry.toString())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AiAssistantRepository", "Error fetching chats_v2 for directory: ${e.localizedMessage}")
            }

            // 2. Fetch registered customer users from 'users' collection
            try {
                val usersSnap = db.collection("users").get().await()
                usersSnap.documents.forEach { doc ->
                    val role = doc.getString("role") ?: "customer"
                    val uid = doc.getString("uid") ?: doc.id
                    if (role != "admin" && role != "technician" && uid != currentTechUid && !seenUids.contains(uid)) {
                        seenUids.add(uid)
                        val name = doc.getString("name") ?: "Customer"
                        val phone = doc.getString("phone") ?: "N/A"
                        val address = doc.getString("address") ?: "N/A"
                        customerEntries.add("• Customer Name: $name | Customer UID: $uid | Phone: $phone | Address: $address | Role: Customer")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("AiAssistantRepository", "Error fetching users for directory: ${e.localizedMessage}")
            }

            if (customerEntries.isNotEmpty()) {
                directoryBuilder.append("ALL SYSTEM CUSTOMERS DIRECTORY (${customerEntries.size} Total Registered Customers):\n")
                customerEntries.forEach { entry ->
                    directoryBuilder.append(entry).append("\n")
                }
            } else {
                directoryBuilder.append("No registered customers found in system directory.\n")
            }

            directoryBuilder.toString().trim()
        } catch (e: Exception) {
            android.util.Log.w("AiAssistantRepository", "Error building customer directory: ${e.localizedMessage}")
            ""
        }
    }

    suspend fun getCustomerRecommendation(customerId: String): Result<String> = withContext(Dispatchers.IO) {
        val history = fetchCustomerHistoryContext(customerId, "")
        callWorker(mode = "customer", customerId = customerId, customerHistory = history)
    }

    suspend fun getTechnicianAssistant(
        question: String,
        customerId: String = "",
        customerName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        if (question.isBlank()) {
            return@withContext Result.failure(Exception("Question cannot be empty"))
        }

        val currentTechUid = auth.currentUser?.uid ?: ""
        val effectiveCustomerId = if (customerId == currentTechUid) "" else customerId

        // 1. Fetch directory of all system customers
        val allCustomersDirectory = fetchAllCustomersDirectoryContext()

        // 2. Fetch specific customer history context if assigned/selected
        val customerHistory = if (effectiveCustomerId.isNotBlank() || customerName.isNotBlank()) {
            fetchCustomerHistoryContext(effectiveCustomerId, customerName)
        } else ""

        // 3. Combine directory and specific history into AI context
        val combinedContext = buildString {
            if (allCustomersDirectory.isNotBlank()) {
                append(allCustomersDirectory)
            }
            if (customerHistory.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(customerHistory)
            }
        }

        // 1. Try primary Worker endpoint first
        val workerResult = callWorker(
            mode = "technician",
            customerId = effectiveCustomerId,
            customerName = customerName,
            question = question,
            customerHistory = combinedContext,
            allowFallback = false
        )
        if (workerResult.isSuccess) {
            return@withContext workerResult
        }

        // 2. Fallback to live AI provider if worker fails
        callLiveAiProvider(
            question = question,
            customerName = customerName,
            customerId = effectiveCustomerId,
            customerHistory = combinedContext
        )
    }

    private suspend fun callWorker(
        mode: String,
        customerId: String,
        customerName: String = "",
        question: String? = null,
        customerHistory: String = "",
        allowFallback: Boolean = true
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = getEffectiveApiKey()

            // Fetch Firebase token with strict 1-second timeout so it never hangs
            val firebaseToken = try {
                withTimeoutOrNull(1000.milliseconds) {
                    auth.currentUser?.getIdToken(false)?.await()?.token
                }
            } catch (_: Exception) {
                null
            }

            val authToken = apiKey.ifBlank { firebaseToken ?: "" }

            val jsonBody = JSONObject().apply {
                put("mode", mode)
                if (customerId.isNotBlank() && customerId != auth.currentUser?.uid) {
                    put("customerId", customerId)
                }
                if (customerName.isNotBlank()) {
                    put("customerName", customerName)
                }
                if (question != null) {
                    put("question", question)
                }
                if (customerHistory.isNotBlank()) {
                    put("customerHistory", customerHistory)
                }
            }

            val requestBuilder = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Content-Type", "application/json")

            if (authToken.isNotBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $authToken")
                requestBuilder.addHeader("X-API-Key", authToken)
            }

            val request = requestBuilder
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = fastWorkerClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string() } ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResponse = JSONObject(responseBody)
                val reply = jsonResponse.optString("reply", "")
                val error = jsonResponse.optString("error", "")

                if (reply.isNotBlank() && !reply.contains("location is not supported", ignoreCase = true)) {
                    Result.success(reply)
                } else {
                    if (allowFallback && !question.isNullOrBlank()) {
                        callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
                    } else {
                        Result.failure(Exception(error.ifBlank { reply.ifBlank { "Worker response error" } }))
                    }
                }
            } else {
                if (allowFallback && !question.isNullOrBlank()) {
                    callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
                } else {
                    Result.failure(Exception("HTTP Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            if (allowFallback && !question.isNullOrBlank()) {
                callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun callLiveAiProvider(
        question: String,
        customerName: String = "",
        customerId: String = "",
        customerHistory: String = ""
    ): Result<String> {
        val currentTechUid = auth.currentUser?.uid ?: ""
        val nameOrIdContext = when {
            customerName.isNotBlank() -> "You are currently assisting a technician assigned to customer $customerName. Always refer to the customer by their name ($customerName) when appropriate."
            customerId.isNotBlank() && customerId != currentTechUid -> "You are currently assisting a technician assigned to customer ID $customerId."
            else -> ""
        }

        val historyContext = if (customerHistory.isNotBlank()) {
            "\n\nCUSTOMER DIRECTORY & DATABASE RECORDS:\n$customerHistory\n\nUse the above customer directory and service history records to accurately answer questions about customer names, customer UIDs, phone numbers, addresses, past repairs, parts replaced, diagnostic notes, billing, or previous service dates."
        } else ""

        val systemPrompt = "You are CoolConnect Field AI, an expert HVAC, electrical, and technical diagnostic assistant. Be helpful, concise, professional, conversational, and direct. $nameOrIdContext$historyContext".trim()

        // Direct GET request (Ultra-fast 1-2 sec response time)
        try {
            val encodedPrompt = java.net.URLEncoder.encode(question, "UTF-8").replace("+", "%20")
            val encodedSystem = java.net.URLEncoder.encode(systemPrompt, "UTF-8").replace("+", "%20")
            val url = "https://text.pollinations.ai/$encodedPrompt?system=$encodedSystem"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = liveAiClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string()?.trim() } ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val cleanReply = if (responseBody.startsWith("{")) {
                    try {
                        JSONObject(responseBody).optString("content", responseBody)
                    } catch (_: Exception) {
                        responseBody
                    }
                } else {
                    responseBody
                }

                if (cleanReply.isNotBlank() && !cleanReply.contains("location is not supported", ignoreCase = true)) {
                    return Result.success(cleanReply)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AiAssistantRepository", "Live AI GET provider error: ${e.localizedMessage}")
        }

        // Direct POST request fallback
        try {
            val jsonBody = JSONObject().apply {
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", question)
                    })
                })
                put("model", "openai")
            }

            val request = Request.Builder()
                .url("https://text.pollinations.ai/")
                .addHeader("Content-Type", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = liveAiClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string()?.trim() } ?: ""

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val cleanReply = if (responseBody.startsWith("{")) {
                    try {
                        JSONObject(responseBody).optString("content", responseBody)
                    } catch (_: Exception) {
                        responseBody
                    }
                } else {
                    responseBody
                }

                if (cleanReply.isNotBlank() && !cleanReply.contains("location is not supported", ignoreCase = true)) {
                    return Result.success(cleanReply)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AiAssistantRepository", "Live AI POST provider error: ${e.localizedMessage}")
        }

        return Result.failure(Exception("AI Assistant is temporarily busy. Please try again in a moment."))
    }
}
