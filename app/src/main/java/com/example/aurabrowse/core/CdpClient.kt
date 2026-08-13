package com.example.aurabrowse.core

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class CdpPage(val id: String, val title: String, val url: String, val wsUrl: String)

/**
 * CDP JSON-RPC client that talks directly to the on-device WebView debug server
 * over the localabstract Unix socket (no adb/tcp forwarding needed), same as Kiwi.
 */
class CdpClient(
    private val onEvent: (String, JSONObject) -> Unit,
    private val onState: (Boolean) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private val gen = AtomicInteger(0)
    private val pending = ConcurrentHashMap<Int, CompletableFuture<JSONObject?>>()
    private val writeLock = Any()
    @Volatile private var socket: LocalSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var connected = false
    private var nextId = 1

    companion object {
        private const val TAG = "AuraCdp"
    }

    fun connect(wsUrl: String? = null) {
        close()
        val g = gen.incrementAndGet()
        Thread({
            try {
                val target = wsUrl ?: discoverDefaultWsUrl()
                if (target.isNullOrBlank()) throw IOException("no debuggable page (is WebView debugging enabled?)")
                Log.d(TAG, "connect target=$target")
                val sock = openSocket()
                val (input, output) = websocketUpgrade(sock, target)
                socket = sock
                out = output
                connected = true
                main.post { if (g == gen.get()) onState(true) }
                enableDomains()
                readLoop(sock, input, g)
            } catch (e: Exception) {
                Log.e(TAG, "connect failed: ${e.message}", e)
                runCatching { socket?.close() }
                if (g == gen.get()) {
                    socket = null
                    out = null
                    connected = false
                    main.post { onState(false) }
                }
            }
        }, "cdp").start()
    }

    fun send(method: String, params: JSONObject = JSONObject()) {
        val id = synchronized(this) { nextId++ }
        writeFrame(0x1, JSONObject().put("id", id).put("method", method).put("params", params).toString().toByteArray(Charsets.UTF_8), masked = true)
    }

    fun request(method: String, params: JSONObject = JSONObject()): CompletableFuture<JSONObject?> {
        val id = synchronized(this) { nextId++ }
        val future = CompletableFuture<JSONObject?>()
        pending[id] = future
        writeFrame(0x1, JSONObject().put("id", id).put("method", method).put("params", params).toString().toByteArray(Charsets.UTF_8), masked = true)
        return future
    }

    fun listPages(): List<CdpPage> = runCatching {
        openSocket().use { sock ->
            Log.d(TAG, "listPages connected")
            val body = httpGet(sock, "/json")
            Log.d(TAG, "listPages body len=${body.length}")
            val arr = JSONArray(body)
            (0 until arr.length()).map { i ->
                val p = arr.getJSONObject(i)
                CdpPage(p.optString("id"), p.optString("title"), p.optString("url"), p.optString("webSocketDebuggerUrl"))
            }
        }
    }.onFailure { Log.e(TAG, "listPages failed: ${it.message}", it) }.getOrDefault(emptyList())

    fun close() {
        gen.incrementAndGet()
        runCatching { socket?.close() }
        socket = null
        out = null
        connected = false
        main.post { onState(false) }
    }

    private fun discoverDefaultWsUrl(): String? {
        val pages = listPages()
        return pages.firstOrNull { it.wsUrl.isNotBlank() }?.wsUrl
    }

    private fun enableDomains() {
        send("Runtime.enable")
        send("Console.enable")
        send("Network.enable")
        send("DOM.enable")
        send("Page.enable")
    }

    private fun readLoop(sock: LocalSocket, input: InputStream, g: Int) {
        try {
            while (g == gen.get() && !sock.isClosed) {
                val frame = readFrame(input) ?: break
                when (frame.first) {
                    0x1 -> handleMessage(String(frame.second, Charsets.UTF_8))
                    0x9 -> writeFrame(0xA, frame.second, masked = true)
                    0x8 -> { writeFrame(0x8, ByteArray(0), masked = true); break }
                }
            }
        } catch (_: Exception) {
        } finally {
            if (g == gen.get() && connected) {
                connected = false
                main.post { onState(false) }
            }
        }
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        json.optInt("id").takeIf { it != 0 }?.let { id ->
            pending.remove(id)?.complete(json.optJSONObject("result") ?: JSONObject())
            return
        }
        val method = json.optString("method")
        if (method.isNotEmpty()) {
            val params = json.optJSONObject("params") ?: JSONObject()
            main.post { onEvent(method, params) }
        }
    }

    private fun openSocket(): LocalSocket {
        val names = buildList {
            add("webview_devtools_remote_${Process.myPid()}")
            add("webview_devtools_remote")
        }
        var last: IOException? = null
        for (name in names) {
            try {
                Log.d(TAG, "trying socket $name")
                val sock = LocalSocket()
                sock.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
                Log.d(TAG, "connected to $name")
                return sock
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("no webview debug socket")
    }

    private fun httpGet(sock: LocalSocket, path: String): String {
        val outStream = sock.outputStream
        outStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
        outStream.flush()
        val input = sock.inputStream
        val head = readUntil(input, "\r\n\r\n")
        val clen = head.lines().firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }?.substringAfter(':')?.trim()?.toIntOrNull() ?: -1
        val body = if (clen >= 0) ByteArray(clen).also { readFully(input, it) } else input.readBytes()
        return body.toString(Charsets.UTF_8)
    }

    private fun websocketUpgrade(sock: LocalSocket, wsUrl: String): Pair<InputStream, OutputStream> {
        val path = runCatching { Uri.parse(wsUrl).path }.getOrNull() ?: "/devtools/page/1"
        val key = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.encodeToString(it, Base64.NO_WRAP) }
        val request = "GET $path HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n"
        val outStream = sock.outputStream
        outStream.write(request.toByteArray(Charsets.UTF_8))
        outStream.flush()
        val response = readUntil(sock.inputStream, "\r\n\r\n")
        if (!response.startsWith("HTTP/1.1 101")) throw IOException("upgrade failed: $response")
        return Pair(sock.inputStream, outStream)
    }

    private fun readUntil(input: InputStream, delimiter: String): String {
        val sb = StringBuilder()
        val delim = delimiter.toByteArray(Charsets.UTF_8)
        var matched = 0
        while (true) {
            val b = input.read()
            if (b == -1) break
            sb.append(b.toChar())
            matched = if (b == (delim[matched].toInt() and 0xFF)) matched + 1 else 0
            if (matched == delim.size) break
        }
        return sb.toString()
    }

    private fun readFully(input: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) {
            val n = input.read(buf, off, buf.size - off)
            if (n == -1) throw IOException("unexpected eof")
            off += n
        }
    }

    private fun readFrame(input: InputStream): Pair<Int, ByteArray>? {
        val b0 = input.read()
        if (b0 == -1) return null
        val opcode = b0 and 0x0F
        val b1 = input.read()
        if (b1 == -1) return null
        var len = (b1 and 0x7F).toLong()
        if (len == 126L) {
            val hi = input.read(); val lo = input.read()
            if (hi == -1 || lo == -1) return null
            len = ((hi shl 8) or lo).toLong()
        } else if (len == 127L) {
            var l = 0L
            for (i in 0 until 8) { val b = input.read(); if (b == -1) return null; l = (l shl 8) or (b.toLong() and 0xFF) }
            len = l
        }
        val masked = b1 and 0x80 != 0
        val mask = if (masked) ByteArray(4).also { readFully(input, it) } else null
        val data = ByteArray(len.toInt())
        readFully(input, data)
        if (mask != null) for (i in data.indices) data[i] = ((data[i].toInt() and 0xFF) xor (mask[i % 4].toInt() and 0xFF)).toByte()
        return Pair(opcode, data)
    }

    private fun writeFrame(opcode: Int, payload: ByteArray, masked: Boolean) {
        val outStream = out ?: return
        synchronized(writeLock) {
            val header = ByteArrayOutputStream(16)
            header.write(0x80 or opcode)
            val len = payload.size
            when {
                len < 126 -> header.write((if (masked) 0x80 else 0) or len)
                len < 65536 -> {
                    header.write((if (masked) 0x80 else 0) or 126)
                    header.write((len shr 8) and 0xFF)
                    header.write(len and 0xFF)
                }
                else -> {
                    header.write((if (masked) 0x80 else 0) or 127)
                    for (i in 7 downTo 0) header.write(((len.toLong() shr (8 * i)) and 0xFF).toInt())
                }
            }
            if (masked) {
                val mask = ByteArray(4).also { SecureRandom().nextBytes(it) }
                header.write(mask[0].toInt() and 0xFF)
                header.write(mask[1].toInt() and 0xFF)
                header.write(mask[2].toInt() and 0xFF)
                header.write(mask[3].toInt() and 0xFF)
                for (i in payload.indices) payload[i] = ((payload[i].toInt() and 0xFF) xor (mask[i % 4].toInt() and 0xFF)).toByte()
            }
            outStream.write(header.toByteArray())
            outStream.write(payload)
            outStream.flush()
        }
    }
}
