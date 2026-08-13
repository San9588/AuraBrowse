package com.example.aurabrowse.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.*
import com.example.aurabrowse.adblock.AdBlocker

class BrowserClient(private val blocker: AdBlocker, private val onPage: (String, String) -> Unit) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = if (blocker.enabled && blocker.isBlocked(request.url.toString())) blocker.emptyResponse() else null
    override fun onPageFinished(view: WebView, url: String) { onPage(url, view.title.orEmpty()) }
}

class BrowserChromeClient(private val context: Context, private val onProgress: (Int) -> Unit) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) { onProgress(newProgress) }
}

fun installDownloads(webView: WebView, context: Context) {
    webView.setDownloadListener { url, userAgent, disposition, mime, _ ->
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setMimeType(mime); addRequestHeader("User-Agent", userAgent)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mime))
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
    }
}
