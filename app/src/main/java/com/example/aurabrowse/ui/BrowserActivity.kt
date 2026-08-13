package com.example.aurabrowse.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.aurabrowse.core.Settings
import com.example.aurabrowse.core.WebViewPool

class BrowserActivity : ComponentActivity() {
    private val model by viewModels<BrowserViewModel>()
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < 29) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val url = model.active().url
                when {
                    url != "about:home" && url != "about:devtools" -> model.navigate("about:home")
                    model.tabs.value.size > 1 -> model.close(model.activeId.value)
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        if (getSharedPreferences("settings", MODE_PRIVATE).getBoolean("enable_devtools", false)) WebView.setWebContentsDebuggingEnabled(true)
        setContent { AuraBrowseTheme { BrowserScreen(model, onPoolCreated = { poolRef = it }, onPoolDisposed = { poolRef = null }) } }
    }
    private var poolRef: WebViewPool? = null
    override fun onStop() { super.onStop(); poolRef?.pause() }
    override fun onResume() { super.onResume(); poolRef?.resume() }
}

@Composable private fun BrowserScreen(model: BrowserViewModel, onPoolCreated: (WebViewPool) -> Unit, onPoolDisposed: () -> Unit) {
    val context = LocalContext.current
    val tabs by model.tabs.collectAsState(); val activeId by model.activeId.collectAsState(); val active = tabs.firstOrNull { it.id == activeId } ?: tabs.first()
    val settings = remember { Settings(context) }
    val searchEngine by settings.searchEngine.collectAsState(initial = "google")
    val adblockEnabled by settings.adblockEnabled.collectAsState(initial = true)
    val devtoolsEnabled by settings.devtools.collectAsState(initial = false)
    var address by remember(activeId, active.url) { mutableStateOf(if (active.url == "about:home") "" else active.url) }
    val progressByTab = remember { mutableStateMapOf<String, Int>() }
    val progress = progressByTab[activeId] ?: 0
    var showTabPage by remember { mutableStateOf(false) }; var showProfiles by remember { mutableStateOf(false) }; var dialogTab by remember { mutableStateOf<BrowserTab?>(null) }
    val blocker = remember { AdBlocker(context) }
    LaunchedEffect(adblockEnabled) { blocker.enabled = adblockEnabled }
    LaunchedEffect(devtoolsEnabled) { WebView.setWebContentsDebuggingEnabled(devtoolsEnabled) }
    val pool = remember {
        WebViewPool(context, blocker, settings, { tabId, url, title ->
            model.updatePage(tabId, url, title)
            if (tabId == model.activeId.value) address = url
        }, { tabId, p -> progressByTab[tabId] = p }, { model.addTab() }).also(onPoolCreated)
    }
    LaunchedEffect(Unit) { model.closedTabs.collect { id -> pool.close(id); progressByTab.remove(id) } }
    DisposableEffect(Unit) { onDispose { onPoolDisposed(); pool.destroy() } }
    val open: (String) -> Unit = { raw -> val url = raw.toDestination(searchEngine); address = url; model.navigate(url); progressByTab[activeId] = 0; pool.load(activeId, url) }

    if (showTabPage) {
        TabPage(tabs, activeId, model, onBack = { showTabPage = false }, onDialog = { dialogTab = it })
    } else {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (active.url == "about:home") {
                HomePage(address, Modifier.weight(1f), { address = it }, { open(it) }, { context.startActivity(Intent(context, SettingsActivity::class.java)) })
            } else if (active.url == "about:devtools") {
                DevToolsScreen(Modifier.weight(1f))
            } else {
                Omnibox(address, { address = it }, { open(address) }, { pool.reload(activeId) })
                if (progress in 1..99) LinearProgressIndicator({ progress / 100f }, Modifier.fillMaxWidth().height(1.dp))
                key(activeId) {
                    AndroidView(factory = { pool.get(activeId) }, modifier = Modifier.weight(1f), update = { wv ->
                        val target = active.url
                        if (!pool.hasContent(activeId) && target != "about:home" && target != "about:devtools" && target.isNotBlank()) pool.load(activeId, target)
                    })
                }
            }
            TabPillRow(tabs, activeId, model, onDialog = { dialogTab = it })
            BottomBar(activeId, tabs.size, { if (tabs.size > 1) model.close(activeId) }, { showTabPage = true }, { model.bookmarkActive() }, { context.startActivity(Intent(context, SettingsActivity::class.java)) })
        }
    }
    if (showProfiles) ProfileSheet(model, { showProfiles = false })
    dialogTab?.let { tab -> TabActionsDialog(tab, model, onRefresh = { pool.reload(tab.id) }, dismiss = { dialogTab = null }) }
}

