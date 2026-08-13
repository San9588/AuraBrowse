package com.example.aurabrowse.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aurabrowse.core.CdpClient
import org.json.JSONObject

class DevToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DevToolsScreen() } }
    }
}

data class DevtoolsLog(val text: String, val detail: String = "")

@Composable fun DevToolsScreen(modifier: Modifier = Modifier) {
    var endpoint by remember { mutableStateOf("ws://127.0.0.1:9222/devtools/page/") }
    var connected by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("console") }
    var expression by remember { mutableStateOf("") }
    val console = remember { mutableStateListOf<DevtoolsLog>() }
    val network = remember { mutableStateListOf<DevtoolsLog>() }
    val cdp = remember {
        CdpClient { method, params ->
            when {
                method == "Runtime.consoleAPICalled" -> console.add(DevtoolsLog(method, params.optString("type")))
                method == "Runtime.exceptionThrown" -> console.add(DevtoolsLog("exception", params.toString()))
                method == "Network.requestWillBeSent" -> network.add(DevtoolsLog("request", params.optJSONObject("request")?.optString("url", "") ?: ""))
                method == "Network.responseReceived" -> network.add(DevtoolsLog("response", params.optJSONObject("response")?.let { "${it.optInt("status")} ${it.optString("url")}" } ?: ""))
            }
        }
    }
    DisposableEffect(Unit) { onDispose { cdp.close() } }
    Column(modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("developer tools", style = MaterialTheme.typography.headlineSmall); Text(if (connected) "connected" else "offline", color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(endpoint, { endpoint = it }, Modifier.weight(1f), singleLine = true, label = { Text("CDP WebSocket URL") }); Button(onClick = { cdp.connect(endpoint); cdp.enableRuntime(); cdp.enableNetwork(); connected = true }) { Text("connect") } }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected == "console", { selected = "console" }, label = { Text("console (${console.size})") }); FilterChip(selected == "network", { selected = "network" }, label = { Text("network (${network.size})") }); TextButton(onClick = { console.clear(); network.clear() }) { Text("clear") } }
        if (selected == "console") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(console) { item -> Text("${item.text}: ${item.detail}", modifier = Modifier.padding(vertical = 6.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(expression, { expression = it }, Modifier.weight(1f), singleLine = true, label = { Text("Runtime.evaluate") }); Button(onClick = { cdp.evaluate(expression); expression = "" }) { Text("run") } }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(network) { item -> Column(Modifier.padding(vertical = 6.dp)) { Text(item.text); Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}
