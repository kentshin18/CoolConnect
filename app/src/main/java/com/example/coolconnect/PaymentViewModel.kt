package com.example.coolconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class PaymentStatus {
    object Idle : PaymentStatus()
    object Processing : PaymentStatus()
    data class Success(val transactionId: String) : PaymentStatus()
    data class Error(val message: String) : PaymentStatus()
}

@HiltViewModel
class PaymentViewModel @Inject constructor() : ViewModel() {

    private val _paymentStatus = MutableStateFlow<PaymentStatus>(PaymentStatus.Idle)
    val paymentStatus: StateFlow<PaymentStatus> = _paymentStatus.asStateFlow()

    fun processPayment(method: String, amount: Double) {
        viewModelScope.launch {
            _paymentStatus.value = PaymentStatus.Processing
            
            // Simulating Payment Gateway API Call
            delay(2000)
            
            val isSuccessful = true 
            if (isSuccessful) {
                val mockTxnId = "TXN-${System.currentTimeMillis()}"
                _paymentStatus.value = PaymentStatus.Success(mockTxnId)
            } else {
                _paymentStatus.value = PaymentStatus.Error("Payment gateway error.")
            }
        }
    }
    
    fun resetStatus() {
        _paymentStatus.value = PaymentStatus.Idle
    }
}
