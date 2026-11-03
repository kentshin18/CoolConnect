package com.example.coolconnect

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coolconnect.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // System-Wide Permissions Requester (Notifications, GPS Location, Camera, Gallery/Storage)
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        requestPermissions(permissions.toTypedArray(), 1001)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val navController = rememberNavController()

    val prefs = remember { context.getSharedPreferences("coolconnect_prefs", Context.MODE_PRIVATE) }
    val hasSeenOnboarding = remember { prefs.getBoolean("has_seen_onboarding", false) }

    val startDestination = remember {
        if (!hasSeenOnboarding) {
            "landing"
        } else {
            "login"
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("landing") {
            LandingScreen { navController.navigate("onboarding") }
        }
        composable("onboarding") {
            OnboardingScreen(
                onComplete = {
                    prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                    navController.navigate("login") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            )
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate(role) {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("customer") {
            CustomerScreen(onLogout = {
                navController.navigate("login") {
                    popUpTo("customer") { inclusive = true }
                }
            })
        }
        composable("technician") {
            TechnicianScreen(onLogout = {
                navController.navigate("login") {
                    popUpTo("technician") { inclusive = true }
                }
            })
        }
        composable("admin") {
            AdminScreen(onLogout = {
                navController.navigate("login") {
                    popUpTo("admin") { inclusive = true }
                }
            })
        }
    }
}
