package com.example.coolconnect

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Enterprise Scheduling Engine for CoolConnect.
 * Encapsulates core rules:
 * 1. Single active job per technician slot.
 * 2. Slot blocked by Pending, Confirmed/Accepted, Ongoing/In Progress, plus Time-Off blocks.
 * 3. 24-hour expiry for Pending requests.
 * 4. Extended block for overdue Ongoing jobs.
 * 5. 30-minute buffers before and after each job for travel & cleanup.
 * 6. Technician working hours (default 8:00 AM - 6:00 PM) and working days.
 * 7. All timestamps handled in UTC millis & formatted in default Asia/Manila / user local timezone.
 */
object ScheduleManager {

    const val DEFAULT_BUFFER_MINUTES = 30
    const val DEFAULT_SERVICE_DURATION_MINUTES = 120
    const val PENDING_EXPIRY_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
    const val DEFAULT_TIMEZONE = "Asia/Manila"

    data class StatusHistoryEntry(
        val status: String = "",
        val updatedBy: String = "",
        val timestampMillis: Long = System.currentTimeMillis(),
        val fromStatus: String = ""
    )

    data class ServiceBooking(
        val id: String = "",
        val customerUid: String = "",
        val customerName: String = "",
        val customerPhone: String = "",
        val address: String = "",
        val technicianUid: String? = null,
        val technicianName: String = "",
        val serviceType: String = "HVAC Service",
        val status: String = "Pending", // Pending, Confirmed, Accepted, In Progress, Completed, Cancelled, Denied, Expired
        val startTimeMillis: Long = 0L,
        val endTimeMillis: Long = 0L,
        val durationMinutes: Int = DEFAULT_SERVICE_DURATION_MINUTES,
        val bufferMinutes: Int = DEFAULT_BUFFER_MINUTES,
        val createdAtMillis: Long = System.currentTimeMillis(),
        val statusHistory: List<StatusHistoryEntry> = emptyList(),
        val rating: Int? = null,
        val comment: String? = null
    )

    data class TimeOffBlock(
        val id: String = "",
        val technicianName: String = "",
        val startTimeMillis: Long = 0L,
        val endTimeMillis: Long = 0L,
        val reason: String = "Personal Time-Off / Maintenance"
    )

