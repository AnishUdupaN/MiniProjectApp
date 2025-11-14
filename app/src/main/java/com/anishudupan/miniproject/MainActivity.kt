package com.anishudupan.miniproject

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anishudupan.miniproject.ui.theme.MiniProjectTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            MiniProjectTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(onLoginSuccess = {
                            navController.navigate("security") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
                    }
                    composable("security") {
                        SecurityCheckScreen(onChecksCompleted = {
                            navController.navigate("main") {
                                popUpTo("security") { inclusive = true }
                            }
                        })
                    }
                    composable("main") {
                        MiniProjectApp(onLogout = {
                            navController.navigate("login") {
                                popUpTo("main") { inclusive = true }
                            }
                        })
                    }
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun MiniProjectApp(onLogout: () -> Unit = {}) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                if (destination != AppDestinations.SECURITY) { // Exclude security from the nav bar
                    item(
                        icon = {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label
                            )
                        },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }
            item(
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout") },
                label = { Text("Logout") },
                selected = false,
                onClick = onLogout
            )
        }
    ) { 
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> Greeting(
                    name = "Android",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.FAVORITES -> Greeting(
                    name = "Favorites",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.PROFILE -> Greeting(
                    name = "Profile",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.SECURITY -> { /* This is now handled by the initial check */ }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
    SECURITY("Security", Icons.Filled.VerifiedUser),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MiniProjectTheme {
        Greeting("Android")
    }
}
