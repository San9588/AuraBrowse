package com.example.aurabrowse.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aurabrowse.adblock.AdBlocker
import com.example.aurabrowse.core.WebViewPool

class BrowserActivity : ComponentActivity() {
    private val model by viewModels<BrowserViewModel>()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enable_devtools", false)) WebView.setWebContentsDebuggingEnabled(true)
        setContent { AuraBrowseTheme { BrowserScreen(model) } }
    }
}

@Composable private fun AuraBrowseTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(background = Color(0xFF0C0D0D), surface = Color(0xFF171817), primary = Color(0xFF70B8FF), onBackground = Color(0xFFF4F3F0)) else lightColorScheme(background = Color(0xFFF7F7F5), surface = Color.White, primary = Color(0xFF087EDB))
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable private fun BrowserScreen(model: BrowserViewModel) {
    val context = LocalContext.current
    val tabs by model.tabs.collectAsState(); val activeId by model.activeId.collectAsState(); val active = tabs.first { it.id == activeId }
    val profiles by model.profiles.collectAsState(); val currentProfile by model.profileId.collectAsState()
    var address by remember(activeId, active.url) { mutableStateOf(if (active.url == "about:home") "" else active.url) }
    var progress by remember { mutableIntStateOf(0) }; var showTabPage by remember { mutableStateOf(false) }; var showProfiles by remember { mutableStateOf(false) }; var dialogTab by remember { mutableStateOf<BrowserTab?>(null) }
    val blocker = remember { AdBlocker(context) }
    val pool = remember { WebViewPool(context, blocker, { url, title -> model.updatePage(url, title); address = url }, { progress = it }) }
    DisposableEffect(Unit) { onDispose { pool.destroy() } }
    val open: (String) -> Unit = { raw -> val url = raw.toDestination(); address = url; model.navigate(url); pool.get(activeId).loadUrl(url) }

    if (showTabPage) {
        TabPage(tabs, activeId, model, onBack = { showTabPage = false }, onDialog = { dialogTab = it })
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (active.url == "about:home") {
                HomePage(address, currentProfile, profiles.firstOrNull { it.id == currentProfile }?.name ?: "profile", Modifier.weight(1f), { address = it }, { open(it) }, { showProfiles = true }, { context.startActivity(Intent(context, SettingsActivity::class.java)) })
            } else {
                Omnibox(address, { address = it }, { open(address) }, { pool.get(activeId).reload() })
                if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().height(1.dp))
                key(activeId) { AndroidView(factory = { pool.get(activeId) }, modifier = Modifier.weight(1f), update = { if (it.url != active.url) it.loadUrl(active.url) }) }
            }
            TabPillRow(tabs, activeId, model, onDialog = { dialogTab = it })
            BottomBar(activeId, tabs.size, { if (tabs.size > 1) model.close(activeId) }, { showTabPage = true }, { model.bookmarkActive() }, { context.startActivity(Intent(context, SettingsActivity::class.java)) })
        }
    }
    if (showProfiles) ProfileSheet(model, { showProfiles = false })
    dialogTab?.let { tab -> TabActionsDialog(tab, model, { dialogTab = null }) }
}

@Composable private fun HomePage(address: String, profile: String, profileName: String, modifier: Modifier, onAddress: (String) -> Unit, open: (String) -> Unit, profileClick: () -> Unit, settings: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = profileClick, shape = RoundedCornerShape(50), color = Color(0xFF071A2B), border = BorderStroke(2.dp, Color(0xFF0089FF))) { Text(profile.take(2).uppercase(), color = Color(0xFF40A9FF), modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
            Text("AuraBrowse", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 16.dp))
            TextButton(onClick = settings) { Text("⚙", style = MaterialTheme.typography.headlineMedium) }
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFE45B32), modifier = Modifier.size(108.dp)) { Box(contentAlignment = Alignment.Center) { Text("✦ ✧", color = Color.White, style = MaterialTheme.typography.headlineMedium) } }
        }
        Spacer(Modifier.height(44.dp))
        Omnibox(address, onAddress, { open(address) }, {})
        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Shortcut("Amazon", "amazon.com", open); Shortcut("YouTube", "youtube.com", open); Shortcut("Gmail", "gmail.com", open)
            Surface(onClick = { onAddress("") }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.width(82.dp)) { Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("+", style = MaterialTheme.typography.headlineSmall); Text("add", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        Text(profileName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 28.dp))
    }
}

@Composable private fun Shortcut(title: String, host: String, open: (String) -> Unit) {
    Surface(onClick = { open(host) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.width(82.dp)) { Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(38.dp).background(Color(0xFF1A1A1A), RoundedCornerShape(12.dp))); Spacer(Modifier.height(8.dp)); Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
}

@Composable private fun Omnibox(value: String, onValue: (String) -> Unit, submit: () -> Unit, refresh: () -> Unit) {
    OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp).height(60.dp), singleLine = true, shape = RoundedCornerShape(30.dp), placeholder = { Text("Search or enter address") }, leadingIcon = { Text("⌕", style = MaterialTheme.typography.headlineMedium) }, trailingIcon = { Text("♩", modifier = Modifier.clickable { submit() }) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { submit() }))
}

