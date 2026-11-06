package com.example.coolconnect

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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
    SHOP("Shop", Icons.Default.ShoppingCart),
    TRACK("Track", Icons.AutoMirrored.Filled.Assignment),
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
    var activeRecipientUserId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    var isCheckingOut by remember { mutableStateOf(false) }

    val customerName by viewModel.customerName.collectAsState()
    val aiRecommendation by viewModel.aiRecommendation.collectAsState()
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val userHasConsented by viewModel.hasConsented.collectAsState()

    // Privacy & Permissions State
    var showPrivacyConsent by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    val isLocationEnabledAndGranted = remember(context) {
        {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
            val isGpsOn = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
            hasPerm && isGpsOn
        }
    }

    LaunchedEffect(userHasConsented) {
        if (!userHasConsented) {
            showPrivacyConsent = true
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (!fineGranted && !coarseGranted) {
            showLocationDialog = true
        } else {
            showLocationDialog = !isLocationEnabledAndGranted()
        }
    }

    // Reactive BroadcastReceiver for GPS On/Off + Lifecycle ON_RESUME listener
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                if (userHasConsented) {
                    showLocationDialog = !isLocationEnabledAndGranted()
                }
            }
        }
        val filter = android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(receiver, filter)

        val lifecycleOwner = (context as? LifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (userHasConsented) {
                    showLocationDialog = !isLocationEnabledAndGranted()
                }
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    // Live Location Broadcasting for Customer
    LaunchedEffect(userHasConsented) {
        if (userHasConsented) {
            if (!isLocationEnabledAndGranted()) {
                showLocationDialog = true
            }

            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            launcher.launch(permissions.toTypedArray())

            while (true) {
                if (isLocationEnabledAndGranted()) {
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

    val tabHistory = remember { mutableStateListOf(CustomerTab.HOME) }

    fun selectTab(tab: CustomerTab) {
        if (currentTab != tab) {
            tabHistory.remove(tab)
            tabHistory.add(tab)
            currentTab = tab
        }
    }

    // Intercept Back Button for proper navigation stack hierarchy
    BackHandler(enabled = true) {
        when {
            isCheckingOut -> {
                isCheckingOut = false
            }
            activeChatId != null -> {
                activeChatId = null
            }
            tabHistory.size > 1 -> {
                tabHistory.removeAt(tabHistory.lastIndex)
                currentTab = tabHistory.last()
            }
            currentTab != CustomerTab.HOME -> {
                currentTab = CustomerTab.HOME
                tabHistory.clear()
                tabHistory.add(CustomerTab.HOME)
            }
            else -> {
                (context as? android.app.Activity)?.finish()
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

    if (showLocationDialog) {
        CustomerLocationPermissionDialog(
            onGrantPermission = {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                launcher.launch(permissions.toTypedArray())
            },
            onEnableGps = {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                context.startActivity(intent)
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onDismiss = { showLocationDialog = false }
        )
    }

    if (isCheckingOut) {
        FullScreenCheckoutScreen(
            viewModel = viewModel,
            onBack = { isCheckingOut = false },
            onOrderPlaced = {
                isCheckingOut = false
                currentTab = CustomerTab.TRACK
            }
        )
    } else if (activeChatId != null) {
        FullChatScreen(
            chatId = activeChatId!!,
            recipientUserId = activeRecipientUserId,
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
                                    .size(34.dp)
                                    .background(PrimaryBlue.copy(0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AcUnit, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(currentTab.label, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                                Text("CoolConnect • Customer Portal", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { selectTab(CustomerTab.PROFILE) }) {
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
                CustomerBottomBar(currentTab = currentTab, onTabSelected = { selectTab(it) })
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            viewModel = viewModel,
                            customerName = customerName,
                            aiRecommendation = aiRecommendation,
                            onNavigateToServices = { selectTab(CustomerTab.SERVICES) },
                            onNavigateToTrack = { selectTab(CustomerTab.TRACK) },
                            onNavigateToShop = { selectTab(CustomerTab.SHOP) },
                            onNavigateToSupport = {
                                val custUid = auth.currentUser?.uid ?: ""
                                activeChatId = "admin_$custUid"
                                activeRecipientUserId = "admin"
                                activeChatName = "Admin Support"
                            }
                        )
                        CustomerTab.SERVICES -> CustomerServicesView()
                        CustomerTab.TRACK -> OrderTrackingView()
                        CustomerTab.SHOP -> ProductCatalogView(
                            viewModel = viewModel,
                            onProceedToCheckout = { isCheckingOut = true },
                            onOpenSupportChat = { chatId, recipientUserId, name ->
                                activeChatId = chatId
                                activeRecipientUserId = recipientUserId
                                activeChatName = name
                            }
                        )
                        CustomerTab.CHAT -> ConversationListScreen(
                            currentUserId = auth.currentUser?.uid ?: "",
                            currentUserName = customerName,
                            userRole = "customer",
                            onChatSelected = { id, recipientUserId, name ->
                                activeChatId = id
                                activeRecipientUserId = recipientUserId
                                activeChatName = name
                            }
                        )
                        CustomerTab.PROFILE -> CustomerProfileView(onLogout)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerBottomBar(currentTab: CustomerTab, onTabSelected: (CustomerTab) -> Unit) {
    val bottomBarTabs = remember {
        listOf(
            CustomerTab.HOME,
            CustomerTab.SERVICES,
            CustomerTab.SHOP,
            CustomerTab.TRACK,
            CustomerTab.CHAT
        )
    }
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp).border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
    ) {
        bottomBarTabs.forEach { tab ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label, tint = if (selected) PrimaryBlue else Color(0xFF64748B), modifier = Modifier.size(20.dp)) },
                label = { Text(tab.label, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1, color = if (selected) PrimaryBlue else Color(0xFF64748B)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = PrimaryBlue.copy(alpha = 0.12f))
            )
        }
    }
}

// 🏠 1. FUTURISTIC CUSTOMER DASHBOARD (HOME VIEW)
@Composable
fun CustomerHomeView(
    viewModel: CustomerViewModel = hiltViewModel(),
    customerName: String,
    aiRecommendation: String,
    onNavigateToServices: () -> Unit,
    onNavigateToTrack: () -> Unit,
    onNavigateToShop: () -> Unit,
    onNavigateToSupport: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val uid = auth.currentUser?.uid ?: ""

    val structuredRecs by viewModel.structuredRecommendations.collectAsState()
    val productsCatalog by viewModel.products.collectAsState()

    var activeService by remember { mutableStateOf<Map<String, Any>?>(null) }
    var activeTechPic by remember { mutableStateOf<String?>(null) }
    var lastServiceDate by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    
    val timeGreeting = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LaunchedEffect(activeService) {
        val techName = activeService?.get("technician")?.toString()
        if (!techName.isNullOrBlank() && techName != "Next Available Specialist") {
            db.collection("users").whereEqualTo("role", "technician").whereEqualTo("name", techName)
                .get().addOnSuccessListener { snapshot ->
                    activeTechPic = snapshot.documents.firstOrNull()?.getString("profilePicUrl")
                }
        } else {
            activeTechPic = null
        }
    }

    LaunchedEffect(uid) {
        if (uid.isNotEmpty()) {
            // Fetch Active Service & Maintenance Timing from service_requests
            db.collection("service_requests")
                .whereEqualTo("customerUid", uid)
                .addSnapshotListener { snapshot, _ ->
                    val docs = snapshot?.documents ?: emptyList()
                    val activeDoc = docs.firstOrNull { doc ->
                        val st = doc.getString("status")?.lowercase() ?: ""
                        st.isNotBlank() && st != "completed" && st != "cancelled" && st != "denied" && st != "request cancelled"
                    }
                    
                    if (activeDoc != null) {
                        val data = activeDoc.data?.toMutableMap() ?: mutableMapOf()
                        data["docId"] = activeDoc.id
                        activeService = data
                    } else {
                        activeService = null
                    }

                    // Last completed service for maintenance check
                    val lastComp = docs.firstOrNull { it.getString("status")?.equals("Completed", ignoreCase = true) == true }
                    lastServiceDate = lastComp?.getTimestamp("createdAt") ?: lastComp?.getTimestamp("completedAt")
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

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (activeTechPic != null) {
                            AsyncImage(
                                model = activeTechPic,
                                contentDescription = "Technician",
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, PrimaryBlue, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(activeService!!["issue"]?.toString() ?: "HVAC Service", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 15.sp)
                            Text("Technician: ${activeService!!["technician"] ?: "Next Available Specialist"}", fontSize = 12.sp, color = Color.Gray)
                        }
                    }

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
            QuickActionCard("Track Service", Icons.AutoMirrored.Filled.Assignment, PrimaryGreen, Modifier.weight(1f), onNavigateToTrack)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickActionCard("Hardware Shop", Icons.Default.ShoppingCart, Color(0xFF7B61FF), Modifier.weight(1f), onNavigateToShop)
            QuickActionCard("Support Chat", Icons.AutoMirrored.Filled.Chat, PrimaryBlue, Modifier.weight(1f), onNavigateToSupport)
        }

        Spacer(modifier = Modifier.height(22.dp))

        // ✨ COOLCONNECT AI SMART ASSISTANT HUB
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F6FF)),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color(0xFFD0E2FB)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(PrimaryBlue.copy(0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("CoolConnect AI Assessment", fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 14.sp)
                            Text("Personalized for your home cooling system", fontSize = 10.sp, color = Color.Gray)
                        }
                    }

                    IconButton(
                        onClick = {
                            viewModel.refreshAIRecommendation()
                            Toast.makeText(context, "Re-analyzing service history...", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh AI", tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // AI Live Health Insights Text Box
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = aiRecommendation,
                        fontSize = 12.sp,
                        color = TextDark,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                // Horizontal Carousel of AI Recommendations (Services & Recommended Parts)
                if (structuredRecs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "TOP RECOMMENDED ACTIONS & HARDWARE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Gray,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(structuredRecs) { rec ->
                            val matchedProduct = remember(productsCatalog, rec.targetId, rec.title) {
                                productsCatalog.find { 
                                    (rec.targetId != null && (it.docId == rec.targetId || it.id.toString() == rec.targetId)) ||
                                    it.name.contains(rec.title, ignoreCase = true) || rec.title.contains(it.name, ignoreCase = true)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .width(220.dp)
                                    .clickable {
                                        if (rec.type == RecommendationType.PRODUCT && matchedProduct != null) {
                                            if (viewModel.addToCart(matchedProduct)) {
                                                Toast.makeText(context, "${matchedProduct.name} added to cart!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Stock limit reached (${matchedProduct.stock} units max)", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            onNavigateToServices()
                                        }
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                elevation = CardDefaults.cardElevation(1.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = when (rec.type) {
                                                RecommendationType.PRODUCT -> Color(0xFF7B61FF).copy(0.12f)
                                                RecommendationType.SERVICE -> PrimaryGreen.copy(0.12f)
                                                else -> PrimaryBlue.copy(0.12f)
                                            }
                                        ) {
                                            Text(
                                                text = rec.badge ?: if (rec.type == RecommendationType.PRODUCT) "RECOMMENDED PART" else "AI ACTION",
                                                color = when (rec.type) {
                                                    RecommendationType.PRODUCT -> Color(0xFF7B61FF)
                                                    RecommendationType.SERVICE -> PrimaryGreen
                                                    else -> PrimaryBlue
                                                },
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        if (rec.formattedPrice != null) {
                                            Text(
                                                text = rec.formattedPrice!!,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp,
                                                color = PrimaryBlue
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = rec.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextDark,
                                        maxLines = 2,
                                        lineHeight = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = rec.reason,
                                        fontSize = 10.sp,
                                        color = Color.Gray,
                                        maxLines = 2,
                                        lineHeight = 13.sp
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Button(
                                        onClick = {
                                            if (rec.type == RecommendationType.PRODUCT && matchedProduct != null) {
                                                if (viewModel.addToCart(matchedProduct)) {
                                                    Toast.makeText(context, "${matchedProduct.name} added to cart!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Stock limit reached (${matchedProduct.stock} units max)", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                onNavigateToServices()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(30.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (rec.type == RecommendationType.PRODUCT) PrimaryBlue else PrimaryGreen
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (rec.type == RecommendationType.PRODUCT) Icons.Default.AddShoppingCart else Icons.Default.CalendarMonth,
                                            contentDescription = null,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = rec.callToAction,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // 🛒 RECOMMENDED HARDWARE & REPLACEMENT PARTS SECTION
        if (productsCatalog.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECOMMENDED HARDWARE & PARTS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF888888),
                    modifier = Modifier.padding(start = 2.dp)
                )

                TextButton(onClick = onNavigateToShop, contentPadding = PaddingValues(0.dp)) {
                    Text("View All in Shop →", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(productsCatalog.take(6)) { product ->
                    Card(
                        modifier = Modifier
                            .width(150.dp)
                            .clickable { onNavigateToShop() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(product.color.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!product.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = product.imageUrl,
                                        contentDescription = product.name,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Icon(
                                        imageVector = getProductIcon(product.name, product.category),
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, color = TextDark, fontSize = 12.sp)
                            Text(product.category, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f", product.rating),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "• ${product.soldCount} sold",
                                    fontSize = 10.sp,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(product.price, color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 13.sp)

                            Spacer(modifier = Modifier.height(6.dp))

                            val isAvailable = product.stock > 0 && product.badge != "OUT OF STOCK"
                            Button(
                                onClick = {
                                    if (isAvailable) {
                                        if (viewModel.addToCart(product)) {
                                            Toast.makeText(context, "${product.name} added to cart!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Stock limit reached (${product.stock} units max)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = isAvailable,
                                modifier = Modifier.fillMaxWidth().height(28.dp),
                                contentPadding = PaddingValues(0.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAvailable) PrimaryBlue else Color.Gray
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (isAvailable) Icons.Default.AddShoppingCart else Icons.Default.Block,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isAvailable) "Add to Cart" else "Out of Stock",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

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

// 🛒 3. HARDWARE CATALOG & PRODUCT DETAIL
@Composable
fun ProductCatalogView(
    viewModel: CustomerViewModel = hiltViewModel(),
    onProceedToCheckout: () -> Unit = {},
    onOpenSupportChat: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val cart by viewModel.cart.collectAsState()
    val db = remember { FirebaseFirestore.getInstance() }
    val productsList = remember { mutableStateListOf<Product>() }
    var isLoadingProducts by remember { mutableStateOf(true) }

    val defaultSampleProducts = remember {
        listOf(
            Product(
                1, "Compressor", "₱8,000", "Compressors", Color(0xFFE3F2FD),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Heavy-duty rotary aircon compressor designed for split and window-type HVAC units.",
                specs = "1.5 HP - 2.5 HP • R410A / R32 Compatible • High Thermal Endurance",
                warranty = "2 Years Official Warranty"
            ),
            Product(
                2, "Board", "₱5,500", "Circuit Boards", Color(0xFFF1F8E9),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Universal inverter & non-inverter air conditioner main control PCB board with display module.",
                specs = "Multi-Brand Compatible • Relay Protected • Remote Sensor Included",
                warranty = "1 Year Warranty"
            ),
            Product(
                3, "Fan Motor", "₱4,000", "Motors & Fans", Color(0xFFFFFDE7),
                badge = "IN STOCK",
                imageUrl = "",
                description = "High-RPM condenser and evaporator fan motor for outdoor and indoor HVAC units.",
                specs = "220V / 60Hz • Copper Winding • Sealed Ball Bearings",
                warranty = "1 Year Warranty"
            ),
            Product(
                4, "Freon", "₱7,800", "Refrigerants", Color(0xFFE8F5E9),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Genuine high-purity R410A / R32 refrigerant gas cylinder (11.3kg) for AC recharge.",
                specs = "99.9% Pure Refrigerant • Eco-Friendly Low ODP • 11.3kg Tank",
                warranty = "Guaranteed Sealed Cylinder"
            ),
            Product(
                5, "Fan Blade", "₱700", "Motors & Fans", Color(0xFFFFF3E0),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Balanced aerodynamic outdoor condenser propeller fan blade engineered for low acoustic noise.",
                specs = "3-Blade Aerodynamic ABS • UV & Weather Resistant • 400mm Dia",
                warranty = "1 Year Warranty"
            ),
            Product(
                6, "Fan Blower", "₱550", "Motors & Fans", Color(0xFFE0F7FA),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Precision cross-flow cylindrical indoor blower wheel for split-type indoor evaporator units.",
                specs = "Anti-Static ABS Plastic • Dynamic Balanced • Fits 0.75HP - 2.0HP",
                warranty = "1 Year Warranty"
            ),
            Product(
                7, "Sensor", "₱300", "Sensors & Driers", Color(0xFFF3E5F5),
                badge = "IN STOCK",
                imageUrl = "",
                description = "High-sensitivity ambient and coil temperature thermistor sensor for split and window AC units.",
                specs = "5K / 10K NTC Thermistor • Waterproof Copper Probe • 40cm Lead",
                warranty = "1 Year Warranty"
            ),
            Product(
                8, "Copper Tube", "₱750", "Refrigerants", Color(0xFFFFF8E1),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Pre-insulated double copper tubing roll (5 meters) for indoor to outdoor split-type AC installation.",
                specs = "1/4\" & 3/8\" Pair • Seamless Annealed Copper • Fire-Retardant Foam",
                warranty = "1 Year Warranty"
            ),
            Product(
                9, "Filter Drier", "₱900", "Sensors & Driers", Color(0xFFE8EAF6),
                badge = "IN STOCK",
                imageUrl = "",
                description = "Heavy-duty liquid line filter drier to trap moisture, acid, and foreign particles in HVAC lines.",
                specs = "1/4\" Flare Connections • Desiccant Moisture Absorber • Solid Core",
                warranty = "1 Year Warranty"
            )
        )
    }

    val catalogFromVm by viewModel.products.collectAsState()
    val displayList = if (catalogFromVm.isNotEmpty()) catalogFromVm else defaultSampleProducts

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedProduct by remember { mutableStateOf<Product?>(null) }
    var previewingImageProduct by remember { mutableStateOf<Product?>(null) }
    var showCartDialog by remember { mutableStateOf(false) }

    val categories = remember(displayList) {
        listOf("All") + displayList.map { it.category }.distinct()
    }

    val filteredProducts = displayList.filter { p ->
        val matchesCategory = selectedCategory == "All" || p.category == selectedCategory
        val matchesQuery = p.name.contains(searchQuery, ignoreCase = true) || p.description.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    if (previewingImageProduct != null) {
        FullScreenProductImageViewer(
            product = previewingImageProduct!!,
            viewModel = viewModel,
            onDismiss = { previewingImageProduct = null },
            onAddToCart = {
                if (viewModel.addToCart(previewingImageProduct!!)) {
                    Toast.makeText(context, "${previewingImageProduct!!.name} added to cart!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Stock limit reached (${previewingImageProduct!!.stock} units max)", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (selectedProduct != null) {
        ProductDetailDialog(
            product = selectedProduct!!,
            viewModel = viewModel,
            onDismiss = { selectedProduct = null },
            onProceedToCheckout = {
                selectedProduct = null
                onProceedToCheckout()
            },
            onOpenSupportChat = { chatId, recipientUserId, name ->
                selectedProduct = null
                onOpenSupportChat(chatId, recipientUserId, name)
            }
        )
    }

    if (showCartDialog) {
        CartDialog(
            viewModel = viewModel,
            onDismiss = { showCartDialog = false },
            onProceedToCheckout = {
                showCartDialog = false
                onProceedToCheckout()
            }
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
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { cat ->
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
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(product.color.copy(alpha = 0.5f))
                                .clickable { previewingImageProduct = product },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImageWithShimmer(
                                model = if (product.galleryImages.isNotEmpty()) product.galleryImages.first() else product.imageUrl,
                                contentDescription = product.name,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentScale = ContentScale.Fit,
                                fallbackIcon = getProductIcon(product.name, product.category)
                            )

                            // Full View zoom overlay
                            Surface(
                                color = Color.Black.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(topEnd = 8.dp),
                                modifier = Modifier.align(Alignment.BottomStart)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomIn,
                                        contentDescription = "Full View",
                                        tint = Color.White,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (product.galleryImages.size > 1) "${product.galleryImages.size} Photos" else "Full View",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, color = TextDark, fontSize = 13.sp)
                        Text(product.category, fontSize = 11.sp, color = Color(0xFF757575))
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", product.rating),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Text(
                                text = if (product.reviewCount > 0) " (${product.reviewCount})" else " (New)",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "• ${product.soldCount} sold",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        Text(product.price, color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 14.sp)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val isAvailable = product.stock > 0 && product.badge != "OUT OF STOCK"
                        Button(
                            onClick = {
                                if (isAvailable) {
                                    if (viewModel.addToCart(product)) {
                                        Toast.makeText(context, "${product.name} added to cart!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Stock limit reached (${product.stock} units max)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            enabled = isAvailable,
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAvailable) PrimaryBlue else Color.Gray,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAvailable) Icons.Default.AddShoppingCart else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isAvailable) "Add to Cart" else "Out of Stock",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartDialog(
    viewModel: CustomerViewModel, 
    onDismiss: () -> Unit,
    onProceedToCheckout: () -> Unit
) {
    val context = LocalContext.current
    val cart by viewModel.cart.collectAsState()
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    
    var customerAddress by remember { mutableStateOf("Fetching delivery address...") }
    var selectedProductIds by remember { mutableStateOf<Set<Int>>(emptySet()) }

    LaunchedEffect(cart) {
        val currentIds = cart.map { it.product.id }.toSet()
        if (selectedProductIds.isEmpty()) {
            selectedProductIds = currentIds
        } else {
            selectedProductIds = selectedProductIds.intersect(currentIds) + currentIds
        }
    }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            val street = doc.getString("street") ?: doc.getString("address") ?: ""
            val city = doc.getString("city") ?: ""
            val province = doc.getString("province") ?: ""
            val full = listOf(street, city, province).filter { it.isNotBlank() }.joinToString(", ")
            customerAddress = if (full.isNotBlank()) full else "Set address in Profile"
        }
    }

    val totalItemCount = cart.sumOf { it.quantity }
    val isAllSelected = cart.isNotEmpty() && selectedProductIds.containsAll(cart.map { it.product.id })

    fun parsePriceDouble(priceStr: String): Double {
        return priceStr.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    val calculatedTotal = cart.filter { selectedProductIds.contains(it.product.id) }
        .sumOf { parsePriceDouble(it.product.price) * it.quantity }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 2.dp,
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextDark
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Shopping cart ($totalItemCount)",
                                color = TextDark,
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = customerAddress,
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        TextButton(
                            onClick = { viewModel.clearCart() },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Clear", color = PrimaryBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                selectedProductIds = if (isAllSelected) {
                                    emptySet()
                                } else {
                                    cart.map { it.product.id }.toSet()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isAllSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = "Select All",
                                tint = if (isAllSelected) PrimaryBlue else Color(0xFF94A3B8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("All", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Text(
                            text = "₱${String.format(Locale.getDefault(), "%,.2f", calculatedTotal)}",
                            color = PrimaryBlue,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )

                        Button(
                            onClick = {
                                if (selectedProductIds.isNotEmpty()) {
                                    val unselected = cart.filter { !selectedProductIds.contains(it.product.id) }
                                    unselected.forEach { viewModel.removeFromCart(it.product.id) }
                                    onDismiss()
                                    onProceedToCheckout()
                                }
                            },
                            enabled = selectedProductIds.isNotEmpty() && cart.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text("Check out", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            },
            containerColor = AppLightBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(AppLightBg)
            ) {
                if (cart.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = null,
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your cart is empty",
                            color = Color.Gray,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(cart, key = { it.product.id }) { item ->
                            val isSelected = selectedProductIds.contains(item.product.id)
                            CartItemRow(
                                item = item,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedProductIds = if (isSelected) {
                                        selectedProductIds - item.product.id
                                    } else {
                                        selectedProductIds + item.product.id
                                    }
                                },
                                onUpdateQty = { delta ->
                                    val success = viewModel.updateQuantity(item.product.id, delta)
                                    if (!success && delta > 0) {
                                        Toast.makeText(context, "Stock limit reached (${item.product.stock} units max)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: CartItem,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onUpdateQty: (Int) -> Unit
) {
    val maxStock = if (item.product.stock > 0) item.product.stock else 10
    val isMaxStock = item.quantity >= maxStock

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleSelect,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Select Item",
                    tint = if (isSelected) PrimaryBlue else Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(item.product.color.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                AsyncImageWithShimmer(
                    model = if (item.product.galleryImages.isNotEmpty()) item.product.galleryImages.first() else item.product.imageUrl,
                    contentDescription = item.product.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit,
                    fallbackIcon = getProductIcon(item.product.name, item.product.category)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextDark,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.product.price,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFF1F5F9),
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    IconButton(
                        onClick = { onUpdateQty(-1) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease",
                            tint = TextDark,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Text(
                        text = "${item.quantity}",
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    IconButton(
                        onClick = { onUpdateQty(1) },
                        enabled = !isMaxStock,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = if (isMaxStock) Color.LightGray else PrimaryBlue,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenCheckoutScreen(
    viewModel: CustomerViewModel,
    onBack: () -> Unit,
    onOrderPlaced: () -> Unit
) {
    val cart by viewModel.cart.collectAsState()
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }

    var customerName by remember { mutableStateOf("") }
    var streetAddress by remember { mutableStateOf("") }
    var brgy by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var province by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedPaymentMethod by remember { mutableStateOf("QR Ph (GCash, Maya, InstaPay)") }
    var isSubmitting by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showQrPhModal by remember { mutableStateOf(false) }
    var showEditAddressModal by remember { mutableStateOf(false) }

    fun parsePriceDouble(priceStr: String): Double {
        return priceStr.replace("₱", "").replace(",", "").trim().toDoubleOrNull() ?: 0.0
    }

    val subtotal = cart.sumOf { parsePriceDouble(it.product.price) * it.quantity }
    val vat = subtotal * 0.12
    val total = subtotal + vat
    val totalItemCount = cart.sumOf { it.quantity }

    LaunchedEffect(Unit) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        db.collection("users").document(uid).get().addOnSuccessListener { doc ->
            customerName = doc.getString("name") ?: "Customer"
            streetAddress = doc.getString("street") ?: doc.getString("address") ?: ""
            brgy = doc.getString("brgy") ?: ""
            city = doc.getString("city") ?: ""
            province = doc.getString("province") ?: ""
            zipCode = doc.getString("postalCode") ?: ""
            phone = doc.getString("phone") ?: ""
        }
    }

    if (showEditAddressModal) {
        AlertDialog(
            onDismissRequest = { showEditAddressModal = false },
            title = { Text("Edit Delivery Address", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = streetAddress,
                        onValueChange = { streetAddress = it },
                        label = { Text("House No. / Street Address") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = brgy,
                        onValueChange = { brgy = it },
                        label = { Text("Barangay") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = city,
                            onValueChange = { city = it },
                            label = { Text("City") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            value = province,
                            onValueChange = { province = it },
                            label = { Text("Province") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone Number") },
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
                            val fullAddress = listOf(streetAddress, brgy, city, province, zipCode).filter { it.isNotBlank() }.joinToString(", ")
                            db.collection("users").document(uid).update(
                                mapOf(
                                    "street" to streetAddress,
                                    "brgy" to brgy,
                                    "city" to city,
                                    "province" to province,
                                    "phone" to phone,
                                    "address" to fullAddress
                                )
                            )
                        }
                        showEditAddressModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Save Address", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditAddressModal = false }) { Text("Cancel", color = Color.Gray) }
            }
        )
    }

    if (showQrPhModal) {
        val fullAddress = listOf(streetAddress, brgy, city, province, zipCode).filter { it.isNotBlank() }.joinToString(", ")
        QrPhPaymentDialog(
            amount = total,
            orderOrServiceId = "ORD-${System.currentTimeMillis().toString().takeLast(6)}",
            onPaymentConfirmed = { refNum ->
                showQrPhModal = false
                isSubmitting = true
                viewModel.checkout(fullAddress, "QR Ph Paid (Ref: $refNum)") { result ->
                    isSubmitting = false
                    result.onSuccess {
                        showSuccessDialog = true
                    }.onFailure {
                        Toast.makeText(context, "Order failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onDismiss = { showQrPhModal = false }
        )
    }

    if (showSuccessDialog) {
        CelebrationSuccessDialog(
            title = "Order Placed & Installation Requested!",
            message = "Your hardware order has been submitted. An Admin will review your items and assign an expert field technician to deliver and install your order at your address.",
            onDismiss = {
                showSuccessDialog = false
                onOrderPlaced()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Checkout", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Total ($totalItemCount item${if (totalItemCount > 1) "s" else ""})",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "₱${String.format(Locale.getDefault(), "%,.2f", total)}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryBlue
                        )
                    }

                    Button(
                        onClick = {
                            val fullAddress = listOf(streetAddress, brgy, city, province, zipCode).filter { it.isNotBlank() }.joinToString(", ")
                            if (fullAddress.length < 5) {
                                Toast.makeText(context, "Please set a valid delivery address", Toast.LENGTH_SHORT).show()
                                showEditAddressModal = true
                                return@Button
                            }

                            if (selectedPaymentMethod.startsWith("QR Ph")) {
                                showQrPhModal = true
                            } else {
                                isSubmitting = true
                                viewModel.checkout(fullAddress, selectedPaymentMethod) { result ->
                                    isSubmitting = false
                                    result.onSuccess {
                                        showSuccessDialog = true
                                    }.onFailure {
                                        Toast.makeText(context, "Order failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSubmitting && cart.isNotEmpty(),
                        modifier = Modifier.height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Place order", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        containerColor = AppLightBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(14.dp)
        ) {
            // 📍 1. DELIVERY ADDRESS CARD (TikTok Checkout Header Style)
            val fullAddressStr = remember(streetAddress, brgy, city, province, zipCode) {
                listOf(streetAddress, brgy, city, province, zipCode).filter { it.isNotBlank() }.joinToString(", ")
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showEditAddressModal = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = "Address",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = customerName.ifBlank { "Delivery Recipient" },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = TextDark
                        )
                        if (phone.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "($phone)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Change Address",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (fullAddressStr.isNotBlank()) fullAddressStr else "Tap to set delivery address",
                        fontSize = 12.sp,
                        color = if (fullAddressStr.isNotBlank()) TextDark else Color.Red,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 🛒 2. ORDERED PRODUCTS CARD LIST
            cart.forEach { item ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(item.product.color.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImageWithShimmer(
                                    model = if (item.product.galleryImages.isNotEmpty()) item.product.galleryImages.first() else item.product.imageUrl,
                                    contentDescription = item.product.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    contentScale = ContentScale.Fit,
                                    fallbackIcon = getProductIcon(item.product.name, item.product.category)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.product.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = TextDark,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.product.category,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.product.price,
                                    color = PrimaryBlue,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Quantity Stepper Controls
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF1F5F9),
                                border = BorderStroke(0.5.dp, Color(0xFFE2E8F0))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    IconButton(
                                        onClick = { viewModel.updateQuantity(item.product.id, -1) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Decrease",
                                            tint = TextDark,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }

                                    Text(
                                        text = "${item.quantity}",
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val success = viewModel.updateQuantity(item.product.id, 1)
                                            if (!success) {
                                                Toast.makeText(context, "Stock limit reached", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Increase",
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Delivery ETA Banner
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = Color(0xFFE3F2FD),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocalShipping,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Estimated Dispatch: Within 24 Hours",
                                        fontSize = 11.sp,
                                        color = PrimaryBlue,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "FREE Delivery & Install",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 📊 3. ORDER SUMMARY CARD
            Text(
                text = "Order summary",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDark,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Product subtotal", fontSize = 13.sp, color = Color.Gray)
                        Text("₱${String.format(Locale.getDefault(), "%,.2f", subtotal)}", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("VAT (12%)", fontSize = 13.sp, color = Color.Gray)
                        Text("₱${String.format(Locale.getDefault(), "%,.2f", vat)}", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Delivery & Installation", fontSize = 13.sp, color = Color.Gray)
                        Text("FREE", fontSize = 13.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF1F5F9))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total", fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextDark)
                        Text("₱${String.format(Locale.getDefault(), "%,.2f", total)}", fontWeight = FontWeight.Black, fontSize = 17.sp, color = PrimaryBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 💳 4. PAYMENT METHOD SELECTION CARD
            Text(
                text = "Payment method",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = TextDark,
                modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    listOf("QR Ph (GCash, Maya, InstaPay)", "GCash Direct", "Maya Direct", "Cash on Delivery").forEach { method ->
                        val isSel = selectedPaymentMethod == method
                        val isQr = method.startsWith("QR Ph")
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedPaymentMethod = method },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) PrimaryBlue.copy(0.06f) else Color.White,
                            border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSel,
                                    onClick = { selectedPaymentMethod = method },
                                    colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = method,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = TextDark,
                                            fontSize = 13.sp
                                        )
                                        if (isQr) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFF0038A8).copy(0.12f)
                                            ) {
                                                Text(
                                                    text = "RECOMMENDED",
                                                    color = Color(0xFF0038A8),
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (isQr) {
                                        Text(
                                            text = "Instant scan via GCash, Maya, or any PH bank",
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 📜 5. TERMS DISCLAIMER
            Text(
                text = "By placing an order, you agree to the CoolConnect Terms of Use and Sale and acknowledge that you have read the Privacy Policy.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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

// SHIMMER SKELETON IMAGE LOADER
@Composable
fun AsyncImageWithShimmer(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    fallbackIcon: ImageVector = Icons.Default.Memory
) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }

    val transition = rememberInfiniteTransition(label = "shimmer_anim")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "translate"
    )

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFFE2E8F0),
            Color(0xFFF8FAFC),
            Color(0xFFE2E8F0)
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (isLoading && !isError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(shimmerBrush)
            )
        }

        if (isError || model == null || model.toString().isBlank()) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = PrimaryBlue.copy(alpha = 0.5f),
                modifier = Modifier.size(36.dp)
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onLoading = { isLoading = true; isError = false },
                onSuccess = { isLoading = false; isError = false },
                onError = { isLoading = false; isError = true }
            )
        }
    }
}

// FULLSCREEN ZOOMABLE PRODUCT IMAGE VIEWER (LIGHTBOX WITH MULTI-IMAGE CAROUSEL)
@Composable
fun FullScreenProductImageViewer(
    product: Product,
    viewModel: CustomerViewModel? = null,
    onDismiss: () -> Unit,
    onAddToCart: (() -> Unit)? = null
) {
    val gallery = remember(product) {
        if (product.galleryImages.isNotEmpty()) product.galleryImages else listOf(product.imageUrl)
    }
    var activeImageIndex by remember { mutableIntStateOf(0) }
    val activeUrl = gallery.getOrNull(activeImageIndex) ?: product.imageUrl

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
        ) {
            // Main Zoomable Image View
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeImageIndex) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!activeUrl.isNullOrBlank()) {
                    AsyncImageWithShimmer(
                        model = activeUrl,
                        contentDescription = product.name,
                        contentScale = ContentScale.Fit,
                        fallbackIcon = getProductIcon(product.name, product.category),
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .fillMaxHeight(0.68f)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                } else {
                    Icon(
                        imageVector = getProductIcon(product.name, product.category),
                        contentDescription = product.name,
                        tint = PrimaryBlue,
                        modifier = Modifier
                            .size(140.dp)
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                }
            }

            // Left / Right Navigation Arrows if multiple photos exist
            if (gallery.size > 1) {
                if (activeImageIndex > 0) {
                    IconButton(
                        onClick = {
                            activeImageIndex--
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous Photo", tint = Color.White)
                    }
                }

                if (activeImageIndex < gallery.size - 1) {
                    IconButton(
                        onClick = {
                            activeImageIndex++
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.ChevronRight, "Next Photo", tint = Color.White)
                    }
                }
            }

            // Top Header Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "${product.category} • ${product.price} • ★ ${String.format(Locale.getDefault(), "%.1f", product.rating)} (${product.soldCount} sold)" + if (gallery.size > 1) " (${activeImageIndex + 1}/${gallery.size})" else "",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            // Bottom Thumbnails or Gesture hint
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (gallery.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 10.dp)
                    ) {
                        itemsIndexed(gallery) { idx, u ->
                            val isSel = idx == activeImageIndex
                            Surface(
                                modifier = Modifier
                                    .size(50.dp)
                                    .clickable {
                                        activeImageIndex = idx
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                border = BorderStroke(if (isSel) 2.dp else 1.dp, if (isSel) PrimaryBlue else Color.White.copy(alpha = 0.3f))
                            ) {
                                AsyncImageWithShimmer(
                                    model = u,
                                    contentDescription = "Thumb ${idx + 1}",
                                    modifier = Modifier.padding(4.dp),
                                    contentScale = ContentScale.Fit,
                                    fallbackIcon = getProductIcon(product.name, product.category)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pinch to zoom • Drag to pan",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Bottom Action Bar
            val isAvailable = product.stock > 0 && product.badge != "OUT OF STOCK"
            Surface(
                color = Color(0xFF1E293B),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = product.price,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        )
                        Text(
                            text = if (isAvailable) "In Stock (${product.stock} units)" else "Out of Stock",
                            color = if (isAvailable) PrimaryGreen else Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onAddToCart != null) {
                        Button(
                            onClick = {
                                onAddToCart()
                            },
                            enabled = isAvailable,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                disabledContainerColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isAvailable) Icons.Default.AddShoppingCart else Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAvailable) "Add to Cart" else "Out of Stock",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// PRODUCT DETAIL & BOOKING FULL-SCREEN PAGE
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailDialog(
    product: Product,
    viewModel: CustomerViewModel,
    onDismiss: () -> Unit,
    onProceedToCheckout: () -> Unit,
    onOpenSupportChat: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current
    val cart by viewModel.cart.collectAsState()

    var customerName by remember { mutableStateOf("Customer") }
    var customerAddress by remember { mutableStateOf("") }
    var showFullScreenViewer by remember { mutableStateOf(false) }
    var selectedImgIndex by remember { mutableIntStateOf(0) }
    var showCartDialog by remember { mutableStateOf(false) }

    // Live Firestore Product Fields
    var liveStock by remember { mutableIntStateOf(product.stock) }
    var livePrice by remember { mutableStateOf(product.price) }
    var liveRating by remember { mutableStateOf(product.rating) }
    var liveReviewCount by remember { mutableIntStateOf(product.reviewCount) }
    var liveSoldCount by remember { mutableIntStateOf(product.soldCount) }
    var liveDescription by remember { mutableStateOf(product.description) }
    var liveSpecs by remember { mutableStateOf(product.specs) }
    var liveWarranty by remember { mutableStateOf(product.warranty) }

    // Realtime Customer Reviews for this Product
    val productReviews = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(product) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                customerName = doc.getString("name") ?: "Customer"
                customerAddress = doc.getString("address") ?: ""
            }
        }

        // Live Product Data Listener from Firestore
        if (product.docId.isNotBlank()) {
            db.collection("products").document(product.docId).addSnapshotListener { pDoc, _ ->
                if (pDoc != null && pDoc.exists()) {
                    liveStock = (pDoc.get("stock") as? Number)?.toInt() ?: liveStock
                    val rawP = pDoc.get("price")
                    if (rawP != null) {
                        val pVal = (rawP as? Number)?.toDouble() ?: 0.0
                        livePrice = "₱${String.format(Locale.getDefault(), "%,.0f", pVal)}"
                    }
                    liveRating = (pDoc.get("rating") as? Number)?.toDouble() ?: liveRating
                    liveReviewCount = (pDoc.get("reviewCount") as? Number)?.toInt() ?: liveReviewCount
                    liveSoldCount = (pDoc.get("soldCount") as? Number)?.toInt() ?: liveSoldCount
                    liveDescription = pDoc.getString("description") ?: liveDescription
                    liveSpecs = pDoc.getString("specs") ?: liveSpecs
                    liveWarranty = pDoc.getString("warranty") ?: liveWarranty
                }
            }
        }

        // Live Sales Count aggregated from Orders
        db.collection("orders").addSnapshotListener { oSnap, _ ->
            var count = 0
            oSnap?.documents?.forEach { oDoc ->
                val itemsList = oDoc.get("items") as? List<*>
                itemsList?.forEach { itemObj ->
                    if (itemObj is Map<*, *>) {
                        val pName = itemObj["name"]?.toString() ?: ""
                        if (pName.trim().equals(product.name.trim(), ignoreCase = true)) {
                            val qty = (itemObj["quantity"] as? Number)?.toInt() ?: 1
                            count += qty
                        }
                    }
                }
            }
            if (count > liveSoldCount) {
                liveSoldCount = count
            }
        }

        // Live Reviews Listener from Firestore 'reviews'
        db.collection("reviews").addSnapshotListener { rSnap, _ ->
            productReviews.clear()
            rSnap?.documents?.forEach { rDoc ->
                val pName = rDoc.getString("productName") ?: rDoc.getString("itemName") ?: rDoc.getString("techName") ?: ""
                val pDocId = rDoc.getString("productDocId") ?: rDoc.getString("productId") ?: ""
                val isMatch = (pDocId.isNotBlank() && pDocId == product.docId) ||
                        (pName.isNotBlank() && pName.trim().equals(product.name.trim(), ignoreCase = true))
                if (isMatch) {
                    val rData = rDoc.data ?: emptyMap()
                    productReviews.add(rData)
                }
            }
            if (productReviews.isNotEmpty()) {
                liveReviewCount = productReviews.size
                val avg = productReviews.mapNotNull { (it["rating"] as? Number)?.toDouble() }.average()
                if (!avg.isNaN()) {
                    liveRating = avg
                }
            }
        }
    }

    val isAvailable = liveStock > 0 && product.badge != "OUT OF STOCK"
    val gallery = remember(product) { product.galleryImages }
    val activeUrl = gallery.getOrNull(selectedImgIndex) ?: product.imageUrl

    if (showFullScreenViewer) {
        FullScreenProductImageViewer(
            product = product,
            viewModel = viewModel,
            onDismiss = { showFullScreenViewer = false },
            onAddToCart = {
                if (isAvailable) {
                    viewModel.addToCart(product)
                    Toast.makeText(context, "${product.name} added to cart!", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    if (showCartDialog) {
        CartDialog(
            viewModel = viewModel,
            onDismiss = { showCartDialog = false },
            onProceedToCheckout = {
                showCartDialog = false
                onDismiss()
                onProceedToCheckout()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Product Details",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                        }
                    },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (cart.isNotEmpty()) {
                                    Badge(containerColor = PrimaryBlue) {
                                        Text("${cart.sumOf { it.quantity }}", color = Color.White)
                                    }
                                }
                            },
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .clickable { showCartDialog = true }
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = TextDark)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Support Chat Button
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                val custUid = auth.currentUser?.uid ?: ""
                                onOpenSupportChat("admin_$custUid", "admin", "Admin Support")
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Support Chat", tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }

                        // Add to Cart Button
                        OutlinedButton(
                            onClick = {
                                if (isAvailable) {
                                    viewModel.addToCart(product)
                                    Toast.makeText(context, "${product.name} added to cart!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = isAvailable,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, if (isAvailable) PrimaryBlue else Color.LightGray)
                        ) {
                            Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = if (isAvailable) PrimaryBlue else Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add to Cart",
                                fontWeight = FontWeight.Bold,
                                color = if (isAvailable) PrimaryBlue else Color.Gray,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Buy & Checkout Button
                        Button(
                            onClick = {
                                if (isAvailable) {
                                    viewModel.addToCart(product)
                                    onDismiss()
                                    onProceedToCheckout()
                                }
                            },
                            enabled = isAvailable,
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isAvailable) "Buy & Checkout" else "Out of Stock",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFFF8FAFC)
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. FULL WIDTH PRODUCT IMAGE HERO CAROUSEL
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .background(product.color.copy(alpha = 0.4f))
                        .clickable { showFullScreenViewer = true },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImageWithShimmer(
                        model = activeUrl,
                        contentDescription = product.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit,
                        fallbackIcon = getProductIcon(product.name, product.category)
                    )

                    // Page Indicator Pill
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "${selectedImgIndex + 1}/${gallery.size.coerceAtLeast(1)}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Multi-Image Thumbnails Row
                if (gallery.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.fillMaxWidth().background(Color.White)
                    ) {
                        itemsIndexed(gallery) { idx, url ->
                            val isSel = selectedImgIndex == idx
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable { selectedImgIndex = idx },
                                shape = RoundedCornerShape(10.dp),
                                color = product.color.copy(alpha = 0.2f),
                                border = BorderStroke(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = if (isSel) PrimaryBlue else Color(0xFFE2E8F0)
                                )
                            ) {
                                AsyncImageWithShimmer(
                                    model = url,
                                    contentDescription = "Angle ${idx + 1}",
                                    modifier = Modifier.padding(4.dp),
                                    contentScale = ContentScale.Fit,
                                    fallbackIcon = getProductIcon(product.name, product.category)
                                )
                            }
                        }
                    }
                }

                // 2. PRICING, STOCK, TITLE & SALES STATS
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = livePrice,
                                color = PrimaryBlue,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )

                            // Live Inventory Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isAvailable) PrimaryGreen.copy(0.12f) else Color.Red.copy(0.12f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isAvailable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = if (isAvailable) PrimaryGreen else Color.Red,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isAvailable) "In Stock ($liveStock left)" else "Out of Stock",
                                        color = if (isAvailable) PrimaryGreen else Color.Red,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Product Title
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = TextDark,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Ratings & Units Sold Social Proof Bar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = String.format(Locale.getDefault(), "%.1f", liveRating),
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = TextDark
                            )
                            Text(
                                text = " ($liveReviewCount reviews)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("•", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$liveSoldCount sold",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(6.dp), color = AppLightBg) {
                                Text(
                                    text = product.category,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. PRODUCT DESCRIPTION & TECHNICAL SPECS
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "PRODUCT OVERVIEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = liveDescription,
                            fontSize = 13.sp,
                            color = TextDark,
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color(0xFFF1F5F9))
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "SPECIFICATIONS & WARRANTY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Gray,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            color = AppLightBg,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = liveSpecs,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextDark,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Verified, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = liveWarranty,
                                        fontSize = 12.sp,
                                        color = PrimaryGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. REAL CUSTOMER REVIEWS (LIVE FROM FIREBASE)
                Surface(
                    color = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "CUSTOMER REVIEWS (${productReviews.size})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    letterSpacing = 0.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%.1f", liveRating),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = TextDark
                                    )
                                    Text(" / 5.0", fontSize = 12.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    (1..5).forEach { star ->
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = if (star <= liveRating) Color(0xFFFFB300) else Color.LightGray,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (productReviews.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = AppLightBg),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(20.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.StarBorder, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "No customer reviews yet",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "Be the first to review after purchasing this product!",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                productReviews.forEach { rev ->
                                    val reviewer = rev["customerName"]?.toString() ?: rev["userName"]?.toString() ?: "Verified Buyer"
                                    val ratingScore = (rev["rating"] as? Number)?.toInt() ?: 5
                                    val commentText = rev["comment"]?.toString() ?: rev["feedback"]?.toString() ?: "Great product!"
                                    val ts = rev["timestamp"] as? com.google.firebase.Timestamp
                                    val dateStr = remember(ts) {
                                        if (ts != null) SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(ts.toDate())
                                        else "Recent"
                                    }

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = AppLightBg),
                                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(28.dp)
                                                            .background(PrimaryBlue.copy(0.12f), CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = reviewer.take(1).uppercase(Locale.getDefault()),
                                                            fontWeight = FontWeight.Bold,
                                                            color = PrimaryBlue,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = reviewer,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = TextDark
                                                    )
                                                }
                                                Text(
                                                    text = dateStr,
                                                    fontSize = 10.sp,
                                                    color = Color.Gray
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                (1..5).forEach { star ->
                                                    Icon(
                                                        imageVector = Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = if (star <= ratingScore) Color(0xFFFFB300) else Color.LightGray,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = commentText,
                                                fontSize = 12.sp,
                                                color = TextDark,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
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
    var selectedTechForDetailView by remember { mutableStateOf<Map<String, Any>?>(null) }

    var searchQuery by remember { mutableStateOf("") }

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

        db.collection("service_requests").addSnapshotListener { jobsSnap, _ ->
            val jobs = jobsSnap?.documents ?: emptyList()
            techWorkloads.clear()
            technicians.forEach { techData ->
                val tName = techData["name"]?.toString() ?: ""
                val tUid = techData["uid"]?.toString() ?: ""
                val activeCount = jobs.count { doc ->
                    val docTechUid = doc.getString("technicianUid")
                    val docTechName = doc.getString("technicianName") ?: doc.getString("technician") ?: ""
                    val matchesTech = (docTechUid != null && docTechUid == tUid) || docTechName.equals(tName, ignoreCase = true)
                    val st = doc.getString("status") ?: ""
                    matchesTech && st != "Completed" && st != "Denied" && st != "Request Cancelled" && st != "Cancelled"
                }
                if (tName.isNotBlank()) {
                    techWorkloads[tName] = activeCount
                }
            }
        }

        db.collection("users").whereEqualTo("role", "technician").addSnapshotListener { snapshot, _ ->
            technicians.clear()
            snapshot?.documents?.forEach { doc -> 
                val tName = doc.getString("name") ?: "Unknown Tech"
                val tUid = doc.id
                val data = doc.data ?: emptyMap()
                technicians.add(data + ("uid" to tUid))
            }
            isLoadingTechs = false
        }
    }

    val filteredTechs = technicians.filter { techData ->
        val name = techData["name"]?.toString() ?: ""
        val skill = techData["skill"]?.toString() ?: ""
        searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true) || skill.contains(searchQuery, ignoreCase = true)
    }

    if (selectedTechForDetailView != null) {
        val techData = selectedTechForDetailView!!
        val tName = techData["name"]?.toString() ?: "Specialist"
        val tPic = techData["profilePicUrl"]?.toString()
        val tSkill = techData["skill"]?.toString() ?: "HVAC & AC Master"
        val tPhone = techData["phone"]?.toString() ?: "Available via chat"
        val tEmail = techData["email"]?.toString() ?: ""
        val isVerified = techData["isVerified"] == true
        val ratingData = techRatings[tName]
        val activeJobs = techWorkloads[tName] ?: 0

        AlertDialog(
            onDismissRequest = { selectedTechForDetailView = null },
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        if (tPic != null) {
                            AsyncImage(
                                model = tPic,
                                contentDescription = tName,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, PrimaryBlue, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .background(PrimaryBlue.copy(0.12f), CircleShape)
                                    .border(3.dp, PrimaryBlue, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(80.dp), tint = PrimaryBlue)
                            }
                        }

                        if (isVerified) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(PrimaryGreen, CircleShape)
                                    .padding(4.dp)
                            ) {
                                Icon(Icons.Default.Verified, "Verified", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(tName, fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                    
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryBlue.copy(0.1f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(tSkill, color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("RATING", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = if (ratingData != null) String.format(Locale.getDefault(), "%.1f (%d)", ratingData.first, ratingData.second) else "5.0 (New)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextDark
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATUS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            Text(
                                text = if (activeJobs == 0) "Ready" else "Busy ($activeJobs)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (activeJobs == 0) PrimaryGreen else Color.Red
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BADGE", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            Text("Verified Pro", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PrimaryBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(14.dp))

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Phone, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Phone: $tPhone", fontSize = 12.sp, color = TextDark)
                        }
                        if (tEmail.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Email, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Email: $tEmail", fontSize = 12.sp, color = TextDark)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Build, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Equipment: Multi-Brand HVAC Tools & Gauges", fontSize = 12.sp, color = TextDark)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (customerName == "No Name Set" || customerAddress == "No Address Set") {
                                Toast.makeText(context, "Please set your details in Settings first!", Toast.LENGTH_LONG).show()
                            } else {
                                selectedTechForSchedule = tName
                                selectedTechForDetailView = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Schedule Appointment", fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedTechForDetailView = null }) { Text("Close", color = Color.Gray) }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (selectedTechForSchedule != null) {
        val techData = technicians.find { it["name"] == selectedTechForSchedule }
        AppointmentSchedulerDialog(
            techName = selectedTechForSchedule!!,
            techPic = techData?.get("profilePicUrl")?.toString(),
            techSkill = techData?.get("skill")?.toString() ?: "HVAC Specialist",
            customerName = customerName,
            customerPhone = customerPhone,
            customerAddress = customerAddress,
            onDismiss = { selectedTechForSchedule = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Select Field Specialist", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black), color = TextDark)
            Text("Browse verified technicians with authentic profiles & ratings", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search technician by name or skill...", fontSize = 13.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = TextDark)
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (isLoadingTechs) {
            ShimmerCardList(count = 4)
        } else if (filteredTechs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Person, null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No matching technicians found.", color = Color.Gray, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items(filteredTechs) { techData ->
                    val tech = techData["name"]?.toString() ?: "Unknown"
                    val techPic = techData["profilePicUrl"]?.toString()
                    val skill = techData["skill"]?.toString() ?: "HVAC & AC Master"
                    val isVerified = techData["isVerified"] == true
                    val status = techStatuses[tech] ?: "none"
                    val workload = techWorkloads[tech] ?: 0

                    val isFree = workload == 0
                    val isMyTech = status != "none" && status != "denied" && status != "cancelled" && !status.equals("completed", ignoreCase = true)
                    val isInteractable = isFree || isMyTech

                    val ratingData = techRatings[tech]
                    val ratingText = if (ratingData != null) {
                        String.format(Locale.getDefault(), "%.1f ★ (%d reviews)", ratingData.first, ratingData.second)
                    } else {
                        "5.0 ★ (New Specialist)"
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTechForDetailView = techData },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // High quality Avatar with Picture and Status Ring
                                Box {
                                    if (techPic != null) {
                                        AsyncImage(
                                            model = techPic,
                                            contentDescription = tech,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(CircleShape)
                                                .border(
                                                    BorderStroke(
                                                        2.5.dp,
                                                        if (isFree) PrimaryGreen else Color(0xFFFFB300)
                                                    ),
                                                    CircleShape
                                                ),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(68.dp)
                                                .background(PrimaryBlue.copy(0.12f), CircleShape)
                                                .border(
                                                    BorderStroke(
                                                        2.5.dp,
                                                        if (isFree) PrimaryGreen else Color(0xFFFFB300)
                                                    ),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AccountCircle, null, modifier = Modifier.size(52.dp), tint = PrimaryBlue)
                                        }
                                    }

                                    // Online Status Indicator Dot
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(if (isFree) PrimaryGreen else Color(0xFFFFB300), CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(tech, fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextDark)
                                        if (isVerified) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Default.Verified, "Verified Specialist", tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    // Specialization Skill Pill Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = PrimaryBlue.copy(0.08f)
                                    ) {
                                        Text(
                                            text = skill,
                                            color = PrimaryBlue,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Star, null, tint = Color(0xFFFFB300), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(ratingText, color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF8FAFC))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isFree) PrimaryGreen.copy(0.12f) else Color(0xFFFFF3E0)
                                    ) {
                                        Text(
                                            text = if (isFree) " Ready to Dispatch" else " In Service ($workload active)",
                                            color = if (isFree) PrimaryGreen else Color(0xFFE65100),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { selectedTechForDetailView = techData },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp),
                                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                    ) {
                                        Text("Profile", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                    }

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
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(34.dp)
                                    ) {
                                        Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = when (status.lowercase()) {
                                                "pending" -> "Requested"
                                                "accepted" -> "Accepted"
                                                "denied" -> "Denied"
                                                else -> "Book Now"
                                            },
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (status.lowercase() == "pending" || status.lowercase() == "accepted" || status.lowercase().contains("accepted")) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(
                                        onClick = {
                                            val uid = auth.currentUser?.uid ?: ""
                                            db.collection("service_requests")
                                                .whereEqualTo("customerUid", uid)
                                                .get().addOnSuccessListener { snapshot ->
                                                    snapshot.documents.forEach { doc -> 
                                                        val st = doc.getString("status")?.lowercase() ?: ""
                                                        val docTech = doc.getString("technicianName") ?: doc.getString("technician") ?: ""
                                                        if ((st == "pending" || st == "accepted" || st.contains("accepted")) && (tech.isBlank() || docTech.equals(tech, ignoreCase = true))) {
                                                            doc.reference.update(mapOf("status" to "cancelled", "updatedAt" to com.google.firebase.Timestamp.now())) 
                                                        }
                                                    }
                                                }

                                            db.collection("chats_v2").document("admin_$uid").collection("messages").add(hashMapOf(
                                                "senderId" to uid,
                                                "senderName" to customerName,
                                                "text" to "I have cancelled my request for $tech.",
                                                "timestamp" to com.google.firebase.Timestamp.now()
                                            ))

                                            Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text("Cancel Active Booking", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

// 📅 EVENT CALENDAR & TIME PICKER APPOINTMENT SCHEDULER
@Composable
fun AppointmentSchedulerDialog(
    techName: String,
    techPic: String? = null,
    techSkill: String = "HVAC Specialist",
    customerName: String,
    customerPhone: String,
    customerAddress: String,
    onDismiss: () -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val context = LocalContext.current

    val bookingsState = remember { mutableStateListOf<ScheduleManager.ServiceBooking>() }
    val timeOffState = remember { mutableStateListOf<ScheduleManager.TimeOffBlock>() }

    // Fetch live bookings & time off blocks
    DisposableEffect(techName) {
        val listenerGroup = db.collection("service_requests")
            .addSnapshotListener { snapshot, _ ->
                bookingsState.clear()
                snapshot?.documents?.forEach { doc ->
                    val status = doc.getString("status") ?: "Pending"
                    val startTime = (doc.get("startTimeMillis") as? Number)?.toLong()
                        ?: doc.getTimestamp("scheduledStart")?.toDate()?.time
                        ?: doc.getTimestamp("scheduledTimestamp")?.toDate()?.time ?: 0L
                    val duration = (doc.get("durationMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES
                    val buffer = (doc.get("bufferMinutes") as? Number)?.toInt() ?: ScheduleManager.DEFAULT_BUFFER_MINUTES
                    val endTime = (doc.get("endTimeMillis") as? Number)?.toLong()
                        ?: doc.getTimestamp("scheduledEnd")?.toDate()?.time
                        ?: if (startTime > 0) startTime + (duration * 60 * 1000L) else 0L

                    bookingsState.add(
                        ScheduleManager.ServiceBooking(
                            id = doc.id,
                            customerUid = doc.getString("customerUid") ?: "",
                            customerName = doc.getString("customerName") ?: "Customer",
                            technicianUid = doc.getString("technicianUid"),
                            technicianName = doc.getString("technicianName") ?: doc.getString("technician") ?: doc.getString("assignedTechnician") ?: "",
                            serviceType = doc.getString("issue") ?: "HVAC Service",
                            status = status,
                            startTimeMillis = startTime,
                            endTimeMillis = endTime,
                            durationMinutes = duration,
                            bufferMinutes = buffer,
                            createdAtMillis = (doc.get("createdAtMillis") as? Number)?.toLong()
                                ?: doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                        )
                    )
                }
            }

        val timeOffListener = db.collection("time_off_blocks")
            .addSnapshotListener { snapshot, _ ->
                timeOffState.clear()
                snapshot?.documents?.forEach { doc ->
                    timeOffState.add(
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
            listenerGroup.remove()
            timeOffListener.remove()
        }
    }

    // Calendar & Event Time Picker state
    val todayCal = remember { Calendar.getInstance() }
    val selectedCalendar = remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, 1) // Default to tomorrow
            }
        )
    }

    val displayMonthCalendar = remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
            }
        )
    }

    // Calculate dynamic slots for selected calendar date
    val dynamicSlots = remember(selectedCalendar.value, bookingsState, timeOffState) {
        ScheduleManager.getDailySlotsForTechnician(
            targetCalendar = selectedCalendar.value,
            technicianConfig = ScheduleManager.TechnicianScheduleConfig(technicianName = techName),
            bookings = bookingsState,
            timeOffBlocks = timeOffState
        )
    }

    var selectedSlotObj by remember { mutableStateOf<ScheduleManager.TimeSlot?>(null) }
    LaunchedEffect(dynamicSlots) {
        selectedSlotObj = dynamicSlots.firstOrNull { it.isAvailable } ?: dynamicSlots.firstOrNull()
    }

    var issueNote by remember { mutableStateOf("") }
    var isBooking by remember { mutableStateOf(false) }

    val quickIssueTags = listOf("AC Cleaning", "Unit Not Cooling", "Freon Refill", "Leak Repair", "Noise Check")

    // Helper to format date label
    val fullDateFormatter = remember { SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Technician Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = AppLightBg,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (techPic != null) {
                            AsyncImage(
                                model = techPic,
                                contentDescription = techName,
                                modifier = Modifier.size(50.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier.size(50.dp).background(PrimaryBlue.copy(0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountCircle, null, tint = PrimaryBlue, modifier = Modifier.size(36.dp))
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(techName, fontWeight = FontWeight.Black, fontSize = 16.sp, color = TextDark)
                            Text(techSkill, fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            Text("📍 Address: ${customerAddress.ifBlank { "On Profile" }}", fontSize = 10.sp, color = PrimaryBlue, maxLines = 1)
                        }
                    }
                }

                // ✨ 1. INTERACTIVE MONTH EVENT CALENDAR PICKER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Month & Year Header Navigation
                        val currentMonthCal = displayMonthCalendar.value
                        val monthYearTitle = remember(currentMonthCal.timeInMillis) {
                            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonthCal.time)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newCal = currentMonthCal.clone() as Calendar
                                    newCal.add(Calendar.MONTH, -1)
                                    val minMonth = todayCal.clone() as Calendar
                                    minMonth.set(Calendar.DAY_OF_MONTH, 1)
                                    if (!newCal.before(minMonth)) {
                                        displayMonthCalendar.value = newCal
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Prev Month", tint = TextDark, modifier = Modifier.size(20.dp))
                            }

                            Text(monthYearTitle, fontWeight = FontWeight.Black, fontSize = 15.sp, color = TextDark)

                            IconButton(
                                onClick = {
                                    val newCal = currentMonthCal.clone() as Calendar
                                    newCal.add(Calendar.MONTH, 1)
                                    displayMonthCalendar.value = newCal
                                }
                            ) {
                                Icon(Icons.Default.ChevronRight, "Next Month", tint = TextDark, modifier = Modifier.size(24.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Days of Week Header
                        val daysOfWeek = listOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            daysOfWeek.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Days Grid Logic
                        val year = currentMonthCal.get(Calendar.YEAR)
                        val month = currentMonthCal.get(Calendar.MONTH)
                        
                        val tempCal = currentMonthCal.clone() as Calendar
                        tempCal.set(Calendar.DAY_OF_MONTH, 1)
                        val firstDayOfWeek = tempCal.get(Calendar.DAY_OF_WEEK)
                        val maxDaysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

                        val offsetCount = firstDayOfWeek - 1
                        val totalCells = offsetCount + maxDaysInMonth
                        val numRows = (totalCells + 6) / 7

                        val selCal = selectedCalendar.value
                        val selYear = selCal.get(Calendar.YEAR)
                        val selMonth = selCal.get(Calendar.MONTH)
                        val selDay = selCal.get(Calendar.DAY_OF_MONTH)

                        val todYear = todayCal.get(Calendar.YEAR)
                        val todMonth = todayCal.get(Calendar.MONTH)
                        val todDay = todayCal.get(Calendar.DAY_OF_MONTH)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (rowIndex in 0 until numRows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (colIndex in 0 until 7) {
                                        val cellIndex = rowIndex * 7 + colIndex
                                        val dayNumber = cellIndex - offsetCount + 1

                                        if (dayNumber in 1..maxDaysInMonth) {
                                            val isPast = (year < todYear) ||
                                                    (year == todYear && month < todMonth) ||
                                                    (year == todYear && month == todMonth && dayNumber < todDay)

                                            val isToday = (year == todYear && month == todMonth && dayNumber == todDay)
                                            val isSelected = (year == selYear && month == selMonth && dayNumber == selDay)

                                            val dayCal = Calendar.getInstance().apply { set(year, month, dayNumber) }
                                            val isDayAvailable = remember(dayNumber, month, year, bookingsState.size, timeOffState.size) {
                                                ScheduleManager.isDateAvailable(
                                                    targetCalendar = dayCal,
                                                    allTechConfigs = listOf(ScheduleManager.TechnicianScheduleConfig(technicianName = techName)),
                                                    selectedTechName = techName,
                                                    bookings = bookingsState,
                                                    timeOffBlocks = timeOffState
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .padding(2.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isSelected -> PrimaryBlue
                                                            isToday -> PrimaryGreen.copy(0.12f)
                                                            !isDayAvailable && !isPast -> Color(0xFFFEE2E2) // Red tint for fully booked
                                                            else -> Color.Transparent
                                                        }
                                                    )
                                                    .border(
                                                        BorderStroke(
                                                            width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                                                            color = if (isToday && !isSelected) PrimaryGreen else Color.Transparent
                                                        ),
                                                        CircleShape
                                                    )
                                                    .clickable(enabled = !isPast) {
                                                        val updated = Calendar.getInstance()
                                                        updated.set(year, month, dayNumber)
                                                        selectedCalendar.value = updated
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        text = "$dayNumber",
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isSelected || isToday) FontWeight.Black else FontWeight.Medium,
                                                        color = when {
                                                            isSelected -> Color.White
                                                            isPast -> Color.LightGray
                                                            !isDayAvailable -> Color.Red
                                                            isToday -> PrimaryGreen
                                                            else -> TextDark
                                                        }
                                                    )
                                                    if (!isDayAvailable && !isPast && !isSelected) {
                                                        Text("FULL", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.Red)
                                                    }
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ✨ 2. REAL-TIME ACCURATE TIME SLOTS
                Text("Select Available Time Slot (30m buffer included):", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                if (dynamicSlots.isEmpty()) {
                    Text("No working hours or available slots on this day.", fontSize = 12.sp, color = Color.Red, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        dynamicSlots.forEach { slot ->
                            val isSel = selectedSlotObj?.startTimeMillis == slot.startTimeMillis
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = slot.isAvailable) { selectedSlotObj = slot },
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSel -> PrimaryGreen.copy(0.15f)
                                    !slot.isAvailable -> Color(0xFFF1F5F9)
                                    else -> AppLightBg
                                },
                                border = BorderStroke(
                                    width = if (isSel) 2.dp else 1.dp,
                                    color = when {
                                        isSel -> PrimaryGreen
                                        !slot.isAvailable -> Color.LightGray
                                        else -> FieldBorder
                                    }
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Schedule,
                                            null,
                                            tint = if (slot.isAvailable) PrimaryGreen else Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            slot.formattedTimeRange,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (slot.isAvailable) TextDark else Color.Gray
                                        )
                                    }

                                    Text(
                                        text = if (slot.isAvailable) "Available" else (slot.blockedReason ?: "Unavailable"),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (slot.isAvailable) PrimaryGreen else Color.Red
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ✨ 3. SERVICE REASON / ISSUE INPUT
                Text("Describe HVAC Issue or Service:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = issueNote,
                    onValueChange = { issueNote = it },
                    placeholder = { Text("e.g. AC Cleaning & Maintenance, Leaking Water...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = TextDark)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Quick issue tag chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(quickIssueTags) { tag ->
                        Surface(
                            modifier = Modifier.clickable { issueNote = tag },
                            shape = RoundedCornerShape(8.dp),
                            color = PrimaryBlue.copy(0.08f)
                        ) {
                            Text(tag, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ✨ BOOKING SUMMARY BANNER
                val formattedDateStr = remember(selectedCalendar.value) { fullDateFormatter.format(selectedCalendar.value.time) }
                val slotLabel = selectedSlotObj?.formattedTimeRange ?: "No slot selected"
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(0.08f)),
                    border = BorderStroke(1.dp, PrimaryGreen.copy(0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("APPOINTMENT SUMMARY", fontWeight = FontWeight.Black, fontSize = 10.sp, color = PrimaryGreen)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("📅 Date: $formattedDateStr", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("⏰ Time: $slotLabel", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        if (issueNote.isNotBlank()) {
                            Text("🛠️ Issue: $issueNote", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBooking && (selectedSlotObj?.isAvailable == true),
                onClick = {
                    val targetSlot = selectedSlotObj ?: return@Button
                    val uid = auth.currentUser?.uid ?: return@Button
                    isBooking = true

                    // SERVER-SIDE ATOMIC CONFLICT VERIFICATION BEFORE COMMIT
                    val conflict = ScheduleManager.hasConflict(
                        technicianName = techName,
                        startTimeMillis = targetSlot.startTimeMillis,
                        endTimeMillis = targetSlot.endTimeMillis,
                        bookings = bookingsState,
                        timeOffBlocks = timeOffState
                    )

                    if (conflict) {
                        Toast.makeText(context, "This time was just booked, please choose another slot.", Toast.LENGTH_LONG).show()
                        isBooking = false
                        return@Button
                    }

                    val dateLabel = fullDateFormatter.format(java.util.Date(targetSlot.startTimeMillis))
                    val finalIssue = issueNote.ifBlank { "Scheduled Service Appointment" }

                    val reqId = db.collection("service_requests").document().id
                    val nowTimestamp = com.google.firebase.Timestamp.now()
                    val serviceRequest = mapOf(
                        "requestId" to reqId,
                        "type" to "service",
                        "customerUid" to uid,
                        "customerName" to customerName,
                        "customerPhone" to customerPhone,
                        "customerAddress" to customerAddress,
                        "technicianName" to techName,
                        "technician" to techName,
                        "assignedTechnician" to techName,
                        "status" to "Pending",
                        "scheduledDate" to dateLabel,
                        "scheduledTime" to targetSlot.formattedTimeRange,
                        "scheduledStart" to com.google.firebase.Timestamp(java.util.Date(targetSlot.startTimeMillis)),
                        "scheduledEnd" to com.google.firebase.Timestamp(java.util.Date(targetSlot.endTimeMillis)),
                        "startTimeMillis" to targetSlot.startTimeMillis,
                        "endTimeMillis" to targetSlot.endTimeMillis,
                        "durationMinutes" to ScheduleManager.DEFAULT_SERVICE_DURATION_MINUTES,
                        "bufferMinutes" to ScheduleManager.DEFAULT_BUFFER_MINUTES,
                        "createdAt" to nowTimestamp,
                        "updatedAt" to nowTimestamp,
                        "createdAtMillis" to System.currentTimeMillis(),
                        "issue" to "$finalIssue ($dateLabel - ${targetSlot.formattedTimeRange})",
                        "statusHistory" to listOf(
                            mapOf(
                                "status" to "Pending",
                                "updatedBy" to customerName,
                                "timestampMillis" to System.currentTimeMillis(),
                                "fromStatus" to "None"
                            )
                        )
                    )

                    db.collection("service_requests").document(reqId).set(serviceRequest)
                    db.collection("service_history").document(reqId).set(serviceRequest, com.google.firebase.firestore.SetOptions.merge())

                    val autoMsg = hashMapOf(
                        "senderId" to uid,
                        "senderName" to customerName,
                        "text" to "Hello! I am scheduling a service appointment with $techName for $dateLabel during ${targetSlot.formattedTimeRange}. Service detail: $finalIssue.",
                        "timestamp" to nowTimestamp
                    )
                    db.collection("chats_v2").document("admin_$uid").collection("messages").add(autoMsg)

                    Toast.makeText(context, "Appointment requested for $dateLabel (${targetSlot.formattedTimeRange})!", Toast.LENGTH_LONG).show()
                    isBooking = false
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isBooking) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                else {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Confirm Appointment", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

// 💬 5. LIVE CHAT VIEW
@Composable
fun CustomerChatHubView(onChatSelected: (String, String) -> Unit) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: return

    var adminLastMsg by remember { mutableStateOf("") }
    var adminLastTs by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var adminStatus by remember { mutableStateOf<String?>(null) }
    var assignedTech by remember { mutableStateOf<String?>(null) }

    var techLastMsg by remember { mutableStateOf("") }
    var techLastTs by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var techStatus by remember { mutableStateOf<String?>(null) }

    val userProfilePics = remember { mutableStateMapOf<String, String?>() }
    val userOnlineStatus = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(uid) {
        db.collection("users").addSnapshotListener { uSnap, _ ->
            uSnap?.documents?.forEach { uDoc ->
                val name = uDoc.getString("name") ?: ""
                val role = uDoc.getString("role") ?: ""
                val pic = uDoc.getString("profilePicUrl")
                val online = uDoc.getBoolean("isOnline") ?: false

                if (name.isNotBlank()) {
                    userProfilePics[name.lowercase(Locale.getDefault())] = pic
                    userOnlineStatus[name.lowercase(Locale.getDefault())] = online
                }
                if (role.lowercase(Locale.getDefault()) == "admin") {
                    userProfilePics["admin"] = pic
                    userOnlineStatus["admin"] = online
                }
            }
        }

        db.collection("chats_v2").document("admin_$uid").addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                assignedTech = doc.getString("assignedTechnician")
                adminLastMsg = doc.getString("lastMessage") ?: "Customer Service Channel"
                adminLastTs = doc.getTimestamp("timestamp")
                adminStatus = doc.getString("status")
            }
        }
    }

    LaunchedEffect(assignedTech) {
        if (!assignedTech.isNullOrBlank()) {
            val techChatId = "tech_${assignedTech}_$uid"
            db.collection("chats_v2").document(techChatId).addSnapshotListener { tDoc, _ ->
                if (tDoc != null && tDoc.exists()) {
                    techLastMsg = tDoc.getString("lastMessage") ?: "Direct Field Service Line"
                    techLastTs = tDoc.getTimestamp("timestamp")
                    techStatus = tDoc.getString("status")
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Messages", style = MaterialTheme.typography.headlineMedium, color = TextDark, fontWeight = FontWeight.Black)
        Text("Direct channels with Admin Support and Field Technicians", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Admin Channel
            item {
                val adminPic = userProfilePics["admin"]
                val isAdminOnline = userOnlineStatus["admin"] ?: false
                val adminTimeStr = remember(adminLastTs) {
                    if (adminLastTs == null) "" else {
                        val date = adminLastTs!!.toDate()
                        val now = Calendar.getInstance()
                        val cal = Calendar.getInstance().apply { time = date }
                        if (now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR))
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                        else SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChatSelected("admin_$uid", "Admin Support") },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(48.dp)) {
                            if (!adminPic.isNullOrBlank()) {
                                AsyncImage(
                                    model = adminPic,
                                    contentDescription = "Admin Support",
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(PrimaryBlue.copy(0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "A",
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            if (isAdminOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(PrimaryGreen, CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Admin Support", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 15.sp)
                                if (adminTimeStr.isNotBlank()) {
                                    Text(adminTimeStr, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = adminLastMsg.ifBlank { "Customer Service Channel" },
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                            if (!adminStatus.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Status: $adminStatus",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (adminStatus.equals("Completed", ignoreCase = true)) PrimaryGreen else PrimaryBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // Assigned Tech Channel
            if (assignedTech != null) {
                item {
                    val techName = assignedTech!!
                    val techPic = userProfilePics[techName.lowercase(Locale.getDefault())]
                    val isTechOnline = userOnlineStatus[techName.lowercase(Locale.getDefault())] ?: false
                    val techTimeStr = remember(techLastTs) {
                        if (techLastTs == null) "" else {
                            val date = techLastTs!!.toDate()
                            val now = Calendar.getInstance()
                            val cal = Calendar.getInstance().apply { time = date }
                            if (now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR))
                                SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                            else SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChatSelected("tech_${techName}_$uid", techName) },
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(48.dp)) {
                                if (!techPic.isNullOrBlank()) {
                                    AsyncImage(
                                        model = techPic,
                                        contentDescription = techName,
                                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(PrimaryGreen.copy(0.12f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = techName.take(1).uppercase(Locale.getDefault()),
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryGreen,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                                if (isTechOnline) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(PrimaryGreen, CircleShape)
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(techName, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 15.sp)
                                    if (techTimeStr.isNotBlank()) {
                                        Text(techTimeStr, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = techLastMsg.ifBlank { "Assigned Field Specialist" },
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1
                                )
                                if (!techStatus.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Status: $techStatus",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (techStatus.equals("Completed", ignoreCase = true)) PrimaryGreen else PrimaryBlue
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
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
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val uid = auth.currentUser?.uid ?: ""
    val services = remember { mutableStateListOf<Map<String, Any>>() }
    var customerName by remember { mutableStateOf("") }
    var parentAssignedTech by remember { mutableStateOf("") }
    var isLoadingServices by remember { mutableStateOf(true) }

    var selectedFilter by remember { mutableStateOf("All") }
    var selectedServiceForRating by remember { mutableStateOf<Map<String, Any>?>(null) }
    var selectedServiceForReceipt by remember { mutableStateOf<Map<String, Any>?>(null) }

    LaunchedEffect(Unit) {
        db.collection("users").document(uid).get().addOnSuccessListener { customerName = it.getString("name") ?: "Customer" }

        db.collection("service_requests")
            .whereEqualTo("customerUid", uid)
            .addSnapshotListener { snapshot, _ ->
                services.clear()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    data["requestId"] = doc.id
                    services.add(data)
                }
                isLoadingServices = false
            }
    }

    val activeCount = services.count { svc ->
        val st = svc["status"]?.toString()?.lowercase() ?: ""
        st != "completed" && st != "cancelled" && st != "denied"
    }

    val completedCount = services.count { svc ->
        svc["status"]?.toString()?.lowercase() == "completed"
    }

    val cancelledCount = services.count { svc ->
        val st = svc["status"]?.toString()?.lowercase() ?: ""
        st == "cancelled" || st == "denied"
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Order & Service Tracking", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark))
                Text("Live Dispatch Timeline & Digital Receipts", fontSize = 12.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📊 HIGH-DENSITY DASHBOARD METRICS
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("PLATFORM STATUS", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    Text(if (activeCount > 0) "$activeCount Active Dispatch" else "All Services Clear", fontSize = 15.sp, fontWeight = FontWeight.Black, color = if (activeCount > 0) PrimaryBlue else PrimaryGreen)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(shape = RoundedCornerShape(10.dp), color = PrimaryBlue.copy(0.08f)) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$activeCount", fontWeight = FontWeight.Black, fontSize = 14.sp, color = PrimaryBlue)
                            Text("Active", fontSize = 9.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(shape = RoundedCornerShape(10.dp), color = PrimaryGreen.copy(0.08f)) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$completedCount", fontWeight = FontWeight.Black, fontSize = 14.sp, color = PrimaryGreen)
                            Text("Completed", fontSize = 9.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🏷️ STATUS FILTER CHIPS WITH COUNTERS
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "All" to services.size,
                "Active" to activeCount,
                "Completed" to completedCount,
                "Cancelled" to cancelledCount
            ).forEach { (flt, count) ->
                val isSel = selectedFilter == flt
                Surface(
                    modifier = Modifier.clickable { selectedFilter = flt }.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) PrimaryBlue else Color.White,
                    border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color(0xFFE2E8F0))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            flt,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else TextDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) Color.White.copy(0.25f) else AppLightBg
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSel) Color.White else Color.Gray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isLoadingServices) {
            ShimmerCardList(count = 3)
        } else if (filteredServices.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(32.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = Color.LightGray, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Orders or Services Found", fontWeight = FontWeight.Black, color = TextDark, fontSize = 16.sp)
                    Text("You have no service records matching the '$selectedFilter' filter.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredServices.forEach { svc ->
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

                    val rawTech = svc["technician"]?.toString() ?: ""
                    val techDisplay = if (rawTech.contains("Hardware Team", true) || rawTech.isBlank()) {
                        if (parentAssignedTech.isNotBlank()) "Expert: $parentAssignedTech (Installation)" else "Technician: Awaiting Admin Dispatch"
                    } else {
                        "Expert: $rawTech"
                    }

                    val issueText = svc["issue"]?.toString() ?: "HVAC Service & Repair"
                    val techNotes = (svc["completionNotes"] ?: svc["notes"] ?: svc["workNotes"])?.toString()
                    val partsUsed = (svc["partsUsed"] ?: svc["partsDetails"])?.toString()
                    val partsFeeVal = (svc["partsFee"] as? Number)?.toDouble() ?: 0.0
                    val laborFeeVal = (svc["laborFee"] as? Number)?.toDouble() ?: 0.0
                    val totalFeeVal = (svc["totalAmount"] as? Number)?.toDouble() ?: (laborFeeVal + partsFeeVal)
                    val aiDiag = svc["aiDiagnostic"]?.toString()

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header Row: Tech Info + Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(PrimaryBlue.copy(0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (issueText.contains("Hardware", true)) Icons.Default.ShoppingCart else Icons.Default.Build,
                                            contentDescription = null,
                                            tint = PrimaryBlue,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = techDisplay,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCompleted) PrimaryGreen.copy(0.12f) else PrimaryBlue.copy(0.12f)
                                ) {
                                    Text(
                                        text = status.uppercase(),
                                        color = if (isCompleted) PrimaryGreen else PrimaryBlue,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (isLate) {
                                Spacer(modifier = Modifier.height(6.dp))
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

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(issueText, fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)

                            if (!schedDate.isNullOrBlank() && !schedTime.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarToday, null, tint = PrimaryBlue, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("$schedDate • $schedTime", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                }
                            }

                            // 📝 TECHNICIAN NOTES & SUMMARY
                            if (!techNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFF8FAFC),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Notes, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Technician Notes:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PrimaryBlue)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(techNotes, fontSize = 11.sp, color = TextDark, lineHeight = 15.sp)
                                    }
                                }
                            }

                            // 🔧 PARTS USED & REPLACEMENT MATERIALS
                            if (!partsUsed.isNullOrBlank() || partsFeeVal > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFFF0FDF4),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Memory, null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Parts Used:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = PrimaryGreen)
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val partsText = if (!partsUsed.isNullOrBlank()) {
                                            "$partsUsed" + if (partsFeeVal > 0) " (₱${String.format(Locale.getDefault(), "%,.2f", partsFeeVal)})" else ""
                                        } else {
                                            "Parts Fee: ₱${String.format(Locale.getDefault(), "%,.2f", partsFeeVal)}"
                                        }
                                        Text(partsText, fontSize = 11.sp, color = TextDark, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }

                            // 💰 FINANCIAL SUMMARY
                            if (totalFeeVal > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Labor: ₱${String.format(Locale.getDefault(), "%,.0f", laborFeeVal)} | Parts: ₱${String.format(Locale.getDefault(), "%,.0f", partsFeeVal)}",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                    Text(
                                        text = "Total: ₱${String.format(Locale.getDefault(), "%,.2f", totalFeeVal)}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = PrimaryBlue
                                    )
                                }
                            }

                            // 🤖 AI DIAGNOSTIC INSIGHT
                            if (!aiDiag.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(aiDiag, fontSize = 10.sp, color = PrimaryBlue, lineHeight = 14.sp)
                                    }
                                }
                            }

                            // Compact Line & Step Timeline
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))

                            ServiceStepTimeline(status = status)

                            if (isCompleted) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { selectedServiceForReceipt = svc },
                                        modifier = Modifier.fillMaxWidth().height(38.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(6.dp))
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
                                            modifier = Modifier.fillMaxWidth().height(40.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp), tint = Color.White)
                                            Spacer(modifier = Modifier.width(6.dp))
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
    val stLower = status.lowercase()
    val currentStep = when {
        stLower.contains("completed") -> 3
        stLower.contains("progress") -> 2
        stLower.contains("accepted") -> 1
        stLower.contains("pending") || stLower.contains("requested") || stLower.contains("order") -> 0
        else -> 1
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
    val partsUsed = (service["partsUsed"] ?: service["partsDetails"])?.toString()
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
                if (parts > 0 || !partsUsed.isNullOrBlank()) {
                    val labelStr = if (!partsUsed.isNullOrBlank()) "Parts ($partsUsed)" else "Hardware & Parts Fee"
                    ReceiptFeeRow(labelStr, "₱${String.format(Locale.getDefault(), "%.2f", parts)}")
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
                    ReportExporter.exportSingleReceiptToFile(context, customerName, service)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Download E-Receipt File", fontWeight = FontWeight.Bold)
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

                    db.collection("service_requests").document(serviceDocId)
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
    var showLogoutDialog by remember { mutableStateOf(false) }

    var showChangePasswordDialog by remember { mutableStateOf(false) }
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showNotificationPrefsDialog by remember { mutableStateOf(false) }
    var showHelpSupportDialog by remember { mutableStateOf(false) }

    if (showChangePasswordDialog) {
        CustomerChangePasswordDialog(email = email, onDismiss = { showChangePasswordDialog = false })
    }

    if (showChangePinDialog) {
        CustomerChangePinDialog(onDismiss = { showChangePinDialog = false })
    }

    if (showNotificationPrefsDialog) {
        CustomerNotificationPrefsDialog(onDismiss = { showNotificationPrefsDialog = false })
    }

    if (showHelpSupportDialog) {
        CustomerHelpSupportDialog(onDismiss = { showHelpSupportDialog = false })
    }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

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
                    onClick = { showChangePasswordDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.Default.Pin,
                    title = "Change Pin",
                    onClick = { showChangePinDialog = true }
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
                        onCheckedChange = {
                            enableBiometrics = it
                            Toast.makeText(context, if (it) "Biometrics authentication enabled!" else "Biometrics disabled", Toast.LENGTH_SHORT).show()
                        },
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
                    onClick = { showNotificationPrefsDialog = true }
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
                    onClick = { showHelpSupportDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                ProfileOptionRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Log Out",
                    subtitle = "Sign out of your CoolConnect account",
                    onClick = { showLogoutDialog = true }
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(order.orderId, fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when(order.status) {
                        "Processing" -> Color(0xFFFF9800).copy(0.12f)
                        "Delivered", "Completed" -> PrimaryGreen.copy(0.12f)
                        else -> Color.Gray.copy(0.12f)
                    }
                ) {
                    Text(
                        order.status, 
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp, 
                        fontWeight = FontWeight.Bold,
                        color = when(order.status) {
                            "Processing" -> Color(0xFFFF9800)
                            "Delivered", "Completed" -> PrimaryGreen
                            else -> Color.Gray
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            order.items.forEach { item ->
                val name = item["name"]?.toString() ?: "Hardware Item"
                val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                val price = item["price"]?.toString() ?: ""
                Text("• ${qty}x $name ($price)", fontSize = 12.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color.LightGray.copy(0.2f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(sdf.format(order.timestamp.toDate()), fontSize = 11.sp, color = Color.Gray)
                Text("Total Paid: ₱${String.format(Locale.getDefault(), "%,.2f", order.total)}", fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 13.sp)
            }
            if (order.shippingAddress.isNotBlank()) {
                Text("Address: ${order.shippingAddress}", fontSize = 10.sp, color = Color.Gray, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
fun CustomerChangePasswordDialog(email: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var isSending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Change Password", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Column {
                Text(
                    "To secure your account, we will send a official password reset link to your registered email address:",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppLightBg,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(email, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold, color = TextDark, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isSending = true
                    auth.sendPasswordResetEmail(email).addOnSuccessListener {
                        Toast.makeText(context, "Password reset email sent to $email!", Toast.LENGTH_LONG).show()
                        isSending = false
                        onDismiss()
                    }.addOnFailureListener { e ->
                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        isSending = false
                    }
                },
                enabled = !isSending,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (isSending) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                else Text("Send Password Reset Email", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun CustomerChangePinDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var pinInput by remember { mutableStateOf("") }
    var confirmPinInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Pin, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Security PIN", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Column {
                Text("Set a 4-digit Security PIN for quick checkout & authentication:", fontSize = 12.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                    label = { Text("Enter 4-Digit PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPinInput,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) confirmPinInput = it },
                    label = { Text("Confirm 4-Digit PIN") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length != 4) {
                        Toast.makeText(context, "PIN must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (pinInput != confirmPinInput) {
                        Toast.makeText(context, "PINs do not match", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val uid = auth.currentUser?.uid ?: return@Button
                    db.collection("users").document(uid).update("pin", pinInput)
                    Toast.makeText(context, "Security PIN updated successfully!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save Security PIN", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun CustomerNotificationPrefsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var dispatchAlerts by remember { mutableStateOf(true) }
    var orderUpdates by remember { mutableStateOf(true) }
    var promoReminders by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification Preferences", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Column {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Service Dispatch & Arrival Alerts", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Text("Loud audio alerts when technician is en-route", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(checked = dispatchAlerts, onCheckedChange = { dispatchAlerts = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue))
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hardware Delivery Tracking", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Text("Updates on purchased items & delivery", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(checked = orderUpdates, onCheckedChange = { orderUpdates = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue))
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Maintenance & Seasonal Promos", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                        Text("Quarterly cleaning reminders & discounts", fontSize = 10.sp, color = Color.Gray)
                    }
                    Switch(checked = promoReminders, onCheckedChange = { promoReminders = it }, colors = SwitchDefaults.colors(checkedTrackColor = PrimaryBlue))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Toast.makeText(context, "Notification preferences updated!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save Preferences", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun CustomerHelpSupportDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Help & Customer Support", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Need assistance with your HVAC booking or hardware order?", fontSize = 12.sp, color = Color.Gray)

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:0288882665"))
                        context.startActivity(callIntent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = AppLightBg,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Call Hotline: (02) 8888-COOL", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                            Text("24/7 Operations Command Line", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:support@coolconnect.com"))
                        context.startActivity(emailIntent)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = AppLightBg,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Email Support", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                            Text("support@coolconnect.com", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), shape = RoundedCornerShape(10.dp)) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
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

// 📍 CUSTOMER LOCATION PERMISSION DIALOG
@Composable
fun CustomerLocationPermissionDialog(
    onGrantPermission: () -> Unit,
    onEnableGps: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFFFFF0F0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOff,
                    contentDescription = "Location Off",
                    tint = Color.Red,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                text = "Location is Turned Off",
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
                color = TextDark,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "CoolConnect requires location access to match you with nearby technicians and broadcast live updates during service dispatches.",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = PrimaryBlue.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Turn on GPS and allow location permission so field technicians can navigate to your address.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryBlue,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEnableGps()
                    onGrantPermission()
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Turn On Location (GPS)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TextDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open App Settings", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Not Now", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    )
}
