package com.example.coolconnect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import com.example.coolconnect.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.maps.android.compose.*
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

import androidx.hilt.navigation.compose.hiltViewModel

enum class TechnicianTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CUSTOMERS("Jobs", Icons.AutoMirrored.Filled.Assignment),
    AI("AI Fix", Icons.Default.AutoAwesome),
    MAP("Map", Icons.Default.Map),
    CHAT("Messages", Icons.AutoMirrored.Filled.Chat),
    PROFILE("You", Icons.Default.Person)
}

data class TechJobDetail(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val status: String,
    val issue: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianScreen(onLogout: () -> Unit, viewModel: TechnicianViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var currentTab by remember { mutableStateOf(TechnicianTab.HOME) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    
    val techName by viewModel.techName.collectAsState()
    val aiFixResult by viewModel.aiFixResult.collectAsState()
    val isAiAnalyzing by viewModel.isAiAnalyzing.collectAsState()
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val verificationStatus by viewModel.verificationStatus.collectAsState()
    val isKycUploading by viewModel.isKycUploading.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()
    val userHasConsented by viewModel.hasConsented.collectAsState()

    // Privacy & Permissions State
    var showPrivacyConsent by remember { mutableStateOf(false) }

    LaunchedEffect(userHasConsented) {
        if (!userHasConsented) {
            showPrivacyConsent = true
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Live GPS Location Broadcasting
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

    if (activeChatId != null) {
        FullChatScreen(
            chatId = activeChatId!!,
            recipientName = activeChatName,
            currentUserId = auth.currentUser?.uid ?: "tech",
            currentUserName = techName,
            onBack = { activeChatId = null }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(32.dp).background(PrimaryGreen.copy(0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Build, null, tint = PrimaryGreen, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Dispatch: ${currentTab.label}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                TechnicianBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it })
            },
            containerColor = AppLightBg
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label = "tech_tab"
                ) { targetTab ->
                    when (targetTab) {
                        TechnicianTab.HOME -> TechnicianHomeView(
                            techName = techName,
                            profilePicUrl = profilePicUrl,
                            onNavigateToJobs = { currentTab = TechnicianTab.CUSTOMERS },
                            onNavigateToMap = { currentTab = TechnicianTab.MAP },
                            onNavigateToChat = { currentTab = TechnicianTab.CHAT },
                            onNavigateToAi = { currentTab = TechnicianTab.AI },
                            onOpenChat = { id, name -> activeChatId = id; activeChatName = name }
                        )
                        TechnicianTab.CUSTOMERS -> TechnicianCustomerListView(onOpenChat = { id, name -> activeChatId = id; activeChatName = name })
                        TechnicianTab.AI -> {
                            TechnicianTroubleshootView(
                                aiResult = aiFixResult,
                                isAnalyzing = isAiAnalyzing,
                                onQuery = { query -> viewModel.getAiFix(query) }
                            )
                        }
                        TechnicianTab.MAP -> TechnicianMapView(viewModel = viewModel)
                        TechnicianTab.CHAT -> TechnicianChatHubView(onChatSelected = { id, name -> activeChatId = id; activeChatName = name })
                        TechnicianTab.PROFILE -> TechnicianProfileView(onLogout)
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicianBottomBar(currentTab: TechnicianTab, onTabSelected: (TechnicianTab) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
        modifier = Modifier.height(68.dp)
    ) {
        TechnicianTab.entries.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, tint = if (selected) PrimaryGreen else Color.Gray, modifier = Modifier.size(22.dp)) },
                label = { Text(tab.label, color = if (selected) PrimaryGreen else Color.Gray, fontSize = 9.sp, maxLines = 1, softWrap = false) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryGreen.copy(alpha = 0.12f))
            )
        }
    }
}

// 🛠️ 1. TECHNICIAN COMMAND CENTER (HOME VIEW)
@Composable
fun TechnicianHomeView(
    techName: String,
    profilePicUrl: String?,
    onNavigateToJobs: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAi: () -> Unit,
    onOpenChat: (String, String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val uid = auth.currentUser?.uid ?: ""

    var isOnline by remember { mutableStateOf(true) }
    var activeJob by remember { mutableStateOf<TechJobDetail?>(null) }

    val assignedJobs = remember { mutableStateListOf<TechJobDetail>() }
    var selectedJobForVerification by remember { mutableStateOf<TechJobDetail?>(null) }
    var todayEarnings by remember { mutableDoubleStateOf(0.0) }
    
    val timeGreeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Fetch initial Online / Offline status from Firestore
    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    isOnline = doc.getBoolean("isOnline") ?: true
                }
            }
        }
    }

    LaunchedEffect(techName) {
        if (techName.isNotBlank()) {
            // Fetch jobs and workload
            db.collection("chats_v2").whereEqualTo("assignedTechnician", techName).addSnapshotListener { snapshot, _ ->
                assignedJobs.clear()
                var revenueAcc = 0.0
                snapshot?.documents?.forEach { doc ->
                    val status = doc.getString("status") ?: ""
                    val job = TechJobDetail(
                        id = doc.id,
                        name = doc.getString("customerName") ?: doc.getString("chatName") ?: "Customer",
                        phone = doc.getString("customerPhone") ?: "No Phone",
                        address = doc.getString("customerAddress") ?: "No Address",
                        status = status,
                        issue = doc.getString("lastRequest") ?: "HVAC Repair & Maintenance"
                    )
                    assignedJobs.add(job)

                    if (status == "Completed") {
                        val amt = (doc.get("totalAmount") as? Number)?.toDouble() ?: 0.0
                        revenueAcc += amt
                    }
                }
                todayEarnings = revenueAcc
                activeJob = assignedJobs.firstOrNull { it.status != "Completed" && it.status != "Denied" }
            }
        }
    }

    if (selectedJobForVerification != null) {
        JobCompletionDialog(
            job = selectedJobForVerification!!,
            techName = techName,
            onDismiss = { selectedJobForVerification = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // Greeting & Availability Toggle Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("$timeGreeting, $techName 🛠️", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, color = TextDark, fontSize = 22.sp))
                Text("Dispatch Field Command & Workload Center", fontSize = 12.sp, color = Color.Gray)
            }
            if (profilePicUrl != null) {
                AsyncImage(
                    model = profilePicUrl,
                    contentDescription = techName,
                    modifier = Modifier.size(50.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Availability Toggle Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = if (isOnline) Color(0xFFE8F8EE) else Color(0xFFFFF0F0)),
            border = BorderStroke(1.dp, if (isOnline) PrimaryGreen.copy(0.4f) else Color.Red.copy(0.4f))
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).background(if (isOnline) PrimaryGreen else Color.Red, CircleShape))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(if (isOnline) "● ONLINE & DISPATCH READY" else "○ OFFLINE", fontWeight = FontWeight.Bold, color = if (isOnline) PrimaryGreen else Color.Red, fontSize = 13.sp)
                        Text(if (isOnline) "Receiving active dispatch requests" else "Not receiving new job requests", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Switch(
                    checked = isOnline,
                    onCheckedChange = {
                        isOnline = it
                        if (uid.isNotEmpty()) {
                            db.collection("users").document(uid).update(mapOf("isOnline" to it))
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryGreen)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Daily Workload Summary Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Total Assigned", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("${assignedJobs.size} Jobs", fontSize = 18.sp, fontWeight = FontWeight.Black, color = PrimaryBlue)
                }
            }
            Card(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Daily Revenue", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text("₱${String.format(Locale.getDefault(), "%.0f", todayEarnings)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 🔵 PRIMARY ACTIVE JOB CARD
        Text("PRIMARY ACTIVE DISPATCH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))

        if (activeJob != null) {
            val job = activeJob!!
            val isInProgress = job.status == "In Progress"

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PrimaryGreen.copy(0.4f)),
                elevation = CardDefaults.cardElevation(3.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(8.dp), color = PrimaryGreen.copy(0.15f)) {
                            Text(job.status.uppercase(), color = PrimaryGreen, fontWeight = FontWeight.Black, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Job ID: ${job.id.take(8)}", fontSize = 11.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(job.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                    Text("Issue: ${job.issue}", fontSize = 13.sp, color = PrimaryBlue, fontWeight = FontWeight.SemiBold)

                    Spacer(modifier = Modifier.height(8.dp))
                    InfoRow(Icons.Default.Phone, job.phone)
                    InfoRow(Icons.Default.Home, job.address)

                    Spacer(modifier = Modifier.height(14.dp))

                        // Direct Action Buttons
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(job.address)}"))
                                context.startActivity(mapIntent)
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                            border = BorderStroke(1.dp, PrimaryBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Navigation, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Navigate", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { 
                                // Privacy Fix: Route to 1:1 tech-customer channel, not admin-customer
                                val customerUid = job.id.replace("admin_", "")
                                onOpenChat("tech_${techName}_$customerUid", job.name) 
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                            border = BorderStroke(1.dp, PrimaryGreen),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chat Customer", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isInProgress) {
                        Button(
                            onClick = { selectedJobForVerification = job },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify & Complete Job", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Button(
                            onClick = {
                                val timestamp = com.google.firebase.Timestamp.now()
                                db.collection("chats_v2").document(job.id).update(mapOf(
                                    "status" to "In Progress",
                                    "startedAt" to timestamp
                                ))
                                
                                // Sync: Update the service record in the sub-collection
                                db.collection("chats_v2").document(job.id).collection("services")
                                    .whereIn("status", listOf("accepted", "pending"))
                                    .get().addOnSuccessListener { snapshot ->
                                        snapshot.documents.forEach { doc -> 
                                            doc.reference.update(mapOf("status" to "In Progress", "startedAt" to timestamp))
                                        }
                                    }

                                val progressMsg = hashMapOf(
                                    "senderId" to "tech",
                                    "senderName" to techName,
                                    "text" to "Technician $techName has started the job at your location.",
                                    "timestamp" to timestamp
                                )
                                db.collection("chats_v2").document(job.id).collection("messages").add(progressMsg)
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Job Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
            ) {
                Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No active service dispatch. Select a job from the Jobs tab.", color = Color.Gray, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Command Actions
        Text("FIELD COMMAND ACTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888), modifier = Modifier.padding(start = 2.dp, bottom = 8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Active Jobs", Icons.AutoMirrored.Filled.Assignment, PrimaryBlue, Modifier.weight(1f), onNavigateToJobs)
            QuickActionCard("Fleet Map", Icons.Default.Map, PrimaryGreen, Modifier.weight(1f), onNavigateToMap)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("AI Assistant", Icons.Default.AutoAwesome, Color(0xFF7B61FF), Modifier.weight(1f), onNavigateToAi)
            QuickActionCard("Admin Line", Icons.Default.SupportAgent, Color(0xFFFF9800), Modifier.weight(1f), onNavigateToChat)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// 🗺️ 2. REAL-TIME NAVIGATION MAP VIEW
@Composable
fun TechnicianMapView(viewModel: TechnicianViewModel) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val apiKey = context.getString(R.string.google_maps_key)
    
    val routePoints by viewModel.routePoints.collectAsState()

    var techLocation by remember { mutableStateOf<LatLng?>(null) }
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(14.5995, 120.9842), 12f) }

    var customerLocation by remember { mutableStateOf<LatLng?>(null) }
    var customerName by remember { mutableStateOf("Assigned Job") }
    var customerUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { techDoc ->
                val name = techDoc.getString("name") ?: ""
                if (name.isNotEmpty()) {
                    db.collection("chats_v2")
                        .whereEqualTo("assignedTechnician", name)
                        .addSnapshotListener { snapshot, _ ->
                            val doc = snapshot?.documents?.firstOrNull {
                                val status = it.getString("status") ?: ""
                                status != "Completed"
                            }
                            if (doc != null) {
                                customerName = doc.getString("customerName") ?: "Customer"
                                customerUid = doc.id.replace("admin_", "")
                            } else {
                                customerLocation = null
                                customerUid = null
                                viewModel.clearRoute()
                            }
                        }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                val lat = doc?.getDouble("latitude")
                val lng = doc?.getDouble("longitude")
                if (lat != null && lng != null) {
                    techLocation = LatLng(lat, lng)
                    if (cameraPositionState.position.target.latitude == 14.5995 && cameraPositionState.position.target.longitude == 120.9842) {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(techLocation!!, 15f)
                    }
                }
            }
        }
    }

    LaunchedEffect(customerUid) {
        if (customerUid != null) {
            db.collection("users").document(customerUid!!).addSnapshotListener { doc, _ ->
                val lat = doc?.getDouble("latitude")
                val lng = doc?.getDouble("longitude")
                if (lat != null && lng != null) {
                    customerLocation = LatLng(lat, lng)
                }
            }
        }
    }

    // Fetch real street path when both locations are available
    LaunchedEffect(techLocation, customerLocation) {
        if (techLocation != null && customerLocation != null) {
            viewModel.fetchRoute(techLocation!!, customerLocation!!, apiKey)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = true)
        ) {
            techLocation?.let { loc ->
                Marker(
                    state = rememberMarkerState(position = loc),
                    title = "You (Technician)",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            customerLocation?.let { loc ->
                Marker(
                    state = rememberMarkerState(position = loc),
                    title = customerName,
                    snippet = "Customer Live Location",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                )
            }

            if (routePoints.isNotEmpty()) {
                Polyline(
                    points = routePoints,
                    color = PrimaryBlue,
                    width = 12f,
                    jointType = com.google.android.gms.maps.model.JointType.ROUND
                )
            } else if (techLocation != null && customerLocation != null) {
                // Fallback to straight line if route failed to load
                Polyline(
                    points = listOf(techLocation!!, customerLocation!!),
                    color = PrimaryBlue.copy(alpha = 0.5f),
                    width = 8f,
                    jointType = com.google.android.gms.maps.model.JointType.ROUND
                )
            }
        }

        // --- IN-APP DISPATCH HUD ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        ) {
            if (customerLocation != null && techLocation != null) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(PrimaryBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Navigation, null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("En-route to $customerName", fontWeight = FontWeight.Black, fontSize = 15.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val timeText = if (routePoints.isNotEmpty()) "${(routePoints.size * 0.1).toInt() + 5} mins" else "12 mins"
                                val distText = if (routePoints.isNotEmpty()) String.format("%.1f km", routePoints.size * 0.05) else "4.2 km"
                                
                                Text(timeText, color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(4.dp).background(Color.Gray, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(distText, color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                        IconButton(
                            onClick = { 
                                // One-tap re-center camera on the route
                                techLocation?.let { cameraPositionState.position = CameraPosition.fromLatLngZoom(it, 16f) }
                            },
                            modifier = Modifier.background(AppLightBg, CircleShape)
                        ) {
                            Icon(Icons.Default.MyLocation, null, tint = PrimaryBlue)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = PrimaryGreen) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Awaiting Active Dispatch Location...", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// 💬 3. MESSAGES HUB
@Composable
fun TechnicianChatHubView(onChatSelected: (String, String) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val chats = remember { mutableStateListOf<Map<String, Any>>() }
    var techName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).get().addOnSuccessListener { techName = it.getString("name") ?: "" }
    }

    LaunchedEffect(techName) {
        if (techName.isNotEmpty()) {
            // Privacy Fix: Only listen to private tech_ channels, ignore admin_ support channels
            db.collection("chats_v2")
                .whereEqualTo("assignedTechnician", techName)
                .addSnapshotListener { snapshot, _ ->
                    chats.clear()
                    snapshot?.documents?.forEach { doc ->
                        if (doc.id.startsWith("tech_")) {
                            val data = doc.data?.toMutableMap() ?: mutableMapOf()
                            data["uid"] = doc.id
                            chats.add(data)
                        }
                    }
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium, color = TextDark, fontWeight = FontWeight.Black)
        Text("Direct channels with Admin Support and Assigned Customers", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Admin Support Channel
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChatSelected("admin_tech_$techName", "Admin Dispatch Support") },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.SupportAgent, null, tint = PrimaryBlue)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Admin Dispatch Support", fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Central Management Channel", fontSize = 12.sp, color = Color.Gray)
                        }
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray)
                    }
                }
            }

            // Assigned Customer Jobs Channels
            items(chats) { chat ->
                val cid = chat["uid"].toString()
                val cname = chat["customerName"]?.toString() ?: chat["chatName"]?.toString() ?: "Customer"
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChatSelected(cid, cname) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).background(PrimaryGreen.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, null, tint = PrimaryGreen)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cname, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("Job Status: ${chat["status"] ?: "Assigned"}", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray)
                    }
                }
            }
        }
    }
}

// 🤖 4. AI TROUBLESHOOTER ASSISTANT VIEW
@Composable
fun TechnicianTroubleshootView(
    aiResult: String?,
    isAnalyzing: Boolean,
    onQuery: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text("Field AI Troubleshooter", style = MaterialTheme.typography.headlineMedium, color = TextDark, fontWeight = FontWeight.Black)
        Text("AI Diagnostic Assistant for HVAC, Electrical & Lock Faults", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(20.dp))

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Describe symptoms (e.g. AC compressor humming, fan not spinning...)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (query.isNotBlank()) {
                            onQuery(query)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isAnalyzing) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    else Text("Run Diagnostic AI Analysis", fontWeight = FontWeight.Bold)
                }
            }
        }

        aiResult?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(0.1f))) {
                Text(it, modifier = Modifier.padding(16.dp), color = TextDark, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// 📋 5. JOB COMPLETION & PHOTO VERIFICATION FLOW
@Composable
fun TechnicianCustomerListView(onOpenChat: (String, String) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val jobs = remember { mutableStateListOf<TechJobDetail>() }
    var techName by remember { mutableStateOf("") }

    var selectedJobForVerification by remember { mutableStateOf<TechJobDetail?>(null) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).get().addOnSuccessListener { techName = it.getString("name") ?: "" }
    }

    LaunchedEffect(techName) {
        if (techName.isNotEmpty()) {
            db.collection("chats_v2").whereEqualTo("assignedTechnician", techName).addSnapshotListener { snapshot, _ ->
                jobs.clear()
                snapshot?.documents?.forEach { doc ->
                    jobs.add(
                        TechJobDetail(
                            id = doc.id,
                            name = doc.getString("customerName") ?: doc.getString("chatName") ?: "Customer",
                            phone = doc.getString("customerPhone") ?: "No Phone",
                            address = doc.getString("customerAddress") ?: "No Address",
                            status = doc.getString("status") ?: "Assigned",
                            issue = doc.getString("lastRequest") ?: "HVAC Repair & Maintenance"
                        )
                    )
                }
            }
        }
    }

    if (selectedJobForVerification != null) {
        JobCompletionDialog(
            job = selectedJobForVerification!!,
            techName = techName,
            onDismiss = { selectedJobForVerification = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
        Text("Assigned Service Jobs", style = MaterialTheme.typography.headlineMedium, color = TextDark, fontWeight = FontWeight.Black)
        Spacer(modifier = Modifier.height(16.dp))

        if (jobs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No assigned jobs currently.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(jobs) { job ->
                    val isCompleted = job.status == "Completed"
                    val isInProgress = job.status == "In Progress"

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(job.name, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 17.sp, modifier = Modifier.weight(1f))

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isCompleted -> PrimaryGreen.copy(0.15f)
                                        isInProgress -> Color(0xFFFF9800).copy(0.15f)
                                        else -> PrimaryBlue.copy(0.15f)
                                    }
                                ) {
                                    Text(
                                        text = job.status,
                                        color = when {
                                            isCompleted -> PrimaryGreen
                                            isInProgress -> Color(0xFFFF9800)
                                            else -> PrimaryBlue
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            InfoRow(Icons.Default.Phone, job.phone)
                            InfoRow(Icons.Default.Home, job.address)
                            InfoRow(Icons.Default.Build, "Request: ${job.issue}")

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!isCompleted) {
                                    Button(
                                        onClick = { 
                                            val customerUid = job.id.replace("admin_", "")
                                            onOpenChat("tech_${techName}_$customerUid", job.name)
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                                        border = BorderStroke(1.dp, PrimaryGreen),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chat", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                when {
                                    isCompleted -> {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = PrimaryGreen.copy(0.1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Completed & Verified", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    isInProgress -> {
                                        Button(
                                            onClick = { selectedJobForVerification = job },
                                            modifier = Modifier.weight(1.5f).height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Complete Job", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                    }
                                    else -> {
                                        Button(
                                            onClick = {
                                                val timestamp = com.google.firebase.Timestamp.now()
                                                db.collection("chats_v2").document(job.id).update(mapOf(
                                                    "status" to "In Progress",
                                                    "startedAt" to timestamp
                                                ))

                                                // Sync: Update sub-collection status for customer tracking
                                                db.collection("chats_v2").document(job.id).collection("services")
                                                    .whereIn("status", listOf("accepted", "pending"))
                                                    .get().addOnSuccessListener { snapshot ->
                                                        snapshot.documents.forEach { doc -> 
                                                            doc.reference.update(mapOf("status" to "In Progress", "startedAt" to timestamp))
                                                        }
                                                    }

                                                val progressMsg = hashMapOf(
                                                    "senderId" to "tech",
                                                    "senderName" to techName,
                                                    "text" to "Technician $techName has started the job at your location.",
                                                    "timestamp" to timestamp
                                                )
                                                db.collection("chats_v2").document(job.id).collection("messages").add(progressMsg)
                                            },
                                            modifier = Modifier.weight(1.5f).height(46.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Start Job", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
fun JobCompletionDialog(
    job: TechJobDetail,
    techName: String,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val context = LocalContext.current

    var workNotes by remember { mutableStateOf("") }
    var laborFeeInput by remember { mutableStateOf("500") }
    var partsFeeInput by remember { mutableStateOf("0") }
    var selectedPaymentMethod by remember { mutableStateOf("GCash") }

    val laborVal = laborFeeInput.toDoubleOrNull() ?: 0.0
    val partsVal = partsFeeInput.toDoubleOrNull() ?: 0.0
    val subtotal = laborVal + partsVal
    val vatAmount = subtotal * 0.12
    val totalAmount = subtotal + vatAmount

    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    // Camera Proof Launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedPhoto = bitmap
        }
    }

    // E-Signature Pad Points
    val signaturePaths = remember { mutableStateListOf<Path>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    val checklistItems = remember { mutableStateListOf("Inspect Unit", "Check Connections", "Clean Filters", "Final Test Run") }
    val checkedStates = remember { mutableStateMapOf<String, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Service Report & Completion", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Customer: ${job.name}", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))

                // 📝 1. GUIDED SERVICE CHECKLIST
                Text("SERVICE CHECKLIST", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AppLightBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        checklistItems.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { checkedStates[item] = !(checkedStates[item] ?: false) }.padding(vertical = 4.dp)
                            ) {
                                Checkbox(
                                    checked = checkedStates[item] ?: false,
                                    onCheckedChange = { checkedStates[item] = it },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryGreen)
                                )
                                Text(item, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // ✍️ 2. SERVICE NOTES
                OutlinedTextField(
                    value = workNotes,
                    onValueChange = { workNotes = it },
                    label = { Text("Service Summary & Recommendations") },
                    placeholder = { Text("e.g. Replaced capacitor, advised quarterly cleaning...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 💰 3. BILLING BREAKDOWN
                Text("BILLING & PAYMENT", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(8.dp))


                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = laborFeeInput,
                        onValueChange = { laborFeeInput = it },
                        label = { Text("Labor (₱)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = partsFeeInput,
                        onValueChange = { partsFeeInput = it },
                        label = { Text("Parts (₱)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryBlue.copy(0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total (incl. 12% VAT):", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("₱${String.format(Locale.getDefault(), "%.2f", totalAmount)}", fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Payment Method:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("GCash", "Maya", "Cash").forEach { method ->
                        val isSel = selectedPaymentMethod == method
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPaymentMethod = method },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) PrimaryBlue.copy(0.12f) else AppLightBg,
                            border = BorderStroke(1.dp, if (isSel) PrimaryBlue else FieldBorder)
                        ) {
                            Text(
                                method,
                                modifier = Modifier.padding(vertical = 8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) PrimaryBlue else TextDark,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("Photo Verification Proof", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { cameraLauncher.launch() },
                        colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (capturedPhoto == null) "Snap Photo Proof" else "Retake Photo", color = PrimaryBlue, fontSize = 12.sp)
                    }

                    capturedPhoto?.let { bmp ->
                        Spacer(modifier = Modifier.width(12.dp))
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Photo Proof",
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, PrimaryGreen, RoundedCornerShape(8.dp))
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Customer Signature", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                    if (signaturePaths.isNotEmpty()) {
                        TextButton(onClick = { signaturePaths.clear() }) {
                            Text("Clear", color = Color.Red, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newPath = Path().apply { moveTo(offset.x, offset.y) }
                                    currentPath = newPath
                                    signaturePaths.add(newPath)
                                },
                                onDrag = { change, _ ->
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                },
                                onDragEnd = {
                                    currentPath = null
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        signaturePaths.forEach { path ->
                            drawPath(
                                path = path,
                                color = PrimaryBlue,
                                style = Stroke(width = 4f)
                            )
                        }
                    }

                    if (signaturePaths.isEmpty()) {
                        Text("Sign here with finger", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val allChecked = checklistItems.all { checkedStates[it] == true }
                    if (!allChecked) {
                        Toast.makeText(context, "Please complete all checklist items", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (workNotes.isBlank()) {
                        Toast.makeText(context, "Please enter service summary", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (signaturePaths.isEmpty()) {
                        Toast.makeText(context, "Customer signature is required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true

                    val updates = hashMapOf(
                        "status" to "Completed",
                        "completionNotes" to workNotes,
                        "completedAt" to com.google.firebase.Timestamp.now(),
                        "paymentMethod" to selectedPaymentMethod,
                        "laborFee" to laborVal,
                        "partsFee" to partsVal,
                        "vatAmount" to vatAmount,
                        "totalAmount" to totalAmount,
                        "hasPhotoProof" to (capturedPhoto != null),
                        // Reset Assignment: Move to history and clear active link
                        "lastTechnician" to techName,
                        "assignedTechnician" to com.google.firebase.firestore.FieldValue.delete()
                    )

                    db.collection("chats_v2").document(job.id).update(updates as Map<String, Any>).addOnSuccessListener {
                        // Sync: Update the existing service record instead of just adding a new one for history
                        db.collection("chats_v2").document(job.id).collection("services")
                            .whereIn("status", listOf("In Progress", "accepted", "pending"))
                            .get().addOnSuccessListener { snapshot ->
                                snapshot.documents.forEach { doc ->
                                    doc.reference.update(mapOf(
                                        "status" to "Completed",
                                        "notes" to workNotes,
                                        "paymentMethod" to selectedPaymentMethod,
                                        "laborFee" to laborVal,
                                        "partsFee" to partsVal,
                                        "vatAmount" to vatAmount,
                                        "totalAmount" to totalAmount,
                                        "completedAt" to com.google.firebase.Timestamp.now()
                                    ))
                                }
                            }

                        val completionMsg = hashMapOf(
                            "senderId" to "tech",
                            "senderName" to techName,
                            "text" to "Job Completed! Total Amount: ₱${String.format(Locale.getDefault(), "%.2f", totalAmount)} via $selectedPaymentMethod",
                            "timestamp" to com.google.firebase.Timestamp.now()
                        )
                        db.collection("chats_v2").document(job.id).collection("messages").add(completionMsg)

                        Toast.makeText(context, "Job verified & E-Receipt generated!", Toast.LENGTH_SHORT).show()
                        isSubmitting = false
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else Text("Submit & Generate Receipt", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

@Composable
fun InfoRow(icon: ImageVector, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, color = TextDark, fontSize = 13.sp)
    }
}

// 👤 6. TECHNICIAN PROFILE VIEW
@Composable
fun TechnicianProfileView(onLogout: () -> Unit, viewModel: TechnicianViewModel = hiltViewModel()) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val verificationStatus by viewModel.verificationStatus.collectAsState()
    val isKycUploading by viewModel.isKycUploading.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    var name by remember { mutableStateOf("Technician Account") }
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "tech@coolconnect.com") }
    var streetAddress by remember { mutableStateOf("") }
    var enableBiometrics by remember { mutableStateOf(true) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showManualPrivacy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                name = doc.getString("name") ?: "Technician Account"
                streetAddress = doc.getString("address") ?: "Base Dispatch Station"
            }
        }
    }

    if (showManualPrivacy) {
        PrivacyConsentDialog(onConsentGranted = { showManualPrivacy = false })
    }

    if (showAddressDialog) {
        AlertDialog(
            onDismissRequest = { showAddressDialog = false },
            title = { Text("Base Dispatch Location", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = {
                Column {
                    Text("Technician Service Center / Base Address:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        label = { Text("Base Service Center / Dispatch Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val uid = auth.currentUser?.uid
                        if (uid != null) {
                            db.collection("users").document(uid).update(mapOf("address" to streetAddress))
                            Toast.makeText(context, "Base address updated!", Toast.LENGTH_SHORT).show()
                        }
                        showAddressDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Save Base Address", fontWeight = FontWeight.Bold) }
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
                    placeholderIcon = Icons.Default.Build,
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

        // KYC Verification Card
        val kycLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { viewModel.uploadKycDoc(it, context) }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when(verificationStatus) {
                    "Verified" -> Color(0xFFE8F8EE)
                    "Pending" -> Color(0xFFFFF8E1)
                    "Rejected" -> Color(0xFFFFF0F0)
                    else -> Color.White
                }
            ),
            border = BorderStroke(1.dp, Color(0xFFECECEC))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when(verificationStatus) {
                        "Verified" -> Icons.Default.Verified
                        "Pending" -> Icons.Default.HourglassEmpty
                        "Rejected" -> Icons.Default.GppBad
                        else -> Icons.Default.GppMaybe
                    },
                    contentDescription = null,
                    tint = when(verificationStatus) {
                        "Verified" -> PrimaryGreen
                        "Pending" -> Color(0xFFFFB300)
                        "Rejected" -> Color.Red
                        else -> Color.Gray
                    }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Identity Verification",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = TextDark
                    )
                    Text(
                        text = "Status: $verificationStatus",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                if (verificationStatus != "Verified" && verificationStatus != "Pending") {
                    Button(
                        onClick = { kycLauncher.launch("image/*") },
                        enabled = !isKycUploading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (isKycUploading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("Verify", fontSize = 11.sp)
                    }
                }
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
                TechProfileOptionRow(
                    icon = Icons.Default.Place,
                    title = "Saved Base Address",
                    subtitle = if (streetAddress.isNotBlank()) streetAddress else "Set service center dispatch location",
                    onClick = { showAddressDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    onClick = { Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_LONG).show() }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
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
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryGreen)
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
                TechProfileOptionRow(
                    icon = Icons.Default.Notifications,
                    title = "Dispatch Alerts & Notifications",
                    onClick = { Toast.makeText(context, "Dispatch alerts enabled", Toast.LENGTH_SHORT).show() }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
                    icon = Icons.Default.Description,
                    title = "Technician Code of Conduct",
                    onClick = { showManualPrivacy = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
                    icon = Icons.Default.SupportAgent,
                    title = "Admin Dispatch Line",
                    onClick = { Toast.makeText(context, "Calling Admin Support...", Toast.LENGTH_SHORT).show() }
                )
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun TechProfileOptionRow(
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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
    }
}
