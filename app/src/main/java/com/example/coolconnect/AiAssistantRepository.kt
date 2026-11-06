package com.example.coolconnect

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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

private const val TAG = "AiAssistantRepository"
private const val ENDPOINT = "https://field-ai.coldovertake.workers.dev"

@Singleton
class AiAssistantRepository @Inject constructor(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    @param:ApplicationContext private val context: Context? = null,
) {
    // Shared OkHttpClient with extended read timeout (20s) for streaming and cold starts
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    var customApiKey: String = ""

    // Directory in-memory cache (60 seconds TTL)
    @Volatile private var cachedCustomerDirectory: String = ""
    @Volatile private var customerDirectoryCacheTimestamp: Long = 0L
    private val CACHE_TTL_MS = 60_000L

    private fun getEffectiveApiKey(): String {
        if (customApiKey.isNotBlank()) return customApiKey
        val resKey = context?.getString(R.string.ai_secret_key)
        if (!resKey.isNullOrBlank() && resKey != "YOUR_AI_SECRET_KEY_HERE") {
            return resKey
        }
        return ""
    }

    private suspend fun fetchCustomerHistoryContext(
        customerId: String,
        customerName: String,
        question: String = ""
    ): String = withContext(Dispatchers.IO) {
        var targetId = customerId
        var targetName = customerName

        // If no customer is explicitly assigned, check if the question mentions a registered customer
        if (targetId.isBlank() && targetName.isBlank() && question.isNotBlank()) {
            val directory = fetchAllCustomersDirectoryContext()
            if (directory.isNotBlank()) {
                val lines = directory.lines()
                for (line in lines) {
                    if (line.contains("Customer Name:")) {
                        val namePart = line.substringAfter("Customer Name:").substringBefore("|").trim()
                        val uidPart = line.substringAfter("Customer UID:").substringBefore("|").trim()
                        if (namePart.isNotBlank() && question.contains(namePart, ignoreCase = true)) {
                            targetName = namePart
                            targetId = uidPart
                            Log.d(TAG, "Auto-detected customer from question: name='$targetName', uid='$targetId'")
                            break
                        }
                    }
                }
            }
        }

        if (targetId.isBlank() && targetName.isBlank()) return@withContext ""

        withTimeoutOrNull(3000.milliseconds) {
            val startTime = System.currentTimeMillis()
            try {
                val historyBuilder = StringBuilder()
                val targetChatId = if (targetId.startsWith("admin_")) targetId else "admin_$targetId"
                val rawId = targetId.replace("admin_", "").replace("tech_", "")

                coroutineScope {
                    val chatDocDeferred = async {
                        try {
                            if (targetChatId.isNotBlank()) db.collection("chats_v2").document(targetChatId).get().await() else null
                        } catch (_: Exception) { null }
                    }

                    val serviceRequestsDeferred = async {
                        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                        if (rawId.isNotBlank()) {
                            try {
                                val srSnap = db.collection("service_requests")
                                    .whereEqualTo("customerUid", rawId)
                                    .limit(20)
                                    .get()
                                    .await()
                                docs.addAll(srSnap.documents)
                            } catch (_: Exception) {}
                        }
                        if (docs.isEmpty() && targetName.isNotBlank()) {
                            try {
                                val srSnapName = db.collection("service_requests")
                                    .whereEqualTo("customerName", targetName)
                                    .limit(20)
                                    .get()
                                    .await()
                                docs.addAll(srSnapName.documents)
                            } catch (_: Exception) {}
                        }
                        docs
                    }

                    val serviceHistoryDeferred = async {
                        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                        if (rawId.isNotBlank()) {
                            try {
                                val shSnap = db.collection("service_history")
                                    .whereEqualTo("customerUid", rawId)
                                    .limit(20)
                                    .get()
                                    .await()
                                docs.addAll(shSnap.documents)
                            } catch (_: Exception) {}
                        }
                        if (docs.isEmpty() && targetName.isNotBlank()) {
                            try {
                                val shSnapName = db.collection("service_history")
                                    .whereEqualTo("customerName", targetName)
                                    .limit(20)
                                    .get()
                                    .await()
                                docs.addAll(shSnapName.documents)
                            } catch (_: Exception) {}
                        }
                        docs
                    }

                    val ordersDeferred = async {
                        val docs = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
                        if (rawId.isNotBlank()) {
                            try {
                                val ordersSnap = db.collection("orders")
                                    .whereEqualTo("customerUid", rawId)
                                    .limit(20)
                                    .get()
                                    .await()
                                docs.addAll(ordersSnap.documents)
                            } catch (_: Exception) {}
                        }
                        docs
                    }

                    val chatDocSnap = chatDocDeferred.await()
                    val serviceReqDocs = serviceRequestsDeferred.await()
                    val serviceHistDocs = serviceHistoryDeferred.await()
                    val orderDocs = ordersDeferred.await()

                    val docCustomerName = chatDocSnap?.getString("customerName") ?: chatDocSnap?.getString("chatName") ?: targetName
                    val currentStatus = chatDocSnap?.getString("status") ?: "Active"
                    val lastRequest = chatDocSnap?.getString("lastRequest") ?: ""

                    if (docCustomerName.isNotBlank() || rawId.isNotBlank()) {
                        historyBuilder.append("Customer Name: ${docCustomerName.ifBlank { rawId }}\n")
                        historyBuilder.append("Current Booking Status: $currentStatus\n")
                        if (lastRequest.isNotBlank()) {
                            historyBuilder.append("Current Request: $lastRequest\n")
                        }
                    }

                    val allServiceDocsMap = mutableMapOf<String, com.google.firebase.firestore.DocumentSnapshot>()
                    serviceReqDocs.forEach { allServiceDocsMap[it.id] = it }
                    serviceHistDocs.forEach { allServiceDocsMap[it.id] = it }

                    val serviceDocs = allServiceDocsMap.values.toList()

                    if (serviceDocs.isNotEmpty()) {
                        historyBuilder.append("Past Service Entries (${serviceDocs.size} total):\n")

                        val sortedDocs = serviceDocs.sortedByDescending { doc ->
                            doc.getTimestamp("completedAt")?.toDate()?.time
                                ?: doc.getTimestamp("timestamp")?.toDate()?.time
                                ?: doc.getTimestamp("scheduledTimestamp")?.toDate()?.time
                                ?: doc.getTimestamp("createdAt")?.toDate()?.time
                                ?: 0L
                        }

                        for ((index, doc) in sortedDocs.withIndex()) {
                            val status = doc.getString("status") ?: "Unknown"
                            val issue = doc.getString("issue") ?: doc.getString("serviceType") ?: doc.getString("lastRequest") ?: doc.getString("notes") ?: "HVAC Service"
                            val notes = doc.getString("completionNotes") ?: doc.getString("notes") ?: doc.getString("details") ?: "N/A"
                            val partsUsed = doc.getString("partsUsed") ?: "None"
                            val totalAmount = (doc.get("totalAmount") as? Number)?.toDouble() ?: (doc.get("price") as? Number)?.toDouble()
                            val tech = doc.getString("lastTechnician") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: doc.getString("technicianName") ?: "N/A"
                            val dateObj = doc.getTimestamp("completedAt")?.toDate()
                                ?: doc.getTimestamp("timestamp")?.toDate()
                                ?: doc.getTimestamp("scheduledTimestamp")?.toDate()
                                ?: doc.getTimestamp("createdAt")?.toDate()
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
                }

                Log.d(TAG, "Customer history fetched in ${System.currentTimeMillis() - startTime} ms for customerId: '$targetId'")
                historyBuilder.toString().trim()
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching customer history for AI: ${e.localizedMessage}")
                ""
            }
        } ?: ""
    }

    private suspend fun fetchAllCustomersDirectoryContext(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (cachedCustomerDirectory.isNotBlank() && (now - customerDirectoryCacheTimestamp) < CACHE_TTL_MS) {
            Log.d(TAG, "Using cached customer directory (${cachedCustomerDirectory.length} chars)")
            return@withContext cachedCustomerDirectory
        }

        val result = withTimeoutOrNull(2000.milliseconds) {
            val startTime = System.currentTimeMillis()
            try {
                val directoryBuilder = StringBuilder()
                val customerEntries = mutableListOf<String>()
                val seenUids = mutableSetOf<String>()
                val currentTechUid = auth.currentUser?.uid ?: ""

                // 1. Fetch active/recent customer chats from 'chats_v2' (limited to 50 entries)
                try {
                    val chatsSnap = db.collection("chats_v2").limit(50).get().await()
                    chatsSnap.documents.forEach { doc ->
                        val cUid = doc.getString("customerUid")
                            ?: doc.id.replace("admin_", "").replace("tech_", "")
                        val cName = doc.getString("customerName") ?: doc.getString("chatName") ?: ""
                        val status = doc.getString("status") ?: "Active"
                        val issue = doc.getString("lastRequest") ?: doc.getString("issue") ?: ""
                        val tech = doc.getString("assignedTechnician") ?: doc.getString("lastTechnician") ?: "Unassigned"

                        if (cUid.isNotBlank() && cUid != currentTechUid && !seenUids.contains(cUid)) {
                            seenUids.add(cUid)
                            val nameStr = cName.ifBlank { "Customer" }
                            // Compliant with Data Privacy Act: Omit phone numbers and exact residential addresses from prompt context
                            val entry = StringBuilder("• Customer Name: $nameStr | Customer UID: $cUid | Booking Status: $status")
                            if (issue.isNotBlank()) entry.append(" | Request: $issue")
                            if (tech.isNotBlank()) entry.append(" | Tech: $tech")
                            customerEntries.add(entry.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching chats_v2 for directory: ${e.localizedMessage}")
                }

                // 2. Fetch registered customer users from 'users' collection (limited to 50 entries)
                try {
                    val usersSnap = db.collection("users").limit(50).get().await()
                    usersSnap.documents.forEach { doc ->
                        val role = doc.getString("role") ?: "customer"
                        val uid = doc.getString("uid") ?: doc.id
                        if (role != "admin" && role != "technician" && uid != currentTechUid && !seenUids.contains(uid)) {
                            seenUids.add(uid)
                            val name = doc.getString("name") ?: "Customer"
                            // Compliant with Data Privacy Act: Omit phone numbers and exact residential addresses from prompt context
                            customerEntries.add("• Customer Name: $name | Customer UID: $uid | Role: Customer")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching users for directory: ${e.localizedMessage}")
                }

                if (customerEntries.isNotEmpty()) {
                    directoryBuilder.append("ALL SYSTEM CUSTOMERS DIRECTORY (${customerEntries.size} Total Registered Customers):\n")
                    customerEntries.forEach { entry ->
                        directoryBuilder.append(entry).append("\n")
                    }
                } else {
                    directoryBuilder.append("No registered customers found in system directory.\n")
                }

                Log.d(TAG, "Customer directory fetched in ${System.currentTimeMillis() - startTime} ms (${customerEntries.size} entries)")
                directoryBuilder.toString().trim()
            } catch (e: Exception) {
                Log.w(TAG, "Error building customer directory: ${e.localizedMessage}")
                ""
            }
        } ?: ""

        if (result.isNotBlank()) {
            cachedCustomerDirectory = result
            customerDirectoryCacheTimestamp = now
        }
        result
    }

    suspend fun getCustomerRecommendation(customerId: String): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "getCustomerRecommendation started for customerId: '$customerId'")

        val history = fetchCustomerHistoryContext(customerId, "")
        val promptQuestion = "Based on my HVAC service history and orders, give me a personalized 1-2 sentence health assessment of my AC unit and your top recommended service or replacement part (like Compressor, Filter, Freon, or Maintenance)."

        val workerStartTime = System.currentTimeMillis()
        val workerResult = callWorker(
            mode = "customer",
            customerId = customerId,
            question = promptQuestion,
            customerHistory = history,
            allowFallback = false
        )
        val workerDuration = System.currentTimeMillis() - workerStartTime
        Log.d(TAG, "Worker response time: $workerDuration ms (isSuccess: ${workerResult.isSuccess})")

        if (workerResult.isSuccess) {
            Log.d(TAG, "getCustomerRecommendation completed via Worker in ${System.currentTimeMillis() - startTime} ms total")
            return@withContext workerResult
        }

        Log.d(TAG, "Worker returned failure. Triggering Live AI fallback...")
        val fallbackStartTime = System.currentTimeMillis()
        val fallbackResult = callLiveAiProvider(
            question = promptQuestion,
            customerName = "",
            customerId = customerId,
            customerHistory = history
        )
        val fallbackDuration = System.currentTimeMillis() - fallbackStartTime
        Log.d(TAG, "Live AI fallback response time: $fallbackDuration ms (isSuccess: ${fallbackResult.isSuccess})")

        Log.d(TAG, "getCustomerRecommendation completed in ${System.currentTimeMillis() - startTime} ms total")
        fallbackResult
    }

    suspend fun getTechnicianAssistant(
        question: String,
        customerId: String = "",
        customerName: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "getTechnicianAssistant started (customerId: '$customerId', customerName: '$customerName')")

        if (question.isBlank()) {
            return@withContext Result.failure(Exception("Question cannot be empty"))
        }

        val currentTechUid = auth.currentUser?.uid ?: ""
        val effectiveCustomerId = if (customerId == currentTechUid) "" else customerId
        val isSpecificCustomerSelected = effectiveCustomerId.isNotBlank() || customerName.isNotBlank()

        val contextStartTime = System.currentTimeMillis()
        val (allCustomersDirectory, customerHistory) = coroutineScope {
            val directoryDeferred = async {
                if (!isSpecificCustomerSelected) {
                    fetchAllCustomersDirectoryContext()
                } else {
                    ""
                }
            }
            val historyDeferred = async {
                fetchCustomerHistoryContext(effectiveCustomerId, customerName, question)
            }
            Pair(directoryDeferred.await(), historyDeferred.await())
        }
        Log.d(TAG, "Context fetched in ${System.currentTimeMillis() - contextStartTime} ms (isSpecificCustomerSelected: $isSpecificCustomerSelected)")

        val combinedContext = buildString {
            if (allCustomersDirectory.isNotBlank()) {
                append(allCustomersDirectory)
            }
            if (customerHistory.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(customerHistory)
            }
        }

        val workerStartTime = System.currentTimeMillis()
        val workerResult = callWorker(
            mode = "technician",
            customerId = effectiveCustomerId,
            customerName = customerName,
            question = question,
            customerHistory = combinedContext,
            allowFallback = false
        )
        val workerDuration = System.currentTimeMillis() - workerStartTime
        Log.d(TAG, "Worker response time: $workerDuration ms (isSuccess: ${workerResult.isSuccess})")

        if (workerResult.isSuccess) {
            Log.d(TAG, "getTechnicianAssistant completed via Worker in ${System.currentTimeMillis() - startTime} ms total")
            return@withContext workerResult
        }

        Log.d(TAG, "Worker failed or returned error. Triggering Live AI fallback...")
        val fallbackStartTime = System.currentTimeMillis()
        val fallbackResult = callLiveAiProvider(
            question = question,
            customerName = customerName,
            customerId = effectiveCustomerId,
            customerHistory = combinedContext
        )
        val fallbackDuration = System.currentTimeMillis() - fallbackStartTime
        Log.d(TAG, "Live AI fallback response time: $fallbackDuration ms (isSuccess: ${fallbackResult.isSuccess})")

        Log.d(TAG, "getTechnicianAssistant completed in ${System.currentTimeMillis() - startTime} ms total")
        fallbackResult
    }

    fun getTechnicianAssistantStream(
        question: String,
        customerId: String = "",
        customerName: String = ""
    ): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "getTechnicianAssistantStream started (customerId: '$customerId', customerName: '$customerName')")

        if (question.isBlank()) {
            throw Exception("Question cannot be empty")
        }

        val currentTechUid = auth.currentUser?.uid ?: ""
        val effectiveCustomerId = if (customerId == currentTechUid) "" else customerId
        val isSpecificCustomerSelected = effectiveCustomerId.isNotBlank() || customerName.isNotBlank()

        val contextStartTime = System.currentTimeMillis()
        val (allCustomersDirectory, customerHistory) = coroutineScope {
            val directoryDeferred = async {
                if (!isSpecificCustomerSelected) {
                    fetchAllCustomersDirectoryContext()
                } else {
                    ""
                }
            }
            val historyDeferred = async {
                fetchCustomerHistoryContext(effectiveCustomerId, customerName, question)
            }
            Pair(directoryDeferred.await(), historyDeferred.await())
        }
        Log.d(TAG, "Context fetched in ${System.currentTimeMillis() - contextStartTime} ms (isSpecificCustomerSelected: $isSpecificCustomerSelected)")

        val combinedContext = buildString {
            if (allCustomersDirectory.isNotBlank()) {
                append(allCustomersDirectory)
            }
            if (customerHistory.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(customerHistory)
            }
        }

        var workerSuccess = false
        val workerStartTime = System.currentTimeMillis()

        try {
            callWorkerStream(
                mode = "technician",
                customerId = effectiveCustomerId,
                customerName = customerName,
                question = question,
                customerHistory = combinedContext
            ).collect { chunk ->
                workerSuccess = true
                emit(chunk)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Worker streaming failed after ${System.currentTimeMillis() - workerStartTime} ms: ${e.localizedMessage}")
        }

        if (workerSuccess) {
            Log.d(TAG, "getTechnicianAssistantStream completed via Worker in ${System.currentTimeMillis() - startTime} ms total")
            return@flow
        }

        Log.d(TAG, "Worker stream failed. Triggering Live AI fallback stream...")
        val fallbackStartTime = System.currentTimeMillis()
        callLiveAiProviderStream(
            question = question,
            customerName = customerName,
            customerId = effectiveCustomerId,
            customerHistory = combinedContext
        ).collect { chunk ->
            emit(chunk)
        }
        Log.d(TAG, "getTechnicianAssistantStream completed via fallback in ${System.currentTimeMillis() - startTime} ms total")
    }.flowOn(Dispatchers.IO)

    private suspend fun callWorker(
        mode: String,
        customerId: String,
        customerName: String = "",
        question: String? = null,
        customerHistory: String = "",
        allowFallback: Boolean = true,
        attempt: Int = 1
    ): Result<String> = withContext(Dispatchers.IO) {
        val workerStartTime = System.currentTimeMillis()
        try {
            val apiKey = getEffectiveApiKey()

            // Fetch Firebase token with strict 1.5-second timeout
            val firebaseToken = try {
                withTimeoutOrNull(1500.milliseconds) {
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

            val response = httpClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string() } ?: ""

            Log.d(TAG, "Worker HTTP call (attempt $attempt) completed in ${System.currentTimeMillis() - workerStartTime} ms with code ${response.code}")

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val jsonResponse = JSONObject(responseBody)
                val reply = jsonResponse.optString("reply", "")
                val error = jsonResponse.optString("error", "")

                if (reply.isNotBlank() && !reply.contains("location is not supported", ignoreCase = true)) {
                    Result.success(reply)
                } else {
                    if (attempt == 1) {
                        Log.d(TAG, "Worker reply invalid on attempt 1, retrying worker once after 300ms...")
                        delay(300)
                        callWorker(mode, customerId, customerName, question, customerHistory, allowFallback, attempt = 2)
                    } else if (allowFallback && !question.isNullOrBlank()) {
                        Log.d(TAG, "Worker reply invalid on attempt 2, triggering internal fallback...")
                        callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
                    } else {
                        Result.failure(Exception(error.ifBlank { reply.ifBlank { "Worker response error" } }))
                    }
                }
            } else {
                if (attempt == 1) {
                    Log.d(TAG, "Worker HTTP ${response.code} on attempt 1, retrying worker once after 300ms...")
                    delay(300)
                    callWorker(mode, customerId, customerName, question, customerHistory, allowFallback, attempt = 2)
                } else if (allowFallback && !question.isNullOrBlank()) {
                    Log.d(TAG, "Worker response unsuccessful (code ${response.code}), triggering internal fallback...")
                    callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
                } else {
                    Result.failure(Exception("HTTP Error ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Worker call threw exception on attempt $attempt in ${System.currentTimeMillis() - workerStartTime} ms: ${e.localizedMessage}")
            if (attempt == 1) {
                Log.d(TAG, "Worker exception on attempt 1, retrying worker once after 300ms...")
                delay(300)
                callWorker(mode, customerId, customerName, question, customerHistory, allowFallback, attempt = 2)
            } else if (allowFallback && !question.isNullOrBlank()) {
                Log.d(TAG, "Triggering internal fallback after attempt 2 exception...")
                callLiveAiProvider(question = question, customerName = customerName, customerId = customerId, customerHistory = customerHistory)
            } else {
                Result.failure(e)
            }
        }
    }

    private fun callWorkerStream(
        mode: String,
        customerId: String,
        customerName: String = "",
        question: String,
        customerHistory: String = ""
    ): Flow<String> = flow {
        val workerStartTime = System.currentTimeMillis()
        val apiKey = getEffectiveApiKey()

        val firebaseToken = try {
            withTimeoutOrNull(1500.milliseconds) {
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
            put("question", question)
            if (customerHistory.isNotBlank()) {
                put("customerHistory", customerHistory)
            }
            put("stream", true)
        }

        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "text/event-stream, application/json")

        if (authToken.isNotBlank()) {
            requestBuilder.addHeader("Authorization", "Bearer $authToken")
            requestBuilder.addHeader("X-API-Key", authToken)
        }

        val request = requestBuilder
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        Log.d(TAG, "Worker stream HTTP response code: ${response.code} after ${System.currentTimeMillis() - workerStartTime} ms")

        if (!response.isSuccessful) {
            throw Exception("HTTP Error ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val source = body.source()

        var hasEmittedAny = false
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue

            val content = parseStreamLine(line)
            if (content.isNotBlank() && !content.contains("location is not supported", ignoreCase = true)) {
                hasEmittedAny = true
                emit(content)
            }
        }

        if (!hasEmittedAny) {
            throw Exception("No valid content emitted from worker stream")
        }
    }.flowOn(Dispatchers.IO)

    private fun callLiveAiProvider(
        question: String,
        customerName: String = "",
        customerId: String = "",
        customerHistory: String = ""
    ): Result<String> {
        val startTime = System.currentTimeMillis()
        val currentTechUid = auth.currentUser?.uid ?: ""
        val nameOrIdContext = when {
            customerName.isNotBlank() -> "You are currently assisting a technician assigned to customer $customerName. Always refer to the customer by their name ($customerName) when appropriate."
            customerId.isNotBlank() && customerId != currentTechUid -> "You are currently assisting a technician assigned to customer ID $customerId."
            else -> ""
        }

        val historyContext = if (customerHistory.isNotBlank()) {
            "\n\nCUSTOMER DIRECTORY & DATABASE RECORDS:\n$customerHistory\n\nUse the above customer directory and service history records to accurately answer questions about customer names, customer UIDs, past repairs, parts replaced, diagnostic notes, billing, or previous service dates."
        } else ""

        val systemPrompt = "You are CoolConnect Field AI, an expert HVAC, electrical, and technical diagnostic assistant. Be helpful, concise, professional, conversational, and direct. $nameOrIdContext$historyContext".trim()

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

            val response = httpClient.newCall(request).execute()
            val responseBody = response.use { it.body?.string()?.trim() } ?: ""

            Log.d(TAG, "Live AI POST HTTP call completed in ${System.currentTimeMillis() - startTime} ms with code ${response.code}")

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
            Log.w(TAG, "Live AI POST provider error after ${System.currentTimeMillis() - startTime} ms: ${e.localizedMessage}")
        }

        return Result.failure(Exception("AI Assistant is temporarily busy. Please try again in a moment."))
    }

    private fun callLiveAiProviderStream(
        question: String,
        customerName: String = "",
        customerId: String = "",
        customerHistory: String = ""
    ): Flow<String> = flow {
        val startTime = System.currentTimeMillis()
        val currentTechUid = auth.currentUser?.uid ?: ""
        val nameOrIdContext = when {
            customerName.isNotBlank() -> "You are currently assisting a technician assigned to customer $customerName. Always refer to the customer by their name ($customerName) when appropriate."
            customerId.isNotBlank() && customerId != currentTechUid -> "You are currently assisting a technician assigned to customer ID $customerId."
            else -> ""
        }

        val historyContext = if (customerHistory.isNotBlank()) {
            "\n\nCUSTOMER DIRECTORY & DATABASE RECORDS:\n$customerHistory\n\nUse the above customer directory and service history records to accurately answer questions about customer names, customer UIDs, past repairs, parts replaced, diagnostic notes, billing, or previous service dates."
        } else ""

        val systemPrompt = "You are CoolConnect Field AI, an expert HVAC, electrical, and technical diagnostic assistant. Be helpful, concise, professional, conversational, and direct. $nameOrIdContext$historyContext".trim()

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

        val response = httpClient.newCall(request).execute()
        Log.d(TAG, "Live AI stream HTTP response code: ${response.code} after ${System.currentTimeMillis() - startTime} ms")

        if (!response.isSuccessful) {
            throw Exception("AI Assistant is temporarily busy. Please try again in a moment.")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val source = body.source()

        var hasEmittedAny = false
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (line.isBlank()) continue

            val content = parseStreamLine(line)
            if (content.isNotBlank() && !content.contains("location is not supported", ignoreCase = true)) {
                hasEmittedAny = true
                emit(content)
            }
        }

        if (!hasEmittedAny) {
            throw Exception("AI Assistant is temporarily busy. Please try again in a moment.")
        }
    }.flowOn(Dispatchers.IO)

    private fun parseStreamLine(line: String): String {
        val trimmed = line.trim()
        val rawData = if (trimmed.startsWith("data:")) {
            trimmed.substringAfter("data:").trim()
        } else {
            trimmed
        }

        if (rawData == "[DONE]" || rawData.isBlank()) return ""

        return if (rawData.startsWith("{")) {
            try {
                val json = JSONObject(rawData)
                val reply = json.optString("reply", "")
                if (reply.isNotBlank()) return reply

                val content = json.optString("content", "")
                if (content.isNotBlank()) return content

                val candidates = json.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val contentObj = candidates.getJSONObject(0).optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        val text = parts.getJSONObject(0).optString("text", "")
                        if (text.isNotBlank()) return text
                    }
                }

                val choices = json.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val delta = choices.getJSONObject(0).optJSONObject("delta")
                    val deltaContent = delta?.optString("content", "") ?: ""
                    if (deltaContent.isNotBlank()) return deltaContent

                    val message = choices.getJSONObject(0).optJSONObject("message")
                    val msgContent = message?.optString("content", "") ?: ""
                    if (msgContent.isNotBlank()) return msgContent
                }

                rawData
            } catch (_: Exception) {
                rawData
            }
        } else {
            rawData
        }
    }
}
