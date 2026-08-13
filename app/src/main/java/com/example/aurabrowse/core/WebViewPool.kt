package com.example.aurabrowse.core

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import com.example.aurabrowse.adblock.AdBlocker

class WebViewPool(
    private val context: Context,
    private val blocker: AdBlocker,
    private val appSettings: Settings,
    private val onPage: (String, String, String) -> Unit,
    private val onProgress: (String, Int) -> Unit,
    private val onNewTab: () -> String
) {
    private val views = linkedMapOf<String, WebView>()
    private val explicitLoads = mutableSetOf<String>()

    fun get(id: String): WebView = views.getOrPut(id) { build(id) }

    fun load(id: String, url: String) {
        val view = views.getOrPut(id) { build(id) }
        explicitLoads += id
        view.loadUrl(url)
    }

    fun hasContent(id: String): Boolean {
        if (explicitLoads.contains(id)) return true
        val view = views[id] ?: return false
        return !view.url.isNullOrEmpty() && view.url != "about:blank"
    }

    fun reload(id: String) { views[id]?.reload() }

    fun close(id: String) {
        views.remove(id)?.release()
        explicitLoads -= id
    }

    fun pause() { views.values.forEach { it.onPause() } }
    fun resume() { views.values.forEach { it.onResume() } }

    fun destroy() {
        views.values.forEach { it.release() }
        views.clear()
        explicitLoads.clear()
    }

    private fun WebView.release() {
        (parent as? ViewGroup)?.removeView(this)
        stopLoading()
        webChromeClient = null
        webViewClient = null
        loadUrl("about:blank")
        destroy()
    }

    private fun build(id: String): WebView = WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        settings.apply {
            javaScriptEnabled = appSettings.isJavaScriptEnabled()
            domStorageEnabled = true
            loadsImagesAutomatically = true
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = true
            databaseEnabled = true
            userAgentString = mobileChromeUserAgent(userAgentString)
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
            setSupportMultipleWindows(true)
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webViewClient = BrowserClient(blocker) { url, title -> onPage(id, url, title) }
        webChromeClient = BrowserChromeClient({ progress -> onProgress(id, progress) }) { resultMsg ->
            val newId = onNewTab()
            val child = build(newId)
            views[newId] = child
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return@BrowserChromeClient false
            transport.setWebView(child)
            resultMsg.sendToTarget()
            true
        }
        installDownloads(this, context)
    }

    private fun mobileChromeUserAgent(default: String): String = default
        .replace("; wv", "")
        .replace("Version/4.0 ", "")
        .replace(Regex("Chrome/\\d+(\\.\\d+)+"), "Chrome/130.0.6723.58")
}
