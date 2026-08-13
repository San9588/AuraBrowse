package com.example.aurabrowse.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aurabrowse.core.CdpClient
import org.json.JSONObject

class DevToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuraBrowseTheme { DevToolsScreen() } }
    }
}

data class DevtoolsLog(val text: String, val detail: String = "")

private val mainHandler = Handler(Looper.getMainLooper())

@Composable fun DevToolsScreen(modifier: Modifier = Modifier) {
    var connected by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf("console") }
    var expression by remember { mutableStateOf("") }
    var pageTitle by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("offline") }
    val console = remember { mutableStateListOf<DevtoolsLog>() }
    val network = remember { mutableStateListOf<DevtoolsLog>() }
    val cdp = remember {
        CdpClient(
            onEvent = { method, params ->
                when {
                    method == "Runtime.consoleAPICalled" -> console.add(DevtoolsLog("console", argsText(params)))
                    method == "Runtime.exceptionThrown" -> console.add(DevtoolsLog("exception", params.toString()))
                    method == "Network.requestWillBeSent" -> network.add(DevtoolsLog("request", params.optJSONObject("request")?.optString("url", "") ?: ""))
                    method == "Network.responseReceived" -> network.add(DevtoolsLog("response", params.optJSONObject("response")?.let { "${it.optInt("status")} ${it.optString("url")}" } ?: ""))
                }
            },
            onState = { ok -> connected = ok; status = if (ok) "connected" else "offline" }
        )
    }
    fun doConnect() {
        Thread({
            val pages = cdp.listPages()
            val first = pages.firstOrNull()
            mainHandler.post {
                if (first != null) {
                    pageTitle = first.title.ifBlank { first.url }
                    status = "connecting…"
                    cdp.connect(first.wsUrl)
                } else {
                    pageTitle = "no debuggable page"
                    status = "offline (enable dev tools in settings, then restart app)"
                }
            }
        }, "cdp-list").start()
    }
    DisposableEffect(Unit) {
        doConnect()
        onDispose { cdp.close() }
    }
    Column(modifier.fillMaxSize().padding(16.dp).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("developer tools", style = MaterialTheme.typography.headlineSmall); Text(status, color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { doConnect() }) { Text("connect") }
            Text(pageTitle, modifier = Modifier.align(Alignment.CenterVertically).weight(1f), maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected == "console", { selected = "console" }, label = { Text("console (${console.size})") }); FilterChip(selected == "network", { selected = "network" }, label = { Text("network (${network.size})") }); TextButton(onClick = { console.clear(); network.clear() }) { Text("clear") } }
        if (selected == "console") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(console) { item -> Text("${item.text}: ${item.detail}", modifier = Modifier.padding(vertical = 6.dp)) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { OutlinedTextField(expression, { expression = it }, Modifier.weight(1f), singleLine = true, label = { Text("Runtime.evaluate") }); Button(onClick = { if (connected) { cdp.send("Runtime.evaluate", JSONObject().put("expression", expression).put("returnByValue", true)); expression = "" } }) { Text("run") } }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) { items(network) { item -> Column(Modifier.padding(vertical = 6.dp)) { Text(item.text); Text(item.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
        }
    }
}

private fun argsText(params: JSONObject): String {
    val args = params.optJSONArray("args") ?: return ""
    val out = buildString {
        for (i in 0 until args.length()) {
            val obj = args.optJSONObject(i) ?: continue
            if (i > 0) append(" ")
            append(obj.optString("value").ifBlank { obj.optString("description") })
        }
    }
    return out.ifBlank { params.optString("type") }
}
