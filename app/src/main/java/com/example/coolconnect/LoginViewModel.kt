package com.example.coolconnect

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val messaging: FirebaseMessaging,
    private val repository: CoolConnectRepository
) : ViewModel() {
    
    fun login(email: String, password: String, onResult: (Result<String>) -> Unit) {
        // Hardcoded admin fallback for backward compatibility with user's code
        if (email == "cooladmin123" && password == "connect123") {
            onResult(Result.success("admin"))
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        saveFcmToken(uid)
                        db.collection("users").document(uid).get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role") ?: "customer"
                                onResult(Result.success(role))
                            }
                            .addOnFailureListener { onResult(Result.failure(it)) }
                    } else {
                        onResult(Result.success("customer"))
                    }
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Login failed")))
                }
            }
    }

    fun register(userData: Map<String, Any>, password: String, onResult: (Result<Unit>) -> Unit) {
        val email = userData["email"] as String
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val uid = auth.currentUser?.uid
                    if (uid != null) {
                        db.collection("users").document(uid).set(userData)
                            .addOnSuccessListener { 
                                auth.signOut()
                                onResult(Result.success(Unit)) 
                            }
                            .addOnFailureListener { onResult(Result.failure(it)) }
                    }
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Registration failed")))
                }
            }
    }

    fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) {
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onResult(Result.success(Unit))
                } else {
                    onResult(Result.failure(task.exception ?: Exception("Reset failed")))
                }
            }
    }

    private fun saveFcmToken(uid: String) {
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                db.collection("users").document(uid).update("fcmToken", task.result)
            }
        }
    }
}