@Composable private fun HomePage(address: String, modifier: Modifier, onAddress: (String) -> Unit, open: (String) -> Unit, settings: () -> Unit) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 34.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(48.dp))
            Text("AuraBrowse", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
            Surface(onClick = { model.select(tab.id) }, shape = RoundedCornerShape(50), color = if (tab.id == activeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = gesture.widthIn(max = 148.dp)) { Text(if (tab.url == "about:home") "New tab" else tab.title.ifBlank { "New page" }, color = if (tab.id == activeId) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) }
        }
    }
}

@Composable private fun BottomBar(activeId: String, count: Int, close: () -> Unit, tabs: () -> Unit, bookmark: () -> Unit, settings: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().navigationBarsPadding().height(72.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineLarge)
            Text("›", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineLarge)
            Surface(onClick = tabs, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface), modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Text(count.toString(), color = MaterialTheme.colorScheme.onSurface) } }
            Text("♡", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.clickable { bookmark() })
            Text("•••", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { settings() })
        }
    }
}

@Composable private fun TabPage(tabs: List<BrowserTab>, activeId: String, model: BrowserViewModel, onBack: () -> Unit, onDialog: (BrowserTab) -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val groups by model.groups.collectAsState()
    val profiles by model.profiles.collectAsState()
    val currentProfile by model.profileId.collectAsState()
    val pagesLayout by settings.pagesLayout.collectAsState(initial = "card")
    var showGroupDialog by remember { mutableStateOf(false) }
    var showProfiles by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(top = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack) { Text("‹ back") }; Text("tabs", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f)); TextButton(onClick = { model.addTab(); onBack() }) { Text("+ new") } }
        Text("tab groups", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(groups, key = { it.id }) { group -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, Color(group.color))) { Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp)) { Text(group.name); TextButton(onClick = { model.deleteGroup(group) }) { Text("×") } } } }; item { Surface(onClick = { showGroupDialog = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) { Text("+ new group", modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) } } }
        Text("tabs", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        if (pagesLayout == "row") {
            LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(tabs, key = { it.id }) { tab -> Surface(onClick = { model.select(tab.id); onBack() }, shape = RoundedCornerShape(18.dp), color = if (tab.id == activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) { Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(tab.title, maxLines = 1); Text(tab.url, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; TextButton(onClick = { onDialog(tab) }) { Text("⊞") } } } } }
        } else {
            LazyRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(tabs, key = { it.id }) { tab -> Surface(onClick = { model.select(tab.id); onBack() }, shape = RoundedCornerShape(18.dp), color = if (tab.id == activeId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, modifier = Modifier.width(160.dp).height(130.dp)) { Column(Modifier.padding(16.dp)) { Row { Text(tab.title, modifier = Modifier.weight(1f)); TextButton(onClick = { onDialog(tab) }) { Text("⊞") } }; Spacer(Modifier.weight(1f)); Text(tab.url, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
        }
        Spacer(Modifier.weight(1f))
        Text("profile", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Surface(onClick = { model.switchProfile(profile.id); showProfiles = false }, shape = RoundedCornerShape(50), color = if (profile.id == currentProfile) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) { Text(profile.name, color = if (profile.id == currentProfile) Color.White else MaterialTheme.colorScheme.onSurface, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
            }
            item { Surface(onClick = { showProfiles = true }, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) { Text("manage", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) } }
        }
    }
    if (showGroupDialog) NameDialog("new group", "group name", { model.addGroup(it); showGroupDialog = false }, { showGroupDialog = false })
    if (showProfiles) ProfileSheet(model, { showProfiles = false })
}

@Composable private fun TabActionsDialog(tab: BrowserTab, model: BrowserViewModel, onRefresh: () -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    val groups by model.groups.collectAsState()
    val profiles by model.profiles.collectAsState()
    val currentProfile by model.profileId.collectAsState()
    var choosingGroup by remember { mutableStateOf(false) }
    var choosingMoveProfile by remember { mutableStateOf(false) }
    var choosingOpenProfile by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(tab.title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Action("↗  move to profile") { choosingMoveProfile = true }; Action("□  open in profile") { choosingOpenProfile = true }; Action("⊞  add to group") { choosingGroup = true }; if (tab.groupId != null) Action("−  remove from group") { model.assignToGroup(tab.id, null); dismiss() }; Action("⌑  pin tab") {}; Action("♧  share") { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, tab.url) }, "Share")); dismiss() }; Action("♡  bookmark") { model.bookmarkActive(); dismiss() }; Action("⟳  refresh") { onRefresh(); dismiss() }; Action("▣  developer tools") { model.addDevToolsTab(); dismiss() }; if (tab.url.startsWith("http")) Action("⊕  install as app") { installAsApp(context, tab); dismiss() }; Action("×  close tab") { model.close(tab.id); dismiss() } } }, confirmButton = { TextButton(onClick = dismiss) { Text("done") } })
    if (choosingGroup) {
        AlertDialog(onDismissRequest = { choosingGroup = false }, title = { Text("add to group") }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { if (groups.isEmpty()) Text("create a group first") else groups.forEach { group -> TextButton(onClick = { model.assignToGroup(tab.id, group.id); choosingGroup = false; dismiss() }, modifier = Modifier.fillMaxWidth()) { Text(group.name, modifier = Modifier.fillMaxWidth()) } } } }, confirmButton = { TextButton(onClick = { choosingGroup = false }) { Text("cancel") } })
    }
    if (choosingMoveProfile) {
        ProfilePickerDialog("move to profile", profiles, currentProfile, { target -> model.moveToProfile(tab.id, target); dismiss() }, { choosingMoveProfile = false })
    }
    if (choosingOpenProfile) {
        ProfilePickerDialog("open in profile", profiles, currentProfile, { target -> model.openInProfile(tab.id, target); dismiss() }, { choosingOpenProfile = false })
    }
}

