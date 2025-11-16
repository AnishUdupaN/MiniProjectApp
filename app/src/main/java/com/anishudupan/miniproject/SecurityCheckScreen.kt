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
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class CheckRequest(val username: String, val latitude: String, val longitude: String, val altitude: String)

@Serializable
data class CheckResponse(
    @SerialName("Error") val error: String? = null,
    @SerialName("device_id") val deviceId: String? = null
)

@Serializable
data class ShaCheckRequest(val username: String, val sha256: String)

@Serializable
data class ShaCheckResponse(val error: String?)

@Serializable
data class CheckFailedRequest(val username: String, val message: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityCheckScreen(onChecksCompleted: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val coroutineScope = rememberCoroutineScope()

    val checks = listOf(
        "Device Integrity Check",
        "App Integrity Check",
        "Location Check"
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
            } else {
                triggerRecheck = !triggerRecheck
            }
        }
    )

    val locationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        triggerRecheck = !triggerRecheck
    }

    LaunchedEffect(hasLocationPermission, triggerRecheck) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return@LaunchedEffect
        }
        completedChecks = 1 // Location Permission Granted

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

        if (!isLocationEnabled) {
            showLocationDisabledDialog = true
            return@LaunchedEffect
        }
        completedChecks = 2 // Location Services Enabled

        val devOptionsEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        if (devOptionsEnabled) {
            checkFailed = true
            failureMessage = "Developer options are enabled. Security check failed. This action has been reported to the admin."

            coroutineScope.launch {
                try {
                    val client = HttpClient(Android) { install(ContentNegotiation) { json() } }
                    client.post("http://${AppConfig.hostname}/checkfailed") {
                        contentType(ContentType.Application.Json)
                        setBody(CheckFailedRequest(AppConfig.username!!, "Device Integrity Check Failed."))
                    }
                } catch (e: Exception) {
                    Log.e("SecurityCheckScreen", "Failed to report check failure", e)
                }
            }
            showFailureDialog = true
            return@LaunchedEffect
        }
        completedChecks = 3 // Device Integrity Check Passed

        val appSignature = getAppSignature(context)
        if (appSignature == null) {
            checkFailed = true
            failureMessage = "App integrity check failed: Could not get app signature."
            showFailureDialog = true
            return@LaunchedEffect
        }

        try {
            val client = HttpClient(Android) { install(ContentNegotiation) { json() } }
            val response: ShaCheckResponse = client.post("http://${AppConfig.hostname}/shacheck") {
                contentType(ContentType.Application.Json)
                setBody(ShaCheckRequest(AppConfig.username!!, appSignature))
            }.body()

            if (response.error != null) {
                checkFailed = true
                failureMessage = "The app integrity check failed and this incident has been reported to the admin."
                showFailureDialog = true
                return@LaunchedEffect
            }
        } catch (e: Exception) {
            checkFailed = true
            failureMessage = "App integrity check failed: ${e.message}"
            showFailureDialog = true
            return@LaunchedEffect
        }
        completedChecks = 4 // App Integrity Check Passed

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
                    val httpResponse = client.post("http://${AppConfig.hostname}/checklocation") {
                        contentType(ContentType.Application.Json)
                        setBody(requestBody)
                    }
                    responseJson = httpResponse.bodyAsText()
                    val response: CheckResponse = json.decodeFromString(responseJson)

                    if (response.error == null && response.deviceId != null) {
                        AppConfig.deviceId = response.deviceId
                        completedChecks = 5 // Location Check Passed
                        onChecksCompleted()
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
    }

    Scaffold {
        innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Security Checks...", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 32.dp))

            checks.forEachIndexed { index, check ->
                val isCompleted = index < completedChecks
                val isFailed = checkFailed && index == completedChecks
                val currentCheckIsRunning = index == completedChecks && !checkFailed

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFailed) {
                        Icon(Icons.Default.Close, "Failed", tint = Color.Red)
                    } else if (isCompleted) {
                        Icon(Icons.Default.Check, "Success", tint = Color.Green)
                    } else if (currentCheckIsRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else { // Not started yet
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            if (isFailed) failureMessage ?: "Check Failed" else check,
                            modifier = Modifier.padding(16.dp),
                            color = if (isFailed) Color.Red else Color.Unspecified
                        )
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

private fun getAppSignature(context: Context): String? {
    try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        }

        val signatures = packageInfo.signingInfo?.apkContentsSigners
        if (!signatures.isNullOrEmpty()) {
            val signature = signatures[0]
            val messageDigest = MessageDigest.getInstance("SHA-256")
            messageDigest.update(signature.toByteArray())
            val hash = messageDigest.digest()
            return hash.joinToString("") { "%02x".format(it) }
        }
    } catch (e: Exception) {
        Log.e("SecurityCheckScreen", "Failed to get app signature", e)
    }
    return null
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
