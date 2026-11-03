package com.example.coolconnect

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarViewMode { DAY, WEEK, MONTH }

/**
 * Shared color coding helper by job/block status:
 * Pending -> Orange
 * Confirmed / Accepted -> Blue
 * Ongoing / In Progress -> Purple
 * Completed -> Green
 * Blocked / Time-off -> Red
 */
fun getStatusColor(status: String): Color {
    return when (status.lowercase(Locale.ROOT)) {
        "pending" -> Color(0xFFF59E0B) // Orange
        "confirmed", "accepted" -> Color(0xFF3B82F6) // Blue
        "ongoing", "in progress" -> Color(0xFF8B5CF6) // Purple
        "completed" -> Color(0xFF10B981) // Green
        "blocked", "time-off" -> Color(0xFFEF4444) // Red
        "cancelled", "denied", "declined", "expired" -> Color(0xFF6B7280) // Gray
        else -> Color(0xFF3B82F6)
    }
}

/**
 * Technician Interactive Calendar View (Day, Week, Month)
 */
@Composable
fun TechnicianScheduleCalendar(
    techName: String,
    onOpenChat: (String, String) -> Unit = { _, _ -> }
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var viewMode by remember { mutableStateOf(CalendarViewMode.WEEK) }
    val selectedCalendar = remember { mutableStateOf(Calendar.getInstance()) }

    val bookings = remember { mutableStateListOf<ScheduleManager.ServiceBooking>() }
    val timeOffBlocks = remember { mutableStateListOf<ScheduleManager.TimeOffBlock>() }
    var techConfig by remember { mutableStateOf(ScheduleManager.TechnicianScheduleConfig(technicianName = techName)) }

    var showTimeOffDialog by remember { mutableStateOf(false) }
    var showWorkingHoursDialog by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("ALL") }

    // Real-time listener for technician jobs, time off, and config
    DisposableEffect(techName) {
        if (techName.isBlank()) {
            return@DisposableEffect onDispose {}
        }
        val bookingsListener = db.collection("service_requests")
            .addSnapshotListener { snapshot, _ ->
                bookings.clear()
                snapshot?.documents?.forEach { doc ->
                    val assigned = doc.getString("technicianName") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: ""
                    if (assigned.equals(techName, ignoreCase = true)) {
                        val startTime = (doc.get("startTimeMillis") as? Number)?.toLong()
                            ?: (doc.get("scheduledTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                        val duration = (doc.get("durationMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES
                        val endTime = (doc.get("endTimeMillis") as? Number)?.toLong()
                            ?: if (startTime > 0) startTime + (duration * 60 * 1000L) else 0L

                        bookings.add(
                            ScheduleManager.ServiceBooking(
                                id = doc.id,
                                customerUid = doc.getString("customerUid") ?: "",
                                customerName = doc.getString("customerName") ?: "Customer",
                                customerPhone = doc.getString("customerPhone") ?: "No Phone",
                                address = doc.getString("customerAddress") ?: doc.getString("address") ?: "No Address",
                                technicianName = assigned,
                                serviceType = doc.getString("issue") ?: "HVAC Service",
                                status = doc.getString("status") ?: "Pending",
                                startTimeMillis = startTime,
                                endTimeMillis = endTime,
                                createdAtMillis = (doc.get("createdAtMillis") as? Number)?.toLong()
                                    ?: (doc.get("timestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            }

        val timeOffListener = db.collection("time_off_blocks")
            .addSnapshotListener { snapshot, _ ->
                timeOffBlocks.clear()
                snapshot?.documents?.forEach { doc ->
                    val tName = doc.getString("technicianName") ?: ""
                    if (tName.equals(techName, ignoreCase = true)) {
                        timeOffBlocks.add(
                            ScheduleManager.TimeOffBlock(
                                id = doc.id,
                                technicianName = tName,
                                startTimeMillis = (doc.get("startTimeMillis") as? Number)?.toLong() ?: 0L,
                                endTimeMillis = (doc.get("endTimeMillis") as? Number)?.toLong() ?: 0L,
                                reason = doc.getString("reason") ?: "Time Off"
                            )
                        )
                    }
                }
            }

        val configListener = db.collection("technician_configs").document(techName)
            .addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val workingDays = (doc.get("workingDays") as? List<Number>)?.map { it.toInt() }
                        ?: listOf(2, 3, 4, 5, 6, 7)
                    techConfig = ScheduleManager.TechnicianScheduleConfig(
                        technicianName = techName,
                        startHour = (doc.get("startHour") as? Number)?.toInt() ?: 8,
                        startMinute = (doc.get("startMinute") as? Number)?.toInt() ?: 0,
                        endHour = (doc.get("endHour") as? Number)?.toInt() ?: 18,
                        endMinute = (doc.get("endMinute") as? Number)?.toInt() ?: 0,
                        workingDays = workingDays,
                        timeZoneId = doc.getString("timeZoneId") ?: ScheduleManager.DEFAULT_TIMEZONE
                    )
                }
            }

        onDispose {
            bookingsListener.remove()
            timeOffListener.remove()
            configListener.remove()
        }
    }

    if (showTimeOffDialog) {
        AddTimeOffDialog(
            techName = techName,
            onDismiss = { showTimeOffDialog = false }
        )
    }

    if (showWorkingHoursDialog) {
        EditWorkingHoursDialog(
            config = techConfig,
            onDismiss = { showWorkingHoursDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Technician Calendar", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                Text("Working Hours: ${String.format(Locale.US, "%02d:00 - %02d:00", techConfig.startHour, techConfig.endHour)}", fontSize = 11.sp, color = Color.Gray)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { showWorkingHoursDialog = true }) {
                    Icon(Icons.Default.Settings, "Working Hours", tint = PrimaryBlue)
                }
                IconButton(onClick = { showTimeOffDialog = true }) {
                    Icon(Icons.Default.EventBusy, "Add Time Off", tint = Color.Red)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // View Mode Selector (Day, Week, Month)
        Row(
            modifier = Modifier.fillMaxWidth().background(AppLightBg, RoundedCornerShape(12.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            CalendarViewMode.entries.forEach { mode ->
                val isSel = viewMode == mode
                Surface(
                    modifier = Modifier.weight(1f).clickable { viewMode = mode },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) PrimaryBlue else Color.Transparent
                ) {
                    Text(
                        text = mode.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) Color.White else Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status Filter Chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val filters = listOf("ALL", "Pending", "Confirmed", "Ongoing", "Completed", "Blocked")
            items(filters) { f ->
                val isSel = statusFilter == f
                Surface(
                    modifier = Modifier.clickable { statusFilter = f },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSel) PrimaryBlue.copy(0.15f) else AppLightBg,
                    border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color.Transparent)
                ) {
                    Text(
                        text = f,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSel) PrimaryBlue else Color.Gray,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Calendar Content View based on mode
        val filteredBookings = remember(bookings, statusFilter) {
            if (statusFilter == "ALL") bookings
            else bookings.filter { it.status.equals(statusFilter, ignoreCase = true) }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filteredBookings.isEmpty() && timeOffBlocks.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.CalendarToday, null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No jobs or blocked slots found for current filter.", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }
            } else {
                items(timeOffBlocks) { block ->
                    TimeOffBlockCard(block = block, db = db, context = context)
                }

                items(filteredBookings) { booking ->
                    TechnicianJobScheduleCard(
                        booking = booking,
                        techName = techName,
                        db = db,
                        context = context,
                        onOpenChat = onOpenChat
                    )
                }
            }
        }
    }
}

@Composable
fun TechnicianJobScheduleCard(
    booking: ScheduleManager.ServiceBooking,
    techName: String,
    db: FirebaseFirestore,
    context: android.content.Context,
    onOpenChat: (String, String) -> Unit
) {
    val statusColor = getStatusColor(booking.status)
    val timeFormat = remember { SimpleDateFormat("MMM d, yyyy • hh:mm a", Locale.getDefault()) }
    val formattedTime = if (booking.startTimeMillis > 0) timeFormat.format(Date(booking.startTimeMillis)) else "Scheduled"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = booking.status.uppercase(),
                        color = statusColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Text("📅 $formattedTime", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text("Customer: ${booking.customerName}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            Text("Service: ${booking.serviceType}", fontSize = 12.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(booking.address, fontSize = 11.sp, color = Color.Gray)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Phone, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Phone: ${booking.customerPhone}", fontSize = 11.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action buttons for Pending, Confirmed, Ongoing
            val isPending = booking.status.equals("pending", ignoreCase = true)
            val isConfirmed = booking.status.equals("confirmed", ignoreCase = true) || booking.status.equals("accepted", ignoreCase = true)
            val isOngoing = booking.status.equals("ongoing", ignoreCase = true) || booking.status.equals("in progress", ignoreCase = true)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isPending) {
                    Button(
                        onClick = {
                            updateStatus(db, booking.customerUid, booking.id, "Accepted", booking.status, techName)
                            Toast.makeText(context, "Request Accepted!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept Request", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            updateStatus(db, booking.customerUid, booking.id, "Declined", booking.status, techName)
                            Toast.makeText(context, "Request Declined. Slot released.", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Decline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (isConfirmed) {
                    Button(
                        onClick = {
                            updateStatus(db, booking.customerUid, booking.id, "In Progress", booking.status, techName)
                            Toast.makeText(context, "Job Started (Ongoing)", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Start Job (Ongoing)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedButton(
                    onClick = { onOpenChat("tech_${techName}_${booking.customerUid}", booking.customerName) },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Chat, null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Chat", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun updateStatus(
    db: FirebaseFirestore,
    customerUid: String,
    serviceDocId: String,
    newStatus: String,
    fromStatus: String,
    updatedBy: String
) {
    val historyEntry = mapOf(
        "status" to newStatus,
        "updatedBy" to updatedBy,
        "timestampMillis" to System.currentTimeMillis(),
        "fromStatus" to fromStatus
    )

    val updates = mapOf(
        "status" to newStatus,
        "statusHistory" to FieldValue.arrayUnion(historyEntry)
    )

    if (serviceDocId.isNotBlank()) {
        db.collection("service_requests").document(serviceDocId).update(updates)
        db.collection("service_history").document(serviceDocId).set(updates, com.google.firebase.firestore.SetOptions.merge())
    }
}

@Composable
fun TimeOffBlockCard(block: ScheduleManager.TimeOffBlock, db: FirebaseFirestore, context: android.content.Context) {
    val timeFormat = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val startLabel = timeFormat.format(Date(block.startTimeMillis))
    val endLabel = timeFormat.format(Date(block.endTimeMillis))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🚫 TIME-OFF / BLOCKED PERIOD", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Red)
                Text("Reason: ${block.reason}", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Text("$startLabel - $endLabel", fontSize = 11.sp, color = Color.Gray)
            }

            IconButton(
                onClick = {
                    db.collection("time_off_blocks").document(block.id).delete()
                    Toast.makeText(context, "Time off unblocked", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun AddTimeOffDialog(techName: String, onDismiss: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var reason by remember { mutableStateOf("Personal Time Off") }
    val calStart = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1) } }
    val calEnd = remember { Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 5) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Manual Time-Off / Block Slot", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Block periods will prevent customers from booking slots during this window.", fontSize = 12.sp, color = Color.Gray)

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason for Time-Off") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val block = mapOf(
                        "technicianName" to techName,
                        "startTimeMillis" to calStart.timeInMillis,
                        "endTimeMillis" to calEnd.timeInMillis,
                        "reason" to reason.ifBlank { "Time Off" },
                        "createdAtMillis" to System.currentTimeMillis()
                    )
                    db.collection("time_off_blocks").add(block)
                    Toast.makeText(context, "Time-Off Block Added", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Save Time-Off Block", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditWorkingHoursDialog(config: ScheduleManager.TechnicianScheduleConfig, onDismiss: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var startHour by remember { mutableIntStateOf(config.startHour) }
    var endHour by remember { mutableIntStateOf(config.endHour) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Working Hours", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Set your daily available shift hours:", fontSize = 12.sp, color = Color.Gray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Start Hour: $startHour:00", fontWeight = FontWeight.Bold)
                    Slider(
                        value = startHour.toFloat(),
                        onValueChange = { startHour = it.toInt() },
                        valueRange = 6f..12f,
                        steps = 5,
                        modifier = Modifier.width(160.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("End Hour: $endHour:00", fontWeight = FontWeight.Bold)
                    Slider(
                        value = endHour.toFloat(),
                        onValueChange = { endHour = it.toInt() },
                        valueRange = 14f..22f,
                        steps = 7,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doc = mapOf(
                        "technicianName" to config.technicianName,
                        "startHour" to startHour,
                        "startMinute" to 0,
                        "endHour" to endHour,
                        "endMinute" to 0,
                        "workingDays" to config.workingDays,
                        "timeZoneId" to config.timeZoneId
                    )
                    db.collection("technician_configs").document(config.technicianName).set(doc, com.google.firebase.firestore.SetOptions.merge())
                    Toast.makeText(context, "Working hours updated!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Save Hours", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * Admin Combined Schedule Calendar Component
 */
@Composable
fun AdminCombinedScheduleCalendar(technicians: List<Map<String, Any>>) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var selectedTechFilter by remember { mutableStateOf("ALL") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }

    val allBookings = remember { mutableStateListOf<ScheduleManager.ServiceBooking>() }
    val timeOffBlocks = remember { mutableStateListOf<ScheduleManager.TimeOffBlock>() }

    var reassignTargetJob by remember { mutableStateOf<ScheduleManager.ServiceBooking?>(null) }
    var overrideStatusTargetJob by remember { mutableStateOf<ScheduleManager.ServiceBooking?>(null) }

    DisposableEffect(Unit) {
        val bookingsListener = db.collection("service_requests")
            .addSnapshotListener { snapshot, _ ->
                allBookings.clear()
                snapshot?.documents?.forEach { doc ->
                    val startTime = (doc.get("startTimeMillis") as? Number)?.toLong()
                        ?: (doc.get("scheduledTimestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                    val duration = (doc.get("durationMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES
                    val endTime = (doc.get("endTimeMillis") as? Number)?.toLong()
                        ?: if (startTime > 0) startTime + (duration * 60 * 1000L) else 0L

                    allBookings.add(
                        ScheduleManager.ServiceBooking(
                            id = doc.id,
                            customerUid = doc.getString("customerUid") ?: doc.reference.parent.parent?.id?.replace("admin_", "") ?: "",
                            customerName = doc.getString("customerName") ?: "Customer",
                            customerPhone = doc.getString("customerPhone") ?: "No Phone",
                            address = doc.getString("customerAddress") ?: doc.getString("address") ?: "No Address",
                            technicianName = doc.getString("technician") ?: doc.getString("assignedTechnician") ?: "Unassigned",
                            serviceType = doc.getString("issue") ?: "HVAC Service",
                            status = doc.getString("status") ?: "Pending",
                            startTimeMillis = startTime,
                            endTimeMillis = endTime,
                            createdAtMillis = (doc.get("createdAtMillis") as? Number)?.toLong()
                                ?: (doc.get("timestamp") as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    )
                }
            }

        val timeOffListener = db.collection("time_off_blocks")
            .addSnapshotListener { snapshot, _ ->
                timeOffBlocks.clear()
                snapshot?.documents?.forEach { doc ->
                    timeOffBlocks.add(
                        ScheduleManager.TimeOffBlock(
                            id = doc.id,
                            technicianName = doc.getString("technicianName") ?: "",
                            startTimeMillis = (doc.get("startTimeMillis") as? Number)?.toLong() ?: 0L,
                            endTimeMillis = (doc.get("endTimeMillis") as? Number)?.toLong() ?: 0L,
                            reason = doc.getString("reason") ?: "Time Off"
                        )
                    )
                }
            }

        onDispose {
            bookingsListener.remove()
            timeOffListener.remove()
        }
    }

    if (reassignTargetJob != null) {
        AdminReassignJobDialog(
            job = reassignTargetJob!!,
            technicians = technicians,
            allBookings = allBookings,
            timeOffBlocks = timeOffBlocks,
            onDismiss = { reassignTargetJob = null }
        )
    }

    if (overrideStatusTargetJob != null) {
        AdminOverrideStatusDialog(
            job = overrideStatusTargetJob!!,
            onDismiss = { overrideStatusTargetJob = null }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("COMBINED FLEET SCHEDULE CALENDAR", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(10.dp))

        // Filters: Technician & Status
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Tech Filter Chip Row
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(
                        selected = selectedTechFilter == "ALL",
                        onClick = { selectedTechFilter = "ALL" },
                        label = { Text("All Techs") }
                    )
                }
                items(technicians) { tech ->
                    val tName = tech["name"]?.toString() ?: "Tech"
                    FilterChip(
                        selected = selectedTechFilter == tName,
                        onClick = { selectedTechFilter = tName },
                        label = { Text(tName) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val filteredBookings = remember(allBookings, selectedTechFilter, selectedStatusFilter) {
            allBookings.filter { booking ->
                (selectedTechFilter == "ALL" || booking.technicianName.equals(selectedTechFilter, ignoreCase = true)) &&
                        (selectedStatusFilter == "ALL" || booking.status.equals(selectedStatusFilter, ignoreCase = true))
            }
        }

        if (filteredBookings.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No fleet bookings found for selected filter.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredBookings.forEach { job ->
                    val statusColor = getStatusColor(job.status)
                    val timeFormat = remember { SimpleDateFormat("MMM d • hh:mm a", Locale.getDefault()) }
                    val timeStr = if (job.startTimeMillis > 0) timeFormat.format(Date(job.startTimeMillis)) else "Scheduled"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(shape = RoundedCornerShape(10.dp), color = statusColor.copy(0.12f)) {
                                    Text(
                                        job.status.uppercase(),
                                        color = statusColor,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }

                                Text("📅 $timeStr", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("Customer: ${job.customerName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                            Text("Technician: ${job.technicianName}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue)
                            Text("Service: ${job.serviceType}", fontSize = 11.sp, color = Color.DarkGray)

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { reassignTargetJob = job },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reassign Tech", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = { overrideStatusTargetJob = job },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Override Status", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReassignJobDialog(
    job: ScheduleManager.ServiceBooking,
    technicians: List<Map<String, Any>>,
    allBookings: List<ScheduleManager.ServiceBooking>,
    timeOffBlocks: List<ScheduleManager.TimeOffBlock>,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reassign Job to Free Technician", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("Job: ${job.serviceType} for ${job.customerName}", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))

                technicians.forEach { tech ->
                    val tName = tech["name"]?.toString() ?: "Tech"
                    val conflict = ScheduleManager.hasConflict(
                        technicianName = tName,
                        startTimeMillis = job.startTimeMillis,
                        endTimeMillis = job.endTimeMillis,
                        bookings = allBookings,
                        timeOffBlocks = timeOffBlocks,
                        excludeBookingId = job.id
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !conflict) {
                                val historyEntry = mapOf(
                                    "status" to "Reassigned to $tName",
                                    "updatedBy" to "Admin Override",
                                    "timestampMillis" to System.currentTimeMillis(),
                                    "fromStatus" to job.status
                                )

                                if (job.id.isNotBlank()) {
                                    db.collection("service_requests").document(job.id).update(
                                        mapOf(
                                            "technicianName" to tName,
                                            "technician" to tName,
                                            "assignedTechnician" to tName,
                                            "status" to "Accepted",
                                            "statusHistory" to FieldValue.arrayUnion(historyEntry)
                                        )
                                    )
                                }

                                Toast.makeText(context, "Job reassigned to $tName", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (conflict) Color(0xFFF1F5F9) else AppLightBg,
                        border = BorderStroke(1.dp, if (conflict) Color.LightGray else PrimaryBlue)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tName, fontWeight = FontWeight.Bold, color = if (conflict) Color.Gray else TextDark)
                            Text(
                                text = if (conflict) "BUSY (Conflict)" else "AVAILABLE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (conflict) Color.Red else PrimaryGreen
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AdminOverrideStatusDialog(
    job: ScheduleManager.ServiceBooking,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    val statuses = listOf("Pending", "Accepted", "In Progress", "Completed", "Cancelled", "Denied")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Admin Override Status", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select new status for ${job.customerName}'s job:", fontSize = 12.sp, color = Color.Gray)

                statuses.forEach { st ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                updateStatus(
                                    db = db,
                                    customerUid = job.customerUid,
                                    serviceDocId = job.id,
                                    newStatus = st,
                                    fromStatus = job.status,
                                    updatedBy = "Admin Override"
                                )
                                Toast.makeText(context, "Status updated to $st (Audit Logged)", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = AppLightBg,
                        border = BorderStroke(1.dp, FieldBorder)
                    ) {
                        Text(
                            text = st,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp),
                            color = getStatusColor(st)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
