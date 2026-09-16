package com.example.coolconnect

import androidx.compose.animation.core.*
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
import coil.compose.rememberAsyncImagePainter

@Composable
fun ProfileImageSelector(
    imageUrl: String?,
    onImageSelected: (android.net.Uri) -> Unit,
    modifier: Modifier = Modifier,
    placeholderIcon: ImageVector = Icons.Default.Person,
    isLoading: Boolean = false
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { onImageSelected(it) }
    }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(PrimaryBlue.copy(alpha = 0.1f))
            .clickable(enabled = !isLoading) { launcher.launch("image/*") },
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
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
