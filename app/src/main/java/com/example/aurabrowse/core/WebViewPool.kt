package com.example.aurabrowse.core

import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebView
import com.example.aurabrowse.adblock.AdBlocker

class WebViewPool(private val context: Context, private val blocker: AdBlocker, private val onPage: (String, String) -> Unit, private val onProgress: (Int) -> Unit) {
    private val views = linkedMapOf<String, WebView>()
    fun get(id: String): WebView = views.getOrPut(id) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true; settings.loadsImagesAutomatically = true
            settings.builtInZoomControls = false; settings.displayZoomControls = false
            settings.javaScriptCanOpenWindowsAutomatically = true; settings.setSupportMultipleWindows(false)
            settings.userAgentString = settings.userAgentString.replace("; wv", "").replace("Version/4.0 ", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = BrowserClient(blocker, onPage); webChromeClient = BrowserChromeClient(context, onProgress)
            installDownloads(this, context)
        }
    }
    fun clear(parent: ViewGroup) { parent.removeAllViews() }
    fun destroy() { views.values.forEach { it.destroy() }; views.clear() }
}
