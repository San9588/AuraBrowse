package com.example.aurabrowse.ui

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebViewDatabase
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.aurabrowse.core.Settings
import com.example.aurabrowse.data.AppDatabase
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SettingsContent(Settings(this)) }
    }
}

@Composable private fun SettingsContent(settings: Settings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var theme by remember { mutableStateOf(settings.getString("theme", "system")) }
    val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
    MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF70B8FF)) else lightColorScheme(primary = Color(0xFF087EDB))) {
        var adblock by remember { mutableStateOf(settings.getBoolean("adblock_enabled", true)) }
        var devtools by remember { mutableStateOf(settings.getBoolean("enable_devtools", false)) }
        var searchEngine by remember { mutableStateOf(settings.getString("search_engine", "google")) }
        var js by remember { mutableStateOf(settings.getBoolean("javascript_enabled", true)) }
        Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(18.dp))
            SectionTitle("general")
            SettingRow("homepage", "AuraBrowse home", false) {}
            Text("search engine", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf("google", "duckduckgo", "bing").forEach { value ->
                    FilterChip(selected = searchEngine == value, onClick = { searchEngine = value; settings.set("search_engine", value) }, label = { Text(value) })
                }
            }
            Spacer(Modifier.height(12.dp)); SectionTitle("privacy")
            SettingRow("ad blocking", "Block known advertising and tracking domains", adblock) { enabled -> adblock = enabled; settings.set("adblock_enabled", enabled) }
            SettingRow("clear browsing data", "History, cookies and cache", false) {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebViewDatabase.getInstance(context).clearFormData()
                scope.launch { AppDatabase.get(context).browserDao().clearHistory() }
            }
            Spacer(Modifier.height(12.dp)); SectionTitle("appearance")
            Text("theme", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { listOf("system", "light", "dark").forEach { value -> FilterChip(selected = theme == value, onClick = { theme = value; settings.set("theme", value) }, label = { Text(value) }) } }
            Spacer(Modifier.height(12.dp)); SectionTitle("advanced")
            SettingRow("developer tools", "Enable WebView debugging for CDP", devtools) { enabled -> devtools = enabled; settings.set("enable_devtools", enabled) }
            SettingRow("JavaScript", "Enabled for websites", js) { enabled -> js = enabled; settings.set("javascript_enabled", enabled) }
            Spacer(Modifier.height(18.dp)); SectionTitle("about"); Text("AuraBrowse 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Lightweight system WebView browser", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
@Composable private fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(title); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChange) } }