    data class TechnicianScheduleConfig(
        val technicianName: String = "",
        val startHour: Int = 8,
        val startMinute: Int = 0,
        val endHour: Int = 18,
        val endMinute: Int = 0,
        val workingDays: List<Int> = listOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY
        ),
        val timeZoneId: String = DEFAULT_TIMEZONE
    )

    data class TimeSlot(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val formattedTimeRange: String,
        val isAvailable: Boolean,
        val blockedReason: String? = null,
        val assignedTechName: String? = null
    )

    data class BlockedInterval(
        val startTimeMillis: Long,
        val endTimeMillis: Long,
        val reason: String,
        val jobId: String? = null
    )

    /**
     * Checks whether a booking is active and blocks schedule.
     */
    fun isBlockingStatus(status: String): Boolean {
        val s = status.lowercase(Locale.ROOT)
        return s == "pending" || s == "confirmed" || s == "accepted" || s == "ongoing" || s == "in progress"
    }

    /**
     * Checks if a pending request has exceeded the 24-hour expiry threshold.
     */
    fun isPendingExpired(createdAtMillis: Long, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
        return (currentTimeMillis - createdAtMillis) >= PENDING_EXPIRY_MILLIS
    }

    /**
     * Calculates all blocked intervals for a technician, accounting for:
     * - Pending (non-expired), Confirmed/Accepted, Ongoing jobs
     * - 30-min buffers before and after
     * - Overdue ongoing job extensions
     * - Time-off / manual blocked periods
     */
    fun calculateBlockedIntervals(
        bookings: List<ServiceBooking>,
        timeOffBlocks: List<TimeOffBlock>,
        technicianName: String?,
        technicianUid: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<BlockedInterval> {
        val intervals = mutableListOf<BlockedInterval>()

        val techBookings = if (!technicianUid.isNullOrBlank()) {
            bookings.filter { it.technicianUid == technicianUid || (!technicianName.isNullOrBlank() && it.technicianName.equals(technicianName, ignoreCase = true)) }
        } else if (technicianName.isNullOrBlank() || technicianName == "NEXT AVAILABLE") {
            bookings
        } else {
            bookings.filter { it.technicianName.equals(technicianName, ignoreCase = true) }
        }

        val bufferMs = DEFAULT_BUFFER_MINUTES * 60 * 1000L

        for (booking in techBookings) {
            val status = booking.status.lowercase(Locale.ROOT)

            if (status == "pending" && isPendingExpired(booking.createdAtMillis, currentTimeMillis)) {
                continue // Released expired slot
            }

            if (!isBlockingStatus(booking.status)) {
                continue // Cancelled / Denied / Completed
            }

            var effStart = booking.startTimeMillis - bufferMs
            var effEnd = booking.endTimeMillis + bufferMs

            // Extended block if ongoing job past estimated end time
            if ((status == "ongoing" || status == "in progress") && currentTimeMillis > booking.endTimeMillis) {
                effEnd = currentTimeMillis + bufferMs
            }

            intervals.add(
                BlockedInterval(
                    startTimeMillis = effStart,
                    endTimeMillis = effEnd,
                    reason = "Booked (${booking.status})",
                    jobId = booking.id
                )
            )
        }

        val techBlocks = if (technicianName.isNullOrBlank() || technicianName == "NEXT AVAILABLE") {
            timeOffBlocks
        } else {
            timeOffBlocks.filter { it.technicianName.equals(technicianName, ignoreCase = true) }
        }

        for (block in techBlocks) {
            intervals.add(
                BlockedInterval(
                    startTimeMillis = block.startTimeMillis,
                    endTimeMillis = block.endTimeMillis,
                    reason = block.reason
                )
            )
        }

        return intervals
    }

    /**
     * Computes daily available slots for a given technician and date.
     */
    fun getDailySlotsForTechnician(
        targetCalendar: Calendar,
        technicianConfig: TechnicianScheduleConfig,
        bookings: List<ServiceBooking>,
        timeOffBlocks: List<TimeOffBlock>,
        serviceDurationMinutes: Int = DEFAULT_SERVICE_DURATION_MINUTES,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): List<TimeSlot> {
        val slots = mutableListOf<TimeSlot>()

        val tz = TimeZone.getTimeZone(technicianConfig.timeZoneId)
        val dayCal = Calendar.getInstance(tz).apply {
            timeInMillis = targetCalendar.timeInMillis
        }

        val dayOfWeek = dayCal.get(Calendar.DAY_OF_WEEK)
        val isWorkingDay = technicianConfig.workingDays.contains(dayOfWeek)

        if (!isWorkingDay) {
            return emptyList()
        }

        val startCal = dayCal.clone() as Calendar
        startCal.set(Calendar.HOUR_OF_DAY, technicianConfig.startHour)
        startCal.set(Calendar.MINUTE, technicianConfig.startMinute)
        startCal.set(Calendar.SECOND, 0)
        startCal.set(Calendar.MILLISECOND, 0)

        val endCal = dayCal.clone() as Calendar
        endCal.set(Calendar.HOUR_OF_DAY, technicianConfig.endHour)
        endCal.set(Calendar.MINUTE, technicianConfig.endMinute)
        endCal.set(Calendar.SECOND, 0)
        endCal.set(Calendar.MILLISECOND, 0)

        val blockedIntervals = calculateBlockedIntervals(
            bookings = bookings,
            timeOffBlocks = timeOffBlocks,
            technicianName = technicianConfig.technicianName,
            currentTimeMillis = currentTimeMillis
        )

        val slotDurationMs = serviceDurationMinutes * 60 * 1000L
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US).apply {
            timeZone = tz
        }

        var cursor = startCal.timeInMillis
        val dayEndTime = endCal.timeInMillis

        while (cursor + slotDurationMs <= dayEndTime) {
            val slotStart = cursor
            val slotEnd = cursor + slotDurationMs

            val isPast = slotStart < currentTimeMillis

            var conflict: BlockedInterval? = null
            if (!isPast) {
                conflict = blockedIntervals.find { block ->
                    slotStart < block.endTimeMillis && slotEnd > block.startTimeMillis
                }
            }

            val isAvailable = !isPast && (conflict == null)
            val rangeStr = "${timeFormat.format(Date(slotStart))} - ${timeFormat.format(Date(slotEnd))}"

            slots.add(
                TimeSlot(
                    startTimeMillis = slotStart,
                    endTimeMillis = slotEnd,
                    formattedTimeRange = rangeStr,
                    isAvailable = isAvailable,
                    blockedReason = when {
                        isPast -> "Past Time"
                        conflict != null -> "Unavailable"
                        else -> null
                    },
                    assignedTechName = technicianConfig.technicianName
                )
            )

            // Increment by slot duration (e.g. 2 hours)
            cursor += slotDurationMs
        }

        return slots
    }

    /**
     * Determines whether a date has at least one open available slot.
     */
    fun isDateAvailable(
        targetCalendar: Calendar,
        allTechConfigs: List<TechnicianScheduleConfig>,
        selectedTechName: String?,
        bookings: List<ServiceBooking>,
        timeOffBlocks: List<TimeOffBlock>,
        serviceDurationMinutes: Int = DEFAULT_SERVICE_DURATION_MINUTES,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val techsToCheck = if (selectedTechName.isNullOrBlank() || selectedTechName == "NEXT AVAILABLE") {
            allTechConfigs
        } else {
            allTechConfigs.filter { it.technicianName.equals(selectedTechName, ignoreCase = true) }
        }

        if (techsToCheck.isEmpty()) {
            // Default fallback tech config if none passed
            val defaultConfig = TechnicianScheduleConfig(technicianName = selectedTechName ?: "Default")
            val slots = getDailySlotsForTechnician(
                targetCalendar, defaultConfig, bookings, timeOffBlocks, serviceDurationMinutes, currentTimeMillis
            )
            return slots.any { it.isAvailable }
        }

        return techsToCheck.any { config ->
            val slots = getDailySlotsForTechnician(
                targetCalendar, config, bookings, timeOffBlocks, serviceDurationMinutes, currentTimeMillis
            )
            slots.any { it.isAvailable }
        }
    }

    /**
     * Re-checks conflict server-side / in-memory prior to committing booking.
     */
    fun hasConflict(
        technicianName: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        bookings: List<ServiceBooking>,
        timeOffBlocks: List<TimeOffBlock>,
        excludeBookingId: String? = null,
        technicianUid: String? = null,
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        val bufferMs = DEFAULT_BUFFER_MINUTES * 60 * 1000L
        val reqStartWithBuffer = startTimeMillis - bufferMs
        val reqEndWithBuffer = endTimeMillis + bufferMs

        val blockedIntervals = calculateBlockedIntervals(
            bookings = bookings.filter { it.id != excludeBookingId },
            timeOffBlocks = timeOffBlocks,
            technicianName = technicianName,
            technicianUid = technicianUid,
            currentTimeMillis = currentTimeMillis
        )

        return blockedIntervals.any { block ->
            reqStartWithBuffer < block.endTimeMillis && reqEndWithBuffer > block.startTimeMillis
        }
    }
}
