package com.example.aurabrowse.core

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Small CDP JSON-RPC client. WebView debugging must be enabled before connecting. */
class CdpClient(private val onEvent: (String, JSONObject) -> Unit) {
    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var socket: WebSocket? = null
    private var nextId = 1
    fun connect(pageSocketUrl: String) {
        socket = client.newWebSocket(Request.Builder().url(pageSocketUrl).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { JSONObject(text) }.getOrNull()?.let { event -> event.optString("method").takeIf(String::isNotEmpty)?.let { method -> main.post { onEvent(method, event.optJSONObject("params") ?: JSONObject()) } } }
            }
        })
    }
    fun enableNetwork() = send("Network.enable")
    fun enableRuntime() = send("Runtime.enable")
    fun evaluate(expression: String) = send("Runtime.evaluate", JSONObject().put("expression", expression).put("returnByValue", true))
    fun send(method: String, params: JSONObject = JSONObject()) { socket?.send(JSONObject().put("id", nextId++).put("method", method).put("params", params).toString()) }
    fun close() { socket?.close(1000, "closed"); socket = null; client.dispatcher.executorService.shutdown() }
}
