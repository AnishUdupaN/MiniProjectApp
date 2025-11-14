package com.anishudupan.miniproject

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.anishudupan.miniproject.ui.theme.MiniProjectTheme
import kotlinx.coroutines.delay

@Composable
fun SecurityCheckScreen(onChecksCompleted: () -> Unit) {
    val context = LocalContext.current
    val checks = listOf(
        "Checking Location...",
        "Checking Network...",
        "Checking Device Integrity...",
        "Checking Data Integrity...",
        "Checking App Integrity"
    )
    var completedChecks by remember { mutableIntStateOf(0) }
    var deviceIntegrityFailed by remember { mutableStateOf(false) }
    var showFailureDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val devOptionsEnabled = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0
        if (devOptionsEnabled) {
            completedChecks = checks.indexOf("Checking Device Integrity...") + 1
            deviceIntegrityFailed = true
            showFailureDialog = true
            return@LaunchedEffect // Stop further checks
        }

        for (i in checks.indices) {
            delay(800) // 4 seconds / 5 checks = 0.8 seconds per check
            completedChecks = i + 1
        }
        onChecksCompleted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Security Checks...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        checks.forEachIndexed { index, check ->
            val isCompleted = index < completedChecks
            val isFailed = deviceIntegrityFailed && index == (completedChecks - 1)

            if (isCompleted || !deviceIntegrityFailed) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isFailed) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Failed",
                            tint = Color.Red
                        )
                    } else if (isCompleted) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = Color.Green
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = if (isFailed) "Device Integrity Check Failed" else check,
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
            onDismissRequest = { /* Prevent dismissing by clicking outside */ },
            title = { Text(text = "Security Check Failed") },
            text = { Text(text = "Your device did not pass the security checks.") },
            confirmButton = {
                Button(
                    onClick = { activity?.finish() }
                ) {
                    Text("Exit")
                }
            }
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
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
