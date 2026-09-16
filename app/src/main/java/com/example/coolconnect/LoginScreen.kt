package com.example.coolconnect

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import androidx.hilt.navigation.compose.hiltViewModel

data class FeedbackDialogState(
    val isOpen: Boolean = false,
    val title: String = "",
    val message: String = "",
    val icon: ImageVector = Icons.Default.Info,
    val iconColor: Color = Color.Gray
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    var isLoginTab by remember { mutableStateOf(true) }
    var registrationStep by remember { mutableIntStateOf(1) } // 1: Personal Info, 2: Address

    // Account Credentials
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Customer Registration Details
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var street by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("NCR (Metro Manila)") }
    var province by remember { mutableStateOf("Metro Manila") }
    var city by remember { mutableStateOf("") }
    var brgy by remember { mutableStateOf("") }
    var zipCode by remember { mutableStateOf("") }

    var agreeTerms by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var feedbackState by remember { mutableStateOf(FeedbackDialogState()) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current

    // Smart Multi-Step Back Navigation
    BackHandler {
        if (!isLoginTab && registrationStep == 2) {
            registrationStep = 1
        } else {
            onBack()
        }
    }

    fun showFeedback(title: String, message: String, icon: ImageVector, color: Color) {
        feedbackState = FeedbackDialogState(isOpen = true, title = title, message = message, icon = icon, iconColor = color)
    }

    fun translateFirebaseError(e: Throwable?): String {
        val msg = e?.message ?: ""
        return when {
            msg.contains("invalid-credential", ignoreCase = true) || msg.contains("invalid password", ignoreCase = true) ->
                "The email address or password entered is incorrect."
            msg.contains("user-not-found", ignoreCase = true) ->
                "No registered account found with this email."
            msg.contains("email-already-in-use", ignoreCase = true) ->
                "An account with this email address already exists. Please sign in instead."
            msg.contains("network", ignoreCase = true) ->
                "Network connection issue. Please check your internet connection."
            else -> e?.localizedMessage ?: "An unexpected authentication error occurred. Please try again."
        }
    }

    if (feedbackState.isOpen) {
        AlertDialog(
            onDismissRequest = { feedbackState = feedbackState.copy(isOpen = false) },
            icon = { Icon(feedbackState.icon, null, tint = feedbackState.iconColor, modifier = Modifier.size(44.dp)) },
            title = { Text(feedbackState.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark) },
            text = { Text(feedbackState.message, fontSize = 14.sp, color = Color(0xFF64748B)) },
            confirmButton = {
                Button(
                    onClick = { feedbackState = feedbackState.copy(isOpen = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = Color.White
        )
    }

    if (showPrivacyPolicyDialog) {
        PrivacyConsentDialog(
            onConsentGranted = { showPrivacyPolicyDialog = false }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppLightBg)
    ) {
        // Layer 1: Futuristic Ambient Background
        AuthBackground()

        // Layer 2: Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Brand Header
            CoolConnectAuthHeader()

            Spacer(modifier = Modifier.height(20.dp))

            // Welcome Header Title & Subtitle
            Text(
                text = if (isLoginTab) "Welcome Back" else "Create Account",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = TextDark,
                    fontSize = 28.sp
                )
            )
            Text(
                text = if (isLoginTab) "Sign in to access your intelligent platform" else "Join CoolConnect for smart HVAC & home services",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF64748B)),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tab Mode Switcher Segmented Capsule
            AuthModeSwitcher(
                isLoginTab = isLoginTab,
                onTabChanged = {
                    isLoginTab = it
                    registrationStep = 1
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card Container for Form Content
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedContent(
                        targetState = isLoginTab,
                        transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                        label = "form_switch"
                    ) { targetIsLogin ->
                        if (targetIsLogin) {
                            // --- LOGIN FORM ---
                            Column(modifier = Modifier.fillMaxWidth()) {
                                ModernAuthTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = "Email Address",
                                    icon = Icons.Outlined.Email,
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                    onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                PasswordField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "Password",
                                    passwordVisible = passwordVisible,
                                    onToggleVisibility = { passwordVisible = !passwordVisible },
                                    imeAction = ImeAction.Done,
                                    onImeAction = { focusManager.clearFocus() }
                                )

                                // Forgot Password Link
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (email.isBlank()) {
                                                showFeedback("Email Required", "Please enter your email address to receive a password reset link.", Icons.Default.Info, PrimaryBlue)
                                            } else {
                                                viewModel.resetPassword(email.trim()) { result ->
                                                    result.onSuccess {
                                                        showFeedback("Reset Email Sent", "Password reset instructions have been sent to $email", Icons.Default.CheckCircle, PrimaryGreen)
                                                    }.onFailure {
                                                        showFeedback("Reset Failed", translateFirebaseError(it), Icons.Default.Error, Color.Red)
                                                    }
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Forgot Password?", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            // --- REGISTRATION MULTI-STEP FORM ---
                            Column(modifier = Modifier.fillMaxWidth()) {
                                RegistrationProgress(step = registrationStep)

                                Spacer(modifier = Modifier.height(16.dp))

                                if (registrationStep == 1) {
                                    // Step 1: Personal Credentials
                                    ModernAuthTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = "Full Name",
                                        icon = Icons.Outlined.Person,
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next,
                                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    ModernAuthTextField(
                                        value = phone,
                                        onValueChange = { if (it.length <= 11 && it.all { char -> char.isDigit() }) phone = it },
                                        label = "Phone Number",
                                        placeholder = "09123456789",
                                        icon = Icons.Outlined.Phone,
                                        keyboardType = KeyboardType.Phone,
                                        visualTransformation = PhoneVisualTransformation(),
                                        imeAction = ImeAction.Next,
                                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    ModernAuthTextField(
                                        value = email,
                                        onValueChange = { email = it },
                                        label = "Email Address",
                                        icon = Icons.Outlined.Email,
                                        keyboardType = KeyboardType.Email,
                                        imeAction = ImeAction.Next,
                                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    PasswordField(
                                        value = password,
                                        onValueChange = { password = it },
                                        label = "Password (min 6 characters)",
                                        passwordVisible = passwordVisible,
                                        onToggleVisibility = { passwordVisible = !passwordVisible },
                                        imeAction = ImeAction.Done,
                                        onImeAction = { focusManager.clearFocus() }
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))
                                    PasswordStrengthIndicator(password = password)
                                } else {
                                    // Step 2: Service Address Details
                                    Text("SERVICE DISPATCH LOCATION", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF64748B))
                                    Spacer(modifier = Modifier.height(10.dp))

                                    ModernAuthTextField(
                                        value = street,
                                        onValueChange = { street = it },
                                        label = "Street / Building / House No.",
                                        icon = Icons.Outlined.Home,
                                        imeAction = ImeAction.Next,
                                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Hierarchical Address Selectors
                                    ModernAddressDropdown(
                                        label = "Region",
                                        value = region,
                                        options = AddressData.regions,
                                        icon = Icons.Outlined.Map,
                                        onValueChange = { 
                                            region = it
                                            province = ""
                                            city = ""
                                            brgy = ""
                                        }
                                    )

                                    ModernAddressDropdown(
                                        label = "Province",
                                        value = province,
                                        options = AddressData.provinces[region] ?: emptyList(),
                                        icon = Icons.Outlined.Map,
                                        enabled = region.isNotEmpty(),
                                        onValueChange = { 
                                            province = it
                                            city = ""
                                            brgy = ""
                                        }
                                    )

                                    ModernAddressDropdown(
                                        label = "City / Municipality",
                                        value = city,
                                        options = AddressData.cities[province] ?: emptyList(),
                                        icon = Icons.Outlined.LocationCity,
                                        enabled = province.isNotEmpty(),
                                        onValueChange = { 
                                            city = it
                                            brgy = ""
                                        }
                                    )

                                    ModernAddressDropdown(
                                        label = "Barangay",
                                        value = brgy,
                                        options = AddressData.barangays[city] ?: emptyList(),
                                        icon = Icons.Outlined.PinDrop,
                                        enabled = city.isNotEmpty(),
                                        onValueChange = { brgy = it }
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    ModernAuthTextField(
                                        value = zipCode,
                                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) zipCode = it },
                                        label = "Zip Code",
                                        icon = Icons.Outlined.PinDrop,
                                        keyboardType = KeyboardType.Number,
                                        imeAction = ImeAction.Done,
                                        onImeAction = { focusManager.clearFocus() }
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Terms & Conditions Checkbox
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth().clickable { agreeTerms = !agreeTerms }
                                    ) {
                                        Checkbox(
                                            checked = agreeTerms,
                                            onCheckedChange = { agreeTerms = it },
                                            colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column {
                                            Text(
                                                "I agree to the Terms of Service and Privacy Policy",
                                                fontSize = 11.sp,
                                                color = TextDark,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "View Data Privacy Policy & Cookie Usage",
                                                fontSize = 10.sp,
                                                color = PrimaryBlue,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.clickable {
                                                    showPrivacyPolicyDialog = true
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // ACTION BUTTON
                    AuthButton(
                        text = when {
                            isLoginTab -> "Sign In"
                            registrationStep == 1 -> "Next: Address Details →"
                            else -> "Register Account"
                        },
                        isLoading = isLoading,
                        onClick = {
                            focusManager.clearFocus()

                            if (isLoginTab) {
                                // --- LOGIN EXECUTION ---
                                if (email.isBlank() || password.isBlank()) {
                                    showFeedback("Input Missing", "Please enter both email and password.", Icons.Default.Info, PrimaryBlue)
                                    return@AuthButton
                                }

                                isLoading = true
                                viewModel.login(email.trim(), password) { result ->
                                    isLoading = false
                                    result.onSuccess { role ->
                                        onLoginSuccess(role)
                                    }.onFailure {
                                        showFeedback("Authentication Failed", translateFirebaseError(it), Icons.Default.Error, Color.Red)
                                    }
                                }
                            } else {
                                // --- REGISTRATION MULTI-STEP EXECUTION ---
                                if (registrationStep == 1) {
                                    if (name.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
                                        showFeedback("Missing Information", "Please fill in your name, phone, email, and password.", Icons.Default.Info, PrimaryBlue)
                                        return@AuthButton
                                    }
                                    if (!email.contains("@") || !email.contains(".")) {
                                        showFeedback("Invalid Email", "Please enter a valid email address.", Icons.Default.Info, PrimaryBlue)
                                        return@AuthButton
                                    }
                                    if (password.length < 6) {
                                        showFeedback("Weak Password", "Password must be at least 6 characters long.", Icons.Default.Info, PrimaryBlue)
                                        return@AuthButton
                                    }
                                    registrationStep = 2
                                } else {
                                    if (street.isBlank() || brgy.isBlank() || city.isBlank() || province.isBlank() || zipCode.isBlank()) {
                                        showFeedback("Address Incomplete", "Please complete all address fields for service dispatch.", Icons.Default.Info, PrimaryBlue)
                                        return@AuthButton
                                    }
                                    if (!agreeTerms) {
                                        showFeedback("Agreement Required", "Please check and agree to the Terms of Service.", Icons.Default.Info, PrimaryBlue)
                                        return@AuthButton
                                    }

                                    isLoading = true
                                    val fullAddress = "$street, Brgy. $brgy, $city, $province, $zipCode".trim()
                                    val userData = hashMapOf(
                                        "role" to "customer",
                                        "email" to email.trim(),
                                        "name" to name.trim(),
                                        "phone" to phone.trim(),
                                        "street" to street.trim(),
                                        "brgy" to brgy.trim(),
                                        "city" to city.trim(),
                                        "province" to province.trim(),
                                        "postalCode" to zipCode.trim(),
                                        "regionCity" to "${city.trim()}, ${province.trim()}",
                                        "address" to fullAddress
                                    )

                                    viewModel.register(userData, password) { result ->
                                        isLoading = false
                                        result.onSuccess {
                                            showFeedback("Success!", "Account registered! Please sign in with your credentials.", Icons.Default.CheckCircle, PrimaryGreen)
                                            // Clear all fields
                                            name = ""; phone = ""; email = ""; password = ""
                                            street = ""; region = "NCR (Metro Manila)"; province = "Metro Manila"; city = ""; brgy = ""; zipCode = ""
                                            agreeTerms = false
                                            registrationStep = 1
                                            isLoginTab = true
                                        }.onFailure {
                                            showFeedback("Registration Error", translateFirebaseError(it), Icons.Default.Error, Color.Red)
                                        }
                                    }
                                }
                            }
                        }
                    )

                    if (!isLoginTab && registrationStep == 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { registrationStep = 1 }) {
                            Text("← Back to Step 1: Personal Details", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Secure • Smart • Connected Platform", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8), letterSpacing = 1.sp)
        }
    }
}

// 🌌 1. AMBIENT BACKGROUND
@Composable
private fun AuthBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF4F8FF),
                        Color(0xFFEBF3FF),
                        Color(0xFFF8FAFC)
                    )
                )
            )
        }

        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-60).dp)
                .blur(70.dp)
                .background(PrimaryBlue.copy(alpha = 0.12f), CircleShape)
        )
    }
}

// ◉ 2. COMPACT BRAND HEADER
@Composable
private fun CoolConnectAuthHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(PrimaryBlue.copy(0.1f), CircleShape)
                .border(1.dp, PrimaryBlue.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.AcUnit, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = TextDark, fontWeight = FontWeight.Black, fontSize = 22.sp)) { append("Cool") }
                withStyle(SpanStyle(color = PrimaryBlue, fontWeight = FontWeight.Black, fontSize = 22.sp)) { append("Connect") }
            }
        )
    }
}

// 🔀 3. SEGMENTED SWITCHER
@Composable
private fun AuthModeSwitcher(isLoginTab: Boolean, onTabChanged: (Boolean) -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Color(0xFFF1F5F9),
        modifier = Modifier.fillMaxWidth().height(52.dp).padding(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isLoginTab) Color.White else Color.Transparent, RoundedCornerShape(22.dp))
                    .clickable { onTabChanged(true) },
                contentAlignment = Alignment.Center
            ) {
                Text("Sign In", fontWeight = FontWeight.Bold, color = if (isLoginTab) PrimaryBlue else Color(0xFF64748B), fontSize = 14.sp)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (!isLoginTab) Color.White else Color.Transparent, RoundedCornerShape(22.dp))
                    .clickable { onTabChanged(false) },
                contentAlignment = Alignment.Center
            ) {
                Text("Create Account", fontWeight = FontWeight.Bold, color = if (!isLoginTab) PrimaryBlue else Color(0xFF64748B), fontSize = 14.sp)
            }
        }
    }
}

