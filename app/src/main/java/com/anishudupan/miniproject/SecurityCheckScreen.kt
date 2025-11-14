package com.anishudupan.miniproject

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.anishudupan.miniproject.ui.theme.MiniProjectTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class CheckRequest(val username: String, val latitude: String, val longitude: String, val altitude: String)

@Serializable
data class CheckResponse(
    @SerialName("Error") val error: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Composable
fun SecurityCheckScreen(onChecksCompleted: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val checks = listOf(
        "Checking Location...",
        "Checking Network...",
        "Checking Device Integrity...",
        "Checking Data Integrity...",
        "Checking App Integrity"
    )
    var completedChecks by remember { mutableStateOf(0) }
    var checkFailed by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    var showFailureDialog by remember { mutableStateOf(false) }
    var showLocationDisabledDialog by remember { mutableStateOf(false) }
    var triggerRecheck by remember { mutableStateOf(false) }

    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasLocationPermission = isGranted
            if (!isGranted) {
                checkFailed = true
                failureMessage = "Location permission is required for security checks."
                showFailureDialog = true
            }
        }
    )

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        triggerRecheck = !triggerRecheck
    }

    LaunchedEffect(hasLocationPermission, triggerRecheck) {
        if (hasLocationPermission) {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

            if (!isLocationEnabled) {
                showLocationDisabledDialog = true
                return@LaunchedEffect
            }

            val devOptionsEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
            if (devOptionsEnabled) {
                completedChecks = checks.indexOf("Checking Device Integrity...") + 1
                checkFailed = true
                failureMessage = "Developer options are enabled. Security check failed."
                showFailureDialog = true
                return@LaunchedEffect
            }

            try {
                @SuppressLint("MissingPermission")
                val location: Location? = suspendCancellableCoroutine { continuation ->
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc: Location? ->
                            if (continuation.isActive) {
                                continuation.resume(loc)
                            }
                        }
                        .addOnFailureListener { e ->
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }
                }

                if (location != null) {
                    val json = Json { 
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                        prettyPrint = true
                    }
                    val client = HttpClient(Android) {
                        install(ContentNegotiation) {
                            json(json)
                        }
                        install(HttpTimeout) { requestTimeoutMillis = 5000 }
                    }
                    val requestBody = CheckRequest(AppConfig.username!!, location.latitude.toString(), location.longitude.toString(), location.altitude.toString())
                    val requestJson = json.encodeToString(requestBody)
                    var responseJson: String? = null

                    try {
                        val httpResponse = client.post("http://${AppConfig.hostname}/check") {
                            contentType(ContentType.Application.Json)
                            setBody(requestBody)
                        }
                        responseJson = httpResponse.bodyAsText()
                        val response: CheckResponse = json.decodeFromString(responseJson)

                        if (response.error == null && response.deviceId != null) {
                            AppConfig.deviceId = response.deviceId
                            completedChecks++
                        } else {
                            checkFailed = true
                            if (response.deviceId == null) {
                                failureMessage = "Location Check Failed. You are not in the set Location. This action has been reported to the admin"
                            } else {
                                failureMessage = (response.error ?: "Invalid response from server.") +
                                        "\n\nSent:\n" + requestJson + "\n\nReceived:\n" + responseJson
                            }
                            showFailureDialog = true
                            return@LaunchedEffect
                        }
                    } catch (e: Exception) {
                        Log.e("SecurityCheckScreen", "Location check failed", e)
                        checkFailed = true
                        var errorDetails = "Location check failed: ${e.message}"
                        errorDetails += "\n\nSent:\n$requestJson"
                        if (responseJson != null) {
                            errorDetails += "\n\nReceived:\n$responseJson"
                        }
                        failureMessage = errorDetails
                        showFailureDialog = true
                        return@LaunchedEffect
                    }
                } else {
                    checkFailed = true
                    failureMessage = "Could not retrieve device location. Please ensure location is enabled and try again."
                    showFailureDialog = true
                    return@LaunchedEffect
                }
            } catch (e: Exception) {
                Log.e("SecurityCheckScreen", "Location check failed", e)
                checkFailed = true
                failureMessage = "Location check failed: ${e.message}"
                showFailureDialog = true
                return@LaunchedEffect
            }

            for (i in 1 until checks.size) {
                delay(800)
                completedChecks++
            }
            onChecksCompleted()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Security Checks...", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 32.dp))

        checks.forEachIndexed { index, check ->
            val isCompleted = index < completedChecks
            val isFailed = checkFailed && index == (completedChecks - 1)

            if (isCompleted || !checkFailed) {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isFailed) {
                        Icon(Icons.Default.Close, "Failed", tint = Color.Red)
                    } else if (isCompleted) {
                        Icon(Icons.Default.Check, "Success", tint = Color.Green)
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Text(if (isFailed) failureMessage ?: "Check Failed" else check, modifier = Modifier.padding(16.dp), color = if (isFailed) Color.Red else Color.Unspecified)
                    }
                }
            }
        }
    }

    if (showFailureDialog) {
        val activity = context.findActivity()
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Security Check Failed") },
            text = { Text(failureMessage ?: "An unknown error occurred.") },
            confirmButton = { Button(onClick = { activity?.finish() }) { Text("Exit") } }
        )
    }

    if (showLocationDisabledDialog) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Location Services Disabled") },
            text = { Text("This app requires location services to be enabled for security verification. Please enable location services in your device settings.") },
            confirmButton = {
                Button(onClick = { 
                    locationSettingsLauncher.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    showLocationDisabledDialog = false 
                }) { Text("Go to Settings") }
            },
            dismissButton = { 
                Button(onClick = { 
                    showLocationDisabledDialog = false
                    (context.findActivity())?.finish() 
                }) { Text("Exit") }
            }
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

@Preview(showBackground = true)
@Composable
fun SecurityCheckScreenPreview() {
    MiniProjectTheme {
        SecurityCheckScreen(onChecksCompleted = {})
    }
}