@Composable private fun TabPillRow(tabs: List<BrowserTab>, activeId: String, model: BrowserViewModel, onDialog: (BrowserTab) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tabs, key = { it.id }) { tab ->
            val gesture = Modifier.pointerInput(tab.id) { detectVerticalDragGestures(onVerticalDrag = { _, drag -> if (drag < -12) model.close(tab.id); if (drag > 12) onDialog(tab) }) }
            Surface(onClick = { model.select(tab.id) }, shape = RoundedCornerShape(50), color = if (tab.id == activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = gesture) { Text(if (tab.url == "about:home") "New tab" else tab.title.ifBlank { "New page" }, color = if (tab.id == activeId) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
        }
    }
}

@Composable private fun BottomBar(activeId: String, count: Int, close: () -> Unit, tabs: () -> Unit, bookmark: () -> Unit, settings: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = MaterialTheme.typography.headlineLarge); Text("›", style = MaterialTheme.typography.headlineLarge); Surface(onClick = tabs, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Text(count.toString()) } }; Text("♡", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable { bookmark() }); Text("•••", modifier = Modifier.clickable { settings() })
    }
}

@Composable private fun TabPage(tabs: List<BrowserTab>, activeId: String, model: BrowserViewModel, onBack: () -> Unit, onDialog: (BrowserTab) -> Unit) {
    val groups by model.groups.collectAsState()
    var showGroupDialog by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ back") }; Text("tabs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); TextButton(onClick = { model.addTab(); onBack() }) { Text("+ new") } }
        Text("tab groups", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(groups, key = { it.id }) { group -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Color(group.color))) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) { Text(group.name); TextButton(onClick = { model.deleteGroup(group) }) { Text("×") } } } }; item { Surface(onClick = { showGroupDialog = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) { Text("+ new group", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) } } }
        Text("tabs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tabs, key = { it.id }) { tab -> Surface(onClick = { model.select(tab.id); onBack() }, shape = RoundedCornerShape(18.dp), color = if (tab.id == activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, modifier = Modifier.width(180.dp).height(130.dp)) { Column(Modifier.padding(16.dp)) { Text(tab.title); Spacer(Modifier.weight(1f)); Text(tab.url, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
    }
    if (showGroupDialog) NameDialog("new group", "group name", { model.addGroup(it); showGroupDialog = false }, { showGroupDialog = false })
}

@Composable private fun TabActionsDialog(tab: BrowserTab, model: BrowserViewModel, dismiss: () -> Unit) {
    val groups by model.groups.collectAsState()
    var choosingGroup by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(tab.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Action("↗  move to profile") {}; Action("□  open in profile") {}; Action("⊞  add to group") { choosingGroup = true }; if (tab.groupId != null) Action("−  remove from group") { model.assignToGroup(tab.id, null); dismiss() }; Action("⌑  pin tab") {}; Action("♧  share") {}; Action("♡  bookmark") { model.bookmarkActive(); dismiss() }; Action("⟳  refresh") {}; Action("×  close tab") { model.close(tab.id); dismiss() } } }, confirmButton = { TextButton(onClick = dismiss) { Text("done") } })
    if (choosingGroup) {
        AlertDialog(onDismissRequest = { choosingGroup = false }, title = { Text("add to group") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { if (groups.isEmpty()) Text("create a group first") else groups.forEach { group -> TextButton(onClick = { model.assignToGroup(tab.id, group.id); choosingGroup = false; dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(group.name, modifier = Modifier.fillMaxWidth()) } } } }, confirmButton = { TextButton(onClick = { choosingGroup = false }) { Text("cancel") } })
    }
}
@Composable private fun Action(label: String, click: () -> Unit) { Button(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(label, modifier = Modifier.fillMaxWidth()) } }

private fun String.toDestination(): String = if (startsWith("http://") || startsWith("https://")) this else if (contains(".") && !contains(" ")) "https://$this" else "https://www.google.com/search?q=${java.net.URLEncoder.encode(this, "UTF-8") }"

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun ProfileSheet(model: BrowserViewModel, dismiss: () -> Unit) {
    val profiles by model.profiles.collectAsState()
    val current by model.profileId.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Text("profiles", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        profiles.forEach { profile ->
            Surface(onClick = { model.switchProfile(profile.id); dismiss() }, shape = RoundedCornerShape(14.dp), color = if (profile.id == current) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Text(profile.icon, color = Color(profile.color)); Spacer(Modifier.width(12.dp)); Text(profile.name); Spacer(Modifier.weight(1f)); if (profile.isDefault) Text("default", color = MaterialTheme.colorScheme.onSurfaceVariant) else if (profile.id != current) TextButton(onClick = { model.deleteProfile(profile) }) { Text("delete") } }
            }
        }
        TextButton(onClick = { showCreate = true }, modifier = Modifier.padding(16.dp)) { Text("+ create profile") }
        Spacer(Modifier.navigationBarsPadding())
    }
    if (showCreate) NameDialog("create profile", "profile name", { model.addProfile(it); showCreate = false }, { showCreate = false })
}

@Composable private fun NameDialog(title: String, label: String, save: (String) -> Unit, dismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(label) }, singleLine = true) }, dismissButton = { TextButton(onClick = dismiss) { Text("cancel") } }, confirmButton = { TextButton(onClick = { save(name) }) { Text("create") } })
}
