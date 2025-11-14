package com.anishudupan.miniproject

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val client = HttpClient(Android) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
            val response: List<FileInfo> = client.get("http://${AppConfig.hostname}/listfiles?username=${AppConfig.username}&device_id=${AppConfig.deviceId}").body()
            regularFiles = response.filter { it.viewtype == "normal" }
            viewOnceFiles = response.filter { it.viewtype == "onetime" }
        } catch (e: Exception) {
            errorMessage = "Failed to fetch files: ${e.message}"
            showErrorDialog = true
        }
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

    if (showErrorDialog) {
        val context = LocalContext.current
        val activity = context.findActivity()
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("Error") },
            text = { Text(errorMessage ?: "An unknown error occurred.") },
            confirmButton = { Button(onClick = { activity?.finish() }) { Text("Exit") } }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) return currentContext
        currentContext = currentContext.baseContext
    }
    return null
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    REGULAR_DOCUMENTS("Regular", Icons.Default.Folder),
    VIEW_ONCE_DOCUMENTS("View Once", Icons.Default.RemoveRedEye),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularFilesScreen(files: List<FileInfo>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            Card(
                onClick = { /* TODO */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = file.filename,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = file.viewtype,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewOnceFilesScreen(files: List<FileInfo>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            Card(
                onClick = { /* TODO */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = file.filename,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = file.viewtype,
                        modifier = Modifier.align(Alignment.End)
                    )
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
