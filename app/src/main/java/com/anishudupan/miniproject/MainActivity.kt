package com.anishudupan.miniproject

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import io.ktor.client.plugins.onDownload
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

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

@Serializable
data class GetFileRequest(val username: String, val device_id: String, val filename: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniProjectApp(onLogout: () -> Unit = {}) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.REGULAR_DOCUMENTS) }
    var regularFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var viewOnceFiles by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var showDownloadErrorDialog by remember { mutableStateOf(false) }
    var downloadErrorMessage by remember { mutableStateOf<String?>(null) }
    var fileToRetryDownload by remember { mutableStateOf<FileInfo?>(null) }

    var refreshTrigger by remember { mutableStateOf(0) }
    var fileToView by remember { mutableStateOf<Pair<FileInfo, ByteArray>?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

    val json = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }
    val client = remember {
        HttpClient(Android) {
            install(ContentNegotiation) {
                json(json)
            }
        }
    }

    val downloadAndOpenFile: (FileInfo) -> Unit = { fileInfo ->
        coroutineScope.launch {
            isDownloading = true
            downloadProgress = 0f
            var requestJson = ""
            try {
                val requestBody = GetFileRequest(AppConfig.username!!, AppConfig.deviceId!!, fileInfo.filename)
                requestJson = json.encodeToString(requestBody)
                Log.d("FileDownload", "Request Sent: $requestJson")

                val response: HttpResponse = client.post("http://${AppConfig.hostname}/getfile") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    onDownload { bytesSentTotal, contentLength ->
                        if (contentLength > 0) {
                            downloadProgress = bytesSentTotal.toFloat() / contentLength
                        }
                    }
                }

                if (response.status == HttpStatusCode.OK) {
                    fileToView = fileInfo to response.body()
                } else {
                    val errorBody = response.bodyAsText()
                    Log.e("FileDownload", "Error: ${response.status}, Body: $errorBody")
                    downloadErrorMessage = "Error: ${response.status.value}\n$errorBody"
                    fileToRetryDownload = fileInfo
                    showDownloadErrorDialog = true
                }
            } catch (e: Exception) {
                Log.e("FileDownload", "Failed to download file", e)
                downloadErrorMessage = "Failed to download file: ${e.message}"
                fileToRetryDownload = fileInfo
                showDownloadErrorDialog = true
            } finally {
                isDownloading = false
            }
        }
    }

    LaunchedEffect(refreshTrigger) {
        if (fileToView == null) { // only refresh if not viewing a file
            try {
                val response: List<FileInfo> = client.get("http://${AppConfig.hostname}/listfiles?username=${AppConfig.username}&device_id=${AppConfig.deviceId}").body()
                regularFiles = response.filter { it.viewtype == "normal" }
                viewOnceFiles = response.filter { it.viewtype == "onetime" }
            } catch (e: Exception) {
                errorMessage = "Failed to fetch files: ${e.message}"
                showErrorDialog = true
            }
        }
    }

    val onFileClose: (Boolean) -> Unit = { shouldRetry ->
        val fileToRetry = fileToView?.first
        fileToView = null
        if (shouldRetry && fileToRetry != null) {
            downloadAndOpenFile(fileToRetry)
        } else {
            if (fileToRetry?.viewtype == "onetime") refreshTrigger++
        }
    }

    if (fileToView != null) {
        FileViewerScreen(
            fileInfo = fileToView!!.first,
            fileContent = fileToView!!.second,
            onClose = onFileClose
        )
    } else {
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
                Column {
                    if (isDownloading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            progress = downloadProgress
                        )
                    }
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
            }
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.REGULAR_DOCUMENTS -> RegularFilesScreen(
                    files = regularFiles,
                    onFileClick = { downloadAndOpenFile(it) },
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.VIEW_ONCE_DOCUMENTS -> ViewOnceFilesScreen(
                    files = viewOnceFiles,
                    onFileClick = { downloadAndOpenFile(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
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

    if (showDownloadErrorDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadErrorDialog = false },
            title = { Text("Download Failed") },
            text = { Text(downloadErrorMessage ?: "An unknown error occurred.") },
            confirmButton = {
                Button(onClick = {
                    showDownloadErrorDialog = false
                    fileToRetryDownload?.let { downloadAndOpenFile(it) }
                }) { Text("Try Again") }
            },
            dismissButton = {
                Button(onClick = { showDownloadErrorDialog = false }) { Text("OK") }
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

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    REGULAR_DOCUMENTS("Regular", Icons.Default.Folder),
    VIEW_ONCE_DOCUMENTS("View Once", Icons.Default.RemoveRedEye),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegularFilesScreen(files: List<FileInfo>, onFileClick: (FileInfo) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            Card(
                onClick = { onFileClick(file) },
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
fun ViewOnceFilesScreen(files: List<FileInfo>, onFileClick: (FileInfo) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(files) { file ->
            Card(
                onClick = { onFileClick(file) },
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
fun FileViewerScreen(fileInfo: FileInfo, fileContent: ByteArray, onClose: (Boolean) -> Unit) {
    BackHandler { onClose(false) }
    var showPdfErrorDialog by remember { mutableStateOf(false) }
    var showUnsupportedDialog by remember { mutableStateOf(false) }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(fileInfo.filename) },
                navigationIcon = {
                    IconButton(onClick = { onClose(false) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            val fileExtension = fileInfo.filename.substringAfterLast('.', "").lowercase()
            Log.d("FileViewer", "File extension: '$fileExtension'")
            when (fileExtension) {
                "jpg", "jpeg", "png", "webp", "dng" -> {
                    val bitmap = remember(fileContent) { BitmapFactory.decodeByteArray(fileContent, 0, fileContent.size) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = fileInfo.filename,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("Could not display image.", modifier = Modifier.align(Alignment.Center))
                    }
                }
                "pdf" -> {
                    PdfView(
                        fileContent = fileContent, 
                        modifier = Modifier.fillMaxSize(),
                        onError = { 
                            Log.e("FileViewer", "Failed to render PDF", it)
                            showPdfErrorDialog = true
                        }
                    )
                }
                "mp4", "mov", "avi", "webm", "mkv", "pptx", "xlsx" -> {
                    showUnsupportedDialog = true
                }
                else -> {
                    if (fileContent.size < 500_000) { // Less than 0.5 MB
                        val text = remember(fileContent) { fileContent.decodeToString() }
                        LazyColumn(modifier = Modifier.padding(16.dp)) {
                            items(text.lines()) { line ->
                                Text(line)
                            }
                        }
                    } else {
                        showUnsupportedDialog = true
                    }
                }
            }
        }
    }

    if (showPdfErrorDialog) {
        AlertDialog(
            onDismissRequest = { onClose(false) },
            title = { Text("Error") },
            text = { Text("Could not open file. An error occured") },
            confirmButton = {
                Button(onClick = { onClose(true) }) { Text("Try Again") }
            },
            dismissButton = {
                Button(onClick = { onClose(false) }) { Text("OK") }
            }
        )
    }

    if (showUnsupportedDialog) {
        AlertDialog(
            onDismissRequest = { onClose(false) },
            title = { Text("Unsupported File") },
            text = { Text("Cannot open this file type.") },
            confirmButton = {
                Button(onClick = { onClose(false) }) { Text("OK") }
            }
        )
    }
}

@Composable
fun PdfView(fileContent: ByteArray, modifier: Modifier = Modifier, onError: (Exception) -> Unit) {
    val context = LocalContext.current
    val renderer by produceState<PdfRenderer?>(initialValue = null, keys = arrayOf(fileContent)) {
        var tempFile: File? = null
        try {
            withContext(Dispatchers.IO) {
                tempFile = File.createTempFile("temp_pdf_", ".pdf", context.cacheDir).apply {
                    writeBytes(fileContent)
                }
                val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                value = PdfRenderer(pfd)
            }
        } catch (e: Exception) {
            Log.e("PdfView", "Failed to create PdfRenderer", e)
            withContext(Dispatchers.IO) {
                tempFile?.delete()
            }
            onError(e)
            value = null
        }

        awaitDispose {
            val currentRenderer = value
            val temp = tempFile
            CoroutineScope(Dispatchers.IO).launch {
                currentRenderer?.close()
                temp?.delete()
            }
        }
    }

    val currentRenderer = renderer
    if (currentRenderer != null) {
        Box(modifier = modifier) {
            LazyColumn {
                items(currentRenderer.pageCount) { index ->
                    val pageBitmap by produceState<Bitmap?>(initialValue = null, keys = arrayOf(currentRenderer, index)) {
                        withContext(Dispatchers.IO) {
                            val bitmap = synchronized(currentRenderer) {
                                val page = currentRenderer.openPage(index)
                                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                page.close()
                                bmp
                            }
                            value = bitmap
                        }
                    }

                    val bitmap = pageBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "PDF Page ${index + 1}",
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Loading page...")
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading PDF...")
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
