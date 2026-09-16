package com.example.coolconnect

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullChatScreen(
    chatId: String,
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

    // Chat Parent Metadata State
    var recipientPhone by remember { mutableStateOf("") }
    var recipientPic by remember { mutableStateOf<String?>(null) }
    var assignedTechName by remember { mutableStateOf<String?>(null) }
    var chatStatus by remember { mutableStateOf<String?>(null) }
    var lastRequest by remember { mutableStateOf<String?>(null) }

    // Tech Assignment State
    var showAssignDialog by remember { mutableStateOf(false) }
    val technicians = remember { mutableStateListOf<String>() }
    var selectedTech by remember { mutableStateOf("") }
    val techWorkloads = remember { mutableStateMapOf<String, Int>() }

    // Camera Launcher for In-Chat Snap
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val photoMsg = hashMapOf(
                "senderId" to currentUserId,
                "senderName" to currentUserName,
                "text" to "📷 [Photo Attachment Sent]",
                "timestamp" to com.google.firebase.Timestamp.now(),
                "isAdmin" to isAdmin
            )
            db.collection("chats_v2").document(chatId).collection("messages").add(photoMsg)
            Toast.makeText(context, "Photo attached and sent!", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(chatId) {
        // Fetch Parent Chat Metadata
        db.collection("chats_v2").document(chatId).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                recipientPhone = doc.getString("customerPhone") ?: doc.getString("phone") ?: ""
                assignedTechName = doc.getString("assignedTechnician")
                chatStatus = doc.getString("status")
                lastRequest = doc.getString("lastRequest")
                
                // Fetch recipient pic
                db.collection("users").whereEqualTo("name", recipientName).limit(1).get().addOnSuccessListener { uSnap ->
                    if (!uSnap.isEmpty) {
                        recipientPic = uSnap.documents[0].getString("profilePicUrl")
                    }
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
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            text = doc.getString("text") ?: "",
                            timestamp = doc.getTimestamp("timestamp") ?: com.google.firebase.Timestamp.now(),
                            isAdmin = doc.getBoolean("isAdmin") ?: false
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            db.collection("chats_v2").document(chatId).update(mapOf("status" to "Denied"))

                            db.collection("chats_v2").document(chatId).collection("services")
                                .whereEqualTo("status", "pending")
                                .get().addOnSuccessListener { snapshot ->
                                    snapshot.documents.forEach { doc -> doc.reference.update("status", "denied") }
                                }

                            db.collection("chats_v2").document(chatId).collection("messages").add(hashMapOf(
                                "senderId" to "admin",
                                "senderName" to "Admin Support",
                                "text" to "Your request has been denied.",
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "isAdmin" to true
                            ))
                            showAssignDialog = false
                        }
                    ) {
                        Text("Deny Request", color = Color(0xFFD32F2F), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            if (selectedTech.isNotEmpty()) {
                                db.collection("chats_v2").document(chatId).update(
                                    mapOf("assignedTechnician" to selectedTech, "status" to "Accepted")
                                )

                                db.collection("chats_v2").document(chatId).collection("services")
                                    .whereEqualTo("technician", selectedTech)
                                    .whereEqualTo("status", "pending")
                                    .get().addOnSuccessListener { snapshot ->
                                        if (snapshot.isEmpty) {
                                            db.collection("chats_v2").document(chatId).collection("services")
                                                .whereEqualTo("status", "pending")
                                                .get().addOnSuccessListener { s2 ->
                                                    s2.documents.forEach { doc -> doc.reference.update("status", "accepted", "technician", selectedTech) }
                                                }
                                        } else {
                                            snapshot.documents.forEach { doc -> doc.reference.update("status", "accepted") }
                                        }
                                    }

                                db.collection("chats_v2").document(chatId).collection("messages").add(hashMapOf(
                                    "senderId" to "admin",
                                    "senderName" to "Admin Support",
                                    "text" to "Technician $selectedTech has been assigned and accepted your request.",
                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                    "isAdmin" to true
                                ))
                                showAssignDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Accept & Assign", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                        if (recipientPic != null) {
                            AsyncImage(
                                model = recipientPic,
                                contentDescription = recipientName,
                                modifier = Modifier.size(38.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(PrimaryBlue.copy(0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(recipientName.take(1).uppercase(), fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(recipientName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).background(PrimaryGreen, CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when {
                                        chatId.startsWith("admin_tech_") -> "Admin Dispatch Line"
                                        chatId.startsWith("admin_") -> "Support Channel"
                                        chatId.startsWith("tech_") -> "Field Service Line"
                                        else -> "Direct Message"
                                    },
                                    fontSize = 10.sp, 
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
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
                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$recipientPhone"))
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
                            "isAdmin" to isAdmin
                        )
                        db.collection("chats_v2").document(chatId).collection("messages").add(msg)
                        db.collection("chats_v2").document(chatId).set(
                            mapOf(
                                "lastMessage" to trimmedText,
                                "timestamp" to com.google.firebase.Timestamp.now(),
                                "chatName" to (if (isAdmin) recipientName else currentUserName)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) {
                    item {
                        DateSeparatorText("Today")
                    }

                    items(messages) { msg ->
                        ChatBubble(msg = msg, isMe = msg.senderId == currentUserId)
                    }
                }
            }
        }
    }
}

@Composable
fun DateSeparatorText(dateText: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE2E8F0)
        ) {
            Text(dateText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B), modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
        }
    }
}

@Composable
fun ChatBubble(msg: ChatMessage, isMe: Boolean) {
    val bubbleColor = if (isMe) PrimaryBlue else Color.White
    val textColor = if (isMe) Color.White else TextDark
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart

    val isPhotoMsg = msg.text.contains("📷")
    val isSystemMsg = msg.senderId == "admin" && (msg.text.contains("assigned") || msg.text.contains("accepted") || msg.text.contains("denied"))

    val formattedTime = remember(msg.timestamp) {
        val date = msg.timestamp.toDate()
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(date)
    }

    if (isSystemMsg) {
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PrimaryBlue.copy(0.08f),
                border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
            ) {
                Text(msg.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
        return
    }

    val shape = if (isMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            if (!isMe) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(PrimaryBlue.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(msg.senderName.take(1).uppercase(), color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Card(
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = bubbleColor),
                elevation = CardDefaults.cardElevation(defaultElevation = if (isMe) 2.dp else 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    if (!isMe) {
                        Text(msg.senderName, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PrimaryBlue)
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    if (isPhotoMsg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CameraAlt, null, tint = if (isMe) Color.White else PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(msg.text, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(msg.text, color = textColor, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = formattedTime,
                        fontSize = 9.sp,
                        color = if (isMe) Color.White.copy(0.7f) else Color.Gray,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
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
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).navigationBarsPadding().imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCameraSnap) {
                Icon(Icons.Default.CameraAlt, "Snap Photo", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text("Type a message...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppLightBg,
                    unfocusedContainerColor = AppLightBg,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            val isEnabled = value.isNotBlank()
            IconButton(
                onClick = onSend,
                enabled = isEnabled,
                modifier = Modifier.background(if (isEnabled) PrimaryBlue else Color.LightGray, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