@Composable private fun ProfilePickerDialog(title: String, profiles: List<com.example.aurabrowse.data.ProfileEntity>, currentProfile: String, pick: (String) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { profiles.filter { it.id != currentProfile }.forEach { profile -> TextButton(onClick = { pick(profile.id) }, modifier = Modifier.fillMaxWidth()) { Text("${profile.icon}  ${profile.name}", modifier = Modifier.fillMaxWidth()) } } } }, confirmButton = { TextButton(onClick = dismiss) { Text("cancel") } })
}
@Composable private fun Action(label: String, click: () -> Unit) { Button(onClick = click, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(label, modifier = Modifier.fillMaxWidth()) } }

private fun installAsApp(context: Context, tab: BrowserTab) {
    val url = tab.url
    val title = tab.title.ifBlank { url }
    val icon = appIcon(title)
    val launch = Intent(context, WebAppActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra("url", url)
        putExtra("title", title)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(Context.SHORTCUT_SERVICE) as android.content.pm.ShortcutManager
        runCatching {
            val shortcut = android.content.pm.ShortcutInfo.Builder(context, "webapp_${url.hashCode()}").apply {
                setShortLabel(title.take(10))
                setLongLabel(title)
                setIcon(android.graphics.drawable.Icon.createWithBitmap(icon))
                setIntent(launch)
            }.build()
            if (manager.isRequestPinShortcutSupported) manager.requestPinShortcut(shortcut, null)
        }
    } else {
        runCatching {
            val add = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra(Intent.EXTRA_SHORTCUT_INTENT, launch)
                putExtra(Intent.EXTRA_SHORTCUT_NAME, title)
                putExtra(Intent.EXTRA_SHORTCUT_ICON, icon)
            }
            context.sendBroadcast(add)
        }
    }
}

private fun appIcon(title: String): Bitmap {
    val size = 108
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0B6BCB.toInt() }
    canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), 24f, 24f, bg)
    val letter = title.take(1).uppercase()
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 52f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val baseline = (size / 2f - (text.descent() + text.ascent()) / 2f)
    canvas.drawText(letter, size / 2f, baseline, text)
    return bitmap
}

private fun String.toDestination(searchEngine: String = "duckduckgo"): String = if (startsWith("http://") || startsWith("https://")) this else if (contains(".") && !contains(" ")) "https://$this" else {
    val query = java.net.URLEncoder.encode(this, "UTF-8")
    when (searchEngine) {
        "bing" -> "https://www.bing.com/search?q=$query"
        "google" -> "https://www.google.com/search?q=$query"
        else -> "https://html.duckduckgo.com/html/?q=$query"
    }
}

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
