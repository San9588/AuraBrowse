package com.example.aurabrowse.core

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.Message
import android.webkit.*
import com.example.aurabrowse.adblock.AdBlocker

class BrowserClient(private val blocker: AdBlocker, private val onPage: (String, String) -> Unit) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = if (blocker.enabled && blocker.isBlocked(request.url.toString())) blocker.emptyResponse() else null
    override fun onPageFinished(view: WebView, url: String) { onPage(url, view.title.orEmpty()) }
}

class BrowserChromeClient(private val onProgress: (Int) -> Unit, private val onCreateWindow: (Message) -> Boolean) : WebChromeClient() {
    override fun onProgressChanged(view: WebView, newProgress: Int) { onProgress(newProgress) }
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = onCreateWindow(resultMsg)
}

fun installDownloads(webView: WebView, context: Context) {
    webView.setDownloadListener { url, userAgent, disposition, mime, _ ->
        runCatching {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mime)
                addRequestHeader("User-Agent", userAgent)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, disposition, mime))
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        }
    }
}
