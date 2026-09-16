package com.example.coolconnect

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.example.coolconnect.ui.theme.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

data class AdminFeedbackState(
    val isOpen: Boolean = false,
    val title: String = "",
    val message: String = "",
    val isError: Boolean = false
)

enum class AdminTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Fleet", Icons.Default.Dashboard),
    INVENTORY("Inventory", Icons.Default.Inventory),
    INBOX("Messages", Icons.AutoMirrored.Filled.Chat),
    TRACKING("Map", Icons.Default.LocationOn),
    HISTORY("History", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    
    val technicians by viewModel.technicians.collectAsState()
    val products by viewModel.products.collectAsState()
    val completedJobs by viewModel.completedJobs.collectAsState()
    val neighborhoodClusters by viewModel.neighborhoodClusters.collectAsState()
    val adminProfilePicUrl by viewModel.adminProfilePicUrl.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    var currentTab by remember { mutableStateOf(AdminTab.DASHBOARD) }
    var feedbackState by remember { mutableStateOf(AdminFeedbackState()) }
    var showPrivacyConsent by remember { mutableStateOf(false) }

    val timeGreeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                if (doc.getBoolean("hasConsented") != true) {
                    showPrivacyConsent = true
                }
            } else {
                showPrivacyConsent = true
            }
        }
    }

    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    
    val newJobNotifs = remember { mutableStateListOf<Map<String, Any>>() }
    val escalations = remember { mutableStateListOf<Map<String, Any>>() }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Listen for Urgent Escalations
        db.collection("escalations").whereEqualTo("status", "URGENT_REVIEW")
            .addSnapshotListener { snapshot, _ ->
                escalations.clear()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    escalations.add(data)
                }
            }
    }

    LaunchedEffect(completedJobs) {
        val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        completedJobs.forEach { job ->
            val ts = (job["completedAt"] as? Long) ?: 0L
            if (ts > yesterday && newJobNotifs.none { it["uid"] == job["uid"] }) {
                newJobNotifs.add(job)
            }
        }
    }

    fun showFeedback(title: String, message: String, isError: Boolean = false) {
        feedbackState = AdminFeedbackState(true, title, message, isError)
    }

    if (showNotifDialog) {
        AdminNotificationDialog(
            notifs = newJobNotifs,
            onDismiss = { showNotifDialog = false },
            onClear = { newJobNotifs.clear() }
        )
    }

    if (showPrivacyConsent) {
        PrivacyConsentDialog(onConsentGranted = {
            val uid = auth.currentUser?.uid
            if (uid != null) { db.collection("users").document(uid).update("hasConsented", true) }
            showPrivacyConsent = false
        })
    }

    if (showProfileMenu) {
        val context = LocalContext.current
        AlertDialog(
            onDismissRequest = { showProfileMenu = false },
            confirmButton = { },
            text = { 
                AdminProfileView(
                    profilePicUrl = adminProfilePicUrl,
                    onLogout = onLogout,
                    onUploadPic = { viewModel.uploadProfilePic(it, context) },
                    isUploading = isUploading
                ) 
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(28.dp)
        )
    }

    if (activeChatId != null) {
        FullChatScreen(
            chatId = activeChatId!!, recipientName = activeChatName,
            currentUserId = "admin", currentUserName = "Admin Support",
            isAdmin = true, onBack = { activeChatId = null }
        )
    } else {
        if (feedbackState.isOpen) {
            AlertDialog(
                onDismissRequest = { feedbackState = feedbackState.copy(isOpen = false) },
                icon = { Icon(imageVector = if (feedbackState.isError) Icons.Default.Error else Icons.Default.CheckCircle, contentDescription = null, tint = if (feedbackState.isError) Color.Red else PrimaryGreen, modifier = Modifier.size(48.dp)) },
                title = { Text(feedbackState.title, fontWeight = FontWeight.Bold) },
                text = { Text(feedbackState.message) },
                confirmButton = {
                    TextButton(onClick = { feedbackState = feedbackState.copy(isOpen = false) }) {
                        Text("OK", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }
                },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.White
            )
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("$timeGreeting, Admin 👋", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(shape = RoundedCornerShape(10.dp), color = PrimaryGreen.copy(0.12f)) {
                                    Text("🟢 Operational", color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("CoolConnect Operations Center", fontSize = 11.sp, color = Color.Gray)
                        }
                    },
                    actions = {
                        if (escalations.isNotEmpty()) {
                            IconButton(onClick = { currentTab = AdminTab.HISTORY }) {
                                BadgedBox(badge = { Badge(containerColor = Color.Red) { Text("${escalations.size}") } }) {
                                    Icon(Icons.Default.PriorityHigh, "Escalations", tint = Color.Red)
                                }
                            }
                        }
                        IconButton(onClick = { showNotifDialog = true }) {
                            BadgedBox(badge = { if (newJobNotifs.isNotEmpty()) { Badge(containerColor = Color.Red) { Text("${newJobNotifs.size}", color = Color.White) } } }) {
                                Icon(Icons.Default.Notifications, "Notifications", tint = TextDark, modifier = Modifier.size(22.dp))
                            }
                        }
                        IconButton(onClick = { showProfileMenu = true }) {
                            if (adminProfilePicUrl != null) {
                                AsyncImage(
                                    model = adminProfilePicUrl,
                                    contentDescription = "Admin Profile",
                                    modifier = Modifier.size(26.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AccountCircle, "Admin Profile", tint = TextDark, modifier = Modifier.size(26.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = { AdminBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it }) },
            containerColor = AppLightBg
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedContent(targetState = currentTab, transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) }, label = "admin_tab") { targetTab ->
                    when (targetTab) {
                        AdminTab.DASHBOARD -> AdminTechAccountsView(technicians = technicians, neighborhoodClusters = neighborhoodClusters, showFeedback = ::showFeedback)
                        AdminTab.INVENTORY -> AdminInventoryView(products = products, showFeedback = ::showFeedback)
                        AdminTab.INBOX -> AdminChatHubView(onChatSelected = { id, name -> activeChatId = id; activeChatName = name })
                        AdminTab.TRACKING -> AdminTrackingView()
                        AdminTab.HISTORY -> AdminReportsView(completedJobs = completedJobs)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminBottomBar(currentTab: AdminTab, onTabSelected: (AdminTab) -> Unit) {
    NavigationBar(containerColor = Color.White, tonalElevation = 8.dp, modifier = Modifier.height(60.dp)) {
        AdminTab.entries.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected, alwaysShowLabel = false,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, tint = if (selected) PrimaryBlue else Color.Gray, modifier = Modifier.size(22.dp)) },
                label = { Text(text = tab.label, color = PrimaryBlue, fontSize = 9.sp, maxLines = 1, softWrap = false, fontWeight = FontWeight.Bold) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryBlue.copy(alpha = 0.08f), selectedIconColor = PrimaryBlue, unselectedIconColor = Color.Gray)
            )
        }
    }
}

@Composable
fun BadgeChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(0.12f)) {
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SpecialtyMetric(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$count", fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextDark)
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
    }
}

// 🛠️ 1. TECHNICIAN FLEET MANAGEMENT
@Composable
fun AdminTechAccountsView(
    technicians: List<Map<String, Any>>, 
    neighborhoodClusters: Map<String, List<Map<String, Any>>>, 
    showFeedback: (String, String, Boolean) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSkillFilter by remember { mutableStateOf("All") }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var selectedTechForDetails by remember { mutableStateOf<Map<String, Any>?>(null) }

    var techName by remember { mutableStateOf("") }
    var techEmail by remember { mutableStateOf("") }
    var techPhone by remember { mutableStateOf("") }
    var techPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var techSkill by remember { mutableStateOf("HVAC & AC Master") }
    var isCreating by remember { mutableStateOf(false) }

    val techRatings = remember { mutableStateMapOf<String, Pair<Float, Int>>() }
    val techWorkloads = remember { mutableStateMapOf<String, Int>() }
    val techCompletedCounts = remember { mutableStateMapOf<String, Int>() }
    val techLateAlerts = remember { mutableStateMapOf<String, Boolean>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(technicians) {
        if (technicians.isNotEmpty()) {
            technicians.forEach { tech ->
                val tName = tech["name"]?.toString() ?: ""
                if (tName.isNotBlank()) {
                    // Listen for both active jobs (assignedTechnician) and completed ones (lastTechnician)
                    db.collection("chats_v2").addSnapshotListener { jSnap, _ ->
                        val relevantDocs = jSnap?.documents?.filter { 
                            it.getString("assignedTechnician") == tName || it.getString("lastTechnician") == tName 
                        } ?: emptyList()
                        
                        techWorkloads[tName] = relevantDocs.count { 
                            val st = it.getString("status") ?: ""
                            st != "Completed" && st != "Denied" && st != "Request Cancelled" 
                        }
                        techCompletedCounts[tName] = relevantDocs.count { 
                            it.getString("status") == "Completed" 
                        }

                        // SLA: Check if tech is currently late for any active job
                        val isLate = relevantDocs.any { doc ->
                            val st = doc.getString("status") ?: ""
                            val schedTs = doc.getTimestamp("scheduledTimestamp")
                            val startedAt = doc.getTimestamp("startedAt")
                            if (st != "Completed" && st != "Denied" && schedTs != null) {
                                val compareTime = startedAt ?: com.google.firebase.Timestamp.now()
                                compareTime.seconds > (schedTs.seconds + 1800)
                            } else false
                        }
                        techLateAlerts[tName] = isLate
                    }
                }
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        db.collection("reviews").addSnapshotListener { snapshot, _ ->
            val ratingGroups = mutableMapOf<String, MutableList<Int>>()
            snapshot?.documents?.forEach { doc ->
                val tName = doc.getString("techName") ?: return@forEach
                ratingGroups.getOrPut(tName) { mutableListOf() }.add(doc.getLong("rating")?.toInt() ?: 5)
            }
            techRatings.clear()
            ratingGroups.forEach { (tName, list) -> techRatings[tName] = Pair(list.average().toFloat(), list.size) }
        }
    }

    val filteredTechs = technicians.filter { tech ->
        val name = tech["name"]?.toString() ?: ""
        val email = tech["email"]?.toString() ?: ""
        val skill = tech["skill"]?.toString() ?: ""
        val matchesQuery = name.contains(searchQuery, true) || email.contains(searchQuery, true)
        val matchesSkill = selectedSkillFilter == "All" || skill.contains(selectedSkillFilter, true)
        matchesQuery && matchesSkill
    }

    if (selectedTechForDetails != null) {
        TechnicianDetailsDialog(tech = selectedTechForDetails!!, activeJobs = techWorkloads[selectedTechForDetails!!["name"]?.toString()] ?: 0, ratingData = techRatings[selectedTechForDetails!!["name"]?.toString()], onDismiss = { selectedTechForDetails = null })
    }

    if (showRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text("Register Field Technician", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = techName, onValueChange = { techName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = techEmail, onValueChange = { techEmail = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = techPhone, onValueChange = { if (it.length <= 11 && it.all { c -> c.isDigit() }) techPhone = it }, label = { Text("Phone") }, placeholder = { Text("09123456789", color = Color.Gray.copy(0.5f)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = techPassword, onValueChange = { techPassword = it }, label = { Text("Password") }, trailingIcon = { IconButton(onClick = { passwordVisible = !passwordVisible }) { Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null) } }, visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Specialization:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    listOf("HVAC & AC Master", "Electrical Specialist", "Smart Lock & Security").forEach { opt ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { techSkill = opt }.padding(vertical = 4.dp)) {
                            RadioButton(selected = techSkill == opt, onClick = { techSkill = opt })
                            Text(opt, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (techName.isNotBlank() && techEmail.isNotBlank()) {
                        isCreating = true
                        auth.createUserWithEmailAndPassword(techEmail.trim(), techPassword).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val userId = auth.currentUser?.uid
                                if (userId != null) {
                                    db.collection("users").document(userId).set(hashMapOf("role" to "technician", "name" to techName.trim(), "email" to techEmail.trim(), "phone" to techPhone.trim(), "skill" to techSkill, "isOnline" to true, "createdAt" to com.google.firebase.Timestamp.now())).addOnSuccessListener {
                                        isCreating = false; showFeedback("Success", "Tech registered!", false); showRegisterDialog = false
                                    }
                                }
                            } else { isCreating = false; showFeedback("Error", task.exception?.message ?: "Failed", true) }
                        }
                    }
                }) { Text("Register") }
            },
            dismissButton = { TextButton(onClick = { showRegisterDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Fleet Operations", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
        Text("Real-time management center", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        // 🏘️ NEIGHBORHOOD DISPATCH HUBS
        if (neighborhoodClusters.isNotEmpty()) {
            Text("NEIGHBORHOOD DISPATCH HUBS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(neighborhoodClusters.toList()) { (area, requests) ->
                    Card(
                        modifier = Modifier.width(160.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(0.05f)),
                        border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PinDrop, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(area, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, color = TextDark)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("${requests.size} PENDING JOBS", color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 10.sp)
                            
                            // Visual urgency indicator
                            val hasEmergency = requests.any { it["status"].toString().contains("SOS") }
                            if (hasEmergency) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("🚨 URGENT REQUEST", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) { drawArc(PrimaryBlue, 0f, 240f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)); drawArc(PrimaryGreen, 245f, 110f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)) }
                        Text("${technicians.size}", fontWeight = FontWeight.Bold)
                    }
                    Column { LegendItem("Active", PrimaryBlue); LegendItem("Ready", PrimaryGreen) }
                    Column { SpecialtyMetric("AC", technicians.count { it["skill"]?.toString()?.contains("AC") == true }); SpecialtyMetric("Elec", technicians.count { it["skill"]?.toString()?.contains("Elec") == true }) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showRegisterDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" Register Technician") }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it }, placeholder = { Text("Search technicians...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) { ShimmerCardList(4) } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredTechs.forEach { tech ->
                    val tName = tech["name"]?.toString() ?: "Tech"; val tSkill = tech["skill"]?.toString() ?: ""
                    val active = techWorkloads[tName] ?: 0; val comps = techCompletedCounts[tName] ?: 0
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedTechForDetails = tech }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFF1F5F9))) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val techPic = tech["profilePicUrl"]?.toString()
                            if (techPic != null) {
                                AsyncImage(
                                    model = techPic,
                                    contentDescription = tName,
                                    modifier = Modifier.size(44.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(modifier = Modifier.size(44.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Build, null, tint = PrimaryBlue) }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tName, fontWeight = FontWeight.Bold)
                                    if (techLateAlerts[tName] == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Warning, "Late Alert", tint = Color.Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text("$tSkill • $comps Jobs", fontSize = 11.sp, color = Color.Gray)
                            }
                            BadgeChip(
                                text = if (active == 0) "Ready" else "Busy",
                                color = if (active == 0) PrimaryGreen else Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}

// 📦 2. INVENTORY MANAGEMENT
@Composable
fun AdminInventoryView(products: List<Map<String, Any>>, showFeedback: (String, String, Boolean) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(products) {
        isLoading = products.isEmpty() && isLoading
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Parts Inventory", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text(" Add Product") }
        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) ShimmerCardList(5) else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                products.filter { it["name"].toString().contains(searchQuery, true) }.forEach { p ->
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, tint = PrimaryBlue); Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) { Text(p["name"].toString(), fontWeight = FontWeight.Bold); Text("Stock: ${p["stock"]} units", fontSize = 11.sp, color = PrimaryGreen) }
                            Text("₱${p["price"]}", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }
                }
            }
        }
    }
    if (showAddDialog) InventoryDialog(product = null, onDismiss = { showAddDialog = false }, onSave = { data ->
        db.collection("products").add(data).addOnSuccessListener {
            showFeedback("Success", "Product added to inventory", false)
        }.addOnFailureListener {
            showFeedback("Error", "Failed to add product", true)
        }
        showAddDialog = false
    })
}

// 💬 3. MESSAGES & OPERATIONS FEED
@Composable
fun AdminChatHubView(onChatSelected: (String, String) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val currentAdminUid = auth.currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }
    val customerChats = remember { mutableStateListOf<Map<String, Any>>() }
    val techChats = remember { mutableStateListOf<Map<String, Any>>() }
    val fieldChats = remember { mutableStateListOf<Map<String, Any>>() }
    var selectedSection by remember { mutableStateOf("Inquiries") }

    LaunchedEffect(Unit) {
        db.collection("chats_v2").addSnapshotListener { snapshot, _ ->
            customerChats.clear(); fieldChats.clear()
            val techChatDataMap = mutableMapOf<String, Map<String, Any>>()
            snapshot?.documents?.forEach { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf(); data["uid"] = doc.id
                val cid = doc.id; val cname = data["customerName"]?.toString() ?: ""
                
                // Participant Filtering: Prevent admin self-chat visibility and filter support channels
                val isSelfSupport = currentAdminUid != null && cid == "admin_$currentAdminUid"
                val isAdminRoleAccount = cname.contains("Admin", true) || cid.contains("admin_support", true)
                
                if (cid.startsWith("admin_") && !cid.startsWith("admin_tech_") && !isAdminRoleAccount && !isSelfSupport) {
                    customerChats.add(data)
                }
                
                if (cid.startsWith("admin_tech_")) techChatDataMap[cid] = data
                if (cid.startsWith("tech_")) fieldChats.add(data.apply { 
                    val parts = cid.split("_"); data["techName"] = if (parts.size >= 2) parts[1] else "Tech"
                })
            }
            db.collection("users").whereEqualTo("role", "technician").get().addOnSuccessListener { uSnap ->
                techChats.clear()
                uSnap.documents.forEach { uDoc ->
                    val tName = uDoc.getString("name") ?: "Tech"
                    techChats.add((techChatDataMap["admin_tech_$tName"] ?: emptyMap()).toMutableMap().apply { this["uid"] = "admin_tech_$tName"; this["techName"] = tName })
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(42.dp).background(AppLightBg, RoundedCornerShape(21.dp)).padding(3.dp)) {
            listOf("Inquiries", "Techs", "Field").forEach { s ->
                val isSel = selectedSection == s
                Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if (isSel) Color.White else Color.Transparent, RoundedCornerShape(18.dp)).clickable { selectedSection = s }, contentAlignment = Alignment.Center) {
                    Text(s, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = if (isSel) PrimaryBlue else Color.Gray)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val list = when(selectedSection) { "Inquiries" -> customerChats; "Techs" -> techChats; else -> fieldChats }
            items(list) { chat ->
                val cid = chat["uid"].toString()
                val cname = chat["customerName"]?.toString() ?: chat["chatName"]?.toString() ?: chat["techName"]?.toString() ?: "Chat"
                Card(modifier = Modifier.fillMaxWidth().clickable { onChatSelected(cid, cname) }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Default.Person, null, tint = PrimaryBlue) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { Text(cname, fontWeight = FontWeight.Bold); Text(chat["lastMessage"]?.toString() ?: "No activity", fontSize = 11.sp, color = Color.Gray, maxLines = 1) }
                    }
                }
            }
        }
    }
}

// 🗺️ 4. TRACKING VIEW
@Composable
fun AdminTrackingView() {
    val db = remember { FirebaseFirestore.getInstance() }
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(14.5995, 120.9842), 11f) }
    val techLocations = remember { mutableStateMapOf<String, LatLng>() }
    LaunchedEffect(Unit) {
        db.collection("users").whereEqualTo("role", "technician").addSnapshotListener { snapshot, _ ->
            techLocations.clear()
            snapshot?.documents?.forEach { doc ->
                val lat = doc.getDouble("latitude"); val lng = doc.getDouble("longitude")
                if (lat != null && lng != null) techLocations[doc.getString("name") ?: "Tech"] = LatLng(lat, lng)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(modifier = Modifier.fillMaxSize(), cameraPositionState = cameraPositionState) {
            techLocations.forEach { (name, loc) -> Marker(state = rememberMarkerState(position = loc), title = name) }
        }
    }
}

// 📊 5. REPORTS & HISTORY
@Composable
fun AdminReportsView(completedJobs: List<Map<String, Any>>) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var totalRevenue by remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(Unit) {
        db.collection("chats_v2").get().addOnSuccessListener { snapshot ->
            totalRevenue = snapshot.documents.sumOf { if (it.getString("status") == "Completed") (it.get("totalAmount") as? Number)?.toDouble() ?: 0.0 else 0.0 }
        }
    }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Operations History", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Text("Performance & Revenue Metrics", fontSize = 12.sp, color = Color.Gray)
            }
            Row {
                IconButton(onClick = { Toast.makeText(context, "Exporting CSV...", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.FileDownload, "Export CSV", tint = PrimaryBlue)
                }
                IconButton(onClick = { Toast.makeText(context, "Generating PDF Report...", Toast.LENGTH_SHORT).show() }) {
                    Icon(Icons.Default.PictureAsPdf, "Export PDF", tint = Color.Red)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Total Revenue", color = Color.Gray, fontSize = 12.sp)
                Text("₱${String.format(Locale.getDefault(), "%,.2f", totalRevenue)}", fontSize = 28.sp, fontWeight = FontWeight.Black, color = PrimaryBlue)
            }
        }
        Text("SERVICE LOGS", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(10.dp))
        completedJobs.forEach { job ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column { Text(job["customerName"].toString(), fontWeight = FontWeight.Bold); Text("Handled by ${job["assignedTechnician"]}", fontSize = 11.sp, color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
fun InventoryDialog(product: Map<String, Any>?, onDismiss: () -> Unit, onSave: (Map<String, Any>) -> Unit) {
    var name by remember { mutableStateOf(product?.get("name")?.toString() ?: "") }
    var stock by remember { mutableStateOf(product?.get("stock")?.toString() ?: "") }
    var price by remember { mutableStateOf(product?.get("price")?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Add New Part" else "Edit Part", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Product Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = stock, onValueChange = { if (it.all { c -> c.isDigit() }) stock = it }, label = { Text("Stock Quantity") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = price, onValueChange = { if (it.isEmpty() || it.toDoubleOrNull() != null) price = it }, label = { Text("Price (₱)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && stock.isNotBlank() && price.isNotBlank()) {
                    onSave(mapOf("name" to name, "stock" to stock.toInt(), "price" to price.toDouble()))
                }
            }) { Text("Save Item") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
fun AdminNotificationDialog(notifs: List<Map<String, Any>>, onDismiss: () -> Unit, onClear: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Recent Activity") }, text = {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
            if (notifs.isEmpty()) Text("No new notifications.", color = Color.Gray) else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(notifs) { n -> Text("Job Completed for ${n["customerName"]}", fontSize = 13.sp) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onClear) { Text("Clear All") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
fun AdminInfoRow(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = TextDark, fontSize = 13.sp)
    }
}

@Composable
fun TechnicianDetailsDialog(tech: Map<String, Any>, activeJobs: Int, ratingData: Pair<Float, Int>?, onDismiss: () -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val verificationStatus = tech["verificationStatus"]?.toString() ?: "Not Verified"
    val kycUrl = tech["kycUrl"]?.toString()

    AlertDialog(onDismissRequest = onDismiss, title = { 
        Row(verticalAlignment = Alignment.CenterVertically) {
            val techPic = tech["profilePicUrl"]?.toString()
            if (techPic != null) {
                AsyncImage(
                    model = techPic,
                    contentDescription = tech["name"].toString(),
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(tech["name"].toString(), fontWeight = FontWeight.Black)
        }
    }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            AdminInfoRow(Icons.Default.Build, "Skill: ${tech["skill"]}")
            AdminInfoRow(Icons.Default.Work, "Active Jobs: $activeJobs")
            AdminInfoRow(Icons.Default.Star, "Rating: ${ratingData?.first ?: 5.0}★ (${ratingData?.second ?: 0} reviews)")
            AdminInfoRow(Icons.Default.Email, "Email: ${tech["email"]}")
            AdminInfoRow(Icons.Default.Phone, "Phone: ${tech["phone"]}")
            
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Verification Status: $verificationStatus", fontWeight = FontWeight.Bold, color = if (verificationStatus == "Verified") PrimaryGreen else Color.Gray)
            
            if (kycUrl != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Verification Document:", fontSize = 12.sp, color = Color.Gray)
                AsyncImage(
                    model = kycUrl,
                    contentDescription = "KYC Doc",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
                
                if (verificationStatus == "Pending") {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    db.collection("users").document(tech["uid"].toString()).update(mapOf("isVerified" to true, "verificationStatus" to "Verified"))
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                        ) {
                            Text("Approve")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    db.collection("users").document(tech["uid"].toString()).update(mapOf("isVerified" to false, "verificationStatus" to "Rejected"))
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Reject")
                        }
                    }
                }
            }
        }
    }, confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") } }, shape = RoundedCornerShape(24.dp))
}

@Composable
fun AdminProfileView(profilePicUrl: String?, onLogout: () -> Unit, onUploadPic: (android.net.Uri) -> Unit, isUploading: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileImageSelector(
            imageUrl = profilePicUrl,
            onImageSelected = onUploadPic,
            modifier = Modifier.size(80.dp),
            placeholderIcon = Icons.Default.AccountCircle,
            isLoading = isUploading
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f)), modifier = Modifier.fillMaxWidth()) { Text("Logout") }
    }
}
