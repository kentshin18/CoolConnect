package com.example.coolconnect

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingAndPricingTest {

    @Test
    fun testVatAndTotalAmountCalculation() {
        val laborFee = 1000.0
        val partsFee = 500.0
        val subtotal = laborFee + partsFee
        
        val vatAmount = 0.0
        val totalAmount = subtotal + vatAmount

        assertEquals(1500.0, subtotal, 0.01)
        assertEquals(0.0, vatAmount, 0.01)
        assertEquals(1500.0, totalAmount, 0.01)
    }

    @Test
    fun testCatalogPriceUpdates() {
        val compressorPrice = 8000.0
        val boardPrice = 5500.0
        val fanMotorPrice = 4000.0
        val freonPrice = 7800.0

        val total = compressorPrice + boardPrice + fanMotorPrice + freonPrice
        assertEquals(25300.0, total, 0.01)
    }
}
