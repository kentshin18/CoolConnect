package com.example.coolconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RecommendationEngineTest {

    private val engine = RecommendationEngine()
    private val now = System.currentTimeMillis()

    @Test
    fun testBrandNewCustomerFallback() {
        val onboarding = OnboardingAnswers(
            propertyType = "Commercial",
            numUnits = 3,
            systemAgeYears = 1,
        )

        val recommendations = engine.getRecommendations(
            customerId = "new_cust_123",
            onboarding = onboarding,
            equipmentList = emptyList(),
            bookingHistory = emptyList(),
            shopOrders = emptyList(),
            productsCatalog = emptyList(),
            currentTimeMillis = now
        )

        assertFalse("Recommendations should not be empty for new customers", recommendations.isEmpty())
        val topRec = recommendations.first()
        assertEquals("rec_fallback_inspection", topRec.id)
        assertTrue(topRec.reason.contains("Commercial"))
        assertTrue(topRec.reason.contains("3 unit(s)"))
        assertEquals(RecommendationType.SERVICE, topRec.type)
    }

    @Test
    fun testCustomerWithOneCompletedBookingMaintenanceTrigger() {
        val sevenMonthsAgo = now - TimeUnit.DAYS.toMillis(210)
        val singleBooking = ScheduleManager.ServiceBooking(
            id = "booking_1",
            customerUid = "cust_one_booking",
            serviceType = "AC Inspection",
            status = "Completed",
            startTimeMillis = sevenMonthsAgo,
            createdAtMillis = sevenMonthsAgo,
            rating = 5
        )

        val equipment = CustomerEquipment(
            id = "eq_1",
            unitType = "Split-Type AC",
            brand = "Carrier",
            model = "Inverter 1.5HP"
        )

        val recommendations = engine.getRecommendations(
            customerId = "cust_one_booking",
            equipmentList = listOf(equipment),
            bookingHistory = listOf(singleBooking),
            shopOrders = emptyList(),
            productsCatalog = emptyList(),
            currentTimeMillis = now
        )

        val tuneupRec = recommendations.find { it.id == "rec_tuneup_due" }
        assertTrue("Should recommend tune-up when last service was > 180 days ago", tuneupRec != null)
        assertTrue(tuneupRec!!.reason.contains("Carrier Split-Type AC"))
        assertTrue(tuneupRec.reason.contains("210 days ago"))
    }

    @Test
    fun testLowRatingUpsellSuppression() {
        val recentBadBooking = ScheduleManager.ServiceBooking(
            id = "booking_bad",
            customerUid = "cust_unhappy",
            serviceType = "Compressor Repair",
            status = "Completed",
            startTimeMillis = now - TimeUnit.DAYS.toMillis(5),
            createdAtMillis = now - TimeUnit.DAYS.toMillis(5),
            rating = 1,
            comment = "Unit stopped cooling again next day!"
        )

        val recommendations = engine.getRecommendations(
            customerId = "cust_unhappy",
            equipmentList = emptyList(),
            bookingHistory = listOf(recentBadBooking),
            shopOrders = emptyList(),
            productsCatalog = emptyList(),
            currentTimeMillis = now
        )

        assertEquals(1, recommendations.size)
        val vipRec = recommendations.first()
        assertEquals("courtesy_checkup", vipRec.id)
        assertEquals("Dedicated Customer Care & Quality Check", vipRec.title)
        assertEquals(RecommendationType.BOOKABLE_ACTION, vipRec.type)
    }

    @Test
    fun testOutOfStockProductExclusion() {
        val outOfStockFilter = Product(
            id = 101,
            name = "HEPA Air Filter",
            price = "₱500.00",
            category = "Filters",
            color = androidx.compose.ui.graphics.Color.Blue,
            stock = 0,
            docId = "filter_doc_101"
        )

        val onboarding = OnboardingAnswers()

        val recommendations = engine.getRecommendations(
            customerId = "cust_out_of_stock",
            onboarding = onboarding,
            equipmentList = emptyList(),
            bookingHistory = emptyList(),
            shopOrders = emptyList(),
            productsCatalog = listOf(outOfStockFilter),
            currentTimeMillis = now
        )

        val hasOutOfStockProduct = recommendations.any { it.targetId == "filter_doc_101" }
        assertFalse("Out-of-stock product should be excluded from recommendations", hasOutOfStockProduct)
    }

    @Test
    fun testRecentServiceDuplicationExclusion() {
        val recentService = ScheduleManager.ServiceBooking(
            id = "booking_recent_clean",
            customerUid = "cust_recent",
            serviceType = "Deep Chemical Coil & Duct Sanitization",
            status = "Completed",
            startTimeMillis = now - TimeUnit.DAYS.toMillis(10),
            createdAtMillis = now - TimeUnit.DAYS.toMillis(10),
            rating = 5
        )

        val oldBooking = ScheduleManager.ServiceBooking(
            id = "booking_old",
            customerUid = "cust_recent",
            serviceType = "General Cleaning",
            status = "Completed",
            startTimeMillis = now - TimeUnit.DAYS.toMillis(200),
            createdAtMillis = now - TimeUnit.DAYS.toMillis(200),
            rating = 5
        )

        val recommendations = engine.getRecommendations(
            customerId = "cust_recent",
            equipmentList = emptyList(),
            bookingHistory = listOf(recentService, oldBooking),
            shopOrders = emptyList(),
            productsCatalog = emptyList(),
            currentTimeMillis = now
        )

        val hasDeepCleanRec = recommendations.any { it.id == "rec_deep_cleaning" }
        assertFalse("Should not recommend deep clean if completed within last 30 days", hasDeepCleanRec)
    }
}
