package com.anishudupan.miniproject

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.anishudupan.miniproject.ui.theme.MiniProjectTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
data class FileInfo(val filename: String, val viewtype: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniProjectApp(onLogout: () -> Unit = {}) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.REGULAR_DOCUMENTS) }
    var regularFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var viewOnceFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        val client = HttpClient(Android) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val response: List<FileInfo> = client.get("http://${AppConfig.hostname}/listfiles?username=${AppConfig.username}&device_id=${AppConfig.deviceId}").body()
        regularFiles = response.filter { it.viewtype == "normal" }
        viewOnceFiles = response.filter { it.viewtype == "onetime" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentDestination.label) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { destination ->
                    NavigationBarItem(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentDestination) {
            AppDestinations.REGULAR_DOCUMENTS -> RegularFilesScreen(
                files = regularFiles,
                modifier = Modifier.padding(innerPadding)
            )
            AppDestinations.VIEW_ONCE_DOCUMENTS -> ViewOnceFilesScreen(
                files = viewOnceFiles,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    REGULAR_DOCUMENTS("Regular", Icons.Default.Folder),
    VIEW_ONCE_DOCUMENTS("View Once", Icons.Default.RemoveRedEye),
}

@Composable
fun RegularFilesScreen(files: List<FileInfo>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn {
            items(files) { file ->
                Button(onClick = { /* TODO */ }) {
                    Text(file.filename)
                }
            }
        }
    }
}

@Composable
fun ViewOnceFilesScreen(files: List<FileInfo>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LazyColumn {
            items(files) { file ->
                Button(onClick = { /* TODO */ }) {
                    Text(file.filename)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MiniProjectTheme {
        MiniProjectApp()
    }
}
