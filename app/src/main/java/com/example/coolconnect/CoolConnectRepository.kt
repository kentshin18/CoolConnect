package com.example.coolconnect

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class CoolConnectRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val dao: CoolConnectDao,
    private val context: Context
) {
    val technicians: Flow<List<Map<String, Any>>> = dao.getAllTechnicians().map { entities ->
        entities.map { it.toMap() }
    }

    val products: Flow<List<Map<String, Any>>> = dao.getAllProducts().map { entities ->
        entities.map { it.toMap() }
    }

    val completedJobs: Flow<List<Map<String, Any>>> = dao.getAllCompletedJobs().map { entities ->
        entities.map { it.toMap() }
    }

    fun getAllPendingServices(): Flow<List<Map<String, Any>>> = callbackFlow {
        val subscription = db.collection("service_requests")
            .whereIn("status", listOf("pending", "Pending", "Pending Dispatch", "Unassigned", "Assigned", "Accepted", "In Progress", "Confirmed"))
            .addSnapshotListener { snapshot, _ ->
                val services = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    data["requestId"] = doc.id
                    data["parentChatId"] = "admin_${doc.getString("customerUid") ?: ""}"
                    data
                } ?: emptyList()
                trySend(services)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Real-time stream of all service bookings mapped into ScheduleManager.ServiceBooking
     */
    fun getAllBookingsFlow(): Flow<List<ScheduleManager.ServiceBooking>> = callbackFlow {
        val subscription = db.collection("service_requests")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CoolConnectRepo", "Error streaming bookings", error)
                    return@addSnapshotListener
                }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val status = doc.getString("status") ?: "Pending"
                    val startTime = (doc.get("startTimeMillis") as? Number)?.toLong()
                        ?: doc.getTimestamp("scheduledStart")?.toDate()?.time
                        ?: doc.getTimestamp("scheduledTimestamp")?.toDate()?.time
                        ?: 0L
                    val duration = (doc.get("durationMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES
                    val buffer = (doc.get("bufferMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_BUFFER_MINUTES
                    val endTime = (doc.get("endTimeMillis") as? Number)?.toLong()
                        ?: doc.getTimestamp("scheduledEnd")?.toDate()?.time
                        ?: if (startTime > 0) startTime + (duration * 60 * 1000L) else 0L

                    ScheduleManager.ServiceBooking(
                        id = doc.id,
                        customerUid = doc.getString("customerUid") ?: "",
                        customerName = doc.getString("customerName") ?: "Customer",
                        customerPhone = doc.getString("customerPhone") ?: "",
                        address = doc.getString("customerAddress") ?: doc.getString("address") ?: "",
                        technicianUid = doc.getString("technicianUid"),
                        technicianName = doc.getString("technicianName") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: "",
                        serviceType = doc.getString("issue") ?: "HVAC Service",
                        status = status,
                        startTimeMillis = startTime,
                        endTimeMillis = endTime,
                        durationMinutes = duration,
                        bufferMinutes = buffer,
                        createdAtMillis = (doc.get("createdAtMillis") as? Number)?.toLong()
                            ?: doc.getTimestamp("createdAt")?.toDate()?.time
                            ?: doc.getTimestamp("timestamp")?.toDate()?.time
                            ?: System.currentTimeMillis(),
                        rating = (doc.get("rating") as? Number)?.toInt(),
                        comment = doc.getString("comment") ?: doc.getString("feedback")
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Real-time stream of technician time-off blocks
     */
    fun getTimeOffBlocksFlow(): Flow<List<ScheduleManager.TimeOffBlock>> = callbackFlow {
        val subscription = db.collection("time_off_blocks")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CoolConnectRepo", "Error streaming time off blocks", error)
                    return@addSnapshotListener
                }
                val blocks = snapshot?.documents?.mapNotNull { doc ->
                    ScheduleManager.TimeOffBlock(
                        id = doc.id,
                        technicianName = doc.getString("technicianName") ?: "",
                        startTimeMillis = (doc.get("startTimeMillis") as? Number)?.toLong() ?: 0L,
                        endTimeMillis = (doc.get("endTimeMillis") as? Number)?.toLong() ?: 0L,
                        reason = doc.getString("reason") ?: "Time Off"
                    )
                } ?: emptyList()
                trySend(blocks)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Real-time stream of technician working hour configs
     */
    fun getTechnicianConfigsFlow(): Flow<List<ScheduleManager.TechnicianScheduleConfig>> = callbackFlow {
        val subscription = db.collection("technician_configs")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CoolConnectRepo", "Error streaming tech configs", error)
                    return@addSnapshotListener
                }
                val configs = snapshot?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val workingDays = (doc.get("workingDays") as? List<Number>)?.map { it.toInt() }
                        ?: listOf(2, 3, 4, 5, 6, 7) // Mon..Sat

                    ScheduleManager.TechnicianScheduleConfig(
                        technicianName = doc.getString("technicianName") ?: doc.id,
                        startHour = (doc.get("startHour") as? Number)?.toInt() ?: 8,
                        startMinute = (doc.get("startMinute") as? Number)?.toInt() ?: 0,
                        endHour = (doc.get("endHour") as? Number)?.toInt() ?: 18,
                        endMinute = (doc.get("endMinute") as? Number)?.toInt() ?: 0,
                        workingDays = workingDays,
                        timeZoneId = doc.getString("timeZoneId") ?: ScheduleManager.DEFAULT_TIMEZONE
                    )
                } ?: emptyList()
                trySend(configs)
            }
        awaitClose { subscription.remove() }
    }

    /**
     * Atomically reserve a slot for a customer with conflict verification.
     */
    suspend fun bookAppointmentAtomic(
        customerUid: String,
        customerName: String,
        customerPhone: String,
        customerAddress: String,
        technicianName: String,
        serviceType: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        scheduledDateStr: String,
        scheduledTimeStr: String,
        existingBookings: List<ScheduleManager.ServiceBooking>,
        timeOffBlocks: List<ScheduleManager.TimeOffBlock>,
        technicianUid: String? = null
    ): Result<String> {
        return try {
            // Re-verify conflict server-side
            val hasConflict = ScheduleManager.hasConflict(
                technicianName = technicianName,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                bookings = existingBookings,
                timeOffBlocks = timeOffBlocks,
                technicianUid = technicianUid
            )

            if (hasConflict) {
                return Result.failure(Exception("This time was just booked or overlaps with existing schedule. Please choose another slot."))
            }

            val timestamp = com.google.firebase.Timestamp.now()
            val initialStatus = "Pending"
            val reqId = db.collection("service_requests").document().id

            val statusHistoryItem = mapOf(
                "status" to initialStatus,
                "updatedBy" to customerName,
                "timestampMillis" to System.currentTimeMillis(),
                "fromStatus" to "None"
            )

            val serviceDoc = mapOf(
                "requestId" to reqId,
                "type" to "service",
                "customerUid" to customerUid,
                "customerName" to customerName,
                "customerPhone" to customerPhone,
                "customerAddress" to customerAddress,
                "technicianUid" to technicianUid,
                "technicianName" to technicianName,
                "technician" to technicianName,
                "assignedTechnician" to technicianName,
                "status" to initialStatus,
                "scheduledDate" to scheduledDateStr,
                "scheduledTime" to scheduledTimeStr,
                "scheduledStart" to com.google.firebase.Timestamp(java.util.Date(startTimeMillis)),
                "scheduledEnd" to com.google.firebase.Timestamp(java.util.Date(endTimeMillis)),
                "startTimeMillis" to startTimeMillis,
                "endTimeMillis" to endTimeMillis,
                "durationMinutes" to ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES,
                "bufferMinutes" to ScheduleManager.DEFAULT_BUFFER_MINUTES,
                "createdAt" to timestamp,
                "updatedAt" to timestamp,
                "createdAtMillis" to System.currentTimeMillis(),
                "issue" to serviceType,
                "statusHistory" to listOf(statusHistoryItem)
            )

            db.collection("service_requests").document(reqId).set(serviceDoc).await()

            // Synchronize to top-level 'service_history' collection for clean querying
            val serviceHistoryData = HashMap(serviceDoc).apply {
                put("serviceId", reqId)
                put("chatId", "admin_$customerUid")
                put("technicianName", technicianName)
            }
            db.collection("service_history").document(reqId).set(serviceHistoryData, com.google.firebase.firestore.SetOptions.merge()).await()

            val chatRef = db.collection("chats_v2").document("admin_$customerUid")
            val autoMsg = hashMapOf(
                "senderId" to customerUid,
                "senderName" to customerName,
                "text" to "Hello! I am scheduling $serviceType with $technicianName for $scheduledDateStr during $scheduledTimeStr.",
                "timestamp" to timestamp
            )
            chatRef.collection("messages").add(autoMsg).await()

            Result.success(reqId)
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Error in bookAppointmentAtomic", e)
            Result.failure(e)
        }
    }

    /**
     * Updates booking status with audit logging in statusHistory
     */
    suspend fun updateBookingStatusWithAudit(
        chatId: String,
        serviceDocId: String?,
        newStatus: String,
        oldStatus: String,
        updatedBy: String
    ) {
        val historyEntry = mapOf(
            "status" to newStatus,
            "updatedBy" to updatedBy,
            "timestampMillis" to System.currentTimeMillis(),
            "fromStatus" to oldStatus
        )

        val updates = mapOf(
            "status" to newStatus,
            "updatedAt" to com.google.firebase.Timestamp.now(),
            "statusHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(historyEntry)
        )

        if (!serviceDocId.isNullOrBlank()) {
            db.collection("service_requests").document(serviceDocId).update(updates).await()
            db.collection("service_history").document(serviceDocId).set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    /**
     * Atomically reassigns a job to another technician after conflict check
     */
    suspend fun reassignTechnicianAtomic(
        chatId: String,
        serviceDocId: String?,
        newTechnicianName: String,
        newTechnicianUid: String?,
        startTimeMillis: Long,
        endTimeMillis: Long,
        reassignedBy: String,
        existingBookings: List<ScheduleManager.ServiceBooking>,
        timeOffBlocks: List<ScheduleManager.TimeOffBlock>
    ): Result<Unit> {
        return try {
            val conflict = ScheduleManager.hasConflict(
                technicianName = newTechnicianName,
                startTimeMillis = startTimeMillis,
                endTimeMillis = endTimeMillis,
                bookings = existingBookings,
                timeOffBlocks = timeOffBlocks,
                excludeBookingId = serviceDocId,
                technicianUid = newTechnicianUid
            )

            if (conflict) {
                return Result.failure(Exception("Technician $newTechnicianName is already booked or unavailable at this slot."))
            }

            val historyEntry = mapOf(
                "status" to "Reassigned to $newTechnicianName",
                "updatedBy" to reassignedBy,
                "timestampMillis" to System.currentTimeMillis(),
                "fromStatus" to "Reassigned"
            )

            val serviceUpdates = mapOf(
                "technicianUid" to newTechnicianUid,
                "technician" to newTechnicianName,
                "assignedTechnician" to newTechnicianName,
                "technicianName" to newTechnicianName,
                "status" to "Accepted",
                "updatedAt" to com.google.firebase.Timestamp.now(),
                "statusHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(historyEntry)
            )

            if (!serviceDocId.isNullOrBlank()) {
                db.collection("service_requests").document(serviceDocId).update(serviceUpdates).await()
                db.collection("service_history").document(serviceDocId).set(serviceUpdates, com.google.firebase.firestore.SetOptions.merge()).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Error in reassignTechnicianAtomic", e)
            Result.failure(e)
        }
    }

    /**
     * Background routine to expire pending bookings older than 24h
     */
    suspend fun expirePendingBookings() {
        try {
            val snapshot = db.collection("service_requests")
                .whereIn("status", listOf("pending", "Pending"))
                .get()
                .await()

            val now = System.currentTimeMillis()
            for (doc in snapshot.documents) {
                val created = (doc.get("createdAtMillis") as? Number)?.toLong()
                    ?: doc.getTimestamp("createdAt")?.toDate()?.time
                    ?: doc.getTimestamp("timestamp")?.toDate()?.time
                    ?: 0L

                if (created > 0 && ScheduleManager.isPendingExpired(created, now)) {
                    val historyEntry = mapOf(
                        "status" to "Expired",
                        "updatedBy" to "System Expire Job",
                        "timestampMillis" to now,
                        "fromStatus" to "Pending"
                    )
                    doc.reference.update(
                        mapOf(
                            "status" to "Expired",
                            "updatedAt" to com.google.firebase.Timestamp.now(),
                            "statusHistory" to com.google.firebase.firestore.FieldValue.arrayUnion(historyEntry)
                        )
                    ).await()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Error expiring pending bookings", e)
        }
    }

    /**
     * One-time idempotent migration function to copy legacy chats_v2 services into top-level service_requests
     */
    suspend fun runServiceRequestsMigration(): Map<String, Int> {
        var migratedCount = 0
        var skippedCount = 0
        var errorCount = 0

        try {
            val techUsersSnap = db.collection("users")
                .whereEqualTo("role", "technician")
                .get()
                .await()

            val nameToUidMap = mutableMapOf<String, String>()
            techUsersSnap.documents.forEach { doc ->
                val name = doc.getString("name") ?: ""
                if (name.isNotBlank()) {
                    nameToUidMap[name.lowercase(java.util.Locale.ROOT)] = doc.id
                }
            }

            val legacyServicesSnap = db.collectionGroup("services").get().await()

            for (doc in legacyServicesSnap.documents) {
                try {
                    val requestId = doc.id
                    val existingDoc = db.collection("service_requests").document(requestId).get().await()
                    if (existingDoc.exists()) {
                        skippedCount++
                        continue
                    }

                    val parentChatId = doc.reference.parent.parent?.id ?: ""
                    val parentCustomerUid = parentChatId.replace("admin_", "").replace("tech_", "")
                    val customerUid = doc.getString("customerUid") ?: parentCustomerUid
                    val techName = doc.getString("technician") ?: doc.getString("assignedTechnician") ?: doc.getString("technicianName") ?: ""
                    val techUid = nameToUidMap[techName.lowercase(java.util.Locale.ROOT)]

                    val orderId = doc.getString("orderId")
                    val type = doc.getString("type") ?: if (!orderId.isNullOrBlank()) "order_installation" else "service"
                    val startTimeMillis = (doc.get("startTimeMillis") as? Number)?.toLong()
                        ?: (doc.get("scheduledTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                    val endTimeMillis = (doc.get("endTimeMillis") as? Number)?.toLong()
                        ?: if (startTimeMillis > 0) startTimeMillis + (120 * 60 * 1000L) else 0L

                    val serviceDocData = mutableMapOf<String, Any?>(
                        "requestId" to requestId,
                        "type" to type,
                        "customerUid" to customerUid,
                        "customerName" to (doc.getString("customerName") ?: "Customer"),
                        "customerPhone" to (doc.getString("customerPhone") ?: ""),
                        "customerAddress" to (doc.getString("customerAddress") ?: doc.getString("address") ?: ""),
                        "technicianUid" to techUid,
                        "technicianName" to techName.ifBlank { "Unassigned" },
                        "status" to (doc.getString("status") ?: "Pending"),
                        "issue" to (doc.getString("issue") ?: doc.getString("lastRequest") ?: "HVAC Service"),
                        "scheduledStart" to (if (startTimeMillis > 0) com.google.firebase.Timestamp(java.util.Date(startTimeMillis)) else null),
                        "scheduledEnd" to (if (endTimeMillis > 0) com.google.firebase.Timestamp(java.util.Date(endTimeMillis)) else null),
                        "startTimeMillis" to startTimeMillis,
                        "endTimeMillis" to endTimeMillis,
                        "orderId" to orderId,
                        "createdAt" to (doc.getTimestamp("timestamp") ?: doc.getTimestamp("scheduledTimestamp") ?: com.google.firebase.Timestamp.now()),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    )

                    doc.getString("completionNotes")?.let { serviceDocData["completionNotes"] = it }
                    doc.getString("partsUsed")?.let { serviceDocData["partsUsed"] = it }
                    doc.getString("paymentMethod")?.let { serviceDocData["paymentMethod"] = it }
                    (doc.get("laborFee") as? Number)?.toDouble()?.let { serviceDocData["laborFee"] = it }
                    (doc.get("partsFee") as? Number)?.toDouble()?.let { serviceDocData["partsFee"] = it }
                    (doc.get("vatAmount") as? Number)?.toDouble()?.let { serviceDocData["vatAmount"] = it }
                    (doc.get("totalAmount") as? Number)?.toDouble()?.let { serviceDocData["totalAmount"] = it }
                    doc.getString("photoProofUrl")?.let { serviceDocData["photoProofUrl"] = it }
                    doc.getString("signatureUrl")?.let { serviceDocData["signatureUrl"] = it }
                    doc.getTimestamp("completedAt")?.let { serviceDocData["completedAt"] = it }

                    db.collection("service_requests").document(requestId).set(serviceDocData.filterValues { it != null }).await()
                    migratedCount++
                } catch (e: Exception) {
                    android.util.Log.e("CoolConnectRepo", "Error migrating service doc ${doc.id}", e)
                    errorCount++
                }
            }
            android.util.Log.i("CoolConnectRepo", "Service requests migration complete: migrated=$migratedCount, skipped=$skippedCount, errors=$errorCount")
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Service requests migration failed", e)
        }
        return mapOf("migrated" to migratedCount, "skipped" to skippedCount, "errors" to errorCount)
    }

    /**
     * Adds a manual time-off block for a technician
     */
    suspend fun addTimeOffBlock(
        technicianName: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        reason: String
    ) {
        val block = mapOf(
            "technicianName" to technicianName,
            "startTimeMillis" to startTimeMillis,
            "endTimeMillis" to endTimeMillis,
            "reason" to reason,
            "createdAtMillis" to System.currentTimeMillis()
        )
        db.collection("time_off_blocks").add(block).await()
    }

    /**
     * Saves or updates a technician's working hours and working days config
     */
    suspend fun saveTechnicianScheduleConfig(config: ScheduleManager.TechnicianScheduleConfig) {
        val doc = mapOf(
            "technicianName" to config.technicianName,
            "startHour" to config.startHour,
            "startMinute" to config.startMinute,
            "endHour" to config.endHour,
            "endMinute" to config.endMinute,
            "workingDays" to config.workingDays,
            "timeZoneId" to config.timeZoneId
        )
        db.collection("technician_configs").document(config.technicianName).set(doc, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun getCustomerOrders(uid: String): Flow<List<ShopOrder>> = callbackFlow {
        val subscription = db.collection("orders")
            .whereEqualTo("customerUid", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("CoolConnectRepo", "Error fetching orders for $uid", error)
                }
                @Suppress("UNCHECKED_CAST")
                val orders = snapshot?.documents?.mapNotNull { doc ->
                    val rawItems = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                    
                    ShopOrder(
                        orderId = doc.getString("orderId") ?: doc.id,
                        customerUid = doc.getString("customerUid") ?: uid,
                        customerName = doc.getString("customerName") ?: "Customer",
                        customerPhone = doc.getString("customerPhone") ?: "",
                        items = rawItems,
                        itemsSummary = doc.getString("itemsSummary") ?: "",
                        subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                        vat = (doc.get("vat") as? Number)?.toDouble() ?: 0.0,
                        total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                        status = doc.getString("status") ?: "Processing",
                        shippingAddress = doc.getString("shippingAddress") ?: "",
                        paymentMethod = doc.getString("paymentMethod") ?: "",
                        assignedTechnician = doc.getString("assignedTechnician") ?: "",
                        timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now()
                    )
                }?.sortedByDescending { it.timestamp.seconds } ?: emptyList()

                trySend(orders)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun uploadProfilePicture(uri: android.net.Uri): String {
        try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")

            val compressedBytes = ImageUtils.compressImage(context, uri, maxWidth = 500, maxHeight = 500, quality = 80)
                ?: throw Exception("Failed to compress image")

            val localFileUrl = ImageUtils.saveImageToInternalStorage(
                context = context,
                bytes = compressedBytes,
                folderName = "profile_pics",
                fileName = "$uid.jpg"
            ) ?: throw Exception("Failed to save image locally")

            android.util.Log.d("CoolConnectRepo", "Profile picture saved locally: $localFileUrl")

            db.collection("users").document(uid).set(
                mapOf("profilePicUrl" to localFileUrl),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            return localFileUrl
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Error in uploadProfilePicture", e)
            throw e
        }
    }

    suspend fun uploadKYCDocument(uri: android.net.Uri): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")

        val compressedBytes = ImageUtils.compressImage(context, uri, maxWidth = 1200, maxHeight = 1200, quality = 80)
            ?: throw Exception("Failed to compress document")

        val localFileUrl = ImageUtils.saveImageToInternalStorage(
            context = context,
            bytes = compressedBytes,
            folderName = "kyc_docs",
            fileName = "$uid.jpg"
        ) ?: throw Exception("Failed to save document locally")

        db.collection("users").document(uid).set(
            mapOf("kycUrl" to localFileUrl, "verificationStatus" to "Pending"),
            com.google.firebase.firestore.SetOptions.merge()
        ).await()

        return localFileUrl
    }

    suspend fun uploadProductImage(uri: android.net.Uri): String {
        val compressedBytes = ImageUtils.compressImage(context, uri, maxWidth = 800, maxHeight = 800, quality = 80)
            ?: throw Exception("Failed to compress product image")

        val fileName = "${java.util.UUID.randomUUID()}.jpg"
        val localFileUrl = ImageUtils.saveImageToInternalStorage(
            context = context,
            bytes = compressedBytes,
            folderName = "product_images",
            fileName = fileName
        ) ?: throw Exception("Failed to save product image locally")

        android.util.Log.d("CoolConnectRepo", "Product image saved locally: $localFileUrl")
        return localFileUrl
    }

    suspend fun uploadBitmapImage(bitmap: android.graphics.Bitmap, folder: String): String {
        val compressedBytes = ImageUtils.compressBitmap(bitmap, maxWidth = 1000, maxHeight = 1000, quality = 80)
            ?: throw Exception("Failed to compress photo")

        val fileName = "${java.util.UUID.randomUUID()}.jpg"
        val localFileUrl = ImageUtils.saveImageToInternalStorage(
            context = context,
            bytes = compressedBytes,
            folderName = folder,
            fileName = fileName
        ) ?: throw Exception("Failed to save photo locally")

        android.util.Log.d("CoolConnectRepo", "Bitmap photo saved locally to $folder: $localFileUrl")
        return localFileUrl
    }

    suspend fun verifyTechnician(uid: String, isVerified: Boolean) {
        db.collection("users").document(uid).update(
            mapOf("isVerified" to isVerified, "verificationStatus" to if (isVerified) "Verified" else "Rejected")
        ).await()
    }

    suspend fun refreshTechnicians() {
        val snapshot = db.collection("users").whereEqualTo("role", "technician").get().await()
        val techs = snapshot.documents.map { doc ->
            TechnicianEntity(
                uid = doc.id,
                name = doc.getString("name") ?: "",
                email = doc.getString("email") ?: "",
                phone = doc.getString("phone") ?: "",
                skill = doc.getString("skill") ?: "",
                isOnline = doc.getBoolean("isOnline") ?: false
            )
        }
        dao.insertTechnicians(techs)
    }

    suspend fun refreshProducts() {
        val snapshot = db.collection("products").get().await()
        val products = snapshot.documents.map { doc ->
            ProductEntity(
                id = doc.id,
                name = doc.getString("name") ?: "",
                price = (doc.get("price") as? Number)?.toDouble() ?: 0.0,
                stock = (doc.get("stock") as? Number)?.toInt() ?: 0
            )
        }
        dao.insertProducts(products)
    }

    suspend fun refreshCompletedJobs() {
        val snapshot = db.collection("chats_v2").whereEqualTo("status", "Completed").get().await()
        val jobs = snapshot.documents.map { doc ->
            CompletedJobEntity(
                uid = doc.id,
                customerName = doc.getString("customerName") ?: "",
                // Fallback to lastTechnician since active assignment was reset
                assignedTechnician = doc.getString("assignedTechnician") ?: doc.getString("lastTechnician") ?: "Unknown",
                totalAmount = (doc.get("totalAmount") as? Number)?.toDouble() ?: 0.0,
                completedAt = doc.getTimestamp("completedAt")?.toDate()?.time ?: 0L
            )
        }
        dao.insertCompletedJobs(jobs)
    }

    private fun TechnicianEntity.toMap() = mapOf(
        "uid" to uid, "name" to name, "email" to email, "phone" to phone, "skill" to skill, "isOnline" to isOnline
    )

    private fun ProductEntity.toMap() = mapOf(
        "id" to id, "name" to name, "price" to price, "stock" to stock
    )

    private fun CompletedJobEntity.toMap() = mapOf(
        "uid" to uid, "customerName" to customerName, "assignedTechnician" to assignedTechnician, "totalAmount" to totalAmount, "completedAt" to completedAt
    )
}
