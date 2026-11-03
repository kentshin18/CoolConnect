package com.example.coolconnect

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.min

enum class RecommendationType {
    SERVICE,
    PRODUCT,
    BOOKABLE_ACTION
}

data class Recommendation(
    val id: String,
    val title: String,
    val reason: String, // "Why this?" explanation
    val callToAction: String, // e.g. "Book Now", "View in Shop", "Schedule Checkup"
    val type: RecommendationType,
    val price: Double? = null,
    val formattedPrice: String? = null,
    val badge: String? = null,
    val targetId: String? = null, // Document ID, Product ID, or Action Route
    val score: Double = 0.0,
)

data class CustomerEquipment(
    val id: String = "",
    val unitType: String = "Central AC", // e.g., "Central AC", "Split-Type AC", "Heat Pump", "Furnace", "Window AC"
    val brand: String = "",
    val model: String = "",
    val installDateMillis: Long? = null,
    val lastServiceDateMillis: Long? = null,
    val propertyType: String = "Home", // "Home" or "Commercial"
    val filterSize: String? = null,
)

data class OnboardingAnswers(
    val propertyType: String = "Home",
    val numUnits: Int = 1,
    val systemAgeYears: Int? = null,
    val lastServiceDateMillis: Long? = null,
    val zipOrCity: String? = null,
)

class RecommendationEngine {

    companion object {
        const val RECENT_SERVICE_EXCLUSION_DAYS = 30L
        const val UPSELL_SUPPRESSION_DAYS = 30L
        const val FILTER_INTERVAL_DAYS = 90L
        const val TUNEUP_INTERVAL_DAYS = 180L // 6 months
    }

