package com.example.aurabrowse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var adblock by remember { mutableStateOf(prefs.getBoolean("adblock_enabled", true)) }
                var devtools by remember { mutableStateOf(prefs.getBoolean("enable_devtools", false)) }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp).statusBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("settings", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))
                    SettingSwitch("ad blocking", "Block known advertising and tracking domains", adblock) {
                        adblock = it
                        prefs.edit().putBoolean("adblock_enabled", it).apply()
                    }
                    SettingSwitch("developer tools", "Enable WebView debugging for CDP", devtools) {
                        devtools = it
                        prefs.edit().putBoolean("enable_devtools", it).apply()
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked, onChange)
    }
}
