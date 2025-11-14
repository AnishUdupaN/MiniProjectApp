package com.anishudupan.miniproject

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.anishudupan.miniproject.ui.theme.MiniProjectTheme
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class LoginResponse(val login: String, val error: String?)

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var hostname by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    Log.i("KtorLogger", message)
                }
            }
            level = LogLevel.ALL
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = hostname,
            onValueChange = { hostname = it },
            label = { Text("Hostname (e.g., 127.0.0.1:8000)") }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            Log.d("LoginScreen", "Login button clicked")
            coroutineScope.launch {
                try {
                    val response: LoginResponse = client.post("http://$hostname/login") {
                        contentType(ContentType.Application.Json)
                        setBody(LoginRequest(username, password))
                    }.body()

                    if (response.login == "pass") {
                        AppConfig.hostname = hostname
                        onLoginSuccess()
                    } else {
                        errorMessage = response.error ?: "Unknown login error"
                    }
                } catch (e: Exception) {
                    errorMessage = "Could not connect to server: ${e.message}"
                    Log.e("LoginScreen", "Login failed", e)
                }
            }
        }) {
            Text("Login")
        }
        errorMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = androidx.compose.ui.graphics.Color.Red)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    MiniProjectTheme {
        LoginScreen(onLoginSuccess = {})
    }
}