    /**
     * Main entry point to rank and generate recommendations for a given customer.
     */
    fun getRecommendations(
        customerId: String,
        onboarding: OnboardingAnswers? = null,
        equipmentList: List<CustomerEquipment> = emptyList(),
        bookingHistory: List<ScheduleManager.ServiceBooking> = emptyList(),
        shopOrders: List<ShopOrder> = emptyList(),
        productsCatalog: List<Product> = emptyList(),
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<Recommendation> {
        val candidates = mutableListOf<Recommendation>()

        // 1. Determine if customer has enough data threshold
        val completedBookings = bookingHistory.filter { 
            it.status.equals("Completed", ignoreCase = true) || it.status.equals("Done", ignoreCase = true) 
        }
        val hasEnoughData = completedBookings.isNotEmpty() || shopOrders.isNotEmpty() || equipmentList.isNotEmpty()

        // 2. Check for recent poor rating or complaint (Rating <= 2 stars in last 30 days)
        val recentPoorService = bookingHistory.find { booking ->
            val isCompletedOrRated = booking.status.equals("Completed", ignoreCase = true) || booking.status.equals("Done", ignoreCase = true)
            val isRecent = (currentTimeMillis - booking.createdAtMillis) <= TimeUnit.DAYS.toMillis(UPSELL_SUPPRESSION_DAYS)
            isCompletedOrRated && isRecent && (booking.rating != null && booking.rating <= 2)
        }

        if (recentPoorService != null) {
            // Priority recommendation: Satisfaction Follow-up / Free Inspection
            candidates.add(
                Recommendation(
                    id = "courtesy_checkup",
                    title = "Dedicated Customer Care & Quality Check",
                    reason = "We noticed your recent service for customer $customerId didn't meet 5-star standards. Our management team wants to make things right with a complimentary system review.",
                    callToAction = "Contact Support",
                    type = RecommendationType.BOOKABLE_ACTION,
                    badge = "VIP CARE",
                    targetId = "support_screen",
                    score = 100.0
                )
            )
            return candidates
        }

        // 3. Evaluate Personalization Rules for Customers with History
        if (hasEnoughData) {
            // Rule A: Time since last maintenance / service
            val lastCompletedService = completedBookings.maxByOrNull { it.startTimeMillis.takeIf { t -> t > 0 } ?: it.createdAtMillis }
            val lastServiceTime = lastCompletedService?.startTimeMillis?.takeIf { it > 0 }
                ?: lastCompletedService?.createdAtMillis
                ?: equipmentList.mapNotNull { it.lastServiceDateMillis }.maxOrNull()

            if (lastServiceTime != null) {
                val daysSinceLastService = TimeUnit.MILLISECONDS.toDays(currentTimeMillis - lastServiceTime)
                if (daysSinceLastService >= TUNEUP_INTERVAL_DAYS) {
                    val eqName = equipmentList.firstOrNull()?.let { "${it.brand} ${it.unitType}".trim() } ?: "HVAC System"
                    candidates.add(
                        Recommendation(
                            id = "rec_tuneup_due",
                            title = "Schedule Bi-Annual AC Performance Tune-Up",
                            reason = "Your $eqName was last serviced $daysSinceLastService days ago. Regular maintenance prevents costly breakdowns and preserves energy efficiency.",
                            callToAction = "Book Maintenance",
                            type = RecommendationType.SERVICE,
                            price = 1500.0,
                            formattedPrice = "₱1,500.00",
                            badge = "DUE FOR SERVICE",
                            targetId = "tuneup_service",
                            score = 85.0 + min(daysSinceLastService / 10.0, 15.0)
                        )
                    )
                }
            }

            // Rule B: Air Filter Replacement Interval (90 days)
            val lastFilterOrder = shopOrders.flatMap { order ->
                order.items.mapNotNull { item ->
                    val name = item["name"]?.toString() ?: ""
                    if (name.contains("filter", ignoreCase = true)) order.timestamp.toDate().time else null
                }
            }.maxOrNull()

            val filterServiceDate = bookingHistory.find { it.serviceType.contains("filter", ignoreCase = true) }?.createdAtMillis
            val lastFilterChange = maxOf(lastFilterOrder ?: 0L, filterServiceDate ?: 0L, equipmentList.mapNotNull { it.lastServiceDateMillis }.maxOrNull() ?: 0L)

            val daysSinceFilter = if (lastFilterChange > 0) TimeUnit.MILLISECONDS.toDays(currentTimeMillis - lastFilterChange) else 100L
            if (daysSinceFilter >= FILTER_INTERVAL_DAYS) {
                val matchingFilter = productsCatalog.find { it.name.contains("filter", ignoreCase = true) && it.stock > 0 }
                val targetIdStr = matchingFilter?.docId?.ifEmpty { matchingFilter.id.toString() }
                    ?: matchingFilter?.id?.toString()
                    ?: "shop"
                candidates.add(
                    Recommendation(
                        id = "rec_filter_replacement",
                        title = "Replace Air Filters for Clean Indoor Air",
                        reason = if (lastFilterChange > 0) "Filters are recommended every 3 months for your unit. It's been $daysSinceFilter days since your last replacement." else "Clean air filters improve airflow, lower power bills, and extend compressor life.",
                        callToAction = "Order Filters",
                        type = RecommendationType.PRODUCT,
                        price = matchingFilter?.price?.replace("₱", "")?.replace(",", "")?.toDoubleOrNull() ?: 450.0,
                        formattedPrice = matchingFilter?.price ?: "₱450.00",
                        badge = "3-MONTH ITEM",
                        targetId = targetIdStr,
                        score = 80.0
                    )
                )
            }

            // Rule C: Aging Equipment Upgrade / Inspection (Age > 8 years)
            val oldestEquipment = equipmentList.find { eq ->
                eq.installDateMillis != null && (currentTimeMillis - eq.installDateMillis) >= TimeUnit.DAYS.toMillis(365 * 8L)
            }
            if (oldestEquipment != null) {
                val ageYears = TimeUnit.MILLISECONDS.toDays(currentTimeMillis - (oldestEquipment.installDateMillis ?: 0L)) / 365
                candidates.add(
                    Recommendation(
                        id = "rec_smart_thermostat",
                        title = "Smart Thermostat & Inverter Upgrade Assessment",
                        reason = "Your ${oldestEquipment.brand} ${oldestEquipment.unitType} is ~$ageYears years old. Upgrading to an eco-inverter system or smart thermostat can cut electricity costs up to 30%.",
                        callToAction = "View Energy Savers",
                        type = RecommendationType.PRODUCT,
                        price = 3500.0,
                        formattedPrice = "₱3,500.00",
                        badge = "ENERGY SAVER",
                        targetId = "shop_thermostat",
                        score = 75.0
                    )
                )
            }

            // Rule D: Duct Cleaning / Deep Coil Cleaning trigger
            val hasHadDeepClean = bookingHistory.any { 
                it.serviceType.contains("deep", ignoreCase = true) || it.serviceType.contains("duct", ignoreCase = true) 
            }
            if (!hasHadDeepClean && completedBookings.size >= 2) {
                candidates.add(
                    Recommendation(
                        id = "rec_deep_cleaning",
                        title = "Deep Chemical Coil & Duct Sanitization",
                        reason = "Recommended once a year to eliminate mold, bacteria, and dust buildup deep inside your evaporator coils.",
                        callToAction = "Book Deep Clean",
                        type = RecommendationType.SERVICE,
                        price = 2200.0,
                        formattedPrice = "₱2,200.00",
                        badge = "HIGHLY RECOMMENDED",
                        targetId = "deep_clean_service",
                        score = 70.0
                    )
                )
            }
        }

        // 4. Seasonal Heuristics (Always applied if climate/month dictates)
        val cal = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
        val month = cal[Calendar.MONTH] // 0-based: 0 = Jan, 2 = March, 4 = May, 8 = Sept, 10 = Nov

        // Philippines / Tropical Summer Peak (March to May)
        if (month in Calendar.MARCH..Calendar.MAY) {
            candidates.add(
                Recommendation(
                    id = "rec_seasonal_summer",
                    title = "Summer Pre-Cooling Audit & AC Pressure Check",
                    reason = "Summer peak heat puts maximum load on your cooling coils. Ensure zero refrigerant leaks and optimal airflow before peak temperatures hit.",
                    callToAction = "Book Summer Audit",
                    type = RecommendationType.BOOKABLE_ACTION,
                    price = 990.0,
                    formattedPrice = "₱990.00",
                    badge = "SEASONAL TOP PICK",
                    targetId = "summer_tuneup",
                    score = 78.0
                )
            )
        } else if (month in Calendar.SEPTEMBER..Calendar.NOVEMBER) {
            // Monsoon Season (Sept to Nov)
            candidates.add(
                Recommendation(
                    id = "rec_seasonal_monsoon",
                    title = "Monsoon Moisture & Mold Prevention Treatment",
                    reason = "High humidity during rainy months increases bacterial growth in drain pans. Book a drain line flush and antimicrobial spray.",
                    callToAction = "Book Moisture Check",
                    type = RecommendationType.SERVICE,
                    price = 1200.0,
                    formattedPrice = "₱1,200.00",
                    badge = "HUMIDITY CARE",
                    targetId = "monsoon_check",
                    score = 76.0
                )
            )
        }

        // 5. Fallback Recommendations for New Customers or Low Data
        if (!hasEnoughData || candidates.isEmpty()) {
            val propType = onboarding?.propertyType ?: "Home"
            val units = onboarding?.numUnits ?: 1

            candidates.add(
                Recommendation(
                    id = "rec_fallback_inspection",
                    title = "Comprehensive Multi-Point System Health Check",
                    reason = "Ideal first step for $propType owners ($units unit(s)). Our certified technician will test electrical draw, pressure levels, and blower balance.",
                    callToAction = "Book System Inspection",
                    type = RecommendationType.SERVICE,
                    price = 850.0,
                    formattedPrice = "₱850.00",
                    badge = "BEST FOR NEW USERS",
                    targetId = "inspection_service",
                    score = 90.0
                )
            )

            // Popular shop item fallback (In-stock product)
            val topProduct = productsCatalog.firstOrNull { it.stock > 0 }
            val fallbackTargetId = topProduct?.docId?.ifEmpty { topProduct.id.toString() }
                ?: topProduct?.id?.toString()
                ?: "shop"

            candidates.add(
                Recommendation(
                    id = "rec_fallback_product",
                    title = topProduct?.name ?: "Universal High-Efficiency AC Air Filter",
                    reason = "Keep your indoor air fresh and protect your unit from dust buildup with our top-rated essential maintenance gear.",
                    callToAction = "Explore Shop",
                    type = RecommendationType.PRODUCT,
                    price = topProduct?.price?.replace("₱", "")?.replace(",", "")?.toDoubleOrNull() ?: 450.0,
                    formattedPrice = topProduct?.price ?: "₱450.00",
                    badge = "POPULAR IN YOUR AREA",
                    targetId = fallbackTargetId,
                    score = 65.0
                )
            )
        }

        // 6. Filtering & Edge Case Handling:
        // - Exclude out-of-stock products
        // - Deduplicate recommendations by ID/title
        // - Exclude services completed within the last 30 days
        val recentServiceTypes = completedBookings
            .filter { (currentTimeMillis - (it.startTimeMillis.takeIf { t -> t > 0 } ?: it.createdAtMillis)) <= TimeUnit.DAYS.toMillis(RECENT_SERVICE_EXCLUSION_DAYS) }
            .map { it.serviceType.lowercase() }

        val filtered = candidates.distinctBy { it.id }
            .filter { rec ->
                // Check out-of-stock if product
                if (rec.type == RecommendationType.PRODUCT && rec.targetId != null) {
                    val prod = productsCatalog.find { it.docId == rec.targetId || it.id.toString() == rec.targetId }
                    if (prod != null && prod.stock <= 0) return@filter false
                }
                // Check recent service duplication
                val isDuplicateRecent = recentServiceTypes.any { recent -> rec.title.lowercase().contains(recent) }
                !isDuplicateRecent
            }

        // Return ranked recommendations sorted by score descending
        return filtered.sortedByDescending { it.score }
    }
}