// 📊 4. REGISTRATION STEP PROGRESS BAR
@Composable
private fun RegistrationProgress(step: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("STEP $step OF 2", fontSize = 11.sp, fontWeight = FontWeight.Black, color = PrimaryBlue, letterSpacing = 1.sp)
            Text(if (step == 1) "Personal Credentials" else "Service Address", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (step == 1) 0.5f else 1.0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = PrimaryBlue,
            trackColor = Color(0xFFE2E8F0)
        )
    }
}

// ✏️ 5. MODERN TEXT FIELD
@Composable
private fun ModernAuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = placeholder?.let { { Text(it, fontSize = 13.sp, color = Color.Gray.copy(alpha = 0.5f)) } },
        leadingIcon = { Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = Color(0xFFE2E8F0),
            focusedContainerColor = Color(0xFFFAFAFA),
            unfocusedContainerColor = Color(0xFFFAFAFA)
        )
    )
}

// 🔒 6. PASSWORD FIELD WITH VISIBILITY TOGGLE
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    passwordVisible: Boolean,
    onToggleVisibility: () -> Unit,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Outlined.Lock, null, tint = PrimaryBlue, modifier = Modifier.size(20.dp)) },
        trailingIcon = {
            val icon = if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff
            IconButton(onClick = onToggleVisibility) {
                Icon(icon, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
        },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onImeAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryBlue,
            unfocusedBorderColor = Color(0xFFE2E8F0),
            focusedContainerColor = Color(0xFFFAFAFA),
            unfocusedContainerColor = Color(0xFFFAFAFA)
        )
    )
}

// 📶 7. PASSWORD STRENGTH INDICATOR
@Composable
private fun PasswordStrengthIndicator(password: String) {
    if (password.isBlank()) return

    val lengthOk = password.length >= 6
    val hasNum = password.any { it.isDigit() }
    val isStrong = lengthOk && hasNum

    val strengthColor = when {
        isStrong -> PrimaryGreen
        lengthOk -> Color(0xFFFF9800)
        else -> Color.Red
    }

    val strengthLabel = when {
        isStrong -> "Strong Password"
        lengthOk -> "Medium Strength"
        else -> "Weak (Min 6 chars required)"
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(strengthColor, CircleShape))
        Spacer(modifier = Modifier.width(6.dp))
        Text(strengthLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = strengthColor)
    }
}

// 🚀 8. AUTH BUTTON
@Composable
private fun AuthButton(
    text: String,
    isLoading: Boolean,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "auth_btn_scale"
    )

    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(btnScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(PrimaryBlue, Color(0xFF0056B3))
                    ),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
