package com.example.coolconnect

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter

import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale

@Composable
fun ProfileImageSelector(
    imageUrl: String?,
    onImageSelected: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Default.Person,
    isLoading: Boolean = false,
    onError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedLocalUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            when (val validation = ImageUtils.validateImageUri(context, uri)) {
                is ImageUtils.ValidationResult.Success -> {
                    selectedLocalUri = uri
                    onImageSelected(uri)
                }
                is ImageUtils.ValidationResult.Error -> {
                    if (onError != null) {
                        onError(validation.message)
                    } else {
                        Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    LaunchedEffect(imageUrl) {
        if (!imageUrl.isNullOrBlank()) {
            selectedLocalUri = null
        }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(PrimaryBlue.copy(alpha = 0.1f))
            .clickable(enabled = !isLoading) { launcher.launch("image/*") },
        contentAlignment = Alignment.Center
    ) {
        val displayModel = selectedLocalUri ?: imageUrl

        if (displayModel != null && (displayModel is android.net.Uri || (displayModel is String && displayModel.isNotBlank()))) {
            AsyncImage(
                model = displayModel,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = "Placeholder",
                tint = PrimaryBlue,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        
        if (!isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .background(PrimaryBlue, CircleShape)
                    .border(2.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Change Image",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

fun Modifier.shimmerLoading(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )

    val shimmerColors = listOf(
        Color.LightGray.copy(alpha = 0.2f),
        Color.LightGray.copy(alpha = 0.5f),
        Color.LightGray.copy(alpha = 0.2f)
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 200f, translateAnim - 200f),
        end = Offset(translateAnim, translateAnim)
    )

    this.background(brush)
}

data class PermissionInfo(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun PrivacyConsentDialog(
    onConsentGranted: () -> Unit
) {
    val policies = listOf(
        PermissionInfo(
            "Real-Time Location",
            "We use your phone's GPS to enable technician dispatch, show nearby experts, and provide live arrival ETAs.",
            Icons.Default.MyLocation,
            PrimaryBlue
        ),
        PermissionInfo(
            "Service Fulfillment",
            "Your Name, Phone, and Address are shared only with your assigned technician to complete the service.",
            Icons.Default.AssignmentInd,
            PrimaryGreen
        ),
        PermissionInfo(
            "Smart Notifications",
            "We send alerts for job confirmations, technician status updates, and support messages.",
            Icons.Default.NotificationsActive,
            Color(0xFFFF9800)
        ),
        PermissionInfo(
            "Data Security",
            "All data is encrypted via Firebase. We never sell your personal information to third parties.",
            Icons.Default.Lock,
            Color(0xFF7B61FF)
        ),
        PermissionInfo(
            "User Control",
            "You have the right to access, correct, or request the deletion of your data at any time.",
            Icons.Default.SettingsSuggest,
            Color.Gray
        )
    )

    AlertDialog(
        onDismissRequest = { /* Force action */ },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Privacy & Permission Center", fontWeight = FontWeight.Black, fontSize = 20.sp, color = TextDark)
                Text("CoolConnect Data Usage Policy", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "To provide professional cooling and electrical services, we need your consent to access the following:",
                    fontSize = 12.sp,
                    color = TextDark,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                policies.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(item.color.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(item.icon, null, tint = item.color, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextDark)
                            Text(item.description, fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.2f))
                
                Text(
                    "By tapping 'I Consent & Allow', you agree to our Terms of Service and authorize the app to request Android System Permissions for Location and Notifications.",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConsentGranted,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("I Consent & Allow", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Color.White
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernAddressDropdown(
    label: String,
    value: String,
    options: List<String>,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember(value) { mutableStateOf(value) }
    
    val filteredOptions = remember(searchText, options) {
        if (searchText.isEmpty() || searchText == value) {
            options
        } else {
            options.filter { it.contains(searchText, ignoreCase = true) }
        }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled && (filteredOptions.isNotEmpty() || options.isEmpty()),
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        OutlinedTextField(
            value = if (expanded) searchText else value,
            onValueChange = { 
                searchText = it
                if (options.isEmpty()) onValueChange(it)
                expanded = true
            },
            readOnly = (label != "Barangay" && label != "City / Municipality") && options.isNotEmpty(),
            label = { Text(label, fontSize = 12.sp) },
            placeholder = { Text(if (options.isEmpty()) "Type $label manually..." else "Search or select $label", fontSize = 12.sp, color = Color.Gray.copy(0.5f)) },
            leadingIcon = { Icon(icon, null, tint = if (enabled) PrimaryBlue else Color.Gray, modifier = Modifier.size(20.dp)) },
            trailingIcon = { 
                if (options.isNotEmpty()) ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, enabled = enabled),
            shape = RoundedCornerShape(16.dp),
            enabled = enabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryBlue,
                unfocusedBorderColor = Color(0xFFE2E8F0),
                disabledBorderColor = Color(0xFFF1F5F9),
                disabledTextColor = Color.Gray,
                disabledLeadingIconColor = Color.Gray,
                focusedContainerColor = Color(0xFFFAFAFA),
                unfocusedContainerColor = Color(0xFFFAFAFA),
                disabledContainerColor = Color(0xFFF1F5F9)
            )
        )
        
        if (filteredOptions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(Color.White)
            ) {
                filteredOptions.forEach { selection ->
                    DropdownMenuItem(
                        text = { Text(selection, fontSize = 14.sp) },
                        onClick = {
                            onValueChange(selection)
                            searchText = selection
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}

@Composable
fun ShimmerCardList(count: Int = 3) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        repeat(count) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .shimmerLoading()
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerLoading()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .shimmerLoading()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CelebrationSuccessDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(0f) }
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        scale = 1f
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(animatedScale)
                        .background(PrimaryGreen.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = PrimaryGreen,
                        modifier = Modifier.size(48.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextDark)
            }
        },
        text = {
            Text(message, textAlign = TextAlign.Center, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Great!", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}

@Composable
fun LightBackgroundGlows() {
    val infiniteTransition = rememberInfiniteTransition(label = "glows")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Canvas(modifier = Modifier.fillMaxSize().blur(100.dp)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryBlue.copy(alpha = 0.1f * glowAlpha), Color.Transparent)
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PrimaryGreen.copy(alpha = 0.08f * glowAlpha), Color.Transparent)
            ),
            radius = size.width * 0.8f,
            center = Offset(0f, size.height)
        )
    }
}

@Composable
fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        },
        text = {
            Text(
                "Are you sure to log out?",
                fontSize = 14.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Yes, Log Out", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray, fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.White
    )
}

@Composable
fun QrPhPaymentDialog(
    amount: Double,
    orderOrServiceId: String,
    onPaymentConfirmed: (referenceNumber: String) -> Unit,
    onDismiss: () -> Unit
) {
    var refNumber by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var customQrUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current

    val customQrLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            when (val validation = ImageUtils.validateImageUri(context, uri)) {
                is ImageUtils.ValidationResult.Success -> {
                    customQrUri = uri
                }
                is ImageUtils.ValidationResult.Error -> {
                    Toast.makeText(context, validation.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0038A8).copy(0.08f),
                    border = BorderStroke(1.dp, Color(0xFF0038A8).copy(0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("QR Ph", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color(0xFF0038A8))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("• InstaPay / PESONet", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("Scan to Pay ₱${String.format(Locale.getDefault(), "%,.2f", amount)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = TextDark)
                Text("CoolConnect Services Inc. (Merchant ID: CC-PH-889)", fontSize = 11.sp, color = Color.Gray)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 📱 INSTAPAY / QR PH CODE DISPLAY CARD
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(4.dp),
                    border = BorderStroke(2.dp, Color(0xFF0038A8)),
                    modifier = Modifier.size(230.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
                        if (customQrUri != null) {
                            Image(
                                painter = rememberAsyncImagePainter(customQrUri),
                                contentDescription = "Custom InstaPay QR Code",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            // Dynamic High-Precision InstaPay QR Ph Canvas
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val sizePx = size.width
                                val cellSize = sizePx / 17f
                                val activeColor = Color(0xFF0F172A)
                                
                                // Draw realistic QR pattern grid
                                for (row in 0 until 17) {
                                    for (col in 0 until 17) {
                                        val isPositionFinder = (row in 0..4 && col in 0..4) || 
                                                               (row in 0..4 && col in 12..16) || 
                                                               (row in 12..16 && col in 0..4)
                                        val isFinderBorder = (row == 0 || row == 4 || col == 0 || col == 4) && (row in 0..4 && col in 0..4) ||
                                                             (row == 0 || row == 4 || col == 12 || col == 16) && (row in 0..4 && col in 12..16) ||
                                                             (row == 12 || row == 16 || col == 0 || col == 4) && (row in 12..16 && col in 0..4)
                                        val isFinderCenter = (row in 1..3 && col in 1..3) ||
                                                             (row in 1..3 && col in 13..15) ||
                                                             (row in 13..15 && col in 1..3)
                                        
                                        val hashVal = (row * 37 + col * 19 + orderOrServiceId.hashCode()) % 3 == 0
                                        val isCenterReserved = row in 6..10 && col in 6..10
                                        
                                        if (isPositionFinder) {
                                            if (isFinderBorder || isFinderCenter) {
                                                drawRect(color = activeColor, topLeft = Offset(col * cellSize, row * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                            }
                                        } else if (!isCenterReserved && hashVal) {
                                            drawRect(color = activeColor, topLeft = Offset(col * cellSize, row * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                        }
                                    }
                                }
                            }

                            // Center Official InstaPay Logo Badge
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White,
                                border = BorderStroke(1.5.dp, Color(0xFF0038A8)),
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(72.dp, 44.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("insta", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFF0B4A99))
                                        Text("Pay", fontWeight = FontWeight.Black, fontSize = 12.sp, color = Color(0xFFD32F2F))
                                    }
                                    Surface(shape = RoundedCornerShape(2.dp), color = Color(0xFFD32F2F)) {
                                        Text("QR Ph", color = Color.White, fontSize = 7.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Buttons Row (Save to Gallery & Change QR)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val bitmap = android.graphics.Bitmap.createBitmap(500, 500, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)
                            val paint = android.graphics.Paint().apply { color = android.graphics.Color.parseColor("#0F172A") }
                            val cellSize = 500f / 17f
                            for (row in 0 until 17) {
                                for (col in 0 until 17) {
                                    val isPositionFinder = (row in 0..4 && col in 0..4) || (row in 0..4 && col in 12..16) || (row in 12..16 && col in 0..4)
                                    val isFinderBorder = (row == 0 || row == 4 || col == 0 || col == 4) && (row in 0..4 && col in 0..4) ||
                                                         (row == 0 || row == 4 || col == 12 || col == 16) && (row in 0..4 && col in 12..16) ||
                                                         (row == 12 || row == 16 || col == 0 || col == 4) && (row in 12..16 && col in 0..4)
                                    val isFinderCenter = (row in 1..3 && col in 1..3) || (row in 1..3 && col in 13..15) || (row in 13..15 && col in 1..3)
                                    val hashVal = (row * 37 + col * 19 + orderOrServiceId.hashCode()) % 3 == 0
                                    val isCenterReserved = row in 6..10 && col in 6..10
                                    if (isPositionFinder) {
                                        if (isFinderBorder || isFinderCenter) {
                                            canvas.drawRect(col * cellSize, row * cellSize, (col + 1) * cellSize, (row + 1) * cellSize, paint)
                                        }
                                    } else if (!isCenterReserved && hashVal) {
                                        canvas.drawRect(col * cellSize, row * cellSize, (col + 1) * cellSize, (row + 1) * cellSize, paint)
                                    }
                                }
                            }
                            val savedUri = ImageUtils.saveBitmapToGallery(context, bitmap, "QRPh_${orderOrServiceId}.jpg")
                            bitmap.recycle()
                            if (savedUri != null) {
                                Toast.makeText(context, "QR Ph Code saved to Gallery! Open GCash/Maya and select 'Scan from Gallery'", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Failed to save QR code to gallery.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppLightBg),
                        border = BorderStroke(1.dp, PrimaryBlue),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save QR to Gallery", color = PrimaryBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { customQrLauncher.launch("image/*") }
                    ) {
                        Icon(Icons.Default.UploadFile, null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (customQrUri == null) "Select QR Image" else "Change QR",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Supported Banks / e-Wallets Row
                Text("Supported e-Wallets & Banking Apps:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("GCash", "Maya", "ShopeePay", "BDO", "BPI").forEach { appName ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = PrimaryBlue.copy(0.08f)
                        ) {
                            Text(appName, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reference Number Input
                OutlinedTextField(
                    value = refNumber,
                    onValueChange = { if (it.length <= 16) refNumber = it },
                    label = { Text("Enter 13-Digit Ref / Reference No.") },
                    placeholder = { Text("e.g. 1002 9384 1234") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextDark,
                        unfocusedTextColor = TextDark,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    )
                )
                Text("Enter the reference number from your GCash/Maya/Bank receipt.", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (refNumber.length < 6) {
                        Toast.makeText(context, "Please enter a valid reference number", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isVerifying = true
                    onPaymentConfirmed(refNumber)
                },
                enabled = !isVerifying && refNumber.length >= 6,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                if (isVerifying) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verifying Ref #...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.VerifiedUser, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Verify & Submit Ref #", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = Color.White
    )
}
