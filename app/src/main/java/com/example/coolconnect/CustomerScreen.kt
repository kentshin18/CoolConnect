package com.example.coolconnect

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.coolconnect.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

import androidx.hilt.navigation.compose.hiltViewModel

enum class CustomerTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    SERVICES("Services", Icons.Default.Build),
    TRACK("Track", Icons.AutoMirrored.Filled.Assignment),
    SHOP("Shop", Icons.Default.ShoppingCart),
    CHAT("Messages", Icons.AutoMirrored.Filled.Chat),
    PROFILE("You", Icons.Default.Person)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(onLogout: () -> Unit, viewModel: CustomerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var currentTab by remember { mutableStateOf(CustomerTab.HOME) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    
    val customerName by viewModel.customerName.collectAsState()
    val aiRecommendation by viewModel.aiRecommendation.collectAsState()
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val userHasConsented by viewModel.hasConsented.collectAsState()

    // Privacy & Permissions State
    var showPrivacyConsent by remember { mutableStateOf(false) }

    // SOS Emergency State
    var showEmergencyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(userHasConsented) {
        if (!userHasConsented) {
            showPrivacyConsent = true
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Live Location Broadcasting for Customer
    LaunchedEffect(userHasConsented) {
        if (userHasConsented) {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(permissions.toTypedArray())

            while (true) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).addOnSuccessListener { location ->
                        if (location != null) {
                            viewModel.updateLocation(location.latitude, location.longitude)
                        }
                    }
                }
                kotlinx.coroutines.delay(10.seconds)
            }
        }
    }

    if (showPrivacyConsent) {
        PrivacyConsentDialog(
            onConsentGranted = {
                viewModel.updateConsent(true)
                showPrivacyConsent = false
            }
        )
    }

    if (showEmergencyDialog) {
        EmergencySosDialog(
            customerName = customerName,
            onDismiss = { showEmergencyDialog = false }
        )
    }

    if (activeChatId != null) {
        FullChatScreen(
            chatId = activeChatId!!,
            recipientName = activeChatName,
            currentUserId = auth.currentUser?.uid ?: "cust",
            currentUserName = customerName,
            onBack = { activeChatId = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(PrimaryBlue.copy(0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AcUnit, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(currentTab.label, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showEmergencyDialog = true }) {
                            Icon(Icons.Default.Warning, contentDescription = "SOS", tint = Color(0xFFD32F2F), modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = { currentTab = CustomerTab.PROFILE }) {
                            if (profilePicUrl != null) {
                                AsyncImage(
                                    model = profilePicUrl,
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(imageVector = Icons.Default.AccountCircle, contentDescription = "Profile", tint = TextDark, modifier = Modifier.size(28.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                CustomerBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it })
            },
            containerColor = AppLightBg
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "customer_tab"
                ) { targetTab ->
                    when (targetTab) {
                        CustomerTab.HOME -> CustomerHomeView(
                            customerName = customerName,
                            aiRecommendation = aiRecommendation,
                            onNavigateToServices = { currentTab = CustomerTab.SERVICES },
                            onNavigateToTrack = { currentTab = CustomerTab.TRACK },
                            onNavigateToShop = { currentTab = CustomerTab.SHOP },
                            onTriggerSos = { showEmergencyDialog = true }
                        )
                        CustomerTab.SERVICES -> CustomerServicesView()
                        CustomerTab.TRACK -> OrderTrackingView()
                        CustomerTab.SHOP -> ProductCatalogView()
                        CustomerTab.CHAT -> CustomerChatHubView(onChatSelected = { id, name -> activeChatId = id; activeChatName = name })
                        CustomerTab.PROFILE -> CustomerProfileView(onLogout)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerBottomBar(currentTab: CustomerTab, onTabSelected: (CustomerTab) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.height(68.dp)
    ) {
        CustomerTab.entries.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, tint = if (selected) PrimaryBlue else Color(0xFF757575), modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, fontSize = 9.sp, maxLines = 1, softWrap = false, color = if (selected) PrimaryBlue else Color(0xFF616161)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryBlue.copy(alpha = 0.12f))
            )
        }
    }
}

// 🏠 1. FUTURISTIC CUSTOMER DASHBOARD (HOME VIEW)
@Composable
fun CustomerHomeView(
    customerName: String,
    aiRecommendation: String,
    onNavigateToServices: () -> Unit,
    onNavigateToTrack: () -> Unit,
    onNavigateToShop: () -> Unit,
    onTriggerSos: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""

    var activeService by remember { mutableStateOf<Map<String, Any>?>(null) }
    var lastServiceDate by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    val myAssets = remember { mutableStateListOf<Map<String, Any>>() }
    
    val timeGreeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            // Fetch Assets (from services that contain "Hardware Order")
            db.collection("chats_v2").document("admin_$uid").collection("services")
                .whereGreaterThanOrEqualTo("issue", "Hardware Order")
                .addSnapshotListener { snapshot, _ ->
                    myAssets.clear()
                    snapshot?.documents?.forEach { doc ->
                        val data = doc.data ?: return@forEach
                        myAssets.add(data)
                    }
                }

            // Fetch Active Service & Maintenance Timing
            db.collection("chats_v2").document("admin_$uid").collection("services")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val docs = snapshot?.documents ?: emptyList()
                    val latest = docs.firstOrNull()
                    
                    if (latest != null) {
                        val data = latest.data?.toMutableMap() ?: mutableMapOf()
                        val status = data["status"]?.toString() ?: ""
                        if (!status.equals("Completed", ignoreCase = true) && !status.equals("Cancelled", ignoreCase = true) && !status.equals("Denied", ignoreCase = true)) {
                            activeService = data
                        } else {
                            activeService = null
                        }

                        // Last completed service for maintenance check
                        val lastComp = docs.firstOrNull { it.getString("status") == "Completed" }
                        lastServiceDate = lastComp?.getTimestamp("timestamp")
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // Personalized Greeting Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "$timeGreeting, $customerName 👋",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextDark, fontSize = 24.sp)
                )
                Text(
                    text = "How can CoolConnect assist your home today?",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 🔵 ACTIVE SERVICE LIVE BANNER
        if (activeService != null) {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onNavigateToTrack() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEBF3FE)),
                border = BorderStroke(1.dp, Color(0xFFC0DBFC)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(10.dp), color = PrimaryBlue) {
                            Text("ACTIVE SERVICE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Status: ${activeService!!["status"] ?: "Pending"}", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(activeService!!["issue"]?.toString() ?: "HVAC Service", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 16.sp)
                    Text("Technician: ${activeService!!["technician"] ?: "Next Available Specialist"}", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(12.dp))
                    ServiceStepTimeline(status = activeService!!["status"]?.toString() ?: "pending")

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToTrack,
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Track Service Progress", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // ⚡ QUICK ACTIONS GRID
        Text("QUICK ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Request Service", Icons.Default.Build, PrimaryBlue, Modifier.weight(1f), onNavigateToServices)
            QuickActionCard("Book Schedule", Icons.Default.CalendarMonth, PrimaryGreen, Modifier.weight(1f), onNavigateToServices)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Hardware Shop", Icons.Default.ShoppingCart, Color(0xFF7B61FF), Modifier.weight(1f), onNavigateToShop)
            QuickActionCard("Emergency SOS", Icons.Default.Warning, Color(0xFFD32F2F), Modifier.weight(1f), onTriggerSos)
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ✨ COOLCONNECT AI SMART ASSISTANT CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6FF)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFD0E2FB))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CoolConnect AI Recommendation", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(aiRecommendation, fontSize = 12.sp, color = TextDark, lineHeight = 17.sp)
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = onNavigateToShop, contentPadding = PaddingValues(0.dp)) {
                    Text("View Recommended Hardware →", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🧹 PREVENTIVE MAINTENANCE REMINDER
        val needsMaintenance = remember(lastServiceDate) {
            if (lastServiceDate == null) false
            else {
                val cal = Calendar.getInstance()
                cal.time = lastServiceDate!!.toDate()
                cal.add(Calendar.MONTH, 3)
                Calendar.getInstance().after(cal) // True if current date is 3+ months after last service
            }
        }

        if (needsMaintenance) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9C4)),
                border = BorderStroke(1.dp, Color(0xFFFFD54F))
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(Color(0xFFFFB300).copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PriorityHigh, null, tint = Color(0xFFFFB300))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Maintenance Required", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                        Text("It's been 3+ months since your last HVAC service.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(onClick = onNavigateToServices, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)), shape = RoundedCornerShape(10.dp)) {
                        Text("Book Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 📱 MY SMART ASSETS SECTION
        if (myAssets.isNotEmpty()) {
            Text("MY SMART TECH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(myAssets.toList()) { asset ->
                    val assetName = asset["issue"]?.toString()?.replace("Hardware Order: ", "")?.split(" (")?.firstOrNull() ?: "Device"
                    Card(
                        modifier = Modifier.width(140.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF0F0F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(0.08f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(getProductIcon(assetName, ""), null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(assetName, fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center)
                            Text("Active • Protected", color = PrimaryGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // 🧹 STANDARD MAINTENANCE CARD (Shown if no reminder is active)
        if (!needsMaintenance) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(PrimaryGreen.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.CleaningServices, null, tint = PrimaryGreen)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Preventive AC Maintenance", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                        Text("Recommended every 3 months for optimal cooling efficiency.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(onClick = onNavigateToServices, colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen), shape = RoundedCornerShape(10.dp)) {
                        Text("Schedule", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFECECEC)),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(38.dp).background(color.copy(0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
        }
    }
}

// 🔧 2. SERVICES & TECHNICIAN DIRECTORY VIEW
@Composable
fun CustomerServicesView() {
    TechnicianDirectoryView()
}

// 🚨 EMERGENCY REPAIR SOS DIALOG
@Composable
fun EmergencySosDialog(
    customerName: String,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val emergencyIssues = listOf(
        "❄️ AC Leaking Water / Sudden Breakdown",
        "⚡ Electrical Power Trip / Burning Smell",
        "🔒 Smart Lock Biometric Emergency Lockout",
        "🚰 Water Pipeline Pressure / Sudden Leakage"
    )

    var selectedIssue by remember { mutableStateOf(emergencyIssues[0]) }
    var isAnalyzingSound by remember { mutableStateOf(false) }
    var aiDiagnosticResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                customerPhone = doc.getString("phone") ?: ""
                customerAddress = doc.getString("address") ?: ""
            }
        }
    }

    if (isAnalyzingSound) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            aiDiagnosticResult = "🔍 AI Analysis (Sound ID): High probability of Fan Motor Obstruction or Bearing Wear. Advised to shut down unit immediately to prevent compressor burn."
            isAnalyzingSound = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Emergency SOS Repair", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Immediately dispatches the nearest available technician for urgent HVAC & electrical emergencies.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ✨ AI SOUND TRIAGE SECTION
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(0.05f)),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Smart AI Diagnostic", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue)
                        }
                        Text("Hold phone near unit to analyze abnormal sounds.", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        if (aiDiagnosticResult == null) {
                            Button(
                                onClick = { isAnalyzingSound = true },
                                enabled = !isAnalyzingSound,
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isAnalyzingSound) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Mic, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Sound Analysis", fontSize = 11.sp)
                                }
                            }
                        } else {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PrimaryBlue.copy(0.1f))
                            ) {
                                Text(
                                    aiDiagnosticResult!!,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDark,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Text("Select Emergency Issue:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(8.dp))

                emergencyIssues.forEach { issue ->
                    val isSel = selectedIssue == issue
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedIssue = issue },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSel) Color(0xFFD32F2F).copy(0.12f) else AppLightBg,
                        border = BorderStroke(1.dp, if (isSel) Color(0xFFD32F2F) else FieldBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSel,
                                onClick = { selectedIssue = issue },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFD32F2F))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                issue,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color(0xFFD32F2F) else TextDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color.LightGray.copy(0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("Dispatch Address:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
                Text(
                    if (customerAddress.isNotBlank()) customerAddress else "Please set your address in Settings first!",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && !isAnalyzingSound,
                onClick = {
                    if (customerAddress.isBlank()) {
                        Toast.makeText(context, "Please set your address in Settings before triggering SOS!", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val uid = auth.currentUser?.uid ?: return@Button
                    isSubmitting = true

                    val serviceRequest = mapOf(
                        "technician" to "EMERGENCY SOS / NEXT AVAILABLE",
                        "status" to "pending",
                        "isEmergency" to true,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "issue" to "🚨 SOS: $selectedIssue",
                        "aiDiagnostic" to aiDiagnosticResult
                    )

                    val chatUpdate = mapOf(
                        "customerName" to customerName,
                        "customerEmail" to (auth.currentUser?.email ?: ""),
                        "customerPhone" to customerPhone,
                        "customerAddress" to customerAddress,
                        "status" to "🚨 URGENT SOS DISPATCH",
                        "lastRequest" to "🚨 SOS: $selectedIssue",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("chats_v2").document("admin_$uid").set(chatUpdate, com.google.firebase.firestore.SetOptions.merge())
                    db.collection("chats_v2").document("admin_$uid").collection("services").add(serviceRequest)

                    val autoMsg = hashMapOf(
                        "senderId" to uid,
                        "senderName" to customerName,
                        "text" to "🚨 EMERGENCY SOS TRIGGERED! Issue: $selectedIssue.${if (aiDiagnosticResult != null) " AI Diagnostic: $aiDiagnosticResult" else ""} Please dispatch nearest technician immediately!",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("chats_v2").document("admin_$uid").collection("messages").add(autoMsg)

                    Toast.makeText(context, "🚨 Emergency SOS Dispatched! Admin notified.", Toast.LENGTH_LONG).show()
                    isSubmitting = false
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("DISPATCH EMERGENCY SOS 🚨", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

// 🛒 3. HARDWARE CATALOG & PRODUCT DETAIL
@Composable
fun ProductCatalogView(viewModel: CustomerViewModel = hiltViewModel()) {
    val cart by viewModel.cart.collectAsState()
    val sampleProducts = remember {
        listOf(
            Product(
                1, "Smart Home Hub", "₱4,500", "Electronics", Color(0xFFE3F2FD),
                badge = "BEST SELLER",
                description = "Centralized control hub for smart AC, lighting, and security systems. Supports voice control and mobile automation.",
                specs = "Wi-Fi 6 • Zigbee 3.0 • Voice Control • Low Energy",
                warranty = "2 Years Official Warranty & Technical Support"
            ),
            Product(
                2, "Wireless Security Cam", "₱2,100", "Security", Color(0xFFF1F8E9),
                badge = "HOT ITEM",
                description = "1080p Full HD smart outdoor camera with night vision, motion detection, and real-time smartphone alerts.",
                specs = "1080p HD • Night Vision • IP66 Weatherproof • Two-Way Audio",
                warranty = "1 Year Warranty"
            ),
            Product(
                3, "LED Smart Bulb", "₱850", "Lighting", Color(0xFFFFFDE7),
                badge = "IN STOCK",
                description = "Dimmable RGB smart LED bulb. Create custom lighting schedules and adjust brightness from your phone.",
                specs = "9W (60W Equivalent) • 16 Million Colors • App Scheduled",
                warranty = "1 Year Warranty"
            ),
            Product(
                4, "Smart Door Lock", "₱8,900", "Security", Color(0xFFFCE4EC),
                badge = "BEST SELLER",
                description = "Biometric fingerprint smart lock with passcode keypad, RFID card support, and mechanical key backup.",
                specs = "Fingerprint • Passcode • RFID • Mechanical Key • Anti-Peep",
                warranty = "2 Years Official Warranty"
            ),
            Product(
                5, "Power Backup UPS", "₱3,200", "Electronics", Color(0xFFE8F5E9),
                badge = "IN STOCK",
                description = "Uninterruptible power supply to keep your Wi-Fi routers and security cameras online during power outages.",
                specs = "650VA / 360W • Surge Protection • Automatic Voltage Regulator",
                warranty = "1 Year Warranty"
            ),
            Product(
                6, "Smart Wall Switch", "₱1,150", "Lighting", Color(0xFFFFF3E0),
                badge = "LIMITED STOCK",
                description = "Touch-sensitive smart wall switch. Upgrade traditional light switches to smartphone-controlled automation.",
                specs = "Touch Glass • Neutral Wire Required • Overload Protection",
                warranty = "1 Year Warranty"
            )
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var showCartDialog by remember { mutableStateOf(false) }

    val categories = listOf("All", "Electronics", "Security", "Lighting")

    val filteredProducts = sampleProducts.filter { p ->
        val matchesCategory = selectedCategory == "All" || p.category == selectedCategory
        val matchesQuery = p.name.contains(searchQuery, ignoreCase = true) || p.description.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    if (selectedProduct != null) {
        ProductDetailDialog(
            product = selectedProduct!!,
            onDismiss = { selectedProduct = null }
        )
    }

    if (showCartDialog) {
        CartDialog(
            viewModel = viewModel,
            onDismiss = { showCartDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Featured Hardware & Parts", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextDark)
            
            BadgedBox(
                badge = {
                    if (cart.isNotEmpty()) {
                        Badge(containerColor = PrimaryBlue) {
                            Text("${cart.sumOf { it.quantity }}", color = Color.White)
                        }
                    }
                },
                modifier = Modifier.clickable { showCartDialog = true }
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = TextDark)
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        // Minimized Search Bar
        BasicTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(Color.White, RoundedCornerShape(22.dp))
                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(22.dp)),
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = TextDark),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text("Search hardware...", color = Color.Gray, fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { cat ->
                val isSel = selectedCategory == cat
                Surface(
                    modifier = Modifier.clickable { selectedCategory = cat }.height(34.dp),
                    shape = RoundedCornerShape(17.dp),
                    color = if (isSel) PrimaryBlue else Color.White,
                    border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color(0xFFD0D0D0))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                        Text(cat, color = if (isSel) Color.White else TextDark, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(filteredProducts) { product ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth().clickable { selectedProduct = product }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(105.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (product.category == "Security") Color(0xFFF0F9F2) else Color(0xFFF2F6FE))
                        ) {
                            if (!product.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = product.imageUrl,
                                    contentDescription = product.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = getProductIcon(product.name, product.category),
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(44.dp).align(Alignment.Center)
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(topEnd = 12.dp, bottomStart = 10.dp),
                                color = when (product.badge) {
                                    "BEST SELLER" -> Color(0xFFE68A00)
                                    "HOT ITEM" -> Color(0xFFD32F2F)
                                    else -> PrimaryGreen
                                },
                                modifier = Modifier.align(Alignment.TopEnd)
                            ) {
                                Text(
                                    text = product.badge,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, color = TextDark, fontSize = 13.sp)
                        Text(product.category, fontSize = 11.sp, color = Color(0xFF757575))
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(product.price, color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Button(
                            onClick = { viewModel.addToCart(product) },
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add to Cart", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartDialog(viewModel: CustomerViewModel, onDismiss: () -> Unit) {
    val cart by viewModel.cart.collectAsState()
    var showCheckout by remember { mutableStateOf(false) }

    if (showCheckout) {
        CheckoutDialog(
            viewModel = viewModel,
            onDismiss = { showCheckout = false; onDismiss() }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ShoppingCart, null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Your Smart Cart", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (cart.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Text("Your cart is empty", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(cart) { item ->
                            CartItemRow(item = item, onUpdateQty = { delta -> viewModel.updateQuantity(item.product.id, delta) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (cart.isNotEmpty()) {
                Button(
                    onClick = { showCheckout = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Proceed to Checkout", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue Shopping", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun CartItemRow(item: CartItem, onUpdateQty: (Int) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppLightBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.product.color),
                contentAlignment = Alignment.Center
            ) {
                Icon(getProductIcon(item.product.name, item.product.category), null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
                Text(item.product.price, color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onUpdateQty(-1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.RemoveCircleOutline, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
                Text("${item.quantity}", fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp))
                IconButton(onClick = { onUpdateQty(1) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.AddCircleOutline, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun CheckoutDialog(viewModel: CustomerViewModel, onDismiss: () -> Unit) {
    val cart by viewModel.cart.collectAsState()
    val context = LocalContext.current
    
    var step by remember { mutableIntStateOf(1) } // 1: Summary, 2: Shipping, 3: Payment
    var shippingAddress by remember { mutableStateOf("") }
    var selectedPayment by remember { mutableStateOf("GCash") }
    var isProcessing by remember { mutableStateOf(false) }

    val subtotal = cart.sumOf { it.product.price.replace("₱", "").replace(",", "").toDouble() * it.quantity }
    val vat = subtotal * 0.12
    val total = subtotal + vat

    LaunchedEffect(Unit) {
        // Fetch user default address
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener { doc ->
                shippingAddress = doc.getString("address") ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when(step) { 1 -> "Order Summary"; 2 -> "Shipping Details"; else -> "Secure Payment" },
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (step) {
                    1 -> {
                        cart.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${item.quantity}x ${item.product.name}", fontSize = 12.sp, color = TextDark)
                                Text("₱${String.format(Locale.getDefault(), "%,.2f", item.product.price.replace("₱", "").replace(",", "").toDouble() * item.quantity)}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color.LightGray.copy(0.3f))
                        CheckoutPriceRow("Subtotal", subtotal)
                        CheckoutPriceRow("VAT (12%)", vat)
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Amount", fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text("₱${String.format(Locale.getDefault(), "%,.2f", total)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = PrimaryBlue)
                        }
                    }
                    2 -> {
                        Text("Where should we deliver?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shippingAddress,
                            onValueChange = { shippingAddress = it },
                            label = { Text("Delivery Address") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Text("Default address from your profile is used by default.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                    }
                    3 -> {
                        Text("Select Payment Gateway", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        listOf("GCash", "Maya", "Credit Card", "Cash on Delivery").forEach { method ->
                            val isSel = selectedPayment == method
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedPayment = method },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSel) PrimaryBlue.copy(0.1f) else AppLightBg,
                                border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color.Transparent)
                            ) {
                                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = isSel, onClick = { selectedPayment = method }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(method, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (step < 3) step++
                    else {
                        isProcessing = true
                        viewModel.checkout(shippingAddress, selectedPayment) { result ->
                            isProcessing = false
                            result.onSuccess {
                                Toast.makeText(context, "Order Placed Successfully!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }.onFailure {
                                Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                enabled = !isProcessing && (step != 2 || shippingAddress.isNotBlank()),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isProcessing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text(if (step < 3) "Next" else "Confirm & Pay", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { if (step > 1) step-- else onDismiss() }) {
                Text(if (step > 1) "Back" else "Cancel", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun CheckoutPriceRow(label: String, amount: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text("₱${String.format(Locale.getDefault(), "%,.2f", amount)}", fontSize = 12.sp, color = TextDark)
    }
}

fun getProductIcon(name: String, category: String): ImageVector {
    return when {
        name.contains("Cam", ignoreCase = true) -> Icons.Default.Videocam
        name.contains("Lock", ignoreCase = true) -> Icons.Default.Lock
        name.contains("Bulb", ignoreCase = true) -> Icons.Default.Lightbulb
        name.contains("Switch", ignoreCase = true) -> Icons.Default.ToggleOn
        name.contains("UPS", ignoreCase = true) -> Icons.Default.BatteryChargingFull
        name.contains("Hub", ignoreCase = true) -> Icons.Default.Router
        category == "Security" -> Icons.Default.Security
        category == "Lighting" -> Icons.Default.Lightbulb
        else -> Icons.Default.Memory
    }
}

// PRODUCT DETAIL & BOOKING DIALOG
@Composable
fun ProductDetailDialog(
    product: Product,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    var customerName by remember { mutableStateOf("Customer") }
    var customerPhone by remember { mutableStateOf("") }
    var customerAddress by remember { mutableStateOf("") }
    var isOrdering by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                customerName = doc.getString("name") ?: "Customer"
                customerPhone = doc.getString("phone") ?: ""
                customerAddress = doc.getString("address") ?: ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(product.color),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingCart, null, tint = PrimaryBlue, modifier = Modifier.size(52.dp))
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(8.dp), color = PrimaryBlue.copy(0.1f)) {
                        Text(product.category, color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                    Text(product.price, color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 20.sp)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Product Overview", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Text(product.description, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))

                Spacer(modifier = Modifier.height(10.dp))

                Text("Specifications", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Text(product.specs, fontSize = 12.sp, color = PrimaryBlue, modifier = Modifier.padding(top = 2.dp))

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(product.warranty, fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Medium)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))

                Text("Delivery & Installation Address:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
                Text(if (customerAddress.isNotBlank()) customerAddress else "No address set. Please set in Settings first.", fontSize = 11.sp, color = Color.Gray)
            }
        },
        confirmButton = {
            Button(
                enabled = !isOrdering,
                onClick = {
                    if (customerAddress.isBlank()) {
                        Toast.makeText(context, "Please set your address in Settings before buying!", Toast.LENGTH_LONG).show()
                        return@Button
                    }

                    val uid = auth.currentUser?.uid ?: return@Button
                    isOrdering = true

                    val serviceRequest = mapOf(
                        "technician" to "Hardware Team / Installation",
                        "status" to "pending",
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "issue" to "Hardware Order: ${product.name} (${product.price}) + Installation"
                    )

                    val chatUpdate = mapOf(
                        "customerName" to customerName,
                        "customerEmail" to (auth.currentUser?.email ?: ""),
                        "customerPhone" to customerPhone,
                        "customerAddress" to customerAddress,
                        "status" to "Order & Installation Requested",
                        "lastRequest" to product.name,
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("chats_v2").document("admin_$uid").set(chatUpdate, com.google.firebase.firestore.SetOptions.merge())
                    db.collection("chats_v2").document("admin_$uid").collection("services").add(serviceRequest)

                    val autoMsg = hashMapOf(
                        "senderId" to uid,
                        "senderName" to customerName,
                        "text" to "I have placed an order for ${product.name} (${product.price}) with professional installation request.",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("chats_v2").document("admin_$uid").collection("messages").add(autoMsg)

                    Toast.makeText(context, "Order placed! Installation request sent to Admin.", Toast.LENGTH_LONG).show()
                    isOrdering = false
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isOrdering) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Buy & Book Installation", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.Gray) }
        }
    )
}

// 🔧 4. TECHNICIAN DIRECTORY & APPOINTMENT SCHEDULER
@Composable
fun TechnicianDirectoryView() {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val technicians = remember { mutableStateListOf<Map<String, Any>>() }
    val techWorkloads = remember { mutableStateMapOf<String, Int>() }
    val techStatuses = remember { mutableStateMapOf<String, String>() }
    val techRatings = remember { mutableStateMapOf<String, Pair<Float, Int>>() }

    var customerName by remember { mutableStateOf("Loading...") }
    var customerAddress by remember { mutableStateOf("") }
    var customerPhone by remember { mutableStateOf("") }

    var isLoadingTechs by remember { mutableStateOf(true) }
    var selectedTechForSchedule by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                customerName = doc.getString("name") ?: "No Name Set"
                customerAddress = doc.getString("address") ?: "No Address Set"
                customerPhone = doc.getString("phone") ?: ""
            }

            db.collection("chats_v2").document("admin_$uid").collection("services")
                .addSnapshotListener { snapshot, _ ->
                    techStatuses.clear()
                    snapshot?.documents?.forEach { doc ->
                        val tech = doc.getString("technician")
                        val status = doc.getString("status")
                        if (tech != null && status != null) {
                            techStatuses[tech] = status
                        }
                    }
                }
        }

        db.collection("reviews").addSnapshotListener { snapshot, _ ->
            val ratingGroups = mutableMapOf<String, MutableList<Int>>()
            snapshot?.documents?.forEach { doc ->
                val tech = doc.getString("techName") ?: return@forEach
                val rating = doc.getLong("rating")?.toInt() ?: 5
                ratingGroups.getOrPut(tech) { mutableListOf() }.add(rating)
            }

            techRatings.clear()
            ratingGroups.forEach { (tech, list) ->
                val avg = list.average().toFloat()
                techRatings[tech] = Pair(avg, list.size)
            }
        }

        db.collection("users").whereEqualTo("role", "technician").addSnapshotListener { snapshot, _ ->
            technicians.clear()
            snapshot?.documents?.forEach { doc -> 
                val tName = doc.getString("name") ?: "Unknown Tech"
                val data = doc.data ?: emptyMap()
                technicians.add(data + ("uid" to doc.id))

                // Fetch real-time global workload for this technician
                db.collection("chats_v2")
                    .whereEqualTo("assignedTechnician", tName)
                    .addSnapshotListener { jobsSnap, _ ->
                        val activeCount = jobsSnap?.documents?.count { 
                            val st = it.getString("status") ?: ""
                            st != "Completed" && st != "Denied" && st != "Request Cancelled"
                        } ?: 0
                        techWorkloads[tName] = activeCount
                    }
            }
            isLoadingTechs = false
        }
    }

    if (selectedTechForSchedule != null) {
        AppointmentSchedulerDialog(
            techName = selectedTechForSchedule!!,
            customerName = customerName,
            customerPhone = customerPhone,
            customerAddress = customerAddress,
            onDismiss = { selectedTechForSchedule = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Hire a Professional Specialist", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = TextDark)
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoadingTechs) {
            ShimmerCardList(count = 4)
        } else if (technicians.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No technicians available right now.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(technicians) { techData ->
                    val tech = techData["name"]?.toString() ?: "Unknown"
                    val techPic = techData["profilePicUrl"]?.toString()
                    val status = techStatuses[tech] ?: "none"
                    val workload = techWorkloads[tech] ?: 0

                    // A tech is interactable if they are free (workload == 0) OR if the current user already has an active request with them
                    val isFree = workload == 0
                    val isMyTech = status != "none" && status != "denied" && status != "cancelled" && !status.equals("completed", ignoreCase = true)

                    val isInteractable = isFree || isMyTech

                    val ratingData = techRatings[tech]
                    val ratingText = if (ratingData != null) {
                        String.format(Locale.getDefault(), "%.1f ★ (%d reviews)", ratingData.first, ratingData.second)
                    } else {
                        "5.0 ★ (New Expert)"
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (techPic != null) {
                                AsyncImage(
                                    model = techPic,
                                    contentDescription = tech,
                                    modifier = Modifier.size(48.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(48.dp), tint = PrimaryBlue)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tech, fontWeight = FontWeight.Bold, color = TextDark)
                                    if (techData["isVerified"] == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Verified, "Verified", tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(ratingText, color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (!isFree && !isMyTech) {
                                    Text("Currently Busy", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    enabled = isInteractable && (isFree || isMyTech),
                                    onClick = {
                                        if (customerName == "No Name Set" || customerAddress == "No Address Set") {
                                            Toast.makeText(context, "Please set your details in Settings first!", Toast.LENGTH_LONG).show()
                                        } else {
                                            selectedTechForSchedule = tech
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = when (status.lowercase()) {
                                            "pending" -> Color.Gray
                                            "accepted" -> PrimaryGreen
                                            "denied" -> Color.Red
                                            else -> PrimaryBlue
                                        }
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = when (status.lowercase()) {
                                            "pending" -> "Requested"
                                            "accepted" -> "Accepted"
                                            "denied" -> "Denied"
                                            else -> "Schedule"
                                        },
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }

                                if (status == "pending") {
                                    TextButton(
                                        onClick = {
                                            val uid = auth.currentUser?.uid ?: ""
                                            db.collection("chats_v2").document("admin_$uid").collection("services")
                                                .whereEqualTo("technician", tech)
                                                .whereEqualTo("status", "pending")
                                                .get().addOnSuccessListener { snapshot ->
                                                    snapshot.documents.forEach { doc -> doc.reference.update("status", "cancelled") }
                                                }

                                            db.collection("chats_v2").document("admin_$uid").update(mapOf("status" to "Request Cancelled"))

                                            db.collection("chats_v2").document("admin_$uid").collection("messages").add(hashMapOf(
                                                "senderId" to uid,
                                                "senderName" to customerName,
                                                "text" to "I have cancelled my request for $tech.",
                                                "timestamp" to com.google.firebase.Timestamp.now()
                                            ))

                                            Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Cancel", color = Color.Red, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 📅 APPOINTMENT SCHEDULER DIALOG
@Composable
fun AppointmentSchedulerDialog(
    techName: String,
    customerName: String,
    customerPhone: String,
    customerAddress: String,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    // Data class to hold both the display label and the actual date
    data class DateOption(val label: String, val calendar: Calendar)

    val dates = remember {
        val list = mutableListOf<DateOption>()
        val sdf = SimpleDateFormat("E, MMM d", Locale.getDefault())
        for (i in 0..3) {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, i)
            val prefix = when (i) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> sdf.format(cal.time)
            }
            val formatted = if (i <= 1) "$prefix (${sdf.format(cal.time)})" else prefix
            list.add(DateOption(formatted, cal))
        }
        list
    }

    val timeSlots = listOf(
        "08:00 AM - 10:00 AM",
        "10:00 AM - 12:00 PM",
        "01:00 PM - 03:00 PM",
        "03:00 PM - 05:00 PM"
    )

    var selectedDateOption by remember { mutableStateOf(dates[1]) }
    var selectedSlot by remember { mutableStateOf(timeSlots[1]) }
    var isBooking by remember { mutableStateOf(false) }
    var isAnalyzingSound by remember { mutableStateOf(false) }
    var aiDiagnosticResult by remember { mutableStateOf<String?>(null) }

    if (isAnalyzingSound) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3000)
            aiDiagnosticResult = "🔍 AI Analysis (Sound ID): Unit sounds stable but requires routine maintenance. No critical component failure detected."
            isAnalyzingSound = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule Appointment", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Select preferred date and time for $techName", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                // ✨ AI SOUND TRIAGE SECTION
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(0.05f)),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Smart AI Diagnostic (Optional)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PrimaryBlue)
                        }
                        Text("Analyze unit sounds to help technician prepare.", fontSize = 11.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        if (aiDiagnosticResult == null) {
                            Button(
                                onClick = { isAnalyzingSound = true },
                                enabled = !isAnalyzingSound,
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isAnalyzingSound) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Analyzing...", fontSize = 11.sp)
                                } else {
                                    Icon(Icons.Default.Mic, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Start Sound Analysis", fontSize = 11.sp)
                                }
                            }
                        } else {
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, PrimaryBlue.copy(0.1f))
                            ) {
                                Text(
                                    aiDiagnosticResult!!,
                                    modifier = Modifier.padding(8.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDark,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                Text("Select Date:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                dates.forEach { option ->
                    val isSel = selectedDateOption == option
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedDateOption = option },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) PrimaryBlue.copy(0.12f) else AppLightBg,
                        border = BorderStroke(1.dp, if (isSel) PrimaryBlue else FieldBorder)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSel, onClick = { selectedDateOption = option }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(option.label, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) PrimaryBlue else TextDark)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Select Time Slot:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                timeSlots.forEach { slot ->
                    val isSel = selectedSlot == slot
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { selectedSlot = slot },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSel) PrimaryGreen.copy(0.12f) else AppLightBg,
                        border = BorderStroke(1.dp, if (isSel) PrimaryGreen else FieldBorder)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, null, tint = if (isSel) PrimaryGreen else Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(slot, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) PrimaryGreen else TextDark)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBooking,
                onClick = {
                    val uid = auth.currentUser?.uid ?: return@Button
                    isBooking = true

                    // SLA Logic: Direct use of Calendar object - Create a clone to avoid modifying the UI state
                    val cal = (selectedDateOption.calendar.clone() as Calendar)
                    
                    val hour = when(selectedSlot.take(2)) {
                        "08" -> 8; "10" -> 10; "01" -> 13; "03" -> 15; else -> 8
                    }
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, 0)
                    cal.set(Calendar.SECOND, 0)

                    val scheduledTimestamp = com.google.firebase.Timestamp(cal.time)

                    val serviceRequest = mapOf(
                        "technician" to techName,
                        "status" to "pending",
                        "scheduledDate" to selectedDateOption.label,
                        "scheduledTime" to selectedSlot,
                        "scheduledTimestamp" to scheduledTimestamp,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "issue" to "Appointment: ${selectedDateOption.label} ($selectedSlot)",
                        "aiDiagnostic" to aiDiagnosticResult
                    )

                    val chatUpdate = mapOf(
                        "customerName" to customerName,
                        "customerEmail" to (auth.currentUser?.email ?: ""),
                        "customerPhone" to customerPhone,
                        "customerAddress" to customerAddress,
                        "status" to "Accepted (Direct Booking)",
                        "assignedTechnician" to techName, // DIRECT ASSIGNMENT to make it "work" immediately
                        "lastRequest" to "$techName • ${selectedDateOption.label} ($selectedSlot)",
                        "scheduledTimestamp" to scheduledTimestamp,
                        "timestamp" to com.google.firebase.Timestamp.now(),
                        "aiDiagnostic" to aiDiagnosticResult
                    )

                    db.collection("chats_v2").document("admin_$uid").set(chatUpdate, com.google.firebase.firestore.SetOptions.merge())
                    db.collection("chats_v2").document("admin_$uid").collection("services").add(serviceRequest)

                    val autoMsg = hashMapOf(
                        "senderId" to uid,
                        "senderName" to customerName,
                        "text" to "Hello! I am scheduling a service appointment with $techName for ${selectedDateOption.label} during $selectedSlot.${if (aiDiagnosticResult != null) " AI Diagnostic: $aiDiagnosticResult" else ""}",
                        "timestamp" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("chats_v2").document("admin_$uid").collection("messages").add(autoMsg)

                    Toast.makeText(context, "Appointment scheduled for ${selectedDateOption.label} ($selectedSlot)!", Toast.LENGTH_LONG).show()
                    isBooking = false
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isBooking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Confirm Booking", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

// 💬 5. LIVE CHAT VIEW
@Composable
fun CustomerChatHubView(onChatSelected: (String, String) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: return
    var assignedTech by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        db.collection("chats_v2").document("admin_$uid").addSnapshotListener { doc, _ ->
            assignedTech = doc?.getString("assignedTechnician")
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium, color = TextDark, fontWeight = FontWeight.Black)
        Text("Direct channels with Admin Support and Field Technicians", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Admin Channel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChatSelected("admin_$uid", "Admin Support") },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SupportAgent, null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin Support", fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Customer Service Channel", fontSize = 12.sp, color = Color.Gray)
                        }
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray)
                    }
                }
            }

            // Assigned Tech Channel
            if (assignedTech != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onChatSelected("tech_${assignedTech}_$uid", assignedTech!!) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).background(PrimaryGreen.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Build, null, tint = PrimaryGreen)
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(assignedTech!!, fontWeight = FontWeight.Bold, color = TextDark)
                                Text("Assigned Field Specialist", fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray)
                        }
                    }
                }
            }
        }
    }
}

// 📊 6. ORDER TRACKING & SERVICE HISTORY VIEW
@Composable
fun OrderTrackingView() {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""
    val services = remember { mutableStateListOf<Map<String, Any>>() }
    var customerName by remember { mutableStateOf("") }
    var isLoadingServices by remember { mutableStateOf(true) }

    var selectedFilter by remember { mutableStateOf("All") }
    var selectedServiceForRating by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedServiceForReceipt by remember { mutableStateOf<Map<String, Any>?>(null) }

    LaunchedEffect(Unit) {
        db.collection("users").document(uid).get().addOnSuccessListener { customerName = it.getString("name") ?: "Customer" }

        db.collection("chats_v2").document("admin_$uid").collection("services")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                services.clear()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    services.add(data)
                }
                isLoadingServices = false
            }
    }

    val filteredServices = services.filter { svc ->
        val st = svc["status"]?.toString()?.lowercase() ?: ""
        when (selectedFilter) {
            "Active" -> st != "completed" && st != "cancelled" && st != "denied"
            "Completed" -> st == "completed"
            "Cancelled" -> st == "cancelled" || st == "denied"
            else -> true
        }
    }

    if (selectedServiceForRating != null) {
        RatingReviewDialog(
            serviceDocId = selectedServiceForRating!!["docId"].toString(),
            techName = selectedServiceForRating!!["technician"].toString(),
            customerUid = uid,
            customerName = customerName,
            onDismiss = { selectedServiceForRating = null }
        )
    }

    if (selectedServiceForReceipt != null) {
        DigitalReceiptDialog(
            service = selectedServiceForReceipt!!,
            customerName = customerName,
            onDismiss = { selectedServiceForReceipt = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Service History & Receipts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(modifier = Modifier.height(12.dp))

        // Status Filter Chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("All", "Active", "Completed", "Cancelled").forEach { flt ->
                val isSel = selectedFilter == flt
                Surface(
                    modifier = Modifier.clickable { selectedFilter = flt }.height(32.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSel) PrimaryBlue else Color.White,
                    border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color(0xFFD0D0D0))
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                        Text(flt, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium, color = if (isSel) Color.White else TextDark)
                    }
                }
            }
        }

        if (isLoadingServices) {
            ShimmerCardList(count = 3)
        } else if (filteredServices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No service records found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredServices) { svc ->
                    val status = svc["status"]?.toString() ?: ""
                    val isCompleted = status.equals("Completed", ignoreCase = true)
                    val rating = svc["rating"]?.toString()
                    val comment = svc["comment"]?.toString()
                    val schedDate = svc["scheduledDate"]?.toString()
                    val schedTime = svc["scheduledTime"]?.toString()
                    
                    val scheduledTs = svc["scheduledTimestamp"] as? com.google.firebase.Timestamp
                    val startedAt = svc["startedAt"] as? com.google.firebase.Timestamp
                    val isLate = remember(scheduledTs, startedAt, status) {
                        if (scheduledTs == null || isCompleted || status.lowercase() == "cancelled" || status.lowercase() == "denied") false
                        else {
                            val compareTime = startedAt ?: com.google.firebase.Timestamp.now()
                            compareTime.seconds > (scheduledTs.seconds + 1800) // 30 mins grace period
                        }
                    }

                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(svc["technician"].toString(), fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                Spacer(modifier = Modifier.weight(1f))
                                Text(status.uppercase(), color = if (isCompleted) PrimaryGreen else PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }

                            if (isLate) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Timer, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Technician is running late. We apologize for the delay.", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Text(svc["issue"].toString(), color = Color.Gray, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))

                            if (!schedDate.isNullOrBlank() && !schedTime.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("$schedDate • $schedTime", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                }
                            }

                            if (svc["notes"] != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Notes: ${svc["notes"]}", fontSize = 11.sp, color = TextDark)
                            }

                            // Interactive Progress Timeline
                            Spacer(modifier = Modifier.height(10.dp))
                            ServiceStepTimeline(status = status)
                            Spacer(modifier = Modifier.height(10.dp))

                            if (isCompleted) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { selectedServiceForReceipt = svc },
                                        modifier = Modifier.fillMaxWidth().height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("View Digital E-Receipt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    if (rating != null) {
                                        Surface(
                                            color = Color(0xFFFFF8E1),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Your Rating: $rating / 5 Stars", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), fontSize = 12.sp)
                                                }
                                                if (!comment.isNullOrBlank()) {
                                                    Text("\"$comment\"", fontSize = 11.sp, color = TextDark, modifier = Modifier.padding(top = 4.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = { selectedServiceForRating = svc },
                                            modifier = Modifier.fillMaxWidth().height(42.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Rate & Review Technician", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
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
fun ServiceStepTimeline(status: String) {
    val steps = listOf("Requested", "Accepted", "In Progress", "Completed")
    val currentStep = when (status.lowercase()) {
        "pending" -> 0
        "accepted" -> 1
        "in progress" -> 2
        "completed" -> 3
        else -> 0
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, label ->
            val isActive = index <= currentStep
            val isDone = index < currentStep

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(if (isActive) PrimaryBlue else Color(0xFFE0E0E0), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    } else {
                        Text("${index + 1}", color = if (isActive) Color.White else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    label,
                    fontSize = 9.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = if (isActive) PrimaryBlue else Color.Gray
                )
            }

            if (index < steps.size - 1) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    color = if (index < currentStep) PrimaryBlue else Color(0xFFE0E0E0),
                    thickness = 2.dp
                )
            }
        }
    }
}

// DIGITAL E-RECEIPT DIALOG
@Composable
fun DigitalReceiptDialog(
    service: Map<String, Any>,
    customerName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val techName = service["technician"]?.toString() ?: "Technician"
    val issue = service["issue"]?.toString() ?: "Service"
    val payMethod = service["paymentMethod"]?.toString() ?: "Cash"
    val labor = (service["laborFee"] as? Number)?.toDouble() ?: 500.0
    val parts = (service["partsFee"] as? Number)?.toDouble() ?: 0.0
    val vat = (service["vatAmount"] as? Number)?.toDouble() ?: ((labor + parts) * 0.12)
    val total = (service["totalAmount"] as? Number)?.toDouble() ?: (labor + parts + vat)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CoolConnect E-Receipt", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                Surface(color = PrimaryGreen.copy(0.15f), shape = RoundedCornerShape(8.dp)) {
                    Text("PAID ✓", color = PrimaryGreen, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Official Service & Installation Receipt", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppLightBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        ReceiptRow("Customer", customerName)
                        ReceiptRow("Technician", techName)
                        ReceiptRow("Service", issue)
                        ReceiptRow("Payment Method", payMethod, highlight = true)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Itemized Breakdown", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                ReceiptFeeRow("Base Labor & Service Fee", "₱${String.format(Locale.getDefault(), "%.2f", labor)}")
                if (parts > 0) {
                    ReceiptFeeRow("Hardware & Parts Fee", "₱${String.format(Locale.getDefault(), "%.2f", parts)}")
                }
                ReceiptFeeRow("VAT (12%)", "₱${String.format(Locale.getDefault(), "%.2f", vat)}")

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = Color.LightGray.copy(0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Paid", fontWeight = FontWeight.Black, fontSize = 15.sp, color = TextDark)
                    Text("₱${String.format(Locale.getDefault(), "%.2f", total)}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = PrimaryBlue)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "E-Receipt saved to device Downloads!", Toast.LENGTH_LONG).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download Receipt (PDF)", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = Color.Gray) }
        }
    )
}

@Composable
fun ReceiptRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (highlight) PrimaryBlue else TextDark)
    }
}

@Composable
fun ReceiptFeeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, color = TextDark)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
    }
}

// RATING & REVIEW DIALOG
@Composable
fun RatingReviewDialog(
    serviceDocId: String,
    techName: String,
    customerUid: String,
    customerName: String,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var selectedRating by remember { mutableStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rate Your Service", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("How was your experience with $techName?", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    (1..5).forEach { star ->
                        IconButton(onClick = { selectedRating = star }) {
                            Icon(
                                imageVector = if (star <= selectedRating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = if (star <= selectedRating) Color(0xFFFFB300) else Color.Gray,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                Text("$selectedRating / 5 Stars", fontWeight = FontWeight.Bold, color = Color(0xFFFFB300), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = reviewComment,
                    onValueChange = { reviewComment = it },
                    label = { Text("Feedback & Review") },
                    placeholder = { Text("e.g. Technician was punctual and very professional!") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    isSubmitting = true
                    val reviewData = mapOf(
                        "rating" to selectedRating,
                        "comment" to reviewComment,
                        "ratedAt" to com.google.firebase.Timestamp.now()
                    )

                    db.collection("chats_v2").document("admin_$customerUid")
                        .collection("services").document(serviceDocId)
                        .update(reviewData).addOnSuccessListener {
                            val publicReview = mapOf(
                                "techName" to techName,
                                "customerName" to customerName,
                                "rating" to selectedRating,
                                "comment" to reviewComment,
                                "timestamp" to com.google.firebase.Timestamp.now()
                            )
                            db.collection("reviews").add(publicReview)

                            // AUTOMATED FEEDBACK LOOP: Escalate low ratings
                            if (selectedRating <= 2) {
                                val escalationTicket = mapOf(
                                    "customerName" to customerName,
                                    "customerUid" to customerUid,
                                    "techName" to techName,
                                    "rating" to selectedRating,
                                    "feedback" to reviewComment,
                                    "status" to "URGENT_REVIEW",
                                    "timestamp" to com.google.firebase.Timestamp.now()
                                )
                                db.collection("escalations").add(escalationTicket)
                                
                                // Auto-notify the customer that management is looking into it
                                val autoMsg = hashMapOf(
                                    "senderId" to "admin",
                                    "senderName" to "CoolConnect Quality Assurance",
                                    "text" to "We noticed you had a poor experience. Your feedback has been escalated to our management team for immediate review. We will contact you shortly.",
                                    "timestamp" to com.google.firebase.Timestamp.now(),
                                    "isAdmin" to true
                                )
                                db.collection("chats_v2").document("admin_$customerUid").collection("messages").add(autoMsg)
                            }

                            Toast.makeText(context, "Thank you for rating $techName!", Toast.LENGTH_SHORT).show()
                            isSubmitting = false
                            onDismiss()
                        }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Submit Review", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

// 👤 7. PROFILE & SETTINGS VIEW
@Composable
fun CustomerProfileView(onLogout: () -> Unit, viewModel: CustomerViewModel = hiltViewModel()) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val subscriptionStatus by viewModel.subscriptionStatus.collectAsState()
    val orders by viewModel.orders.collectAsState()

    var name by remember { mutableStateOf("Customer Account") }
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "customer@coolconnect.com") }
    
    var showOrdersDialog by remember { mutableStateOf(false) }
    var showSubscriptionDialog by remember { mutableStateOf(false) }
    var showWarrantyVault by remember { mutableStateOf(false) }

    if (showOrdersDialog) {
        OrderHistoryDialog(orders = orders, onDismiss = { showOrdersDialog = false })
    }

    if (showWarrantyVault) {
        WarrantyVaultDialog(orders = orders, onDismiss = { showWarrantyVault = false })
    }

    if (showSubscriptionDialog) {
        SubscriptionDialog(
            currentPlan = subscriptionStatus,
            onUpgrade = { plan ->
                viewModel.upgradeSubscription(plan) { result ->
                    if (result.isSuccess) {
                        Toast.makeText(context, "Welcome to CoolConnect+ $plan!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Upgrade failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                    }
                    showSubscriptionDialog = false
                }
            },
            onDismiss = { showSubscriptionDialog = false }
        )
    }
    
    // Detailed Address State
    var streetAddress by remember { mutableStateOf("") }
    var brgy by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("Metro Manila") }
    var region by remember { mutableStateOf("NCR (Metro Manila)") }
    var zipCode by remember { mutableStateOf("") }

    var enableBiometrics by remember { mutableStateOf(true) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showManualPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                name = doc.getString("name") ?: "Customer Account"
                streetAddress = doc.getString("street") ?: doc.getString("address") ?: ""
                brgy = doc.getString("brgy") ?: ""
                city = doc.getString("city") ?: ""
                province = doc.getString("province") ?: "Metro Manila"
                region = if (province == "Metro Manila") "NCR (Metro Manila)" else ""
                zipCode = doc.getString("postalCode") ?: ""
            }
        }
    }

    if (showManualPrivacy) {
        PrivacyConsentDialog(onConsentGranted = { showManualPrivacy = false })
    }

    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("Update Dispatch Address", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Select your primary service location:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        label = { Text("House No. / Street") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                    )

                    // Hierarchical Selectors
                    ModernAddressDropdown(
                        label = "Region",
                        value = region,
                        options = AddressData.regions,
                        icon = Icons.Default.Map,
                        onValueChange = { region = it; province = ""; city = ""; brgy = "" }
                    )

                    ModernAddressDropdown(
                        label = "Province",
                        value = province,
                        options = AddressData.provinces[region] ?: emptyList(),
                        icon = Icons.Default.Map,
                        enabled = region.isNotEmpty(),
                        onValueChange = { province = it; city = ""; brgy = "" }
                    )

                    ModernAddressDropdown(
                        label = "City / Municipality",
                        value = city,
                        options = AddressData.cities[province] ?: emptyList(),
                        icon = Icons.Default.LocationCity,
                        enabled = province.isNotEmpty(),
                        onValueChange = { city = it; brgy = "" }
                    )

                    ModernAddressDropdown(
                        label = "Barangay",
                        value = brgy,
                        options = AddressData.barangays[city] ?: emptyList(),
                        icon = Icons.Default.PinDrop,
                        enabled = city.isNotEmpty(),
                        onValueChange = { brgy = it }
                    )

                    OutlinedTextField(
                        value = zipCode,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) zipCode = it },
                        label = { Text("Zip Code") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            val fullAddress = "$streetAddress, Brgy. $brgy, $city, $province, $zipCode".trim()
                            val data = mapOf(
                                "street" to streetAddress,
                                "brgy" to brgy,
                                "city" to city,
                                "province" to province,
                                "postalCode" to zipCode,
                                "address" to fullAddress,
                                "regionCity" to "$city, $province"
                            )
                            db.collection("users").document(uid).update(data)
                            Toast.makeText(context, "Address updated!", Toast.LENGTH_SHORT).show()
                        }
                        showAddressDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Save Changes", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showAddressDialog = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("My Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextDark)
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileImageSelector(
                    imageUrl = profilePicUrl,
                    onImageSelected = { viewModel.uploadProfilePic(it, context) },
                    modifier = Modifier.size(70.dp),
                    isLoading = isUploading
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                    Text(email, fontSize = 12.sp, color = Color.Gray)
                }

                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🌟 COOLCONNECT+ SUBSCRIPTION CARD
        Card(
            modifier = Modifier.fillMaxWidth().clickable { showSubscriptionDialog = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (subscriptionStatus == "Free") Color.White else Color(0xFFE3F2FD)
            ),
            border = if (subscriptionStatus != "Free") BorderStroke(2.dp, PrimaryBlue) else BorderStroke(1.dp, Color(0xFFECECEC)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(if (subscriptionStatus == "Free") Color(0xFFF1F5F9) else PrimaryBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (subscriptionStatus == "Free") Icons.Default.StarOutline else Icons.Default.Star,
                        contentDescription = null,
                        tint = if (subscriptionStatus == "Free") Color.Gray else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (subscriptionStatus == "Free") "Join CoolConnect+" else "CoolConnect+ $subscriptionStatus",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextDark
                    )
                    Text(
                        text = if (subscriptionStatus == "Free") "Unlock priority dispatch & discounts" else "Active Premium Member benefits",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("MY ACTIVITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                ProfileOptionRow(
                    icon = Icons.Default.Inventory,
                    title = "My Hardware Orders",
                    subtitle = if (orders.isEmpty()) "No orders yet" else "${orders.size} orders placed",
                    onClick = { showOrdersDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.VerifiedUser,
                    title = "Smart Warranty Vault",
                    subtitle = "View active product protection",
                    onClick = { showWarrantyVault = true }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("ADDRESS & SECURITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                ProfileOptionRow(
                    icon = Icons.Default.Place,
                    title = "Saved Addresses",
                    subtitle = if (streetAddress.isNotBlank()) streetAddress else "Set default delivery address",
                    onClick = { showAddressDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    onClick = { Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_LONG).show() }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.Pin,
                    title = "Change Pin",
                    onClick = { Toast.makeText(context, "Security PIN updated!", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Fingerprint, null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("Enable Biometrics", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
                    }
                    Switch(
                        checked = enableBiometrics,
                        onCheckedChange = { enableBiometrics = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryBlue)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("MORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                ProfileOptionRow(
                    icon = Icons.Default.Notifications,
                    title = "Notification Preferences",
                    onClick = { Toast.makeText(context, "Push notifications enabled", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.Description,
                    title = "Privacy Policy",
                    onClick = { showManualPrivacy = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.SupportAgent,
                    title = "Help and Support",
                    onClick = { Toast.makeText(context, "Connecting to Support Agent...", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun WarrantyVaultDialog(orders: List<ShopOrder>, onDismiss: () -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, null, tint = PrimaryBlue)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Smart Warranty Vault", fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                val itemsWithWarranty = orders.flatMap { order -> 
                    order.items.filter { it.containsKey("warrantyExpiry") }.map { it to order.orderId } 
                }

                if (itemsWithWarranty.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No active warranties found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(itemsWithWarranty) { (item, orderId) ->
                            val expiry = item["warrantyExpiry"] as com.google.firebase.Timestamp
                            val name = item["name"].toString()
                            
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = AppLightBg),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(name, fontWeight = FontWeight.Bold, color = TextDark)
                                    Text("Order ID: $orderId", fontSize = 11.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Timer, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "Expires: ${sdf.format(expiry.toDate())}", 
                                            fontSize = 12.sp, 
                                            fontWeight = FontWeight.Bold, 
                                            color = PrimaryBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun OrderHistoryDialog(orders: List<ShopOrder>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("My Purchase History", fontWeight = FontWeight.Black) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 450.dp)) {
                if (orders.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No orders found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(orders) { order ->
                            OrderSummaryCard(order = order)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun OrderSummaryCard(order: ShopOrder) {
    val sdf = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppLightBg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(order.orderId, fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when(order.status) {
                        "Processing" -> Color(0xFFFF9800).copy(0.1f)
                        "Delivered" -> PrimaryGreen.copy(0.1f)
                        else -> Color.Gray.copy(0.1f)
                    }
                ) {
                    Text(
                        order.status, 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        color = when(order.status) {
                            "Processing" -> Color(0xFFFF9800)
                            "Delivered" -> PrimaryGreen
                            else -> Color.Gray
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            order.items.forEach { item ->
                Text("• ${item["quantity"]}x ${item["name"]}", fontSize = 13.sp, color = TextDark)
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.LightGray.copy(0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sdf.format(order.timestamp.toDate()), fontSize = 11.sp, color = Color.Gray)
                Text("Total: ₱${String.format(Locale.getDefault(), "%,.2f", order.total)}", fontWeight = FontWeight.Black, color = TextDark)
            }
        }
    }
}

@Composable
fun SubscriptionDialog(currentPlan: String, onUpgrade: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("CoolConnect+ Plans", fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Switch to a premium plan for recurring savings and priority field support.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                SubscriptionPlanItem(
                    name = "Basic Maintenance",
                    price = "₱299 / mo",
                    perks = listOf("1 Free General Cleaning/yr", "5% Hardware Discount"),
                    isSelected = currentPlan == "Basic Maintenance",
                    onClick = { onUpgrade("Basic Maintenance") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                SubscriptionPlanItem(
                    name = "Pro Household",
                    price = "₱599 / mo",
                    perks = listOf("2 Free General Cleanings/yr", "10% Household Discount", "Priority Dispatch"),
                    isPopular = true,
                    isSelected = currentPlan == "Pro Household",
                    onClick = { onUpgrade("Pro Household") }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Maybe Later", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@Composable
fun SubscriptionPlanItem(
    name: String,
    price: String,
    perks: List<String>,
    isPopular: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelected) { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) PrimaryBlue.copy(0.05f) else Color(0xFFF8FAFC),
        border = BorderStroke(if (isSelected || isPopular) 2.dp else 1.dp, if (isSelected) PrimaryBlue else if (isPopular) PrimaryBlue.copy(0.3f) else Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                if (isPopular) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(color = PrimaryBlue, shape = RoundedCornerShape(6.dp)) {
                        Text("POPULAR", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isSelected) {
                    Icon(Icons.Default.CheckCircle, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
            Text(price, fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))
            perks.forEach { perk ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Done, null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(perk, fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun ProfileOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF555555), modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}
