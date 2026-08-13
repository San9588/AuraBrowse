package com.example.aurabrowse.adblock

import android.content.Context
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import java.net.URI

class AdBlocker(context: Context) {
    private val domains = context.assets.open("blocklist.txt").bufferedReader().useLines { it.map(String::trim).filter { d -> d.isNotEmpty() && !d.startsWith("#") }.toHashSet() }
    @Volatile var enabled = true
    fun isBlocked(url: String): Boolean = runCatching {
        var host = URI(url).host?.lowercase() ?: return false
        while (host.isNotEmpty()) {
            if (host in domains) return true
            host = host.substringAfter('.', "")
        }
        false
    }.getOrDefault(false)
    fun emptyResponse() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
}
