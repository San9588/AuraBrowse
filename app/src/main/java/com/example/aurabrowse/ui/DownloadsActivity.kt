package com.example.aurabrowse.ui

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class DownloadsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState);         setContent { AuraBrowseTheme { DownloadsScreen(this) } } }
}
@Composable private fun DownloadsScreen(context: Context) {
    val manager = remember { context.getSystemService(DownloadManager::class.java) }
    var count by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { count = manager.query(DownloadManager.Query()).count }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp).statusBarsPadding()) { Text("downloads", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.height(16.dp)); Text(if (count == 0) "no downloads yet" else "$count downloads") }
}
