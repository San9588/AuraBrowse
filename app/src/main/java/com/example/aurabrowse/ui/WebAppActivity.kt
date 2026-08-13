package com.example.aurabrowse.ui

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class WebAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: return
        val title = intent.getStringExtra("title").orEmpty()
        setContent { AuraBrowseTheme { WebAppScreen(url, title) } }
    }
}

@Composable private fun WebAppScreen(url: String, appTitle: String) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(appTitle) }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable {
                    (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
                })
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            }
        }
        AndroidView(factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = userAgentString.replace("; wv", "")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, currentUrl: String?) { title = view.title ?: appTitle }
                }
                loadUrl(url)
            }
        }, modifier = Modifier.fillMaxSize())
    }
}
