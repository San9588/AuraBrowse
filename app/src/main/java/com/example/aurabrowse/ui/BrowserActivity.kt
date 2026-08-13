package com.example.aurabrowse.ui

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aurabrowse.adblock.AdBlocker
import com.example.aurabrowse.core.WebViewPool

class BrowserActivity : ComponentActivity() {
    private val model by viewModels<BrowserViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enable_devtools", false)) WebView.setWebContentsDebuggingEnabled(true); setContent { AuraBrowseTheme { BrowserScreen(model) } } }
}

@Composable private fun AuraBrowseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(background = Color(0xFF101110), surface = Color(0xFF101110), primary = Color(0xFFA9B7FF)) else lightColorScheme(background = Color(0xFFFAF9F7), surface = Color(0xFFFAF9F7), primary = Color(0xFF536DFE))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable private fun BrowserScreen(model: BrowserViewModel) {
    val context = LocalContext.current
    val tabs by model.tabs.collectAsState(); val activeId by model.activeId.collectAsState(); val active = tabs.first { it.id == activeId }; val profiles by model.profiles.collectAsState(); val currentProfile by model.profileId.collectAsState()
    var address by remember(activeId) { mutableStateOf(active.url) }; var showTabs by remember { mutableStateOf(false) }; var showProfiles by remember { mutableStateOf(false) }; var progress by remember { mutableIntStateOf(0) }
    val blocker = remember { AdBlocker(context) }
    val pool = remember { WebViewPool(context, blocker, { url, title -> model.updatePage(url, title); address = url }, { progress = it }) }
    DisposableEffect(Unit) { onDispose { pool.destroy() } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = address, onValueChange = { address = it }, modifier = Modifier.weight(1f).height(56.dp), singleLine = true, shape = RoundedCornerShape(16.dp), placeholder = { Text("search or enter address") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { pool.get(activeId).loadUrl(address.toDestination()) }))
                    TextButton(onClick = { model.bookmarkActive() }) { Text("save") }
                    TextButton(onClick = { pool.get(activeId).reload() }) { Text("refresh") }
                }
                if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().height(1.dp))
            }
        }
        key(activeId) { AndroidView(factory = { pool.get(activeId) }, modifier = Modifier.weight(1f), update = { if (it.url != active.url) it.loadUrl(active.url) }) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pool.get(activeId).goBack() }, enabled = pool.get(activeId).canGoBack()) { Text("back") }
            Spacer(Modifier.weight(1f)); AssistChip(onClick = { showProfiles = true }, label = { Text(profiles.firstOrNull { it.id == currentProfile }?.name ?: "profile") }); AssistChip(onClick = { showTabs = true }, label = { Text("${tabs.size} tabs") }); Button(onClick = { model.addTab(); showTabs = false }, shape = RoundedCornerShape(50)) { Text("+") }
        }
    }
    if (showTabs) TabSheet(tabs, activeId, { model.select(it); showTabs = false }, { model.close(it) }, { model.addTab(); showTabs = false }, { showTabs = false })
    if (showProfiles) ProfileSheet(model, { showProfiles = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun TabSheet(tabs: List<BrowserTab>, active: String, select: (String) -> Unit, close: (String) -> Unit, add: () -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("tabs", style = MaterialTheme.typography.headlineSmall); Spacer(Modifier.weight(1f)); TextButton(onClick = add) { Text("new tab") } }
        LazyColumn(Modifier.padding(horizontal = 16.dp).navigationBarsPadding()) { items(tabs, key = { it.id }) { tab ->
            Surface(onClick = { select(tab.id) }, shape = RoundedCornerShape(14.dp), color = if (tab.id == active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(tab.title, maxLines = 1); Text(tab.url, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { close(tab.id) }) { Text("close") } }
            }
        } }
    }
}

private fun String.toDestination(): String = if (startsWith("http://") || startsWith("https://")) this else if (contains(".") && !contains(" ")) "https://$this" else "https://www.google.com/search?q=${java.net.URLEncoder.encode(this, "UTF-8") }"

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileSheet(model: BrowserViewModel, dismiss: () -> Unit) {
    val profiles by model.profiles.collectAsState()
    val current by model.profileId.collectAsState()
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Text("profiles", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        profiles.forEach { profile ->
            Surface(onClick = { model.switchProfile(profile.id); dismiss() }, shape = RoundedCornerShape(14.dp), color = if (profile.id == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(profile.icon, color = Color(profile.color)); Spacer(Modifier.width(12.dp)); Text(profile.name); if (profile.isDefault) { Spacer(Modifier.weight(1f)); Text("default", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } }
            }
        }
        TextButton(onClick = { model.addProfile() }, modifier = Modifier.padding(16.dp)) { Text("add profile") }
        Spacer(Modifier.navigationBarsPadding())
    }
}
