package com.example.aurabrowse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            var theme by remember { mutableStateOf(prefs.getString("theme", "system") ?: "system") }
            val dark = theme == "dark" || (theme == "system" && isSystemInDarkTheme())
            MaterialTheme(colorScheme = if (dark) darkColorScheme(primary = Color(0xFF70B8FF)) else lightColorScheme(primary = Color(0xFF087EDB))) {
                SettingsContent(prefs, theme, { theme = it; prefs.edit().putString("theme", it).apply() })
            }
        }
    }
}

@Composable private fun SettingsContent(prefs: android.content.SharedPreferences, theme: String, setTheme: (String) -> Unit) {
    var adblock by remember { mutableStateOf(prefs.getBoolean("adblock_enabled", true)) }
    var devtools by remember { mutableStateOf(prefs.getBoolean("enable_devtools", false)) }
    var searchEngine by remember { mutableStateOf(prefs.getString("search_engine", "google") ?: "google") }
    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp).statusBarsPadding(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(18.dp))
        SectionTitle("general")
        SettingRow("homepage", "AuraBrowse home", false) {}
        Text("search engine", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            listOf("google", "duckduckgo", "bing").forEach { value ->
                FilterChip(selected = searchEngine == value, onClick = { searchEngine = value; prefs.edit().putString("search_engine", value).apply() }, label = { Text(value) })
            }
        }
        Spacer(Modifier.height(12.dp)); SectionTitle("privacy")
        SettingRow("ad blocking", "Block known advertising and tracking domains", adblock) { adblock = it; prefs.edit().putBoolean("adblock_enabled", it).apply() }
        SettingRow("clear browsing data", "History, cookies and cache", false) {}
        Spacer(Modifier.height(12.dp)); SectionTitle("appearance")
        Text("theme", style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) { listOf("system", "light", "dark").forEach { value -> FilterChip(selected = theme == value, onClick = { setTheme(value) }, label = { Text(value) }) } }
        Spacer(Modifier.height(12.dp)); SectionTitle("advanced")
        SettingRow("developer tools", "Enable WebView debugging for CDP", devtools) { devtools = it; prefs.edit().putBoolean("enable_devtools", it).apply() }
        SettingRow("JavaScript", "Enabled for websites", true) {}
        Spacer(Modifier.height(18.dp)); SectionTitle("about"); Text("AuraBrowse 0.1.0", color = MaterialTheme.colorScheme.onSurfaceVariant); Text("Lightweight system WebView browser", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 8.dp)) }
@Composable private fun SettingRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text(title); Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Switch(checked, onChange) } }
