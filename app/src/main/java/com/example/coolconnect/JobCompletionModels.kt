package com.example.coolconnect

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset

data class FeeBreakdown(
    val labor: Double,
    val parts: Double,
    val vat: Double,
    val total: Double
)

fun calculateFees(laborFee: Double, partsFee: Double): FeeBreakdown {
    val subtotal = laborFee + partsFee
    val vat = 0.0
    val total = subtotal + vat
    return FeeBreakdown(
        labor = laborFee,
        parts = partsFee,
        vat = vat,
        total = total
    )
}

class CompletionDraft {
    var workNotes by mutableStateOf("")
    var partsUsed by mutableStateOf("")
    var laborFeeInput by mutableStateOf("")
    var partsFeeInput by mutableStateOf("0")
    var paymentMethod by mutableStateOf("GCash")
    val checklist = mutableStateMapOf(
        "Inspect Unit" to false,
        "Check Connections" to false,
        "Clean Filters" to false,
        "Final Test Run" to false
    )
    var photoBitmap by mutableStateOf<Bitmap?>(null)
    val signatureStrokes = mutableStateListOf<SnapshotStateList<Offset>>()

    val laborValue: Double
        get() = laborFeeInput.toDoubleOrNull() ?: 0.0

    val partsValue: Double
        get() = partsFeeInput.toDoubleOrNull() ?: 0.0

    val fees: FeeBreakdown
        get() = calculateFees(laborValue, partsValue)
}
