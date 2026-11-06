package com.example.coolconnect

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.example.coolconnect.ui.theme.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import androidx.hilt.navigation.compose.hiltViewModel

enum class TechnicianTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CUSTOMERS("Jobs", Icons.AutoMirrored.Filled.Assignment),
    HISTORY("History", Icons.Default.History),
    AI("AI Fix", Icons.Default.AutoAwesome),
    MAP("Map", Icons.Default.Map),
    CHAT("Messages", Icons.AutoMirrored.Filled.Chat),
    PROFILE("You", Icons.Default.Person)
}

data class TechJobDetail(
    val id: String,
    val customerId: String = "",
    val name: String,
    val phone: String,
    val address: String,
    val status: String,
    val issue: String = "",
    val type: String = "service"
)

enum class HistoryStatusFilter(val label: String) {
    ALL("All"),
    COMPLETED("Completed"),
    DENIED("Denied")
}

enum class HistoryDateFilter(val label: String) {
    ALL_TIME("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    LAST_30_DAYS("Last 30 Days"),
    CUSTOM("Custom Range")
}

data class ServiceHistoryItem(
    val id: String,
    val name: String,
    val phone: String,
    val address: String,
    val status: String,
    val issue: String = "",
    val completionNotes: String = "",
    val partsUsed: String = "",
    val paymentMethod: String = "",
    val laborFee: Double = 0.0,
    val partsFee: Double = 0.0,
    val vatAmount: Double = 0.0,
    val totalAmount: Double = 0.0,
    val photoProofUrl: String = "",
    val signatureUrl: String = "",
    val completedAt: com.google.firebase.Timestamp? = null,
    val deniedAt: com.google.firebase.Timestamp? = null,
    val createdAt: com.google.firebase.Timestamp? = null,
    val timestamp: com.google.firebase.Timestamp? = null
)

// Philippine Time Zone Helper Functions
fun parseUtcMillisToManilaStart(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val manilaCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
    manilaCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    manilaCal.set(Calendar.MILLISECOND, 0)
    return manilaCal.timeInMillis
}

fun parseUtcMillisToManilaEnd(utcMillis: Long): Long {
    val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val manilaCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
    manilaCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
    manilaCal.set(Calendar.MILLISECOND, 999)
    return manilaCal.timeInMillis
}

fun getManilaStartOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun getManilaEndOfDay(timeMillis: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
    cal.timeInMillis = timeMillis
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return cal.timeInMillis
}

fun createSignatureBitmap(strokes: List<List<Offset>>, width: Int = 500, height: Int = 250): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#1E88E5")
        strokeWidth = 6f
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { line ->
        if (line.size > 1) {
            val path = android.graphics.Path()
            path.moveTo(line[0].x, line[0].y)
            for (i in 1 until line.size) {
                path.lineTo(line[i].x, line[i].y)
            }
            canvas.drawPath(path, paint)
        } else if (line.size == 1) {
            canvas.drawCircle(line[0].x, line[0].y, 3f, paint)
        }
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianScreen(onLogout: () -> Unit, viewModel: TechnicianViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var currentTab by remember { mutableStateOf(TechnicianTab.HOME) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeRecipientUserId by remember { mutableStateOf<String?>(null) }
    var activeChatName by remember { mutableStateOf("") }
    var completingJob by remember { mutableStateOf<TechJobDetail?>(null) }
    
    val techName by viewModel.techName.collectAsState()
    val profilePicUrl by viewModel.profilePicUrl.collectAsState()
    val userHasConsented by viewModel.hasConsented.collectAsState()

    // Single Observable Location Permission & GPS State
    fun checkPermission(): Boolean {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isGpsOn = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        return hasPerm && isGpsOn
    }

    var hasLocationPermission by remember { mutableStateOf(checkPermission()) }
    var showPrivacyConsent by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }

    // Intercept Back Button
    BackHandler(enabled = activeChatId == null && completingJob == null) {
        if (currentTab != TechnicianTab.HOME) {
            currentTab = TechnicianTab.HOME
        } else {
            (context as? android.app.Activity)?.finish()
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
        val updated = checkPermission()
        hasLocationPermission = updated
        showLocationDialog = if (!fineGranted && !coarseGranted) {
            true
        } else {
            !updated
        }
    }

    // Reactive BroadcastReceiver + Lifecycle Listener
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: Intent?) {
                val updated = checkPermission()
                hasLocationPermission = updated
                if (userHasConsented) {
                    showLocationDialog = !updated
                }
            }
        }
        val filter = android.content.IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)

