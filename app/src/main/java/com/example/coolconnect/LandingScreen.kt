package com.example.coolconnect

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

@Composable
fun LandingScreen(onGetStarted: () -> Unit) {
    var showLogo by remember { mutableStateOf(false) }
    var showBrand by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }

    // Choreographed Entrance Sequence
    LaunchedEffect(Unit) {
        delay(250.milliseconds)
        showLogo = true
        delay(400.milliseconds)
        showBrand = true
        delay(300.milliseconds)
        showTagline = true
        delay(300.milliseconds)
        showButton = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppLightBg)
    ) {
        // Layer 1: Futuristic Background
        FuturisticBackground()

        // Layer 2: Main Responsive Content Layout
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isSmallScreen = maxHeight < 650.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.height(if (isSmallScreen) 20.dp else 40.dp))

                // Section 1: Multi-Layer Animated Logo
                AnimatedVisibility(
                    visible = showLogo,
                    enter = fadeIn(animationSpec = tween(1000)) + scaleIn(initialScale = 0.5f)
                ) {
                    CoolConnectLogo()
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Section 2: Branding, Tagline & Value Prop
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedVisibility(
                        visible = showBrand,
                        enter = fadeIn(tween(800)) + slideInVertically { it / 2 }
                    ) {
                        BrandSection()
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AnimatedVisibility(
                        visible = showTagline,
                        enter = fadeIn(tween(800)) + slideInVertically { it / 3 }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Smart Solutions. Seamless Service.",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    fontSize = 16.sp
                                ),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Manage your HVAC services, genuine parts, and expert field dispatch in one intelligent platform.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Section 3: Trust Indicator & Futuristic Button
                AnimatedVisibility(
                    visible = showButton,
                    enter = fadeIn(tween(800)) + slideInVertically { it }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TrustIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        FuturisticGetStartedButton(onGetStarted = onGetStarted)
                    }
                }
            }
        }
    }
}

// 🌌 1. FUTURISTIC LAYERED BACKGROUND COMPOSABLE
@Composable
private fun FuturisticBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Soft Light Atmospheric Gradient
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

        // Top Right Primary Blue Halo Glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-80).dp)
                .blur(80.dp)
                .background(PrimaryBlue.copy(alpha = 0.15f), CircleShape)
        )

        // Center Cyan Accent Glow
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.Center)
                .blur(70.dp)
                .background(Color(0xFF00E5FF).copy(alpha = 0.08f), CircleShape)
        )

        // Bottom Left Soft Blue Halo Glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .blur(80.dp)
                .background(PrimaryBlue.copy(alpha = 0.12f), CircleShape)
        )
    }
}

// 🌀 2. MULTI-LAYER ANIMATED LOGO COMPOSABLE
@Composable
private fun CoolConnectLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim")

    // Outer Glow Pulse
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Vertical Floating Motion
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Orbiting Ring Rotation
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.offset(y = floatOffset.dp)
    ) {
        // Outer Pulsing Glow Circle
        Box(
            modifier = Modifier
                .size(175.dp)
                .scale(pulseScale)
                .background(PrimaryBlue.copy(alpha = 0.08f), CircleShape)
        )

        // Orbiting Tech Canvas Ring
        Canvas(
            modifier = Modifier
                .size(145.dp)
                .rotate(rotationAngle)
        ) {
            drawCircle(
                color = PrimaryBlue.copy(alpha = 0.35f),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 15f), 0f)
                )
            )
        }

        // Inner Glass Card Emblem Container
        Box(
            modifier = Modifier
                .size(115.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Brush.linearGradient(listOf(PrimaryBlue.copy(0.4f), Color(0xFF00E5FF).copy(0.4f))), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(PrimaryBlue.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AcUnit,
                        contentDescription = "Cooling Icon",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Smart Node",
                        tint = Color(0xFF00C853),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// 🔤 3. BRANDING TYPOGRAPHY COMPOSABLE
@Composable
private fun BrandSection() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = TextDark)) {
                    append("Cool")
                }
                withStyle(style = SpanStyle(color = PrimaryBlue)) {
                    append("Connect")
                }
            },
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = (-1.5).sp,
                fontSize = 42.sp
            )
        )
    }
}

// 🛡️ 4. TRUST INDICATOR COMPOSABLE
@Composable
private fun TrustIndicator() {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(PrimaryGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "SMART • FAST • CONNECTED",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF64748B),
                letterSpacing = 1.sp
            )
        }
    }
}

// 🚀 5. FUTURISTIC CTA GET STARTED BUTTON
@Composable
private fun FuturisticGetStartedButton(onGetStarted: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }

    val scaleScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "btn_scale"
    )

    Surface(
        onClick = onGetStarted,
        modifier = Modifier
            .fillMaxWidth(0.88f)
            .height(60.dp)
            .scale(scaleScale)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    }
                )
            },
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        shadowElevation = 6.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            PrimaryBlue,
                            Color(0xFF0056B3)
                        )
                    ),
                    shape = RoundedCornerShape(30.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(0.4f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(30.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Get Started",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Arrow Forward",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
