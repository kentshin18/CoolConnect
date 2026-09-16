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
        // This looks across all chat documents for active service requests
        val subscription = db.collectionGroup("services")
            .whereIn("status", listOf("pending", "Accepted", "In Progress"))
            .addSnapshotListener { snapshot, _ ->
                val services = snapshot?.documents?.map { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    data["parentChatId"] = doc.reference.parent.parent?.id ?: ""
                    data
                } ?: emptyList()
                trySend(services)
            }
        awaitClose { subscription.remove() }
    }

    fun getCustomerOrders(uid: String): Flow<List<ShopOrder>> = callbackFlow {
        val subscription = db.collection("orders")
            .whereEqualTo("customerUid", uid)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val orders = snapshot?.documents?.mapNotNull { it.toObject(ShopOrder::class.java) } ?: emptyList()
                trySend(orders)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun uploadProfilePicture(uri: android.net.Uri): String {
        try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            
            // Image Optimization Pipeline
            val compressedBytes = ImageUtils.compressImage(context, uri, maxWidth = 500, maxHeight = 500, quality = 70)
                ?: throw Exception("Failed to compress image")

            android.util.Log.d("CoolConnectRepo", "Starting upload for $uid, compressed size: ${compressedBytes.size} bytes")
            
            val ref = storage.reference.child("profile_pics/$uid.jpg")
            val uploadTask = ref.putBytes(compressedBytes).await()
            android.util.Log.d("CoolConnectRepo", "Upload successful: ${uploadTask.metadata?.path}")
            
            val downloadUrl = ref.downloadUrl.await().toString()
            android.util.Log.d("CoolConnectRepo", "Download URL obtained: $downloadUrl")
            
            db.collection("users").document(uid).update("profilePicUrl", downloadUrl).await()
            android.util.Log.d("CoolConnectRepo", "Firestore updated with new URL")
            
            return downloadUrl
        } catch (e: Exception) {
            android.util.Log.e("CoolConnectRepo", "Error in uploadProfilePicture", e)
            throw e
        }
    }

    suspend fun uploadKYCDocument(uri: android.net.Uri): String {
        val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
        
        // Image Optimization Pipeline
        val compressedBytes = ImageUtils.compressImage(context, uri, maxWidth = 1200, maxHeight = 1200, quality = 80)
            ?: throw Exception("Failed to compress document")

        val ref = storage.reference.child("kyc_docs/$uid.jpg")
        ref.putBytes(compressedBytes).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        db.collection("users").document(uid).update(
            mapOf("kycUrl" to downloadUrl, "verificationStatus" to "Pending")
        ).await()
        return downloadUrl
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
