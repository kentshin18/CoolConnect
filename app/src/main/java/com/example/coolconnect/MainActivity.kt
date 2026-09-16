package com.example.coolconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.coolconnect.ui.theme.MyApplicationTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "landing") {
        composable("landing") {
            LandingScreen { navController.navigate("onboarding") }
        }
        composable("onboarding") {
            OnboardingScreen(onComplete = { navController.navigate("login") })
        }
        composable("login") {
            LoginScreen(
                onLoginSuccess = { role ->
                    navController.navigate(role) {
                        popUpTo("landing") { inclusive = true }
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