        val lifecycleOwner = (context as? LifecycleOwner)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val updated = checkPermission()
                hasLocationPermission = updated
                if (userHasConsented) {
                    showLocationDialog = !updated
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

    // Live GPS Location Broadcasting
    LaunchedEffect(userHasConsented) {
        if (userHasConsented) {
            if (!checkPermission()) {
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
                if (checkPermission()) {
                    try {
                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { location ->
                                if (location != null) {
                                    viewModel.updateLocation(location.latitude, location.longitude)
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.w("TechnicianScreen", "GPS update error: ${e.localizedMessage}")
                            }
                    } catch (e: Exception) {
                        android.util.Log.w("TechnicianScreen", "Location fetch error: ${e.localizedMessage}")
                    }
                }
                delay(10.seconds)
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
        LocationPermissionDialog(
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

    if (completingJob != null) {
        JobCompletionScreen(
            job = completingJob!!,
            techName = techName,
            viewModel = viewModel,
            onExit = { completingJob = null }
        )
    } else if (activeChatId != null) {
        FullChatScreen(
            chatId = activeChatId!!,
            recipientUserId = activeRecipientUserId,
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
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(PrimaryBlue.copy(0.12f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Build, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(currentTab.label, fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                                Text("CoolConnect • Field Tech Portal", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { currentTab = TechnicianTab.CHAT }) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Messages", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                        }
                        IconButton(onClick = { currentTab = TechnicianTab.PROFILE }) {
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
                TechnicianBottomBar(currentTab = currentTab, onTabSelected = { currentTab = it })
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            viewModel = viewModel,
                            hasLocationPermission = hasLocationPermission,
                            onNavigateToJobs = { currentTab = TechnicianTab.CUSTOMERS },
                            onNavigateToMap = { currentTab = TechnicianTab.MAP },
                            onNavigateToChat = { currentTab = TechnicianTab.CHAT },
                            onNavigateToAi = { currentTab = TechnicianTab.AI },
                            onOpenChat = { id, name -> activeChatId = id; activeChatName = name },
                            onRequestLocationPermission = { showLocationDialog = true },
                            onCompleteJob = { completingJob = it }
                        )
                        TechnicianTab.CUSTOMERS -> TechnicianCustomerListView(
                            viewModel = viewModel,
                            onOpenChat = { id, name -> activeChatId = id; activeChatName = name },
                            onCompleteJob = { completingJob = it }
                        )
                        TechnicianTab.HISTORY -> TechnicianHistoryView(viewModel = viewModel)
                        TechnicianTab.AI -> TechnicianTroubleshootView(viewModel = viewModel)
                        TechnicianTab.MAP -> TechnicianMapView(
                            viewModel = viewModel,
                            hasLocationPermission = hasLocationPermission,
                            onRequestLocationPermission = { showLocationDialog = true }
                        )
                        TechnicianTab.CHAT -> ConversationListScreen(
                            currentUserId = auth.currentUser?.uid ?: "",
                            currentUserName = techName,
                            userRole = "technician",
                            onChatSelected = { id, recipientUserId, name ->
                                activeChatId = id
                                activeRecipientUserId = recipientUserId
                                activeChatName = name
                            }
                        )
                        TechnicianTab.PROFILE -> TechnicianProfileView(onLogout = onLogout, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun TechnicianBottomBar(currentTab: TechnicianTab, onTabSelected: (TechnicianTab) -> Unit) {
    val barTabs = listOf(
        TechnicianTab.HOME,
        TechnicianTab.CUSTOMERS,
        TechnicianTab.AI,
        TechnicianTab.MAP,
        TechnicianTab.HISTORY
    )

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.height(64.dp).border(BorderStroke(0.5.dp, Color(0xFFE2E8F0)))
    ) {
        barTabs.forEach { tab ->
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

// 🛠️ 1. TECHNICIAN COMMAND CENTER (HOME VIEW)
@Composable
fun TechnicianHomeView(
    techName: String,
    profilePicUrl: String?,
    viewModel: TechnicianViewModel,
    hasLocationPermission: Boolean,
    onNavigateToJobs: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAi: () -> Unit,
    onOpenChat: (String, String) -> Unit,
    onRequestLocationPermission: () -> Unit = {},
    onCompleteJob: (TechJobDetail) -> Unit = {}
) {
    val context = LocalContext.current
    val isOnlineState by viewModel.isOnline.collectAsState()
    val activeJob by viewModel.primaryJob.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()

    var previousJobCount by remember { mutableIntStateOf(-1) }

    val timeGreeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Sound alert when job count increases
    LaunchedEffect(activeJobs.size) {
        val currentCount = activeJobs.size
        if (previousJobCount != -1 && currentCount > previousJobCount) {
            NotificationHelper.sendDispatchAlert(
                context,
                "🚨 URGENT: New Service Dispatch Assigned!",
                "Technician $techName, you have a new pending service request. Open Field Command."
            )
        }
        previousJobCount = currentCount
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

        // Availability Banner (Initial null = Neutral Loading State)
        val online = isOnlineState
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (online) {
                    true -> Color(0xFFE8F8EE)
                    false -> Color(0xFFFFF0F0)
                    null -> AppLightBg
                }
            ),
            border = BorderStroke(
                1.dp,
                when (online) {
                    true -> PrimaryGreen.copy(0.4f)
                    false -> Color.Red.copy(0.4f)
                    null -> FieldBorder
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                when (online) {
                                    true -> PrimaryGreen
                                    false -> Color.Red
                                    null -> Color.Gray
                                },
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when (online) {
                                true -> "● ONLINE & DISPATCH READY"
                                false -> "○ OFFLINE"
                                null -> "● CONNECTING..."
                            },
                            fontWeight = FontWeight.Bold,
                            color = when (online) {
                                true -> PrimaryGreen
                                false -> Color.Red
                                null -> Color.Gray
                            },
                            fontSize = 13.sp
                        )
                        Text(
                            text = when (online) {
                                true -> "Receiving active dispatch requests"
                                false -> "Not receiving new job requests"
                                null -> "Connecting to dispatch server..."
                            },
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
                Switch(
                    checked = online ?: false,
                    onCheckedChange = { checked ->
                        if (checked && !hasLocationPermission) {
                            onRequestLocationPermission()
                        } else {
                            viewModel.setOnlineStatus(checked)
                        }
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PrimaryGreen)
                )
            }
        }

        if (!hasLocationPermission) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestLocationPermission() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF0F0)),
                border = BorderStroke(1.dp, Color.Red.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Location Permission is OFF", fontWeight = FontWeight.Bold, color = Color.Red, fontSize = 12.sp)
                        Text("Tap to grant location access for live GPS dispatch tracking.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Text("Enable", fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Active Dispatch Card
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

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val mapIntent = Intent(Intent.ACTION_VIEW, "geo:0,0?q=${Uri.encode(job.address)}".toUri())
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
                            onClick = { onOpenChat(job.id, job.name) },
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
                            onClick = { onCompleteJob(job) },
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
                            onClick = { viewModel.startJob(job.id) },
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

        // Quick Actions
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
fun TechnicianMapView(
    viewModel: TechnicianViewModel,
    hasLocationPermission: Boolean,
    onRequestLocationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val apiKey = context.getString(R.string.google_maps_key)
    val coroutineScope = rememberCoroutineScope()
    
    val routePoints by viewModel.routePoints.collectAsState()
    val routeDuration by viewModel.routeDuration.collectAsState()
    val routeDistance by viewModel.routeDistance.collectAsState()
    val primaryJob by viewModel.primaryJob.collectAsState()

    var techLocation by remember { mutableStateOf<LatLng?>(null) }
    var liveCustomerLocation by remember { mutableStateOf<LatLng?>(null) }
    var geocodedLocation by remember { mutableStateOf<LatLng?>(null) }
    val customerLocation = liveCustomerLocation ?: geocodedLocation
    var isCameraInitialized by remember { mutableStateOf(false) }

    val cameraPositionState = rememberCameraPositionState()

    fun focusOnRoute() {
        if (techLocation != null && customerLocation != null) {
            try {
                val builder = com.google.android.gms.maps.model.LatLngBounds.Builder()
                builder.include(techLocation!!)
                builder.include(customerLocation!!)
                routePoints.forEach { builder.include(it) }
                val bounds = builder.build()

                coroutineScope.launch {
                    cameraPositionState.animate(
                        update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngBounds(bounds, 140),
                        durationMs = 1200
                    )
                }
            } catch (_: Exception) {
                techLocation?.let { loc ->
                    coroutineScope.launch {
                        cameraPositionState.animate(
                            update = com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(loc, 15f),
                            durationMs = 800
                        )
                    }
                }
            }
        }
    }

    // Clean DisposableEffect for Technician GPS coordinates
    val uid = auth.currentUser?.uid
    DisposableEffect(uid) {
        if (!uid.isNullOrEmpty()) {
            val listener = db.collection("users").document(uid).addSnapshotListener { doc, _ ->
                if (doc != null && doc.exists()) {
                    val lat = doc.getDouble("latitude")
                    val lng = doc.getDouble("longitude")
                    if (lat != null && lng != null) {
                        val loc = LatLng(lat, lng)
                        techLocation = loc
                        if (!isCameraInitialized) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(loc, 15f)
                            isCameraInitialized = true
                        }
                    } else {
                        techLocation = null
                    }
                } else {
                    techLocation = null
                }
            }
            onDispose { listener.remove() }
        } else {
            techLocation = null
            onDispose { }
        }
    }

    // Clean DisposableEffect for Customer Location of Primary Job
    val customerUid = remember(primaryJob) {
        val rawCuid = primaryJob?.customerId?.ifBlank { primaryJob?.id } ?: ""
        if (rawCuid.isNotBlank()) rawCuid.replace("admin_", "").replace("tech_", "") else null
    }

    DisposableEffect(customerUid, primaryJob?.id) {
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

        fun extractLatLng(doc: com.google.firebase.firestore.DocumentSnapshot?): LatLng? {
            if (doc == null || !doc.exists()) return null
            val lat = doc.getDouble("latitude") ?: doc.getDouble("lat") ?: doc.getDouble("customerLat")
            val lng = doc.getDouble("longitude") ?: doc.getDouble("lng") ?: doc.getDouble("customerLng")
            return if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) LatLng(lat, lng) else null
        }

        if (!customerUid.isNullOrEmpty()) {
            listeners.add(
                db.collection("users").document(customerUid).addSnapshotListener { userDoc, _ ->
                    val userLoc = extractLatLng(userDoc)
                    if (userLoc != null) {
                        liveCustomerLocation = userLoc
                    } else {
                        db.collection("chats_v2").document("admin_$customerUid").get().addOnSuccessListener { chatDoc ->
                            val chatLoc = extractLatLng(chatDoc)
                            if (chatLoc != null) {
                                liveCustomerLocation = chatLoc
                            } else {
                                liveCustomerLocation = null
                            }
                        }
                    }
                }
            )
        }

        val reqId = primaryJob?.id
        if (!reqId.isNullOrEmpty()) {
            listeners.add(
                db.collection("service_requests").document(reqId).addSnapshotListener { reqDoc, _ ->
                    val reqLoc = extractLatLng(reqDoc)
                    if (reqLoc != null) {
                        liveCustomerLocation = reqLoc
                    }
                }
            )
        }

        onDispose {
            listeners.forEach { it.remove() }
        }
    }

    // Fallback Geocoding for Customer Street Address if Live GPS is unavailable
    LaunchedEffect(customerUid, primaryJob?.address, liveCustomerLocation) {
        val addressStr = primaryJob?.address
        if (liveCustomerLocation == null && !addressStr.isNullOrBlank() && addressStr != "No Address" && addressStr != "Fetching delivery address...") {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val geocoder = android.location.Geocoder(context, java.util.Locale.getDefault())
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        geocoder.getFromLocationName(addressStr, 1) { addresses ->
                            if (!addresses.isNullOrEmpty()) {
                                val first = addresses[0]
                                geocodedLocation = LatLng(first.latitude, first.longitude)
                            }
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocationName(addressStr, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val first = addresses[0]
                            geocodedLocation = LatLng(first.latitude, first.longitude)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("TechnicianMapView", "Geocoding fallback failed for '$addressStr': ${e.localizedMessage}")
                }
            }
        } else if (liveCustomerLocation != null) {
            geocodedLocation = null
        }
    }

    // Fetch route when locations are available
    LaunchedEffect(techLocation, customerLocation) {
        if (techLocation != null && customerLocation != null) {
            viewModel.fetchRoute(techLocation!!, customerLocation!!, apiKey)
            delay(800.milliseconds)
            focusOnRoute()
        } else {
            viewModel.clearRoute()
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
                    title = primaryJob?.name ?: "Customer",
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
                Polyline(
                    points = listOf(techLocation!!, customerLocation!!),
                    color = PrimaryBlue.copy(alpha = 0.5f),
                    width = 8f,
                    jointType = com.google.android.gms.maps.model.JointType.ROUND
                )
            }
        }

        if (!hasLocationPermission) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter)
                    .clickable { onRequestLocationPermission() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(6.dp),
                border = BorderStroke(1.dp, Color.Red.copy(0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFFFFF0F0), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.LocationOff, contentDescription = null, tint = Color.Red, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Location Permission Disabled", fontWeight = FontWeight.Bold, color = TextDark, fontSize = 13.sp)
                        Text("Enable location to view real-time route to customer.", fontSize = 11.sp, color = Color.Gray)
                    }
                    Button(
                        onClick = onRequestLocationPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Enable GPS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dispatch HUD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
        ) {
            if (customerLocation != null && techLocation != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { focusOnRoute() },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(8.dp),
                    border = BorderStroke(1.dp, PrimaryBlue.copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(PrimaryBlue, CircleShape)
                                .clickable { focusOnRoute() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Navigation, null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("En-route to ${primaryJob?.name ?: "Customer"}", fontWeight = FontWeight.Black, fontSize = 15.sp, color = TextDark)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val timeText = routeDuration ?: "ETA unavailable"
                                val distText = routeDistance ?: "Distance unavailable"
                                
                                Text(timeText, color = PrimaryGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.size(4.dp).background(Color.Gray, CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(distText, color = Color.Gray, fontSize = 13.sp)
                            }
                            Text("Tap to frame route on map 🗺️", fontSize = 10.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                        }
                        IconButton(
                            onClick = { focusOnRoute() },
                            modifier = Modifier.background(AppLightBg, CircleShape)
                        ) {
                            Icon(Icons.Default.MyLocation, "Center Route", tint = PrimaryBlue)
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f))) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(10.dp), shape = CircleShape, color = if (techLocation == null) Color.Red else PrimaryGreen) {}
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (techLocation == null) "Location unavailable (GPS off or missing coordinates)" else "Awaiting Active Dispatch Customer Location...",
                            color = TextDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

// 🤖 4. AI TROUBLESHOOTER CONVERSATIONAL CHAT VIEW
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianTroubleshootView(
    viewModel: TechnicianViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val assignedCustomerName by viewModel.assignedCustomerName.collectAsState()
    val assignedCustomerId by viewModel.assignedCustomerId.collectAsState()

    val messages = viewModel.aiMessages
    var inputText by remember { mutableStateOf("") }
    var isAiGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAiChatHistory(context)
    }

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

    LaunchedEffect(messages.size, messages.lastOrNull()?.text, messages.lastOrNull()?.isPending) {
        if (messages.isNotEmpty() && isAtBottom) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun sendMessage(queryText: String) {
        val trimmed = queryText.trim()
        if (trimmed.isBlank() || isAiGenerating) return

        val userMsgId = java.util.UUID.randomUUID().toString()
        val aiMsgId = java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        messages.add(AiChatMessage(id = userMsgId, sender = MessageSender.USER, text = trimmed, timestamp = now))
        messages.add(AiChatMessage(id = aiMsgId, sender = MessageSender.AI, text = "", timestamp = now, isPending = true, userPrompt = trimmed))

        inputText = ""
        isAiGenerating = true

        coroutineScope.launch {
            if (isAtBottom) {
                listState.animateScrollToItem(messages.size - 1)
            }

            var fullBuffer = ""
            var displayedText = ""
            var hasStarted = false

            try {
                var streamError: Throwable? = null
                val collectorJob = launch {
                    try {
                        viewModel.queryAiAssistantStream(trimmed, customerId = assignedCustomerId, customerName = assignedCustomerName)
                            .collect { chunk ->
                                fullBuffer += chunk
                            }
                    } catch (e: Throwable) {
                        streamError = e
                    }
                }

                while (collectorJob.isActive || displayedText.length < fullBuffer.length) {
                    if (streamError != null) {
                        throw streamError!!
                    }
                    if (displayedText.length < fullBuffer.length) {
                        val currentIdx = messages.indexOfFirst { it.id == aiMsgId }
                        if (currentIdx != -1) {
                            if (!hasStarted) {
                                hasStarted = true
                                messages[currentIdx] = messages[currentIdx].copy(
                                    isPending = false,
                                    isStreaming = true,
                                    text = ""
                                )
                            }

                            val remaining = fullBuffer.substring(displayedText.length)
                            val spaceIdx = remaining.indexOf(' ')
                            val stepSize = if (spaceIdx in 0..7) spaceIdx + 1 else minOf(4, remaining.length)

                            displayedText += remaining.take(stepSize)
                            messages[currentIdx] = messages[currentIdx].copy(text = displayedText)

                            if (isAtBottom) {
                                listState.animateScrollToItem(messages.size - 1)
                            }
                        }
                        delay(18.milliseconds)
                    } else {
                        delay(20.milliseconds)
                    }
                }

                if (streamError != null) {
                    throw streamError!!
                }

                val currentIdx = messages.indexOfFirst { it.id == aiMsgId }
                if (currentIdx != -1) {
                    messages[currentIdx] = messages[currentIdx].copy(isPending = false, isStreaming = false, isError = false, text = fullBuffer)
                    viewModel.saveAiChatHistory(context)
                }
            } catch (error: Exception) {
                val currentIdx = messages.indexOfFirst { it.id == aiMsgId }
                if (currentIdx != -1) {
                    messages[currentIdx] = messages[currentIdx].copy(
                        isPending = false,
                        isStreaming = false,
                        isError = true,
                        text = "Diagnostic Error: ${error.localizedMessage ?: "Failed to reach AI server. Please verify network settings."}"
                    )
                    viewModel.saveAiChatHistory(context)
                }
            } finally {
                isAiGenerating = false
            }
        }
    }

    fun regenerateAiMessage(targetMsg: AiChatMessage) {
        val prompt = targetMsg.userPrompt ?: return
        if (isAiGenerating) return

        val aiMsgIndex = messages.indexOfFirst { it.id == targetMsg.id }
        if (aiMsgIndex != -1) {
            messages[aiMsgIndex] = messages[aiMsgIndex].copy(isPending = true, isStreaming = false, isError = false, text = "")
            isAiGenerating = true

            coroutineScope.launch {
                var fullBuffer = ""
                var displayedText = ""
                var hasStarted = false

                try {
                    var streamError: Throwable? = null
                    val collectorJob = launch {
                        try {
                            viewModel.queryAiAssistantStream(prompt, customerId = assignedCustomerId, customerName = assignedCustomerName)
                                .collect { chunk ->
                                    fullBuffer += chunk
                                }
                        } catch (e: Throwable) {
                            streamError = e
                        }
                    }

                    while (collectorJob.isActive || displayedText.length < fullBuffer.length) {
                        if (streamError != null) {
                            throw streamError!!
                        }
                        if (displayedText.length < fullBuffer.length) {
                            val currentIdx = messages.indexOfFirst { it.id == targetMsg.id }
                            if (currentIdx != -1) {
                                if (!hasStarted) {
                                    hasStarted = true
                                    messages[currentIdx] = messages[currentIdx].copy(
                                        isPending = false,
                                        isStreaming = true,
                                        text = ""
                                    )
                                }

                                val remaining = fullBuffer.substring(displayedText.length)
                                val spaceIdx = remaining.indexOf(' ')
                                val stepSize = if (spaceIdx in 0..7) spaceIdx + 1 else minOf(4, remaining.length)

                                displayedText += remaining.take(stepSize)
                                messages[currentIdx] = messages[currentIdx].copy(text = displayedText)
                            }
                            delay(18.milliseconds)
                        } else {
                            delay(20.milliseconds)
                        }
                    }

                    if (streamError != null) {
                        throw streamError!!
                    }

                    val currentIdx = messages.indexOfFirst { it.id == targetMsg.id }
                    if (currentIdx != -1) {
                        messages[currentIdx] = messages[currentIdx].copy(isPending = false, isStreaming = false, isError = false, text = fullBuffer)
                        viewModel.saveAiChatHistory(context)
                    }
                } catch (error: Exception) {
                    val currentIdx = messages.indexOfFirst { it.id == targetMsg.id }
                    if (currentIdx != -1) {
                        messages[currentIdx] = messages[currentIdx].copy(
                            isPending = false,
                            isStreaming = false,
                            isError = true,
                            text = "Diagnostic Error: ${error.localizedMessage ?: "Failed to regenerate AI response."}"
                        )
                        viewModel.saveAiChatHistory(context)
                    }
                } finally {
                    isAiGenerating = false
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppLightBg),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp,
                border = BorderStroke(0.5.dp, FieldBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(PrimaryBlue.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "CoolConnect Field AI",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = TextDark,
                                maxLines = 1
                            )
                            Text(
                                text = if (!assignedCustomerName.isNullOrBlank()) {
                                    "Assigned: $assignedCustomerName • ${messages.size} msgs"
                                } else {
                                    "${messages.size} messages in chat"
                                },
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                viewModel.clearAiChatHistory(context)
                                Toast.makeText(context, "Chat cleared", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .background(AppLightBg, CircleShape)
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty()) {
                        AiChatEmptyState(
                            onSelectPrompt = { prompt -> sendMessage(prompt) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(items = messages, key = { _, msg -> msg.id }) { index, msg ->
                                val prevMsg = if (index > 0) messages[index - 1] else null
                                val showTimestamp = prevMsg == null || (msg.timestamp - prevMsg.timestamp > 5 * 60 * 1000L)

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    if (showTimestamp) {
                                        AiChatTimestampHeader(timestamp = msg.timestamp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    AiChatBubble(
                                        message = msg,
                                        onCopy = { textToCopy ->
                                            clipboardManager.setText(AnnotatedString(textToCopy))
                                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        onRegenerate = { msgToRegen -> regenerateAiMessage(msgToRegen) }
                                    )
                                }
                            }
                        }
                    }

                    if (!isAtBottom && messages.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 16.dp, end = 16.dp)
                                .clickable {
                                    coroutineScope.launch { listState.animateScrollToItem(messages.size - 1) }
                                },
                            shape = RoundedCornerShape(20.dp),
                            color = PrimaryBlue,
                            shadowElevation = 6.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowDownward, contentDescription = "Jump to latest", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Jump to latest", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, FieldBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = { Text("Ask Field AI...", fontSize = 13.sp, color = Color.Gray) },
                            modifier = Modifier
                                .weight(1f)
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                        if (!keyEvent.isShiftPressed && inputText.isNotBlank() && !isAiGenerating) {
                                            sendMessage(inputText)
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                },
                            maxLines = 5,
                            minLines = 1,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryBlue,
                                unfocusedBorderColor = FieldBorder,
                                focusedContainerColor = AppLightBg,
                                unfocusedContainerColor = AppLightBg
                            )
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = { sendMessage(inputText) },
                            enabled = inputText.isNotBlank() && !isAiGenerating,
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    color = if (inputText.isNotBlank() && !isAiGenerating) PrimaryBlue else Color(0xFFCBD5E1),
                                    shape = CircleShape
                                )
                        ) {
                            if (isAiGenerating) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiChatBubble(
    message: AiChatMessage,
    onCopy: (String) -> Unit,
    onRegenerate: (AiChatMessage) -> Unit
) {
    val isUser = message.sender == MessageSender.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PrimaryBlue.copy(alpha = 0.12f), CircleShape)
                    .border(1.dp, PrimaryBlue.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "AI Assistant",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Surface(
                shape = if (isUser) {
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                } else {
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
                },
                color = when {
                    isUser -> PrimaryBlue
                    message.isError -> Color(0xFFFFF0F0)
                    else -> Color.White
                },
                border = if (isUser) null else BorderStroke(
                    1.dp,
                    if (message.isError) Color.Red.copy(alpha = 0.4f) else FieldBorder
                ),
                shadowElevation = if (isUser) 1.dp else 2.dp
            ) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    if (message.isPending) {
                        TypingIndicator()
                    } else {
                        val textColor = when {
                            isUser -> Color.White
                            message.isError -> Color(0xFFD32F2F)
                            else -> TextDark
                        }

                        Text(
                            text = if (isUser) AnnotatedString(message.text) else buildFormattedAiText(message.text),
                            color = textColor,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }

            if (!isUser && !message.isPending && message.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onCopy(message.text) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy message",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(2.dp))

                    IconButton(
                        onClick = { onRegenerate(message) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Regenerate response",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing_indicator")

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
        label = "dot2"
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
        modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dot1Alpha }
                .background(PrimaryBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dot2Alpha }
                .background(PrimaryBlue, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .graphicsLayer { alpha = dot3Alpha }
                .background(PrimaryBlue, CircleShape)
        )
    }
}

@Composable
fun AiChatEmptyState(
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestedPrompts = remember {
        listOf(
            "⚡ AC compressor humming but fan won't spin",
            "❄️ Refrigerant leak detection & pressure testing steps",
            "📟 Inverter AC error code E4 diagnostic flow",
            "🔌 Dual run capacitor multimeter testing procedure",
            "🌀 Airflow weak & coil frosting troubleshooting"
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(PrimaryBlue.copy(alpha = 0.1f), CircleShape)
                .border(1.5.dp, PrimaryBlue.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CoolConnect Field AI Assistant",
            fontWeight = FontWeight.Black,
            fontSize = 20.sp,
            color = TextDark,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Instant HVAC, Electrical & Lock fault diagnostics. Ask a question or tap a suggested prompt below.",
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SUGGESTED DIAGNOSTIC PROMPTS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF888888),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestedPrompts.forEach { prompt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPrompt(prompt) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FieldBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = prompt,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDark,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiChatTimestampHeader(timestamp: Long) {
    val dateStr = remember(timestamp) {
        val date = Date(timestamp)
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = date }

        if (now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
            now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
        ) {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(date)
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFE2E8F0).copy(alpha = 0.6f)
        ) {
            Text(
                text = dateStr,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

fun buildFormattedAiText(text: String): AnnotatedString {
    return buildAnnotatedString {
        val parts = text.split("**")
        for (i in parts.indices) {
            if (i % 2 == 1) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(parts[i])
                }
            } else {
                append(parts[i])
            }
        }
    }
}

// 📋 5. JOB COMPLETION & SERVICE HISTORY VIEW
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianCustomerListView(
    viewModel: TechnicianViewModel,
    onOpenChat: (String, String) -> Unit,
    onCompleteJob: (TechJobDetail) -> Unit = {}
) {
    val techName by viewModel.techName.collectAsState()
    val activeJobs by viewModel.activeJobs.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Active Jobs Header
        item {
            Text(
                text = "Assigned Service Jobs",
                style = MaterialTheme.typography.headlineMedium,
                color = TextDark,
                fontWeight = FontWeight.Black
            )
        }

        // Active Jobs List
        if (activeJobs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FieldBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No active jobs assigned.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(activeJobs, key = { "active_${it.id}" }) { job ->
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
                                color = if (isInProgress) Color(0xFFFF9800).copy(0.15f) else PrimaryBlue.copy(0.15f)
                            ) {
                                Text(
                                    text = job.status,
                                    color = if (isInProgress) Color(0xFFFF9800) else PrimaryBlue,
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
                            Button(
                                onClick = { onOpenChat(job.id, job.name) },
                                modifier = Modifier.weight(1f).height(42.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                                border = BorderStroke(1.dp, PrimaryGreen),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Chat", color = PrimaryGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (isInProgress) {
                                Button(
                                    onClick = { onCompleteJob(job) },
                                    modifier = Modifier.weight(1.5f).height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Complete Job", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.startJob(job.id) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnicianHistoryView(
    viewModel: TechnicianViewModel = hiltViewModel()
) {
    val historyJobs by viewModel.historyJobs.collectAsState()

    var selectedHistoryItemForDetail by remember { mutableStateOf<ServiceHistoryItem?>(null) }

    // Filter states
    var selectedStatusFilter by remember { mutableStateOf(HistoryStatusFilter.ALL) }
    var selectedDateFilter by remember { mutableStateOf(HistoryDateFilter.ALL_TIME) }
    var customStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var customEndDateMillis by remember { mutableStateOf<Long?>(null) }
    var showCustomRangeDialog by remember { mutableStateOf(false) }

    // Performant filtering using derivedStateOf
    val filteredHistoryState = remember {
        derivedStateOf {
            historyJobs.filter { item ->
                // Status check
                val statusMatches = when (selectedStatusFilter) {
                    HistoryStatusFilter.ALL -> true
                    HistoryStatusFilter.COMPLETED -> item.status.equals("Completed", ignoreCase = true)
                    HistoryStatusFilter.DENIED -> item.status.equals("Denied", ignoreCase = true)
                }
                if (!statusMatches) return@filter false

                // Real Job Date determination: completedAt for Completed, deniedAt/createdAt/timestamp for Denied
                val targetTimestamp = if (item.status.equals("Completed", ignoreCase = true)) {
                    item.completedAt ?: item.timestamp
                } else {
                    item.deniedAt ?: item.createdAt ?: item.timestamp
                }
                val itemMillis = targetTimestamp?.toDate()?.time ?: 0L

                // Philippine Time Zone Filtering
                when (selectedDateFilter) {
                    HistoryDateFilter.ALL_TIME -> true
                    HistoryDateFilter.TODAY -> {
                        val startOfDay = getManilaStartOfDay()
                        val endOfDay = getManilaEndOfDay()
                        itemMillis in startOfDay..endOfDay
                    }
                    HistoryDateFilter.LAST_7_DAYS -> {
                        val nowManila = System.currentTimeMillis()
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                            timeInMillis = nowManila
                            add(Calendar.DAY_OF_YEAR, -6)
                        }
                        val sevenDaysAgoStart = getManilaStartOfDay(cal.timeInMillis)
                        val todayEnd = getManilaEndOfDay(nowManila)
                        itemMillis in sevenDaysAgoStart..todayEnd
                    }
                    HistoryDateFilter.LAST_30_DAYS -> {
                        val nowManila = System.currentTimeMillis()
                        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila")).apply {
                            timeInMillis = nowManila
                            add(Calendar.DAY_OF_YEAR, -29)
                        }
                        val thirtyDaysAgoStart = getManilaStartOfDay(cal.timeInMillis)
                        val todayEnd = getManilaEndOfDay(nowManila)
                        itemMillis in thirtyDaysAgoStart..todayEnd
                    }
                    HistoryDateFilter.CUSTOM -> {
                        if (customStartDateMillis != null && customEndDateMillis != null) {
                            itemMillis in customStartDateMillis!!..customEndDateMillis!!
                        } else true
                    }
                }
            }.sortedByDescending { item ->
                val targetTs = if (item.status.equals("Completed", ignoreCase = true)) {
                    item.completedAt ?: item.timestamp
                } else {
                    item.deniedAt ?: item.createdAt ?: item.timestamp
                }
                targetTs?.toDate()?.time ?: 0L
            }
        }
    }
    val filteredHistory = filteredHistoryState.value

    if (selectedHistoryItemForDetail != null) {
        ServiceHistoryDetailDialog(
            item = selectedHistoryItemForDetail!!,
            onDismiss = { selectedHistoryItemForDetail = null }
        )
    }

    if (showCustomRangeDialog) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomRangeDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            // Convert UTC millis to Asia/Manila start and end of day
                            customStartDateMillis = parseUtcMillisToManilaStart(start)
                            customEndDateMillis = parseUtcMillisToManilaEnd(end)
                            selectedDateFilter = HistoryDateFilter.CUSTOM
                        }
                        showCustomRangeDialog = false
                    }
                ) {
                    Text("Apply", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomRangeDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text("Select Date Range (Philippine Time)", modifier = Modifier.padding(start = 16.dp, top = 16.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                },
                headline = {
                    Text("Choose start and end date for history", modifier = Modifier.padding(start = 16.dp, bottom = 8.dp), fontSize = 12.sp, color = Color.Gray)
                },
                modifier = Modifier.fillMaxWidth().height(400.dp)
            )
        }
    }

    val customDateText = remember(customStartDateMillis, customEndDateMillis) {
        if (customStartDateMillis != null && customEndDateMillis != null) {
            val s = SimpleDateFormat("MMM d", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Manila") }.format(Date(customStartDateMillis!!))
            val e = SimpleDateFormat("MMM d", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Manila") }.format(Date(customEndDateMillis!!))
            "$s - $e"
        } else null
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Service History Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Service History",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextDark,
                        fontWeight = FontWeight.Black
                    )
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PrimaryBlue.copy(0.12f)
                    ) {
                        Text(
                            text = "${filteredHistory.size} records",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HistoryFilterChips(
                    selectedStatus = selectedStatusFilter,
                    onStatusSelected = { selectedStatusFilter = it },
                    selectedDate = selectedDateFilter,
                    onDateSelected = { dateFilter ->
                        if (dateFilter == HistoryDateFilter.CUSTOM) {
                            showCustomRangeDialog = true
                        } else {
                            selectedDateFilter = dateFilter
                        }
                    },
                    customDateText = customDateText
                )
            }
        }

        // Service History List
        if (historyJobs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FieldBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completed or denied jobs yet.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else if (filteredHistory.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, FieldBorder)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No service history matches these filters.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(filteredHistory, key = { "history_${it.id}" }) { item ->
                ServiceHistoryCard(
                    item = item,
                    onClick = { selectedHistoryItemForDetail = item }
                )
            }
        }
    }
}

@Composable
fun HistoryFilterChips(
    selectedStatus: HistoryStatusFilter,
    onStatusSelected: (HistoryStatusFilter) -> Unit,
    selectedDate: HistoryDateFilter,
    onDateSelected: (HistoryDateFilter) -> Unit,
    customDateText: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Status:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusFilter.entries.forEach { status ->
                    val isSelected = selectedStatus == status
                    val chipBg = when {
                        !isSelected -> AppLightBg
                        status == HistoryStatusFilter.COMPLETED -> PrimaryGreen.copy(0.15f)
                        status == HistoryStatusFilter.DENIED -> Color.Red.copy(0.15f)
                        else -> PrimaryBlue.copy(0.15f)
                    }
                    val chipText = when {
                        !isSelected -> TextDark
                        status == HistoryStatusFilter.COMPLETED -> PrimaryGreen
                        status == HistoryStatusFilter.DENIED -> Color.Red
                        else -> PrimaryBlue
                    }
                    val border = BorderStroke(
                        1.dp,
                        when {
                            !isSelected -> FieldBorder
                            status == HistoryStatusFilter.COMPLETED -> PrimaryGreen
                            status == HistoryStatusFilter.DENIED -> Color.Red
                            else -> PrimaryBlue
                        }
                    )

                    Surface(
                        modifier = Modifier.clickable { onStatusSelected(status) },
                        shape = RoundedCornerShape(16.dp),
                        color = chipBg,
                        border = border
                    ) {
                        Text(
                            text = status.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = chipText,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Range:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryDateFilter.entries.forEach { dateFilter ->
                    val isSelected = selectedDate == dateFilter
                    val label = if (dateFilter == HistoryDateFilter.CUSTOM && isSelected && !customDateText.isNullOrBlank()) {
                        customDateText
                    } else {
                        dateFilter.label
                    }

                    Surface(
                        modifier = Modifier.clickable { onDateSelected(dateFilter) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) PrimaryBlue.copy(0.15f) else AppLightBg,
                        border = BorderStroke(1.dp, if (isSelected) PrimaryBlue else FieldBorder)
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) PrimaryBlue else TextDark,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ServiceHistoryCard(
    item: ServiceHistoryItem,
    onClick: () -> Unit
) {
    val isCompleted = item.status.equals("Completed", ignoreCase = true)
    val isDenied = item.status.equals("Denied", ignoreCase = true)

    val dateStr = remember(item.completedAt, item.deniedAt, item.createdAt, item.timestamp) {
        val targetTs = if (isCompleted) (item.completedAt ?: item.timestamp) else (item.deniedAt ?: item.createdAt ?: item.timestamp)
        val date = targetTs?.toDate()
        if (date != null) {
            SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Manila") }.format(date)
        } else {
            "N/A"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isCompleted -> PrimaryGreen.copy(0.15f)
                        isDenied -> Color.Red.copy(0.15f)
                        else -> PrimaryBlue.copy(0.15f)
                    }
                ) {
                    Text(
                        text = item.status,
                        color = when {
                            isCompleted -> PrimaryGreen
                            isDenied -> Color.Red
                            else -> PrimaryBlue
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            InfoRow(Icons.Default.CalendarToday, dateStr)

            // Honest Summary Text (No fallback to issue description)
            val notesText = item.completionNotes.ifBlank { "No service summary recorded" }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = notesText,
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp
            )

            if (isCompleted && item.totalAmount > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Total Paid:", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                    Text(
                        text = "₱${String.format(Locale.getDefault(), "%.2f", item.totalAmount)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = PrimaryGreen
                    )
                }
            }
        }
    }
}

@Composable
fun ServiceHistoryDetailDialog(
    item: ServiceHistoryItem,
    onDismiss: () -> Unit
) {
    var showPhotoPreview by remember { mutableStateOf(false) }
    var showSignaturePreview by remember { mutableStateOf(false) }

    val isCompleted = item.status.equals("Completed", ignoreCase = true)
    val dateStr = remember(item.completedAt, item.deniedAt, item.createdAt, item.timestamp) {
        val targetTs = if (isCompleted) (item.completedAt ?: item.timestamp) else (item.deniedAt ?: item.createdAt ?: item.timestamp)
        val date = targetTs?.toDate()
        if (date != null) {
            SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).apply { timeZone = TimeZone.getTimeZone("Asia/Manila") }.format(date)
        } else {
            "N/A"
        }
    }

    if (showPhotoPreview && item.photoProofUrl.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showPhotoPreview = false },
            title = { Text("Photo Verification Proof", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.photoProofUrl,
                        contentDescription = "Photo Proof",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoPreview = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        )
    }

    if (showSignaturePreview && item.signatureUrl.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showSignaturePreview = false },
            title = { Text("Customer E-Signature Proof", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, FieldBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = item.signatureUrl,
                        contentDescription = "Signature Proof",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSignaturePreview = false }) {
                    Text("Close", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Service Details",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = TextDark
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isCompleted) PrimaryGreen.copy(0.15f) else Color.Red.copy(0.15f)
                ) {
                    Text(
                        text = item.status,
                        color = if (isCompleted) PrimaryGreen else Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = PrimaryBlue)

                InfoRow(Icons.Default.Phone, item.phone)
                InfoRow(Icons.Default.Home, item.address)
                InfoRow(Icons.Default.CalendarToday, "Date: $dateStr")
                InfoRow(Icons.Default.Build, "Request: ${item.issue}")

                HorizontalDivider(color = FieldBorder)

                Text("SERVICE SUMMARY", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                Text(
                    text = item.completionNotes.ifBlank { "No service summary recorded" },
                    fontSize = 13.sp,
                    color = TextDark,
                    lineHeight = 18.sp
                )

                if (item.partsUsed.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("PARTS USED", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)
                    Text(item.partsUsed, fontSize = 13.sp, color = TextDark)
                }

                if (isCompleted) {
                    HorizontalDivider(color = FieldBorder)
                    Text("BILLING BREAKDOWN", fontWeight = FontWeight.Black, fontSize = 11.sp, color = Color.Gray, letterSpacing = 1.sp)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppLightBg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Labor Fee", fontSize = 12.sp, color = Color.Gray)
                            Text("₱${String.format(Locale.getDefault(), "%.2f", item.laborFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Parts Fee", fontSize = 12.sp, color = Color.Gray)
                            Text("₱${String.format(Locale.getDefault(), "%.2f", item.partsFee)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Amount", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            Text("₱${String.format(Locale.getDefault(), "%.2f", item.totalAmount)}", fontSize = 15.sp, fontWeight = FontWeight.Black, color = PrimaryGreen)
                        }
                        if (item.paymentMethod.isNotBlank()) {
                            Text("Payment Method: ${item.paymentMethod}", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                if (item.photoProofUrl.isNotBlank() || item.signatureUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.photoProofUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = { showPhotoPreview = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PrimaryBlue)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Photo Proof", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (item.signatureUrl.isNotBlank()) {
                            OutlinedButton(
                                onClick = { showSignaturePreview = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PrimaryGreen)
                            ) {
                                Icon(Icons.Default.Gesture, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Signature", color = PrimaryGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold, color = PrimaryBlue)
            }
        }
    )
}

@Composable
fun JobCompletionDialog(
    job: TechJobDetail,
    techName: String,
    onDismiss: () -> Unit,
    viewModel: TechnicianViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    var workNotes by remember { mutableStateOf("") }
    var partsUsedInput by remember { mutableStateOf("") }
    var laborFeeInput by remember { mutableStateOf("500") }
    var partsFeeInput by remember { mutableStateOf("0") }
    var selectedPaymentMethod by remember { mutableStateOf("GCash") }

    val laborVal = laborFeeInput.toDoubleOrNull() ?: 0.0
    val partsVal = partsFeeInput.toDoubleOrNull() ?: 0.0
    val subtotal = laborVal + partsVal
    val vatAmount = 0.0
    val totalAmount = subtotal + vatAmount

    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    var photoUploadFailedAlert by remember { mutableStateOf<String?>(null) }

    // Camera Proof Launcher
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            capturedPhoto = bitmap
        }
    }

    // Performant E-Signature Strokes state
    val signatureStrokes = remember { mutableStateListOf<SnapshotStateList<Offset>>() }

    val checklistItems = remember { mutableStateListOf("Inspect Unit", "Check Connections", "Clean Filters", "Final Test Run") }
    val checkedStates = remember { mutableStateMapOf<String, Boolean>() }

    if (photoUploadFailedAlert != null) {
        AlertDialog(
            onDismissRequest = { photoUploadFailedAlert = null },
            title = { Text("Photo Upload Failed", fontWeight = FontWeight.Bold, color = Color.Red) },
            text = { Text("Could not upload photo proof: ${photoUploadFailedAlert}. Would you like to retry uploading or proceed without a photo?", fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = { photoUploadFailedAlert = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) { Text("Retry Photo Upload", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        capturedPhoto = null
                        photoUploadFailedAlert = null
                        Toast.makeText(context, "Proceeding without photo proof", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Proceed Without Photo", color = Color.Gray) }
            }
        )
    }

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

                // Checklist
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

                // Notes & Parts
                OutlinedTextField(
                    value = workNotes,
                    onValueChange = { workNotes = it },
                    label = { Text("Service Summary & Recommendations") },
                    placeholder = { Text("e.g. Replaced capacitor, advised quarterly cleaning...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = partsUsedInput,
                    onValueChange = { partsUsedInput = it },
                    label = { Text("Parts Used & Materials") },
                    placeholder = { Text("e.g. 45uF Capacitor, R410A Refrigerant (1.5kg)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Billing
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
                        Text("Total Amount:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("₱${String.format(Locale.getDefault(), "%.2f", totalAmount)}", fontWeight = FontWeight.Black, color = PrimaryBlue, fontSize = 16.sp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Select Payment Method:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextDark)
                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("QR Ph", "GCash", "Maya", "Cash").forEach { method ->
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
                        onClick = { cameraLauncher.launch(null) },
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
                    Text("✍️ Customer E-Signature", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                    if (signatureStrokes.isNotEmpty()) {
                        TextButton(onClick = { signatureStrokes.clear() }) {
                            Text("Clear Signature", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Performant E-Signature Canvas (Direct append to active stroke)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .border(1.5.dp, PrimaryBlue.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val newStroke = mutableStateListOf(offset)
                                    signatureStrokes.add(newStroke)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    if (signatureStrokes.isNotEmpty()) {
                                        signatureStrokes.last().add(change.position)
                                    }
                                }
                            )
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        signatureStrokes.forEach { stroke ->
                            if (stroke.size > 1) {
                                for (i in 0 until stroke.size - 1) {
                                    drawLine(
                                        color = PrimaryBlue,
                                        start = stroke[i],
                                        end = stroke[i + 1],
                                        strokeWidth = 6f,
                                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                                    )
                                }
                            } else if (stroke.size == 1) {
                                drawCircle(
                                    color = PrimaryBlue,
                                    radius = 3f,
                                    center = stroke[0]
                                )
                            }
                        }
                    }

                    if (signatureStrokes.isEmpty()) {
                        Text("Sign here using your finger", color = Color.Gray.copy(0.6f), fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
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
                    if (signatureStrokes.isEmpty()) {
                        Toast.makeText(context, "Customer signature is required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true

                    fun proceedWithAtomicCompletion(uploadedPhotoUrl: String) {
                        val signatureBmp = createSignatureBitmap(signatureStrokes)
                        viewModel.uploadBitmapImage(signatureBmp, "signatures") { sigResult ->
                            val signatureUrl = sigResult.getOrDefault("")

                            viewModel.completeJob(
                                jobId = job.id,
                                workNotes = workNotes,
                                partsUsed = partsUsedInput,
                                laborFee = laborVal,
                                partsFee = partsVal,
                                vatAmount = vatAmount,
                                totalAmount = totalAmount,
                                paymentMethod = selectedPaymentMethod,
                                photoProofUrl = uploadedPhotoUrl,
                                signatureUrl = signatureUrl,
                                techName = techName
                            ) { success, errorMsg ->
                                isSubmitting = false
                                if (success) {
                                    Toast.makeText(context, "Job verified & E-Receipt generated!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Failed to complete job: $errorMsg", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }

                    if (capturedPhoto != null) {
                        viewModel.uploadBitmapImage(capturedPhoto!!, "job_proofs") { result ->
                            result.onSuccess { url ->
                                proceedWithAtomicCompletion(url)
                            }.onFailure { err ->
                                isSubmitting = false
                                photoUploadFailedAlert = err.localizedMessage ?: "Network error uploading photo proof."
                            }
                        }
                    } else {
                        proceedWithAtomicCompletion("")
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
    val adminPhone by viewModel.adminPhone.collectAsState()

    var name by remember { mutableStateOf("Technician Account") }
    var email by remember { mutableStateOf(auth.currentUser?.email ?: "tech@coolconnect.com") }
    var streetAddress by remember { mutableStateOf("") }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showManualPrivacy by remember { mutableStateOf(false) }
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
            if (uri != null) {
                when (val validation = ImageUtils.validateImageUri(context, uri)) {
                    is ImageUtils.ValidationResult.Success -> {
                        viewModel.uploadKycDoc(uri, context)
                    }
                    is ImageUtils.ValidationResult.Error -> {
                        Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
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
                    subtitle = streetAddress.ifBlank { "Set service center dispatch location" },
                    onClick = { showAddressDialog = true }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
                    icon = Icons.Default.Lock,
                    title = "Change Password",
                    onClick = {
                        if (email.isNotBlank()) {
                            auth.sendPasswordResetEmail(email)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(context, "Failed to send reset email: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        } else {
                            Toast.makeText(context, "No registered email address found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
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
                    subtitle = "Manage app system notifications",
                    onClick = {
                        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        }
                        context.startActivity(intent)
                    }
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
                    subtitle = if (!adminPhone.isNullOrBlank()) "Direct phone line: $adminPhone" else "Dial central administration line",
                    onClick = {
                        val phone = adminPhone
                        if (!phone.isNullOrBlank()) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                            context.startActivity(intent)
                        } else {
                            Toast.makeText(context, "Admin dispatch phone line is currently unavailable", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                TechProfileOptionRow(
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                    title = "Log Out",
                    subtitle = "Sign out of your technician account",
                    onClick = { showLogoutDialog = true }
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

// 📍 LOCATION PERMISSION DIALOG
@Composable
fun LocationPermissionDialog(
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
                    text = "CoolConnect Field Tech requires GPS location to receive real-time service dispatches, navigate to customer sites, and broadcast en-route updates.",
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
                            text = "Turn on GPS and allow location permission to stay online and receive active service calls.",
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
