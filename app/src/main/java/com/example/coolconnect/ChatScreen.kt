package com.example.coolconnect

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.net.toUri
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.coolconnect.ui.theme.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullChatScreen(
    chatId: String,
    recipientUserId: String? = null,
    recipientName: String,
    currentUserId: String,
    currentUserName: String,
    isAdmin: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // System Back Gesture / Back Button Handler
    BackHandler(enabled = true) {
        onBack()
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var inputText by remember { mutableStateOf("") }

    // Reset unread count for current user upon opening chat
    LaunchedEffect(chatId, currentUserId) {
        if (chatId.isNotBlank() && currentUserId.isNotBlank()) {
            db.collection("chats_v2").document(chatId).set(
                mapOf(
                    "lastReadAt_$currentUserId" to com.google.firebase.Timestamp.now(),
                    "unreadCount_$currentUserId" to 0L
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
    }

    // Chat Parent Metadata & Recipient State
    var recipientPhone by remember { mutableStateOf("") }
    var recipientPic by remember { mutableStateOf<String?>(null) }
    var isRecipientOnline by remember { mutableStateOf(false) }
    var recipientLastSeen by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var isRecipientTyping by remember { mutableStateOf(false) }
    var assignedTechName by remember { mutableStateOf<String?>(null) }
    var chatStatus by remember { mutableStateOf<String?>(null) }
    var lastRequest by remember { mutableStateOf<String?>(null) }

    // Tech Assignment State
    var showAssignDialog by remember { mutableStateOf(false) }
    val technicians = remember { mutableStateListOf<String>() }
    var selectedTech by remember { mutableStateOf("") }
    val techWorkloads = remember { mutableStateMapOf<String, Int>() }

    // Floating Jump To Bottom State
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                lastVisibleItem != null && lastVisibleItem.index >= totalItems - 1
            }
        }
    }

    // Typing sync to Firestore
    LaunchedEffect(inputText) {
        if (chatId.isNotBlank() && currentUserId.isNotBlank()) {
            val isTyping = inputText.isNotBlank()
            db.collection("chats_v2").document(chatId)
                .update(mapOf("typing_$currentUserId" to isTyping))
                .addOnFailureListener { /* Ignore if doc not created */ }
        }
    }

    // Camera Launcher for In-Chat Snap
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            Toast.makeText(context, "Uploading photo attachment...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                try {
                    val bytes = ImageUtils.compressBitmap(bitmap, maxWidth = 1000, maxHeight = 1000, quality = 80)
                    if (bytes != null) {
                        val localFileUrl = ImageUtils.saveImageToInternalStorage(
                            context = context,
                            bytes = bytes,
                            folderName = "chat_attachments",
                            fileName = "${java.util.UUID.randomUUID()}.jpg"
                        ) ?: throw Exception("Failed to save attachment locally")

                        val photoMsg = hashMapOf(
                            "senderId" to currentUserId,
                            "senderName" to currentUserName,
                            "text" to "📷 Photo Attachment",
                            "imageUrl" to localFileUrl,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "isAdmin" to isAdmin,
                            "status" to "sent"
                        )
                        db.collection("chats_v2").document(chatId).collection("messages").add(photoMsg)
                        db.collection("chats_v2").document(chatId).update(mapOf(
                            "lastMessage" to "📷 Photo Attachment",
                            "timestamp" to com.google.firebase.Timestamp.now()
                        ))
                        Toast.makeText(context, "Photo attachment sent!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to upload photo: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(chatId) {
        // Fetch Parent Chat Metadata & Typing State
        db.collection("chats_v2").document(chatId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                recipientPhone = doc.getString("customerPhone") ?: doc.getString("phone") ?: ""
                assignedTechName = doc.getString("assignedTechnician")
                chatStatus = doc.getString("status")
                lastRequest = doc.getString("lastRequest")

                // Check typing state for recipient
                val data = doc.data
                var typingFound = false
                data?.forEach { (key, value) ->
                    if (key.startsWith("typing_") && key != "typing_$currentUserId" && value == true) {
                        typingFound = true
                    }
                }
                isRecipientTyping = typingFound || doc.getBoolean("isTyping") == true
            }
        }

        // Fetch Recipient User Status (Online, Last Seen, Profile Pic) by ID or fallback by name
        if (!recipientUserId.isNullOrBlank()) {
            db.collection("users").document(recipientUserId).addSnapshotListener { uDoc, _ ->
                if (uDoc != null && uDoc.exists()) {
                    recipientPic = uDoc.getString("profilePicUrl")
                    isRecipientOnline = uDoc.getBoolean("isOnline") ?: false
                    recipientLastSeen = uDoc.getTimestamp("lastSeen")
                }
            }
        } else {
            db.collection("users").whereEqualTo("name", recipientName).limit(1).addSnapshotListener { uSnap, _ ->
                if (uSnap != null && !uSnap.isEmpty) {
                    val uDoc = uSnap.documents[0]
                    recipientPic = uDoc.getString("profilePicUrl")
                    isRecipientOnline = uDoc.getBoolean("isOnline") ?: false
                    recipientLastSeen = uDoc.getTimestamp("lastSeen")
                }
            }
        }

        // Fetch Messages
        db.collection("chats_v2").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                messages.clear()
                snapshot?.documents?.forEach { doc ->
                    messages.add(
                        ChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                            isAdmin = doc.getBoolean("isAdmin") ?: false,
                            imageUrl = doc.getString("imageUrl"),
                            senderProfilePic = doc.getString("senderProfilePic"),
                            status = doc.getString("status") ?: "read"
                        )
                    )
                }

                // Scroll to bottom when new messages arrive
                if (messages.isNotEmpty()) {
                    coroutineScope.launch {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }
            }

        if (isAdmin) {
            db.collection("users").whereEqualTo("role", "technician").get().addOnSuccessListener { snapshot ->
                technicians.clear()
                snapshot.documents.forEach { doc ->
                    val tName = doc.getString("name") ?: "Unknown"
                    technicians.add(tName)

                    db.collection("chats_v2")
                        .whereEqualTo("assignedTechnician", tName)
                        .get().addOnSuccessListener { jobsSnap ->
                            val activeCount = jobsSnap.documents.count {
                                val st = it.getString("status") ?: ""
                                st != "Completed" && st != "Denied" && st != "Request Cancelled"
                            }
                            techWorkloads[tName] = activeCount
                        }
                }
            }
        }
    }

    if (showAssignDialog) {
        AlertDialog(
            onDismissRequest = { showAssignDialog = false },
            title = { Text("Assign Field Technician", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text("Select available technician for dispatch:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(10.dp))

                    technicians.forEach { tech ->
                        val activeJobs = techWorkloads[tech] ?: 0
                        val isAvailable = activeJobs == 0
                        val isSel = selectedTech == tech

                        val workloadLabel = when {
                            activeJobs == 0 -> "Available (0 Jobs)"
                            else -> "Busy ($activeJobs Active Job)"
                        }
                        val workloadColor = when {
                            activeJobs == 0 -> PrimaryGreen
                            else -> Color(0xFFD32F2F)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = isAvailable) { selectedTech = tech },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                !isAvailable -> Color.Gray.copy(0.05f)
                                isSel -> PrimaryBlue.copy(0.12f)
                                else -> AppLightBg
                            },
                            border = BorderStroke(1.dp, if (isSel) PrimaryBlue else if (!isAvailable) Color.Transparent else FieldBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { if (isAvailable) selectedTech = tech },
                                    enabled = isAvailable,
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = tech,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAvailable) TextDark else Color.Gray,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = workloadLabel,
                                        color = if (isAvailable) workloadColor else Color.Gray,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                db.collection("chats_v2").document(chatId).update(mapOf("status" to "Denied"))

                                db.collection("chats_v2").document(chatId).collection("services")
                                    .get().addOnSuccessListener { snapshot ->
                                        snapshot.documents.forEach { doc ->
                                            val st = doc.getString("status")?.lowercase() ?: ""
                                            if (st == "pending") doc.reference.update("status", "denied")
                                        }
                                    }

                                db.collection("chats_v2").document(chatId).collection("messages").add(hashMapOf(
                                    "senderId" to "admin",
                                    "senderName" to "Admin Support",
                                    "text" to "Your request has been denied.",
                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                    "isAdmin" to true,
                                    "status" to "sent"
                                ))
                                showAssignDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Deny", color = Color(0xFFD32F2F), fontSize = 13.sp)
                        }

                        if (!assignedTechName.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    db.collection("chats_v2").document(chatId).update(mapOf(
                                        "assignedTechnician" to com.google.firebase.firestore.FieldValue.delete(),
                                        "status" to "pending"
                                    ))

                                    db.collection("chats_v2").document(chatId).collection("services")
                                        .whereEqualTo("technician", assignedTechName)
                                        .get().addOnSuccessListener { snapshot ->
                                            snapshot.documents.forEach { doc ->
                                                doc.reference.update(mapOf("status" to "pending", "technician" to "NEXT AVAILABLE"))
                                            }
                                        }

                                    db.collection("chats_v2").document(chatId).collection("messages").add(hashMapOf(
                                        "senderId" to "admin",
                                        "senderName" to "Admin Support",
                                        "text" to "Technician assignment has been removed. Re-assigning to next available expert.",
                                        "timestamp" to com.google.firebase.Timestamp.now(),
                                        "isAdmin" to true,
                                        "status" to "sent"
                                    ))
                                    showAssignDialog = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Unassign", color = Color.Gray, fontSize = 13.sp)
                            }
                        }

                        Button(
                            onClick = {
                                if (selectedTech.isNotEmpty()) {
                                    db.collection("chats_v2").document(chatId).update(
                                        mapOf("assignedTechnician" to selectedTech, "status" to "Accepted")
                                    )

                                    db.collection("chats_v2").document(chatId).collection("services")
                                        .get().addOnSuccessListener { snapshot ->
                                            snapshot.documents.forEach { doc ->
                                                val st = doc.getString("status")?.lowercase() ?: ""
                                                if (st != "completed" && st != "cancelled") {
                                                    doc.reference.update(mapOf(
                                                        "status" to "accepted",
                                                        "technician" to selectedTech
                                                    ))
                                                }
                                            }
                                        }

                                    db.collection("chats_v2").document(chatId).collection("messages").add(hashMapOf(
                                        "senderId" to "admin",
                                        "senderName" to "Admin Support",
                                        "text" to "Technician $selectedTech has been assigned and accepted your request.",
                                        "timestamp" to com.google.firebase.Timestamp.now(),
                                        "isAdmin" to true,
                                        "status" to "sent"
                                    ))
                                    showAssignDialog = false
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Assign", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(
                            imageUrl = recipientPic,
                            name = recipientName,
                            modifier = Modifier.size(38.dp),
                            isOnline = isRecipientOnline,
                            showOnlineDot = true
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(recipientName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusText = when {
                                    isRecipientOnline -> "● Online"
                                    recipientLastSeen != null -> "Last seen ${formatLastSeen(recipientLastSeen!!)}"
                                    chatId.startsWith("admin_tech_") -> "Admin Dispatch Line"
                                    chatId.startsWith("admin_") -> "Support Channel"
                                    chatId.startsWith("tech_") -> "Field Service Line"
                                    else -> "Direct Message"
                                }
                                val statusColor = if (isRecipientOnline) PrimaryGreen else Color.Gray

                                Text(
                                    text = statusText,
                                    fontSize = 11.sp,
                                    color = statusColor,
                                    fontWeight = if (isRecipientOnline) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark)
                    }
                },
                actions = {
                    if (isAdmin && chatId.startsWith("admin_")) {
                        Button(
                            onClick = { showAssignDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(0.12f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (assignedTechName.isNullOrBlank()) "Assign Tech" else "Tech: $assignedTechName",
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(onClick = {
                        Toast.makeText(context, "Video channel feature coming soon.", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Videocam, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }

                    IconButton(onClick = {
                        if (recipientPhone.isNotBlank()) {
                            val dialIntent = Intent(Intent.ACTION_DIAL, "tel:$recipientPhone".toUri())
                            context.startActivity(dialIntent)
                        } else {
                            Toast.makeText(context, "No phone number available for this contact.", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Call, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            ChatInputBar(
                value = inputText,
                onValueChange = { inputText = it },
                onCameraSnap = { cameraLauncher.launch() },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val trimmedText = inputText.trim()
                        val msg = hashMapOf(
                            "senderId" to currentUserId,
                            "senderName" to currentUserName,
                            "text" to trimmedText,
                            "timestamp" to com.google.firebase.Timestamp.now(),
                            "isAdmin" to isAdmin,
                            "status" to "sent"
                        )
                        db.collection("chats_v2").document(chatId).collection("messages").add(msg)
                        db.collection("chats_v2").document(chatId).set(
                            mapOf(
                                "lastMessage" to trimmedText,
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "chatName" to (if (isAdmin) recipientName else currentUserName),
                                "typing_$currentUserId" to false
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        inputText = ""
                    }
                }
            )
        },
        containerColor = AppLightBg
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Collapsible Service Request Summary Banner
                if (!lastRequest.isNullOrBlank() || !chatStatus.isNullOrBlank()) {
                    Surface(
                        color = Color.White,
                        shadowElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Service Request: ${lastRequest ?: "HVAC Service"}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark, maxLines = 1)
                                Text("Status: ${chatStatus ?: "In Progress"}", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.Medium)
                            }
                            if (assignedTechName != null) {
                                Surface(shape = RoundedCornerShape(8.dp), color = PrimaryGreen.copy(0.12f)) {
                                    Text("Tech: $assignedTechName", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }

                if (messages.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Start the conversation", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 14.sp)
                            Text("Send a message to begin communicating.", fontSize = 12.sp, color = Color.LightGray)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp)
                    ) {
                        itemsIndexed(
                            items = messages,
                            key = { _, msg -> msg.id.ifBlank { "${msg.timestamp.seconds}_${msg.senderId}_${msg.text.hashCode()}" } }
                        ) { index, msg ->
                            val prevMsg = if (index > 0) messages[index - 1] else null
                            val nextMsg = if (index < messages.size - 1) messages[index + 1] else null

                            val currentDateStr = getDateSeparatorText(msg.timestamp)
                            val prevDateStr = prevMsg?.let { getDateSeparatorText(it.timestamp) }
                            val isNewDay = prevMsg == null || currentDateStr != prevDateStr

                            val isMe = if (recipientName.isNotBlank() && msg.senderName.equals(recipientName, ignoreCase = true)) {
                                false
                            } else if (msg.senderId == currentUserId || (currentUserName.isNotBlank() && msg.senderName.equals(currentUserName, ignoreCase = true))) {
                                true
                            } else if (msg.senderId == "tech" || msg.senderId == "technician") {
                                !isAdmin
                            } else if (isAdmin && (msg.isAdmin || msg.senderId == "admin")) {
                                true
                            } else {
                                false
                            }

                            val isFirstFromSender = prevMsg == null || prevMsg.senderId != msg.senderId || isNewDay
                            val isLastFromSender = nextMsg == null || nextMsg.senderId != msg.senderId || getDateSeparatorText(nextMsg.timestamp) != currentDateStr

                            Column(modifier = Modifier.fillMaxWidth()) {
                                if (isNewDay) {
                                    DateSeparatorText(dateText = currentDateStr)
                                }

                                ChatBubble(
                                    msg = msg,
                                    isMe = isMe,
                                    recipientPic = recipientPic,
                                    isRecipientOnline = isRecipientOnline,
                                    showAvatar = !isMe && isLastFromSender,
                                    showSenderName = !isMe && isFirstFromSender,
                                    topSpacing = if (isFirstFromSender) 6.dp else 2.dp
                                )
                            }
                        }

                        if (isRecipientTyping) {
                            item(key = "typing_indicator") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    UserAvatar(
                                        imageUrl = recipientPic,
                                        name = recipientName,
                                        modifier = Modifier.size(30.dp),
                                        isOnline = isRecipientOnline,
                                        showOnlineDot = true
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Card(
                                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        ChatTypingIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Jump to Latest Floating Button
            if (!isAtBottom && messages.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        },
                    shape = RoundedCornerShape(20.dp),
                    color = PrimaryBlue,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = "Jump to latest",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Jump to latest",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UserAvatar(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    isOnline: Boolean = false,
    showOnlineDot: Boolean = true
) {
    Box(modifier = modifier.size(34.dp)) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBlue.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue,
                    fontSize = 14.sp
                )
            }
        }

        if (showOnlineDot) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.BottomEnd)
                    .background(if (isOnline) PrimaryGreen else Color.Gray, CircleShape)
                    .border(1.5.dp, Color.White, CircleShape)
            )
        }
    }
}

@Composable
fun DateSeparatorText(dateText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE2E8F0)
        ) {
            Text(
                text = dateText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ChatBubble(
    msg: ChatMessage,
    isMe: Boolean,
    recipientPic: String? = null,
    isRecipientOnline: Boolean = false,
    showAvatar: Boolean = true,
    showSenderName: Boolean = true,
    topSpacing: androidx.compose.ui.unit.Dp = 4.dp
) {
    var isExpandedTimestamp by remember { mutableStateOf(false) }

    val bubbleColor = if (isMe) PrimaryBlue else Color.White
    val textColor = if (isMe) Color.White else TextDark
    val arrangement = if (isMe) Arrangement.End else Arrangement.Start

    val isPhotoMsg = msg.text.contains("📷")
    val isSystemMsg = msg.senderId == "admin" && (msg.text.contains("assigned") || msg.text.contains("accepted") || msg.text.contains("denied"))

    val shortTime = remember(msg.timestamp) {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(msg.timestamp.toDate())
    }

    val fullTimestampStr = remember(msg.timestamp) {
        SimpleDateFormat("EEE, MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(msg.timestamp.toDate())
    }

    if (isSystemMsg) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryBlue.copy(0.08f),
                border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
            ) {
                Text(
                    text = msg.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryBlue,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        return
    }

    val shape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topSpacing),
        horizontalArrangement = arrangement,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            if (showAvatar) {
                UserAvatar(
                    imageUrl = msg.senderProfilePic ?: recipientPic,
                    name = msg.senderName,
                    modifier = Modifier.size(30.dp),
                    isOnline = isRecipientOnline,
                    showOnlineDot = true
                )
            } else {
                Spacer(modifier = Modifier.width(30.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
        }

        Card(
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isMe) 2.dp else 1.dp),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .clickable { isExpandedTimestamp = !isExpandedTimestamp }
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMe && showSenderName) {
                    Text(
                        text = msg.senderName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = PrimaryBlue
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                if (!msg.imageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(0.05f))
                    ) {
                        AsyncImage(
                            model = msg.imageUrl,
                            contentDescription = "Photo Attachment",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (isPhotoMsg) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = if (isMe) Color.White else PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(msg.text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(msg.text, color = textColor, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (isExpandedTimestamp) fullTimestampStr else shortTime,
                        fontSize = 9.sp,
                        color = if (isMe) Color.White.copy(0.75f) else Color.Gray
                    )

                    if (isMe) {
                        val (receiptIcon, receiptColor) = when (msg.status.lowercase()) {
                            "read" -> Icons.Default.DoneAll to Color(0xFF90CAF9)
                            "delivered" -> Icons.Default.DoneAll to Color.White.copy(0.75f)
                            else -> Icons.Default.Check to Color.White.copy(0.75f)
                        }
                        Icon(
                            imageVector = receiptIcon,
                            contentDescription = msg.status,
                            tint = receiptColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatTypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_dots")

    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot1"
    )

    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dot1Alpha }
                .background(PrimaryBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dot2Alpha }
                .background(PrimaryBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .graphicsLayer { alpha = dot3Alpha }
                .background(PrimaryBlue, CircleShape)
        )
    }
}

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onCameraSnap: () -> Unit,
    onSend: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = onCameraSnap,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(Icons.Default.CameraAlt, "Snap Photo", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Type a message...", fontSize = 14.sp, color = Color.Gray) },
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                            if (!keyEvent.isShiftPressed && value.isNotBlank()) {
                                onSend()
                                true
                            } else false
                        } else false
                    },
                minLines = 1,
                maxLines = 5,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppLightBg,
                    unfocusedContainerColor = AppLightBg,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            val isEnabled = value.isNotBlank()
            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                modifier = Modifier
                    .padding(bottom = 2.dp)
                    .size(42.dp)
                    .background(if (isEnabled) PrimaryBlue else Color(0xFFCBD5E1), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun getDateSeparatorText(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val cal = Calendar.getInstance().apply { time = date }
    val now = Calendar.getInstance()

    val isToday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
            yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

    return when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(date)
    }
}

fun formatLastSeen(timestamp: com.google.firebase.Timestamp): String {
    val date = timestamp.toDate()
    val diffMillis = System.currentTimeMillis() - date.time
    val diffMins = diffMillis / (1000 * 60)
    val diffHours = diffMins / 60

    return when {
        diffMins < 1 -> "just now"
        diffMins < 60 -> "${diffMins}m ago"
        diffHours < 24 -> "${diffHours}h ago"
        else -> SimpleDateFormat("MMM d 'at' h:mm a", Locale.getDefault()).format(date)
    }
}

data class ConversationItem(
    val chatId: String,
    val recipientId: String? = null,
    val recipientName: String,
    val recipientProfilePic: String? = null,
    val recipientRole: String = "",
    val isOnline: Boolean = false,
    val lastMessage: String = "",
    val timestamp: com.google.firebase.Timestamp? = null,
    val unreadCount: Int = 0,
    val status: String? = null,
    val assignedTech: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    currentUserId: String,
    currentUserName: String,
    userRole: String, // "customer", "technician", "admin"
    onChatSelected: (chatId: String, recipientUserId: String?, recipientName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var searchQuery by remember { mutableStateOf("") }
    var adminFilterTab by remember { mutableStateOf("Customers") } // "Customers" or "Technicians"

    val rawConversations = remember { mutableStateListOf<ConversationItem>() }
    val userMap = remember { mutableStateMapOf<String, Map<String, Any>>() } // uid -> userData
    val userByName = remember { mutableStateMapOf<String, Map<String, Any>>() } // lowercaseName -> userData

    // Listen to users collection for avatars and online status without N+1 queries
    DisposableEffect(Unit) {
        val usersListener = db.collection("users").addSnapshotListener { uSnap, _ ->
            uSnap?.documents?.forEach { doc ->
                val data = doc.data ?: return@forEach
                val uid = doc.id
                val name = data["name"]?.toString() ?: ""
                userMap[uid] = data
                if (name.isNotBlank()) {
                    userByName[name.lowercase(Locale.getDefault())] = data
                }
            }
        }
        onDispose { usersListener.remove() }
    }

    // Real-time snapshot listener for role-based conversation list
    DisposableEffect(currentUserId, userRole, adminFilterTab) {
        val chatListener = db.collection("chats_v2").addSnapshotListener { snapshot, _ ->
            if (snapshot == null) return@addSnapshotListener
            val newItems = mutableListOf<ConversationItem>()

            snapshot.documents.forEach { doc ->
                val chatId = doc.id
                val lastMsg = doc.getString("lastMessage") ?: ""
                val ts = doc.getTimestamp("timestamp")
                val status = doc.getString("status")
                val assignedTech = doc.getString("assignedTechnician")
                val customerUid = doc.getString("customerUid") ?: doc.getString("uid") ?: ""
                val customerName = doc.getString("customerName") ?: doc.getString("chatName") ?: "Customer"
                val participantIds = doc.get("participantIds") as? List<*> ?: emptyList<Any>()

                // Calculate unread count
                val unreadCountFromDoc = doc.getLong("unreadCount_$currentUserId")?.toInt()
                val lastReadTs = doc.getTimestamp("lastReadAt_$currentUserId")
                val unreadCount = unreadCountFromDoc ?: run {
                    if (ts != null && lastReadTs != null && ts.seconds > lastReadReadSeconds(lastReadTs)) 1 else 0
                }

                when (userRole.lowercase(Locale.getDefault())) {
                    "customer" -> {
                        // Include admin support channel for this customer
                        if (chatId == "admin_$currentUserId" || (customerUid == currentUserId && chatId.startsWith("admin_"))) {
                            val adminUser = userByName["admin support"] ?: userMap.values.firstOrNull { it["role"] == "admin" }
                            val adminUid = adminUser?.get("uid")?.toString() ?: "admin"
                            val adminPic = adminUser?.get("profilePicUrl")?.toString()
                            val isOnline = adminUser?.get("isOnline") as? Boolean ?: false

                            newItems.add(
                                ConversationItem(
                                    chatId = chatId,
                                    recipientId = adminUid,
                                    recipientName = "Admin Support",
                                    recipientProfilePic = adminPic,
                                    recipientRole = "admin",
                                    isOnline = isOnline,
                                    lastMessage = lastMsg.ifBlank { "Support Channel & Dispatch Line" },
                                    timestamp = ts,
                                    unreadCount = unreadCount,
                                    status = status,
                                    assignedTech = assignedTech
                                )
                            )
                        }
                        // Include technician direct line if customer is participant or tech assigned
                        else if (chatId.startsWith("tech_") && (chatId.endsWith("_$currentUserId") || participantIds.contains(currentUserId))) {
                            val techNameInChat = if (chatId.split("_").size >= 2) chatId.split("_")[1] else "Technician"
                            val techUser = userByName[techNameInChat.lowercase(Locale.getDefault())]
                            val techUid = techUser?.get("uid")?.toString()
                            val techPic = techUser?.get("profilePicUrl")?.toString()
                            val isOnline = techUser?.get("isOnline") as? Boolean ?: false

                            newItems.add(
                                ConversationItem(
                                    chatId = chatId,
                                    recipientId = techUid,
                                    recipientName = techNameInChat,
                                    recipientProfilePic = techPic,
                                    recipientRole = "technician",
                                    isOnline = isOnline,
                                    lastMessage = lastMsg.ifBlank { "Field Service Chat Line" },
                                    timestamp = ts,
                                    unreadCount = unreadCount,
                                    status = status,
                                    assignedTech = assignedTech
                                )
                            )
                        }
                    }

                    "technician" -> {
                        // Include Admin Dispatch line for this technician
                        val techName = currentUserName
                        if (chatId == "admin_tech_$techName" || chatId == "admin_tech_$currentUserId") {
                            val adminUser = userMap.values.firstOrNull { it["role"] == "admin" }
                            val adminUid = adminUser?.get("uid")?.toString() ?: "admin"
                            val adminPic = adminUser?.get("profilePicUrl")?.toString()
                            val isOnline = adminUser?.get("isOnline") as? Boolean ?: false

                            newItems.add(
                                ConversationItem(
                                    chatId = chatId,
                                    recipientId = adminUid,
                                    recipientName = "Admin Dispatch Support",
                                    recipientProfilePic = adminPic,
                                    recipientRole = "admin",
                                    isOnline = isOnline,
                                    lastMessage = lastMsg.ifBlank { "Admin Operations & Job Dispatch" },
                                    timestamp = ts,
                                    unreadCount = unreadCount,
                                    status = status
                                )
                            )
                        }
                        // Include assigned customer chats
                        else if (assignedTech == techName || participantIds.contains(currentUserId) || chatId.startsWith("tech_${techName}_")) {
                            val cUser = userMap[customerUid] ?: userByName[customerName.lowercase(Locale.getDefault())]
                            val cPic = cUser?.get("profilePicUrl")?.toString()
                            val isOnline = cUser?.get("isOnline") as? Boolean ?: false

                            newItems.add(
                                ConversationItem(
                                    chatId = chatId,
                                    recipientId = customerUid.ifBlank { cUser?.get("uid")?.toString() },
                                    recipientName = customerName.ifBlank { "Customer" },
                                    recipientProfilePic = cPic,
                                    recipientRole = "customer",
                                    isOnline = isOnline,
                                    lastMessage = lastMsg.ifBlank { "Assigned Field Service" },
                                    timestamp = ts,
                                    unreadCount = unreadCount,
                                    status = status,
                                    assignedTech = assignedTech
                                )
                            )
                        }
                    }

                    "admin" -> {
                        // Only show active inquiries/conversations with at least 1 message or inquiry
                        if (lastMsg.isNotBlank() || ts != null) {
                            if (adminFilterTab == "Customers") {
                                if (chatId.startsWith("admin_") && !chatId.startsWith("admin_tech_") && chatId != "admin_$currentUserId") {
                                    val cUser = userMap[customerUid] ?: userByName[customerName.lowercase(Locale.getDefault())]
                                    val cPic = cUser?.get("profilePicUrl")?.toString()
                                    val isOnline = cUser?.get("isOnline") as? Boolean ?: false

                                    newItems.add(
                                        ConversationItem(
                                            chatId = chatId,
                                            recipientId = customerUid.ifBlank { cUser?.get("uid")?.toString() },
                                            recipientName = customerName.ifBlank { "Customer Inquiry" },
                                            recipientProfilePic = cPic,
                                            recipientRole = "customer",
                                            isOnline = isOnline,
                                            lastMessage = lastMsg,
                                            timestamp = ts,
                                            unreadCount = unreadCount,
                                            status = status,
                                            assignedTech = assignedTech
                                        )
                                    )
                                }
                            } else {
                                // "Technicians" tab
                                if (chatId.startsWith("admin_tech_")) {
                                    val tName = chatId.removePrefix("admin_tech_")
                                    val tUser = userByName[tName.lowercase(Locale.getDefault())]
                                    val tPic = tUser?.get("profilePicUrl")?.toString()
                                    val isOnline = tUser?.get("isOnline") as? Boolean ?: false

                                    newItems.add(
                                        ConversationItem(
                                            chatId = chatId,
                                            recipientId = tUser?.get("uid")?.toString(),
                                            recipientName = tName,
                                            recipientProfilePic = tPic,
                                            recipientRole = "technician",
                                            isOnline = isOnline,
                                            lastMessage = lastMsg,
                                            timestamp = ts,
                                            unreadCount = unreadCount,
                                            status = status
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            rawConversations.clear()
            rawConversations.addAll(newItems.distinctBy { it.chatId })
        }

        onDispose { chatListener.remove() }
    }

    // Filter by search query & sort by timestamp descending
    val filteredConversations by remember {
        derivedStateOf {
            val list = rawConversations.filter { item ->
                searchQuery.isBlank() ||
                        item.recipientName.contains(searchQuery, ignoreCase = true) ||
                        item.lastMessage.contains(searchQuery, ignoreCase = true)
            }
            list.sortedWith(compareByDescending { it.timestamp?.seconds ?: 0L })
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Text(
            text = "Messages",
            style = MaterialTheme.typography.headlineMedium,
            color = TextDark,
            fontWeight = FontWeight.Black
        )
        Text(
            text = when (userRole.lowercase(Locale.getDefault())) {
                "admin" -> "Operations, Customer Inquiries & Dispatch Lines"
                "technician" -> "Field Support & Assigned Customer Messages"
                else -> "Direct Support & Service Technician Channels"
            },
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search conversations by name...", fontSize = 13.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, "Clear search", tint = Color.Gray)
                    }
                }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = FieldBorder
            )
        )

        // Admin Filter Tabs ("Customers" vs "Technicians")
        if (userRole.equals("admin", ignoreCase = true)) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(AppLightBg, RoundedCornerShape(20.dp))
                    .padding(3.dp)
            ) {
                listOf("Customers", "Technicians").forEach { tab ->
                    val isSel = adminFilterTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isSel) Color.White else Color.Transparent, RoundedCornerShape(18.dp))
                            .clickable { adminFilterTab = tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isSel) PrimaryBlue else Color.Gray
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Empty state or Conversation list
        if (filteredConversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Icon(
                        imageVector = if (userRole.equals("technician", true)) Icons.Default.Engineering else Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "No matching conversations found"
                            userRole.equals("customer", true) -> "No active technician conversations"
                            userRole.equals("technician", true) -> "No assigned customer chats"
                            adminFilterTab == "Customers" -> "No customer inquiries found"
                            else -> "No technician messages found"
                        },
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = when {
                            searchQuery.isNotBlank() -> "Try searching with a different name or message phrase."
                            userRole.equals("customer", true) -> "Once a field technician is assigned to your HVAC request, your direct communication channel will appear here."
                            userRole.equals("technician", true) -> "When you are assigned to an active field service job, customer direct messaging will automatically show up here."
                            adminFilterTab == "Customers" -> "Active customer support chats and inquiries will appear here when messages are sent."
                            else -> "Technician dispatch conversations and updates will be listed here."
                        },
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = filteredConversations,
                    key = { it.chatId }
                ) { item ->
                    ConversationRowItem(
                        item = item,
                        onClick = { onChatSelected(item.chatId, item.recipientId, item.recipientName) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationRowItem(
    item: ConversationItem,
    onClick: () -> Unit
) {
    val formattedTime = remember(item.timestamp) {
        if (item.timestamp == null) ""
        else {
            val date = item.timestamp.toDate()
            val now = Calendar.getInstance()
            val cal = Calendar.getInstance().apply { time = date }
            val isToday = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val isYesterday = yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)

            when {
                isToday -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                isYesterday -> "Yesterday"
                else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(
                imageUrl = item.recipientProfilePic,
                name = item.recipientName,
                modifier = Modifier.size(46.dp),
                isOnline = item.isOnline,
                showOnlineDot = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.recipientName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextDark
                    )

                    if (formattedTime.isNotBlank()) {
                        Text(
                            text = formattedTime,
                            fontSize = 11.sp,
                            color = if (item.unreadCount > 0) PrimaryBlue else Color.Gray,
                            fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.lastMessage,
                        fontSize = 12.sp,
                        color = if (item.unreadCount > 0) TextDark else Color.Gray,
                        fontWeight = if (item.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }

                if (!item.assignedTech.isNullOrBlank() || !item.status.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (!item.assignedTech.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryGreen.copy(0.1f)
                            ) {
                                Text(
                                    text = "Tech: ${item.assignedTech}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryGreen,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (!item.status.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryBlue.copy(0.1f)
                            ) {
                                Text(
                                    text = item.status,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (item.unreadCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun lastReadReadSeconds(timestamp: com.google.firebase.Timestamp?): Long {
    return timestamp?.seconds ?: 0L
}

