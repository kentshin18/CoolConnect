package com.example.coolconnect

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.coolconnect.ui.theme.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class AdminFeedbackState(
    val isOpen: Boolean = false,
    val title: String = "",
    val message: String = "",
    val isError: Boolean = false
)

enum class AdminTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Fleet", Icons.Default.Dashboard),
    TECH_REGISTER("Tech Register", Icons.Default.PersonAdd),
    TRACKING("Service Track", Icons.Default.MyLocation),
    INVENTORY("Inventory", Icons.Default.Inventory),
    INBOX("Messages", Icons.AutoMirrored.Filled.Chat),
    HISTORY("Reports", Icons.Default.History)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onLogout: () -> Unit, viewModel: AdminViewModel = hiltViewModel()) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    
    val technicians by viewModel.technicians.collectAsState()
    val products by viewModel.products.collectAsState()
    val completedJobs by viewModel.completedJobs.collectAsState()
    val pendingServices by viewModel.pendingServices.collectAsState()
    val orders by viewModel.orders.collectAsState()
    val neighborhoodClusters by viewModel.neighborhoodClusters.collectAsState()
    val adminProfilePicUrl by viewModel.adminProfilePicUrl.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    var currentTab by remember { mutableStateOf(AdminTab.DASHBOARD) }
    var feedbackState by remember { mutableStateOf(AdminFeedbackState()) }
    var showPrivacyConsent by remember { mutableStateOf(false) }

    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeRecipientUserId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    val context = LocalContext.current

    // Hamburger Menu & TopBar Controls
    var showHamburgerMenu by remember { mutableStateOf(false) }
    var showNotifDialog by remember { mutableStateOf(false) }
    var showProfileMenu by remember { mutableStateOf(false) }

    val newJobNotifs = remember { mutableStateListOf<Map<String, Any>>() }
    val escalations = remember { mutableStateListOf<Map<String, Any>>() }

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

    // Intercept Back Button
    BackHandler(enabled = activeChatId == null) {
        if (currentTab != AdminTab.DASHBOARD) {
            currentTab = AdminTab.DASHBOARD
        } else {
            (context as? android.app.Activity)?.finish()
        }
    }

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
            chatId = activeChatId!!,
            recipientUserId = activeRecipientUserId,
            recipientName = activeChatName,
            currentUserId = auth.currentUser?.uid ?: "admin",
            currentUserName = "Admin Support",
            isAdmin = true,
            onBack = { activeChatId = null }
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
                    navigationIcon = {
                        Box {
                            IconButton(onClick = { showHamburgerMenu = true }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu Options", tint = TextDark, modifier = Modifier.size(26.dp))
                            }

                            // ☰ HAMBURGER DROPDOWN MENU OPTIONS
                            DropdownMenu(
                                expanded = showHamburgerMenu,
                                onDismissRequest = { showHamburgerMenu = false },
                                modifier = Modifier
                                    .width(260.dp)
                                    .background(Color.White, RoundedCornerShape(16.dp))
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.AccountCircle, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text("Admin Profile", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                                Text("View & upload avatar", fontSize = 11.sp, color = Color.Gray)
                                            }
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        showProfileMenu = true
                                    }
                                )
                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Dashboard, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Fleet Operations", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        currentTab = AdminTab.DASHBOARD
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.PersonAdd, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Register Technician", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        currentTab = AdminTab.TECH_REGISTER
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MyLocation, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Live Service Track", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        currentTab = AdminTab.TRACKING
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Inventory, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Inventory Stock", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        currentTab = AdminTab.INVENTORY
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Assessment, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Service & Sales Reports", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        currentTab = AdminTab.HISTORY
                                    }
                                )

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Notifications, null, tint = TextDark, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Notifications Alert", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        showNotifDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Shield, null, tint = PrimaryGreen, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Privacy Consent", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        showPrivacyConsent = true
                                    }
                                )

                                HorizontalDivider(color = Color(0xFFF1F5F9))

                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = Color.Red, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Sign Out", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Red)
                                        }
                                    },
                                    onClick = {
                                        showHamburgerMenu = false
                                        onLogout()
                                    }
                                )
                            }
                        }
                    },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(PrimaryBlue.copy(0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AdminPanelSettings, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(currentTab.label, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                                Text("CoolConnect • Admin Command Center", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
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
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.AccountCircle, "Admin Profile", tint = TextDark, modifier = Modifier.size(28.dp))
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
                        AdminTab.DASHBOARD -> AdminFleetOperationsView(
                            technicians = technicians, 
                            pendingServices = pendingServices,
                            orders = orders,
                            neighborhoodClusters = neighborhoodClusters, 
                            showFeedback = ::showFeedback,
                            onChatSelected = { id, name -> activeChatId = id; activeChatName = name },
                            onAssignTechnician = { orderId, customerUid, techName, techUid -> viewModel.assignTechnicianToOrder(orderId, customerUid, techName, techUid) },
                            onNavigateToTechRegister = { currentTab = AdminTab.TECH_REGISTER }
                        )
                        AdminTab.TECH_REGISTER -> AdminTechRegisterView(
                            technicians = technicians,
                            showFeedback = ::showFeedback
                        )
                        AdminTab.TRACKING -> AdminServiceTrackView(
                            technicians = technicians,
                            onChatSelected = { id, name -> activeChatId = id; activeChatName = name }
                        )
                        AdminTab.INVENTORY -> AdminInventoryView(products = products, showFeedback = ::showFeedback)
                        AdminTab.INBOX -> ConversationListScreen(
                            currentUserId = auth.currentUser?.uid ?: "admin",
                            currentUserName = "Admin Support",
                            userRole = "admin",
                            onChatSelected = { id, recipientUserId, name ->
                                activeChatId = id
                                activeRecipientUserId = recipientUserId
                                activeChatName = name
                            }
                        )
                        AdminTab.HISTORY -> AdminReportsView(completedJobs = completedJobs)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminBottomBar(currentTab: AdminTab, onTabSelected: (AdminTab) -> Unit) {
    val bottomNavTabs = listOf(
        AdminTab.DASHBOARD,
        AdminTab.TRACKING,
        AdminTab.INBOX
    )

    Surface(
        color = Color.White,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            bottomNavTabs.forEach { tab ->
                val selected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) PrimaryBlue.copy(alpha = 0.12f) else Color.Transparent
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Icon(
                                    tab.icon,
                                    contentDescription = tab.label,
                                    tint = if (selected) PrimaryBlue else Color(0xFF64748B),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            color = if (selected) PrimaryBlue else Color(0xFF64748B)
                        )
                    }
                }
            }
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

// 🛠️ 1. FLEET OPERATIONS DISPATCH DASHBOARD
@Composable
fun AdminFleetOperationsView(
    technicians: List<Map<String, Any>>, 
    pendingServices: List<Map<String, Any>>,
    orders: List<ShopOrder> = emptyList(),
    neighborhoodClusters: Map<String, List<Map<String, Any>>> = emptyMap(),
    showFeedback: (String, String, Boolean) -> Unit,
    onChatSelected: (String, String) -> Unit,
    onAssignTechnician: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onNavigateToTechRegister: () -> Unit = {}
) {
    var selectedOrderForAssign by remember { mutableStateOf<ShopOrder?>(null) }

    if (selectedOrderForAssign != null) {
        AssignTechnicianDialog(
            order = selectedOrderForAssign!!,
            technicians = technicians,
            onDismiss = { selectedOrderForAssign = null },
            onAssign = { techName, techUid ->
                onAssignTechnician(selectedOrderForAssign!!.orderId, selectedOrderForAssign!!.customerUid, techName, techUid)
                showFeedback("Dispatched", "Installer $techName assigned to ${selectedOrderForAssign!!.orderId}", false)
                selectedOrderForAssign = null
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Fleet Operations", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                Text("Dispatch & Order Assignment Center", fontSize = 12.sp, color = Color.Gray)
            }

            Button(
                onClick = onNavigateToTechRegister,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.PersonAdd, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Tech", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fleet Performance Summary Card
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(70.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) { 
                            drawArc(PrimaryBlue, 0f, 240f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
                            drawArc(PrimaryGreen, 245f, 110f, false, style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)) 
                        }
                        Text("${technicians.size}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                    }
                    Column { LegendItem("Active", PrimaryBlue); LegendItem("Ready", PrimaryGreen) }
                    Column { SpecialtyMetric("AC", technicians.count { it["skill"]?.toString()?.contains("AC") == true }); SpecialtyMetric("Elec", technicians.count { it["skill"]?.toString()?.contains("Elec") == true }) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🛒 CUSTOMER HARDWARE ORDERS & INSTALLATION DISPATCH
        if (orders.isNotEmpty()) {
            Text("CUSTOMER HARDWARE ORDERS (${orders.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                orders.forEach { order ->
                    val isAssigned = order.assignedTechnician.isNotBlank() && !order.assignedTechnician.equals("Unassigned", ignoreCase = true)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Header: Order ID + Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(PrimaryBlue.copy(0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ShoppingCart, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(order.orderId, fontWeight = FontWeight.Black, fontSize = 14.sp, color = TextDark)
                                        Text(order.customerName, fontSize = 12.sp, color = Color.Gray)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isAssigned) PrimaryGreen.copy(0.12f) else Color(0xFFFFF3E0)
                                ) {
                                    Text(
                                        text = if (isAssigned) "DISPATCHED" else "PENDING DISPATCH",
                                        color = if (isAssigned) PrimaryGreen else Color(0xFFE65100),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF0F0F0))
                            Spacer(modifier = Modifier.height(10.dp))

                            // Items bought breakdown
                            Text("BOUGHT ITEMS:", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                            if (order.items.isNotEmpty()) {
                                order.items.forEach { item ->
                                    val name = item["name"]?.toString() ?: "Item"
                                    val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                                    val price = item["price"]?.toString() ?: ""
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("• $name (x$qty)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                                        Text(price, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                    }
                                }
                            } else {
                                Text(order.itemsSummary.ifBlank { "Smart HVAC Items" }, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextDark)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Place, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(order.shippingAddress.ifBlank { "Address on profile" }, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("TOTAL AMOUNT", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                                    Text("₱${String.format(Locale.getDefault(), "%,.2f", order.total)}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = PrimaryBlue)
                                }

                                Button(
                                    onClick = { selectedOrderForAssign = order },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAssigned) PrimaryBlue.copy(0.12f) else PrimaryBlue,
                                        contentColor = if (isAssigned) PrimaryBlue else Color.White
                                    ),
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.Engineering, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAssigned) "Installer: ${order.assignedTechnician}" else "Assign Installer & Delivery",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }



        // 🚨 UNASSIGNED DISPATCH QUEUE
        if (pendingServices.isNotEmpty()) {
            Text("UNASSIGNED DISPATCH QUEUE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 20.dp)) {
                pendingServices.forEach { service ->
                    val customerName = service["customerName"]?.toString() ?: "New Customer"
                    val issue = service["issue"]?.toString() ?: "HVAC Service"
                    val parentChatId = service["parentChatId"]?.toString() ?: ""
                    
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (parentChatId.isNotEmpty()) {
                                onChatSelected(parentChatId, customerName)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.Assignment, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(customerName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(issue, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                            }
                            Button(
                                onClick = { if (parentChatId.isNotEmpty()) onChatSelected(parentChatId, customerName) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Dispatch", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 👤 2. DEDICATED TECHNICIAN REGISTER & ONBOARDING TAB
@Composable
fun AdminTechRegisterView(
    technicians: List<Map<String, Any>>,
    showFeedback: (String, String, Boolean) -> Unit
) {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedSkillFilter by remember { mutableStateOf("All") }
    var selectedTechForDetails by remember { mutableStateOf<Map<String, Any>?>(null) }

    // Register Form Fields
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

    LaunchedEffect(technicians) {
        if (technicians.isNotEmpty()) {
            technicians.forEach { tech ->
                val tName = tech["name"]?.toString() ?: ""
                if (tName.isNotBlank()) {
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

                        val isLate = relevantDocs.any { doc ->
                            val st = doc.getString("status") ?: ""
                            val schedTs = doc.getTimestamp("scheduledTimestamp")
                            val startedAt = doc.getTimestamp("startedAt")
                            if (st != "Completed" && st != "Denied" && schedTs != null) {
                                val compareTime = startedAt ?: Timestamp.now()
                                compareTime.seconds > (schedTs.seconds + 1800)
                            } else false
                        }
                        techLateAlerts[tName] = isLate
                    }
                }
            }
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
        val tName = selectedTechForDetails!!["name"]?.toString() ?: ""
        TechnicianDetailsDialog(
            tech = selectedTechForDetails!!, 
            activeJobs = techWorkloads[tName] ?: 0, 
            ratingData = techRatings[tName], 
            onDismiss = { selectedTechForDetails = null }
        )
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Technician Onboarding", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
        Text("Register new field experts & manage accounts", fontSize = 12.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(16.dp))

        // ➕ REGISTRATION FORM CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f)),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(36.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PersonAdd, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Register New Field Technician", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                        Text("Create official technician portal account", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = techName,
                    onValueChange = { techName = it },
                    label = { Text("Full Name") },
                    placeholder = { Text("e.g. Juan Cruz") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = techEmail,
                    onValueChange = { techEmail = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("tech@coolconnect.com") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = techPhone,
                    onValueChange = { if (it.length <= 11 && it.all { c -> c.isDigit() }) techPhone = it },
                    label = { Text("Phone Number") },
                    placeholder = { Text("09123456789") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = techPassword,
                    onValueChange = { techPassword = it },
                    label = { Text("Initial Password") },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, null)
                        }
                    },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Specialization Category:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
                listOf("HVAC & AC Master", "Electrical Specialist", "Smart Lock & Security", "Plumbing & Ducting", "Smart Thermostats").forEach { opt ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { techSkill = opt }.padding(vertical = 4.dp)
                    ) {
                        RadioButton(selected = techSkill == opt, onClick = { techSkill = opt }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue))
                        Text(opt, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        if (techName.isNotBlank() && techEmail.isNotBlank() && techPassword.isNotBlank()) {
                            isCreating = true
                            auth.createUserWithEmailAndPassword(techEmail.trim(), techPassword).addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val userId = auth.currentUser?.uid
                                    if (userId != null) {
                                        db.collection("users").document(userId).set(
                                            hashMapOf(
                                                "role" to "technician",
                                                "name" to techName.trim(),
                                                "email" to techEmail.trim(),
                                                "phone" to techPhone.trim(),
                                                "skill" to techSkill,
                                                "isOnline" to true,
                                                "isVerified" to true,
                                                "verificationStatus" to "Verified",
                                                "createdAt" to Timestamp.now()
                                            )
                                        ).addOnSuccessListener {
                                            isCreating = false
                                            showFeedback("Success", "Technician $techName registered successfully!", false)
                                            techName = ""
                                            techEmail = ""
                                            techPhone = ""
                                            techPassword = ""
                                        }
                                    }
                                } else {
                                    isCreating = false
                                    showFeedback("Registration Error", task.exception?.message ?: "Failed to create account", true)
                                }
                            }
                        } else {
                            showFeedback("Validation", "Please fill in Name, Email and Password", true)
                        }
                    },
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.PersonAdd, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Register & Activate Technician", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 📋 TECHNICIAN DIRECTORY & KYC APPROVALS
        Text("FIELD TECHNICIAN DIRECTORY (${filteredTechs.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by tech name or email...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (technicians.isEmpty()) {
            ShimmerCardList(3)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredTechs.forEach { tech ->
                    val tName = tech["name"]?.toString() ?: "Tech"
                    val tSkill = tech["skill"]?.toString() ?: "HVAC Expert"
                    val active = techWorkloads[tName] ?: 0
                    val comps = techCompletedCounts[tName] ?: 0
                    val isOnline = tech["isOnline"] as? Boolean ?: false
                    val verStatus = tech["verificationStatus"]?.toString() ?: "Verified"

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedTechForDetails = tech },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFF1F5F9))
                    ) {
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
                                Box(modifier = Modifier.size(44.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Build, null, tint = PrimaryBlue)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(tName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                    if (techLateAlerts[tName] == true) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Warning, "Late Alert", tint = Color.Red, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text("$tSkill • $comps Completed", fontSize = 11.sp, color = Color.Gray)
                                Text("Status: $verStatus", fontSize = 10.sp, color = if (verStatus == "Verified") PrimaryGreen else Color.Gray, fontWeight = FontWeight.SemiBold)
                            }

                            BadgeChip(
                                text = when {
                                    !isOnline -> "Offline"
                                    active == 0 -> "Ready"
                                    else -> "Busy ($active)"
                                },
                                color = when {
                                    !isOnline -> Color.Gray
                                    active == 0 -> PrimaryGreen
                                    else -> Color.Red
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// 🗺️ 3. VISIBLE LIVE SERVICE TRACKING VIEW FOR ADMIN
@Composable
fun AdminServiceTrackView(
    technicians: List<Map<String, Any>>,
    onChatSelected: (String, String) -> Unit
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val coroutineScope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(14.5995, 120.9842), 11f) }
    val context = LocalContext.current

    val activeServiceChats = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(Unit) {
        db.collection("chats_v2")
            .whereIn("status", listOf("Accepted", "In Progress", "Dispatched", "En Route", "Pending"))
            .addSnapshotListener { snapshot, _ ->
                activeServiceChats.clear()
                snapshot?.documents?.forEach { doc ->
                    val data = doc.data?.toMutableMap() ?: mutableMapOf()
                    data["docId"] = doc.id
                    activeServiceChats.add(data)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top Live Google Map Tracking View
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = true)
            ) {
                technicians.forEach { tech ->
                    val lat = tech["latitude"] as? Double
                    val lng = tech["longitude"] as? Double
                    val name = tech["name"]?.toString() ?: "Tech"
                    val isOnline = tech["isOnline"] as? Boolean ?: false

                    if (lat != null && lng != null) {
                        Marker(
                            state = rememberMarkerState(position = LatLng(lat, lng)),
                            title = name,
                            snippet = if (isOnline) "🟢 Online & On Duty" else "⚪ Offline",
                            icon = BitmapDescriptorFactory.defaultMarker(
                                if (isOnline) BitmapDescriptorFactory.HUE_GREEN else BitmapDescriptorFactory.HUE_RED
                            )
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.padding(12.dp).align(Alignment.TopStart),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(0.9f),
                shadowElevation = 2.dp
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).background(PrimaryGreen, CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("LIVE FLEET GPS TRACKING", fontSize = 10.sp, fontWeight = FontWeight.Black, color = TextDark)
                }
            }
        }

        // Bottom Live Active Services Tracking Board & Combined Schedule Calendar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Combined Fleet Schedule Calendar
            AdminCombinedScheduleCalendar(technicians = technicians)

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACTIVE SERVICE JOBS TRACKER (${activeServiceChats.size})", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
                Text("Real-time SLA Monitor", fontSize = 11.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (activeServiceChats.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("All Field Jobs Clear!", fontWeight = FontWeight.Bold, color = TextDark)
                        Text("No active pending or ongoing services at the moment.", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            } else {
                activeServiceChats.forEach { chat ->
                    val cid = chat["docId"].toString()
                    val cName = chat["customerName"]?.toString() ?: "Customer"
                    val techName = chat["assignedTechnician"]?.toString() ?: chat["lastTechnician"]?.toString() ?: "Unassigned"
                    val status = chat["status"]?.toString() ?: "In Progress"
                    val issue = chat["issue"]?.toString() ?: chat["lastMessage"]?.toString() ?: "HVAC Service"
                    val address = chat["customerAddress"]?.toString() ?: chat["regionCity"]?.toString() ?: "On Site"
                    
                    // Match assigned technician location
                    val techData = technicians.find { it["name"]?.toString().equals(techName, ignoreCase = true) }
                    val techLat = techData?.get("latitude") as? Double
                    val techLng = techData?.get("longitude") as? Double
                    val techPhone = techData?.get("phone")?.toString() ?: ""

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(34.dp).background(PrimaryBlue.copy(0.1f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Engineering, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(cName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                        Text("Tech: $techName", fontSize = 12.sp, color = Color.Gray)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = when (status) {
                                        "In Progress" -> PrimaryGreen.copy(0.12f)
                                        "Dispatched" -> PrimaryBlue.copy(0.12f)
                                        else -> Color(0xFFFFF3E0)
                                    }
                                ) {
                                    Text(
                                        text = status.uppercase(),
                                        color = when (status) {
                                            "In Progress" -> PrimaryGreen
                                            "Dispatched" -> PrimaryBlue
                                            else -> Color(0xFFE65100)
                                        },
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Service: $issue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Default.Place, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(address, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Track on Map Button
                                Button(
                                    onClick = {
                                        if (techLat != null && techLng != null) {
                                            coroutineScope.launch {
                                                cameraPositionState.animate(
                                                    CameraUpdateFactory.newLatLngZoom(LatLng(techLat, techLng), 15f)
                                                )
                                            }
                                        } else {
                                            Toast.makeText(context, "Technician GPS location offline", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue.copy(0.1f), contentColor = PrimaryBlue),
                                    modifier = Modifier.height(32.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Track on Map", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (techPhone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$techPhone"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(32.dp).background(AppLightBg, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Phone, "Call Tech", tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    Button(
                                        onClick = { onChatSelected(cid, cName) },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Chat, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chat", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

// 📦 4. ENTERPRISE INVENTORY MANAGEMENT COMMAND CENTER
@Composable
fun AdminInventoryView(
    products: List<Map<String, Any>>, 
    showFeedback: (String, String, Boolean) -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val db = remember { FirebaseFirestore.getInstance() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedProductForEdit by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val defaultSampleProducts = remember {
        listOf(
            mapOf("name" to "Compressor", "category" to "Compressors", "price" to 8000.0, "stock" to 12, "badge" to "BEST SELLER", "imageUrl" to "", "description" to "Heavy-duty rotary aircon compressor designed for split and window-type HVAC units.", "specs" to "1.5 HP - 2.5 HP • R410A / R32 Compatible", "warranty" to "2 Years Official Warranty"),
            mapOf("name" to "Board", "category" to "Circuit Boards", "price" to 5500.0, "stock" to 18, "badge" to "BEST SELLER", "imageUrl" to "", "description" to "Universal inverter & non-inverter air conditioner main control PCB board.", "specs" to "Multi-Brand Compatible • Remote Sensor Included", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Fan Motor", "category" to "Motors & Fans", "price" to 4000.0, "stock" to 25, "badge" to "IN STOCK", "imageUrl" to "", "description" to "High-RPM condenser and evaporator fan motor for outdoor and indoor HVAC units.", "specs" to "220V / 60Hz • Copper Winding", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Freon", "category" to "Refrigerants", "price" to 7800.0, "stock" to 15, "badge" to "HOT ITEM", "imageUrl" to "", "description" to "Genuine high-purity R410A / R32 refrigerant gas cylinder (11.3kg).", "specs" to "99.9% Pure Refrigerant • Eco-Friendly 11.3kg", "warranty" to "Guaranteed Sealed Cylinder"),
            mapOf("name" to "Fan Blade", "category" to "Motors & Fans", "price" to 700.0, "stock" to 30, "badge" to "IN STOCK", "imageUrl" to "", "description" to "Balanced aerodynamic outdoor condenser propeller fan blade.", "specs" to "3-Blade Aerodynamic ABS • UV Resistant", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Fan Blower", "category" to "Motors & Fans", "price" to 550.0, "stock" to 20, "badge" to "IN STOCK", "imageUrl" to "", "description" to "Precision cross-flow cylindrical indoor blower wheel for split-type indoor units.", "specs" to "Anti-Static ABS • Fits 0.75HP - 2.0HP", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Sensor", "category" to "Sensors & Driers", "price" to 300.0, "stock" to 40, "badge" to "IN STOCK", "imageUrl" to "", "description" to "High-sensitivity ambient and coil temperature thermistor sensor.", "specs" to "5K / 10K NTC Thermistor • Copper Probe", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Copper Tube", "category" to "Refrigerants", "price" to 750.0, "stock" to 15, "badge" to "IN STOCK", "imageUrl" to "", "description" to "Pre-insulated double copper tubing roll (5 meters) for split-type AC installation.", "specs" to "1/4\" & 3/8\" Pair • Seamless Annealed Copper", "warranty" to "1 Year Warranty"),
            mapOf("name" to "Filter Drier", "category" to "Sensors & Driers", "price" to 900.0, "stock" to 35, "badge" to "IN STOCK", "imageUrl" to "", "description" to "Heavy-duty liquid line filter drier to trap moisture and acid in HVAC lines.", "specs" to "1/4\" Flare Connections • Solid Core", "warranty" to "1 Year Warranty")
        )
    }

    // Auto-Seed Firebase if products collection is empty
    LaunchedEffect(products) {
        if (products.isEmpty()) {
            defaultSampleProducts.forEach { sample ->
                db.collection("products").add(sample + ("timestamp" to Timestamp.now()))
            }
        }
    }

    // Valuation Metrics
    val totalSkus = products.size
    val totalInventoryValue = products.sumOf { p ->
        val price = (p["price"] as? Number)?.toDouble() ?: 0.0
        val stock = (p["stock"] as? Number)?.toInt() ?: 0
        price * stock
    }
    val lowStockCount = products.count { p ->
        ((p["stock"] as? Number)?.toInt() ?: 0) <= 5
    }

    val categories = listOf("All", "Compressors", "Circuit Boards", "Motors & Fans", "Refrigerants", "Sensors & Driers")

    val filteredProducts = products.filter { p ->
        val name = p["name"]?.toString() ?: ""
        val category = p["category"]?.toString() ?: ""
        val matchesCategory = selectedCategoryFilter == "All" || category.equals(selectedCategoryFilter, ignoreCase = true)
        val matchesQuery = name.contains(searchQuery, ignoreCase = true) || category.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    if (showAddDialog || selectedProductForEdit != null) {
        InventoryDialog(
            product = selectedProductForEdit,
            viewModel = viewModel,
            onDismiss = {
                showAddDialog = false
                selectedProductForEdit = null
            },
            onSave = { data ->
                val docId = selectedProductForEdit?.get("id")?.toString() ?: selectedProductForEdit?.get("docId")?.toString()
                if (docId != null) {
                    db.collection("products").document(docId).set(data, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener { showFeedback("Updated", "Product updated in inventory & shop", false) }
                } else {
                    db.collection("products").add(data)
                        .addOnSuccessListener { showFeedback("Success", "Product added to Firebase inventory", false) }
                        .addOnFailureListener { showFeedback("Error", "Failed to add product", true) }
                }
                showAddDialog = false
                selectedProductForEdit = null
            },
            onDelete = {
                val docId = selectedProductForEdit?.get("id")?.toString() ?: selectedProductForEdit?.get("docId")?.toString()
                if (docId != null) {
                    db.collection("products").document(docId).delete()
                        .addOnSuccessListener { showFeedback("Deleted", "Product removed from inventory", false) }
                }
                showAddDialog = false
                selectedProductForEdit = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Inventory Command Center", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark))
            Text("Firebase Real-Time Stock & Asset Control", fontSize = 12.sp, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = { showAddDialog = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        ) {
            Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(20.dp), tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add New Product to Inventory", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 📊 METRICS SUMMARY
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("TOTAL VALUE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("₱${String.format(Locale.getDefault(), "%,.0f", totalInventoryValue)}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = PrimaryBlue)
                    Text("Gross Asset Value", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Card(
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(PrimaryGreen.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Inventory2, null, tint = PrimaryGreen, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PRODUCTS", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$totalSkus SKUs", fontSize = 18.sp, fontWeight = FontWeight.Black, color = TextDark)
                    Text("Catalog Active", fontSize = 10.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                modifier = Modifier.weight(0.9f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = if (lowStockCount > 0) Color(0xFFFFF0F0) else Color.White),
                border = if (lowStockCount > 0) BorderStroke(1.dp, Color.Red.copy(0.3f)) else null,
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(28.dp).background(if (lowStockCount > 0) Color.Red.copy(0.15f) else AppLightBg, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(if (lowStockCount > 0) Icons.Default.Warning else Icons.Default.CheckCircle, null, tint = if (lowStockCount > 0) Color.Red else PrimaryGreen, modifier = Modifier.size(14.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ALERTS", fontSize = 9.sp, fontWeight = FontWeight.Black, color = if (lowStockCount > 0) Color.Red else Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$lowStockCount Low", fontSize = 18.sp, fontWeight = FontWeight.Black, color = if (lowStockCount > 0) Color.Red else TextDark)
                    Text(if (lowStockCount > 0) "Needs Restock" else "Stock Healthy", fontSize = 10.sp, color = if (lowStockCount > 0) Color.Red else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by product name, category...", fontSize = 13.sp, color = Color.Gray) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            textStyle = TextStyle(fontSize = 14.sp, color = TextDark, fontWeight = FontWeight.SemiBold),
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories) { cat ->
                val isSel = selectedCategoryFilter == cat
                val catCount = if (cat == "All") products.size else products.count { it["category"]?.toString()?.equals(cat, true) == true }
                
                Surface(
                    modifier = Modifier.clickable { selectedCategoryFilter = cat }.height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSel) PrimaryBlue else Color.White,
                    border = BorderStroke(1.dp, if (isSel) PrimaryBlue else Color(0xFFE2E8F0))
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(cat, color = if (isSel) Color.White else TextDark, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = CircleShape, color = if (isSel) Color.White.copy(0.25f) else AppLightBg) {
                            Text("$catCount", color = if (isSel) Color.White else Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredProducts.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(32.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inventory2, null, tint = Color.LightGray, modifier = Modifier.size(54.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No Matching Products", fontWeight = FontWeight.Black, color = TextDark, fontSize = 16.sp)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                filteredProducts.chunked(2).forEach { rowProducts ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowProducts.forEach { p ->
                            val name = p["name"]?.toString() ?: "Unnamed Product"
                            val category = p["category"]?.toString() ?: "General"
                            val priceVal = (p["price"] as? Number)?.toDouble() ?: 0.0
                            val stockVal = (p["stock"] as? Number)?.toInt() ?: 0
                            val specs = p["specs"]?.toString() ?: ""
                            val imageUrl = p["imageUrl"]?.toString() ?: ""
                            val badge = p["badge"]?.toString() ?: ""
                            val docId = p["id"]?.toString() ?: p["docId"]?.toString() ?: ""

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedProductForEdit = p },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(2.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Image Picture Box Header
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(115.dp)
                                            .background(AppLightBg)
                                    ) {
                                        if (imageUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = imageUrl,
                                                contentDescription = name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(6.dp),
                                                contentScale = ContentScale.Fit
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Inventory2,
                                                    null,
                                                    tint = PrimaryBlue.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(42.dp)
                                                )
                                            }
                                        }

                                        // Badge / Category Tag Chip
                                        Surface(
                                            modifier = Modifier
                                                .padding(8.dp)
                                                .align(Alignment.TopStart),
                                            shape = RoundedCornerShape(6.dp),
                                            color = PrimaryBlue.copy(alpha = 0.9f)
                                        ) {
                                            Text(
                                                text = (if (badge.isNotBlank()) badge else category).uppercase(),
                                                color = Color.White,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 8.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                            )
                                        }

                                        // Edit & Delete Action Buttons
                                        Row(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .align(Alignment.TopEnd),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            IconButton(
                                                onClick = { selectedProductForEdit = p },
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Default.Edit,
                                                    contentDescription = "Edit",
                                                    tint = PrimaryBlue,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            if (docId.isNotEmpty()) {
                                                IconButton(
                                                    onClick = { db.collection("products").document(docId).delete() },
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(Color.White.copy(alpha = 0.9f), CircleShape)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color.Red,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Content Section
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = TextDark,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (specs.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = specs,
                                                fontSize = 10.sp,
                                                color = Color.Gray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = Color(0xFFF1F5F9))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = if (stockVal <= 5) Color.Red.copy(0.1f) else PrimaryGreen.copy(0.1f)
                                            ) {
                                                Text(
                                                    text = if (stockVal <= 5) "$stockVal low!" else "$stockVal in stock",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = if (stockVal <= 5) Color.Red else PrimaryGreen,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = "₱${String.format(Locale.getDefault(), "%,.0f", priceVal)}",
                                                fontWeight = FontWeight.Black,
                                                color = PrimaryBlue,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (rowProducts.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

// 💬 5. MESSAGES & OPERATIONS CHAT HUB
@Composable
fun AdminChatHubView(onChatSelected: (String, String) -> Unit) {
    val auth = remember { FirebaseAuth.getInstance() }
    val currentAdminUid = auth.currentUser?.uid
    val db = remember { FirebaseFirestore.getInstance() }
    val customerChats = remember { mutableStateListOf<Map<String, Any>>() }
    val techChats = remember { mutableStateListOf<Map<String, Any>>() }
    val fieldChats = remember { mutableStateListOf<Map<String, Any>>() }
    val userProfilePics = remember { mutableStateMapOf<String, String?>() }
    val userOnlineStatus = remember { mutableStateMapOf<String, Boolean>() }
    var selectedSection by remember { mutableStateOf("Inquiries") }

    LaunchedEffect(Unit) {
        db.collection("users").addSnapshotListener { uSnap, _ ->
            uSnap?.documents?.forEach { uDoc ->
                val uId = uDoc.id
                val name = uDoc.getString("name") ?: ""
                val pic = uDoc.getString("profilePicUrl")
                val online = uDoc.getBoolean("isOnline") ?: false

                if (uId.isNotBlank()) userProfilePics[uId] = pic
                if (name.isNotBlank()) {
                    userProfilePics[name.lowercase(Locale.getDefault())] = pic
                    userOnlineStatus[name.lowercase(Locale.getDefault())] = online
                }
            }
        }

        db.collection("chats_v2").addSnapshotListener { snapshot, _ ->
            customerChats.clear(); fieldChats.clear()
            val techChatDataMap = mutableMapOf<String, Map<String, Any>>()
            snapshot?.documents?.forEach { doc ->
                val data = doc.data?.toMutableMap() ?: mutableMapOf(); data["uid"] = doc.id
                val cid = doc.id; val cname = data["customerName"]?.toString() ?: ""
                
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
                    val tPic = uDoc.getString("profilePicUrl")
                    if (tPic != null) userProfilePics[tName.lowercase(Locale.getDefault())] = tPic
                    
                    val chatData = (techChatDataMap["admin_tech_$tName"] ?: emptyMap()).toMutableMap().apply { 
                        this["uid"] = "admin_tech_$tName"
                        this["techName"] = tName
                        if (tPic != null) this["profilePicUrl"] = tPic
                    }
                    techChats.add(chatData)
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
                val customerUid = chat["customerUid"]?.toString()
                val status = chat["status"]?.toString()
                val rawTs = chat["timestamp"] as? com.google.firebase.Timestamp

                val formattedTime = remember(rawTs) {
                    if (rawTs == null) "" else {
                        val date = rawTs.toDate()
                        val now = Calendar.getInstance()
                        val cal = Calendar.getInstance().apply { time = date }
                        if (now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR))
                            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                        else SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
                    }
                }

                val profilePic = remember(cname, customerUid, userProfilePics.size) {
                    (if (!customerUid.isNullOrBlank()) userProfilePics[customerUid] else null)
                        ?: userProfilePics[cname.lowercase(Locale.getDefault())]
                        ?: chat["profilePicUrl"]?.toString()
                }

                val isOnline = remember(cname, userOnlineStatus.size) {
                    userOnlineStatus[cname.lowercase(Locale.getDefault())] ?: false
                }

                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onChatSelected(cid, cname) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp)) {
                            if (!profilePic.isNullOrBlank()) {
                                AsyncImage(
                                    model = profilePic,
                                    contentDescription = cname,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(PrimaryBlue.copy(0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = cname.take(1).uppercase(Locale.getDefault()),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            if (isOnline) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .align(Alignment.BottomEnd)
                                        .background(PrimaryGreen, CircleShape)
                                        .border(1.5.dp, Color.White, CircleShape)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cname, fontWeight = FontWeight.Bold, color = TextDark)
                                if (formattedTime.isNotBlank()) {
                                    Text(formattedTime, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(chat["lastMessage"]?.toString() ?: "No activity", fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                            if (!status.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Status: $status",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (status.equals("Completed", ignoreCase = true)) PrimaryGreen else PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 📊 6. REPORTS & SERVICE HISTORY CSV EXPORT VIEW
@Composable
fun AdminReportsView(completedJobs: List<Map<String, Any>>) {
    val db = remember { FirebaseFirestore.getInstance() }
    
    var totalServiceRevenue by remember { mutableDoubleStateOf(0.0) }
    var totalOrderRevenue by remember { mutableDoubleStateOf(0.0) }
    var showServiceHistoryScreen by remember { mutableStateOf(false) }
    
    LaunchedEffect(completedJobs) {
        db.collection("chats_v2").get().addOnSuccessListener { snapshot ->
            totalServiceRevenue = snapshot.documents.sumOf { if (it.getString("status") == "Completed") (it.get("totalAmount") as? Number)?.toDouble() ?: 0.0 else 0.0 }
        }
        db.collection("orders").get().addOnSuccessListener { snapshot ->
            totalOrderRevenue = snapshot.documents.sumOf { (it.get("total") as? Number)?.toDouble() ?: 0.0 }
        }
    }
    
    val totalRevenue = totalServiceRevenue + totalOrderRevenue

    if (showServiceHistoryScreen) {
        ServiceHistoryFullScreen(
            onBack = { showServiceHistoryScreen = false }
        )
    } else {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Operations History", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black))
                    Text("Platform Revenue & Performance", fontSize = 12.sp, color = Color.Gray)
                }
                
                Button(
                    onClick = { showServiceHistoryScreen = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export Reports", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TOTAL GROSS REVENUE", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    Text("₱${String.format(Locale.getDefault(), "%,.2f", totalRevenue)}", fontSize = 32.sp, fontWeight = FontWeight.Black, color = PrimaryBlue)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFF1F5F9))
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SERVICES", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("₱${String.format(Locale.getDefault(), "%,.2f", totalServiceRevenue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SHOP SALES", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text("₱${String.format(Locale.getDefault(), "%,.2f", totalOrderRevenue)}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("RECENT COMPLETED SERVICES", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))
            
            if (completedJobs.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    Text("No completed services yet.", color = Color.Gray, fontSize = 13.sp)
                }
            }

            completedJobs.forEach { job ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = PrimaryGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) { 
                            Text(job["customerName"].toString(), fontWeight = FontWeight.Bold) 
                            Text("Expert: ${job["assignedTechnician"]}", fontSize = 11.sp, color = Color.Gray) 
                        }
                        Text("₱${String.format(Locale.getDefault(), "%.0f", (job["totalAmount"] as? Number)?.toDouble() ?: 0.0)}", fontWeight = FontWeight.Black, color = PrimaryBlue)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDialog(
    product: Map<String, Any>?, 
    onDismiss: () -> Unit, 
    onSave: (Map<String, Any>) -> Unit,
    onDelete: (() -> Unit)? = null,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(product?.get("name")?.toString() ?: "") }
    var category by remember { mutableStateOf(product?.get("category")?.toString() ?: "Compressors") }
    var stock by remember { mutableStateOf(product?.get("stock")?.toString() ?: "10") }
    var price by remember { mutableStateOf(product?.get("price")?.toString() ?: "1500") }
    var badge by remember { mutableStateOf(product?.get("badge")?.toString() ?: "IN STOCK") }
    var imageUrl by remember { mutableStateOf(product?.get("imageUrl")?.toString() ?: "") }
    @Suppress("UNCHECKED_CAST")
    val initialExtraUrls = remember(product) {
        (product?.get("imageUrls") as? List<String>) ?: emptyList()
    }
    val extraImageUrls = remember { mutableStateListOf<String>().apply { addAll(initialExtraUrls) } }
    var newPhotoUrlInput by remember { mutableStateOf("") }
    var showAddUrlField by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val uriStr = uri.toString()
            if (imageUrl.isBlank()) {
                imageUrl = uriStr
            } else if (!extraImageUrls.contains(uriStr)) {
                extraImageUrls.add(uriStr)
            }
            Toast.makeText(context, "Photo added to gallery!", Toast.LENGTH_SHORT).show()
        }
    }

    var description by remember { mutableStateOf(product?.get("description")?.toString() ?: "High-quality HVAC replacement part designed for heavy-duty operation.") }
    var specs by remember { mutableStateOf(product?.get("specs")?.toString() ?: "HVAC Grade • High Efficiency") }
    var warranty by remember { mutableStateOf(product?.get("warranty")?.toString() ?: "1 Year Official Warranty") }
    var categoryExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Compressors", "Circuit Boards", "Motors & Fans", "Refrigerants", "Sensors & Driers")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(PrimaryBlue.copy(0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (product == null) Icons.Default.AddBox else Icons.Default.Edit,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                if (product == null) "Add Inventory Product" else "Edit Inventory Product",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = TextDark
                            )
                            Text(
                                "Manage details, pricing, & stock levels",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .background(AppLightBg, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // IMAGE PREVIEW CARD
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppLightBg),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Product Image",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "No Image Path Set",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextDark
                                    )
                                    Text(
                                        "Enter image URL or local file URI below",
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }

                    // Product Name
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Product Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Compressor 1.5 HP", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    // Row: Category Dropdown & Badge Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Category Exposed Dropdown
                        ExposedDropdownMenuBox(
                            expanded = categoryExpanded,
                            onExpandedChange = { categoryExpanded = !categoryExpanded },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = category,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Category", fontSize = 12.sp) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    unfocusedBorderColor = Color(0xFFCBD5E1)
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = categoryExpanded,
                                onDismissRequest = { categoryExpanded = false }
                            ) {
                                categories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat, fontSize = 13.sp) },
                                        onClick = {
                                            category = cat
                                            categoryExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Badge / Stock Status Tag
                        OutlinedTextField(
                            value = badge,
                            onValueChange = { badge = it },
                            label = { Text("Badge / Tag", fontSize = 12.sp) },
                            placeholder = { Text("e.g. BEST SELLER", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    // Row: Price & Stock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            label = { Text("Price (₱)", fontSize = 12.sp) },
                            placeholder = { Text("0.00", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        OutlinedTextField(
                            value = stock,
                            onValueChange = { stock = it },
                            label = { Text("Stock Quantity", fontSize = 12.sp) },
                            placeholder = { Text("0", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    // Specifications
                    OutlinedTextField(
                        value = specs,
                        onValueChange = { specs = it },
                        label = { Text("Specifications / Model Specs", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 1.5 HP • R410A Compatible", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )

                    // Warranty & Image URL row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = warranty,
                            onValueChange = { warranty = it },
                            label = { Text("Warranty", fontSize = 12.sp) },
                            placeholder = { Text("e.g. 1 Year Warranty", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        OutlinedTextField(
                            value = imageUrl,
                            onValueChange = { imageUrl = it },
                            label = { Text("Main Image URL / Local URI", fontSize = 12.sp) },
                            placeholder = { Text("e.g. file:///... or image path", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )
                    }

                    // MULTI-PHOTO GALLERY MANAGEMENT SECTION
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Product Photos & Angles", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextDark)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload Photo", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                TextButton(
                                    onClick = { showAddUrlField = !showAddUrlField },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("+ Add URL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                }
                            }
                        }

                        if (showAddUrlField) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newPhotoUrlInput,
                                    onValueChange = { newPhotoUrlInput = it },
                                    placeholder = { Text("Paste image URL...", fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    textStyle = TextStyle(fontSize = 11.sp, color = TextDark)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (newPhotoUrlInput.isNotBlank()) {
                                            val trimmed = newPhotoUrlInput.trim()
                                            if (imageUrl.isBlank()) {
                                                imageUrl = trimmed
                                            } else if (!extraImageUrls.contains(trimmed)) {
                                                extraImageUrls.add(trimmed)
                                            }
                                            newPhotoUrlInput = ""
                                            showAddUrlField = false
                                        }
                                    },
                                    modifier = Modifier.height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                ) {
                                    Text("Add", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val allPhotos = remember(imageUrl, extraImageUrls.toList()) {
                            val list = mutableListOf<Pair<String, Boolean>>()
                            if (imageUrl.isNotBlank()) list.add(Pair(imageUrl, true))
                            extraImageUrls.forEach { u ->
                                if (u.isNotBlank() && u != imageUrl) list.add(Pair(u, false))
                            }
                            list
                        }

                        if (allPhotos.isNotEmpty()) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(allPhotos) { (url, isMain) ->
                                    Box(modifier = Modifier.size(70.dp)) {
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(if (isMain) 2.dp else 1.dp, if (isMain) PrimaryBlue else Color(0xFFCBD5E1)),
                                            colors = CardDefaults.cardColors(containerColor = AppLightBg),
                                            modifier = Modifier.fillMaxSize().clickable {
                                                if (!isMain) {
                                                    val oldMain = imageUrl
                                                    imageUrl = url
                                                    extraImageUrls.remove(url)
                                                    if (oldMain.isNotBlank() && !extraImageUrls.contains(oldMain)) {
                                                        extraImageUrls.add(oldMain)
                                                    }
                                                }
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                AsyncImage(
                                                    model = url,
                                                    contentDescription = "Angle",
                                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                                    contentScale = ContentScale.Fit
                                                )

                                                Surface(
                                                    color = if (isMain) PrimaryBlue else Color.Black.copy(0.6f),
                                                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                                                    modifier = Modifier.align(Alignment.TopStart)
                                                ) {
                                                    Text(
                                                        text = if (isMain) "MAIN" else "ANGLE",
                                                        color = Color.White,
                                                        fontSize = 7.sp,
                                                        fontWeight = FontWeight.Black,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                if (isMain) {
                                                    if (extraImageUrls.isNotEmpty()) {
                                                        imageUrl = extraImageUrls.removeAt(0)
                                                    } else {
                                                        imageUrl = ""
                                                    }
                                                } else {
                                                    extraImageUrls.remove(url)
                                                }
                                            },
                                            modifier = Modifier
                                                .size(20.dp)
                                                .align(Alignment.TopEnd)
                                                .background(Color.Red, CircleShape)
                                        ) {
                                            Icon(Icons.Default.Close, "Remove Photo", tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Product Description", fontSize = 12.sp) },
                        placeholder = { Text("Enter detailed hardware description...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFF1F5F9))
                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onDelete != null && product != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color.Red.copy(0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancel", color = Color.Gray, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (name.isNotBlank() && price.isNotBlank()) {
                                    onSave(
                                        mapOf(
                                            "name" to name.trim(),
                                            "category" to category,
                                            "price" to (price.toDoubleOrNull() ?: 0.0),
                                            "stock" to (stock.toIntOrNull() ?: 10),
                                            "badge" to badge.trim(),
                                            "imageUrl" to imageUrl.trim(),
                                            "imageUrls" to extraImageUrls.filter { it.isNotBlank() },
                                            "description" to description.trim(),
                                            "specs" to specs.trim(),
                                            "warranty" to warranty.trim(),
                                            "timestamp" to Timestamp.now()
                                        )
                                    )
                                }
                            },
                            enabled = name.isNotBlank() && price.isNotBlank(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (product == null) "Add Product" else "Save Changes", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceHistoryFullScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    var isGenerating by remember { mutableStateOf(false) }

    var selectedReportType by remember { mutableStateOf("MASTER") }

    val reportTypes = listOf(
        "MASTER" to Pair("Executive Master Summary", "Consolidated audit of all services, hardware orders, and platform revenue."),
        "FINANCIAL" to Pair("Sales, Revenue & Financial Accounting", "Itemized labor income, parts sales, 12% VAT, and payment methods."),
        "OPERATIONS" to Pair("Technician Services & Dispatch Log", "Jobs by date, technician performance, parts used, and work notes."),
        "HARDWARE" to Pair("Customer Hardware Orders & Parts", "Hardware catalog purchases, delivery locations, and item quantities."),
        "CUSTOMERS" to Pair("Customer Directory & Lifetime Accounts", "Registered customers list, contact details, address, and plan tier."),
        "INVENTORY" to Pair("Hardware Inventory & Stock Audit", "Parts stock availability, unit prices, reorder alerts, and warranty terms.")
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppLightBg
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDark)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Service History Reports", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                        Text("Platform Dataset Export & Analytics", fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp)
            ) {
                Text("SELECT REPORT CATEGORY TO DOWNLOAD:", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(12.dp))

                reportTypes.forEach { (typeKey, pair) ->
                    val (title, description) = pair
                    val isSel = selectedReportType == typeKey

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { selectedReportType = typeKey },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = if (isSel) PrimaryBlue.copy(0.06f) else Color.White),
                        border = BorderStroke(1.5.dp, if (isSel) PrimaryBlue else Color(0xFFE2E8F0))
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = isSel, onClick = { selectedReportType = typeKey }, colors = RadioButtonDefaults.colors(selectedColor = PrimaryBlue))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(description, fontSize = 11.sp, color = Color.Gray, lineHeight = 15.sp)
                            }
                        }
                    }
                }
            }

            Surface(color = Color.White, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = {
                            isGenerating = true
                            when (selectedReportType) {
                                "MASTER" -> {
                                    db.collection("chats_v2").get().addOnSuccessListener { cSnap ->
                                        val services = cSnap.documents.map { doc ->
                                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                            d["docId"] = doc.id
                                            d
                                        }
                                        db.collection("orders").get().addOnSuccessListener { oSnap ->
                                            val orders = oSnap.documents.mapNotNull { doc ->
                                                @Suppress("UNCHECKED_CAST")
                                                val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                                                ShopOrder(
                                                    orderId = doc.getString("orderId") ?: doc.id,
                                                    customerUid = doc.getString("customerUid") ?: "",
                                                    customerName = doc.getString("customerName") ?: "Customer",
                                                    customerPhone = doc.getString("customerPhone") ?: "",
                                                    items = items,
                                                    itemsSummary = doc.getString("itemsSummary") ?: "",
                                                    subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                                                    vat = (doc.get("vat") as? Number)?.toDouble() ?: 0.0,
                                                    total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                                                    status = doc.getString("status") ?: "Pending",
                                                    shippingAddress = doc.getString("shippingAddress") ?: "",
                                                    paymentMethod = doc.getString("paymentMethod") ?: "",
                                                    assignedTechnician = doc.getString("assignedTechnician") ?: "",
                                                    timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                                                )
                                            }
                                            val csv = ReportExporter.generateMasterReportCsv(services, orders)
                                            ReportExporter.exportReportToFile(context, "CoolConnect_Executive_Master_Report", csv)
                                            isGenerating = false
                                        }
                                    }
                                }
                                "FINANCIAL" -> {
                                    db.collection("chats_v2").get().addOnSuccessListener { cSnap ->
                                        val services = cSnap.documents.map { doc ->
                                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                            d["docId"] = doc.id
                                            d
                                        }
                                        db.collection("orders").get().addOnSuccessListener { oSnap ->
                                            val orders = oSnap.documents.mapNotNull { doc ->
                                                @Suppress("UNCHECKED_CAST")
                                                val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                                                ShopOrder(
                                                    orderId = doc.getString("orderId") ?: doc.id,
                                                    customerUid = doc.getString("customerUid") ?: "",
                                                    customerName = doc.getString("customerName") ?: "Customer",
                                                    customerPhone = doc.getString("customerPhone") ?: "",
                                                    items = items,
                                                    itemsSummary = doc.getString("itemsSummary") ?: "",
                                                    subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                                                    vat = (doc.get("vat") as? Number)?.toDouble() ?: 0.0,
                                                    total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                                                    status = doc.getString("status") ?: "Pending",
                                                    shippingAddress = doc.getString("shippingAddress") ?: "",
                                                    paymentMethod = doc.getString("paymentMethod") ?: "",
                                                    assignedTechnician = doc.getString("assignedTechnician") ?: "",
                                                    timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                                                )
                                            }
                                            val csv = ReportExporter.generateFinancialReportCsv(services, orders)
                                            ReportExporter.exportReportToFile(context, "CoolConnect_Financial_Accounting_Report", csv)
                                            isGenerating = false
                                        }
                                    }
                                }
                                "OPERATIONS" -> {
                                    db.collection("chats_v2").get().addOnSuccessListener { cSnap ->
                                        val services = cSnap.documents.map { doc ->
                                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                            d["docId"] = doc.id
                                            d
                                        }
                                        val csv = ReportExporter.generateTechnicianOperationsCsv(services)
                                        ReportExporter.exportReportToFile(context, "CoolConnect_Technician_Operations_Report", csv)
                                        isGenerating = false
                                    }
                                }
                                "HARDWARE" -> {
                                    db.collection("orders").get().addOnSuccessListener { oSnap ->
                                        val orders = oSnap.documents.mapNotNull { doc ->
                                            @Suppress("UNCHECKED_CAST")
                                            val items = doc.get("items") as? List<Map<String, Any>> ?: emptyList()
                                            ShopOrder(
                                                orderId = doc.getString("orderId") ?: doc.id,
                                                customerUid = doc.getString("customerUid") ?: "",
                                                customerName = doc.getString("customerName") ?: "Customer",
                                                customerPhone = doc.getString("customerPhone") ?: "",
                                                items = items,
                                                itemsSummary = doc.getString("itemsSummary") ?: "",
                                                subtotal = (doc.get("subtotal") as? Number)?.toDouble() ?: 0.0,
                                                vat = (doc.get("vat") as? Number)?.toDouble() ?: 0.0,
                                                total = (doc.get("total") as? Number)?.toDouble() ?: 0.0,
                                                status = doc.getString("status") ?: "Pending",
                                                shippingAddress = doc.getString("shippingAddress") ?: "",
                                                paymentMethod = doc.getString("paymentMethod") ?: "",
                                                assignedTechnician = doc.getString("assignedTechnician") ?: "",
                                                timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now()
                                            )
                                        }
                                        val csv = ReportExporter.generateHardwareOrdersCsv(orders)
                                        ReportExporter.exportReportToFile(context, "CoolConnect_Hardware_Orders_Report", csv)
                                        isGenerating = false
                                    }
                                }
                                "CUSTOMERS" -> {
                                    db.collection("users").get().addOnSuccessListener { uSnap ->
                                        val users = uSnap.documents.map { doc ->
                                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                            d["uid"] = doc.id
                                            d
                                        }
                                        val csv = ReportExporter.generateCustomerDirectoryCsv(users)
                                        ReportExporter.exportReportToFile(context, "CoolConnect_Customer_Directory_Report", csv)
                                        isGenerating = false
                                    }
                                }
                                "INVENTORY" -> {
                                    db.collection("products").get().addOnSuccessListener { pSnap ->
                                        val products = pSnap.documents.map { doc ->
                                            val d = doc.data?.toMutableMap() ?: mutableMapOf()
                                            d["id"] = doc.id
                                            d
                                        }
                                        val csv = ReportExporter.generateInventoryReportCsv(products)
                                        ReportExporter.exportReportToFile(context, "CoolConnect_Inventory_Stock_Report", csv)
                                        isGenerating = false
                                    }
                                }
                            }
                        },
                        enabled = !isGenerating,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate & Export (.csv)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminNotificationDialog(notifs: List<Map<String, Any>>, onDismiss: () -> Unit, onClear: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recent Activity Notifications") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (notifs.isEmpty()) Text("No new notifications.", color = Color.Gray) else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(notifs) { n -> Text("Job Completed for ${n["customerName"]}", fontSize = 13.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClear) { Text("Clear All") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
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
        },
        text = {
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
        },
        confirmButton = { Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text("Close") } },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun AdminProfileView(profilePicUrl: String?, onLogout: () -> Unit, onUploadPic: (Uri) -> Unit, isUploading: Boolean = false) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutDialog = false
                onLogout()
            },
            onDismiss = { showLogoutDialog = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProfileImageSelector(
            imageUrl = profilePicUrl,
            onImageSelected = onUploadPic,
            modifier = Modifier.size(80.dp),
            placeholderIcon = Icons.Default.AccountCircle,
            isLoading = isUploading
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showLogoutDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f)), modifier = Modifier.fillMaxWidth()) { Text("Logout") }
    }
}

@Composable
fun AssignTechnicianDialog(
    order: ShopOrder,
    technicians: List<Map<String, Any>>,
    onDismiss: () -> Unit,
    onAssign: (String, String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Assign Delivery & Installer", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
                Text("Order ${order.orderId} • Customer: ${order.customerName}", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = AppLightBg,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("ITEMS BOUGHT:", fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.Gray)
                        Text(order.itemsSummary.ifBlank { "HVAC Smart Hardware" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("DELIVERY ADDRESS:", fontWeight = FontWeight.Black, fontSize = 10.sp, color = Color.Gray)
                        Text(order.shippingAddress.ifBlank { "Address on profile" }, fontSize = 11.sp, color = TextDark)
                    }
                }

                Text("SELECT FIELD TECHNICIAN:", fontWeight = FontWeight.Black, fontSize = 11.sp, color = PrimaryBlue)
                Spacer(modifier = Modifier.height(8.dp))

                if (technicians.isEmpty()) {
                    Text("No registered technicians found.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    technicians.forEach { tech ->
                        val tName = tech["name"]?.toString() ?: "Tech"
                        val tUid = tech["uid"]?.toString() ?: ""
                        val tSkill = tech["skill"]?.toString() ?: "HVAC Master"
                        val isOnline = tech["isOnline"] as? Boolean ?: false
                        val isCurrentlyAssigned = order.assignedTechnician.equals(tName, ignoreCase = true)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { onAssign(tName, tUid) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCurrentlyAssigned) PrimaryBlue.copy(0.1f) else Color.White
                            ),
                            border = BorderStroke(1.dp, if (isCurrentlyAssigned) PrimaryBlue else Color(0xFFE2E8F0))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier.size(36.dp).background(PrimaryBlue.copy(0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(tName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
                                    Text("$tSkill • ${if (isOnline) "Online" else "Offline"}", fontSize = 11.sp, color = Color.Gray)
                                }
                                if (isCurrentlyAssigned) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = PrimaryGreen) {
                                        Text("ASSIGNED", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                                    }
                                } else {
                                    Button(
                                        onClick = { onAssign(tName, tUid) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(30.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                                    ) {
                                        Text("Assign", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
