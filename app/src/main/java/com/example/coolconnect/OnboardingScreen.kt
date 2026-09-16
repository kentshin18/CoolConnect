package com.example.coolconnect

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coolconnect.ui.theme.*
import kotlinx.coroutines.launch

data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pages = listOf(
        OnboardingPage(
            "Smart HVAC Service",
            "Experience the future of cooling. Expert AC repairs, maintenance, and installations at your fingertips.",
            Icons.Default.AcUnit,
            PrimaryBlue
        ),
        OnboardingPage(
            "Real-Time Dispatch",
            "Track your assigned technician in real-time. Know exactly when help will arrive with live GPS ETA.",
            Icons.Default.Speed,
            PrimaryGreen
        ),
        OnboardingPage(
            "Premium Hardware Shop",
            "Browse genuine parts and smart home equipment. Direct delivery and professional installation guaranteed.",
            Icons.Default.ShoppingCart,
            Color(0xFF7B61FF)
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    var showTerms by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        if (!showTerms) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Skip Button
                Row(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTerms = true }) {
                        Text("Skip", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { position ->
                    OnboardingPageView(pages[position])
                }

                // Bottom Navigation Area
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pager Indicators
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(pages.size) { index ->
                            val isSelected = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryBlue else Color(0xFFE2E8F0))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Next / Start Button
                    Button(
                        onClick = {
                            if (pagerState.currentPage < pages.size - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                showTerms = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Text(
                            text = if (pagerState.currentPage == pages.size - 1) "Get Started" else "Next",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        } else {
            // Terms and Agreements Layer
            TermsAndAgreementScreen(onAgree = onComplete)
        }
    }
}

@Composable
fun OnboardingPageView(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .background(page.color.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = page.color,
                modifier = Modifier.size(100.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black,
                color = TextDark,
                textAlign = TextAlign.Center
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = Color.Gray,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
fun TermsAndAgreementScreen(onAgree: () -> Unit) {
    var accepted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        Icon(
            Icons.Default.Gavel, 
            null, 
            tint = PrimaryBlue, 
            modifier = Modifier.size(48.dp).align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            "Terms & Service Agreement",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Text(
            "Please read carefully before using CoolConnect",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        AgreementSection("1. Data Collection", "CoolConnect collects personal data such as name, phone, and address to facilitate service bookings and field technician dispatch.")
        AgreementSection("2. Location Tracking", "Technicians are tracked in real-time during active jobs to provide customers with accurate ETAs and dispatch efficiency.")
        AgreementSection("3. Service Payments", "Payments for services and parts are processed securely. Digital receipts are generated for every completed transaction.")
        AgreementSection("4. User Conduct", "Users agree to provide accurate information and maintain a respectful environment when communicating with support or technicians.")
        AgreementSection("5. Privacy & Security", "All user data is encrypted and stored securely on Firebase. We do not sell your personal data to third parties.")

        Spacer(modifier = Modifier.height(32.dp))
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { accepted = !accepted }
        ) {
            Checkbox(
                checked = accepted,
                onCheckedChange = { accepted = it },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryBlue)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "I have read and agree to the Terms of Service and Privacy Policy of CoolConnect.",
                fontSize = 12.sp,
                color = TextDark,
                lineHeight = 16.sp
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onAgree,
            enabled = accepted,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Text("Accept & Continue", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AgreementSection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark)
        Spacer(modifier = Modifier.height(4.dp))
        Text(content, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
    }
}
