package com.example.aurabrowse.core

import android.net.Uri
import android.webkit.CookieManager
import com.example.aurabrowse.data.BrowserDao
import com.example.aurabrowse.data.CookieEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CookieStore(private val dao: BrowserDao) {

    suspend fun snapshot(url: String, profileId: String) {
        val host = runCatching { Uri.parse(url).host }.getOrNull() ?: return
        if (host.isBlank()) return
        val value = CookieManager.getInstance().getCookie(url)
        if (!value.isNullOrBlank()) dao.saveCookie(CookieEntity(profileId, host, value))
    }

    suspend fun switchTo(from: String?, to: String) = withContext(Dispatchers.Main) {
        val cm = CookieManager.getInstance()
        if (from != null) {
            val domains = dao.cookieDomains()
            for (domain in domains) {
                val value = cm.getCookie("https://$domain/")
                if (!value.isNullOrBlank()) dao.saveCookie(CookieEntity(from, domain, value))
            }
        }
        suspendCancellableCoroutine { cont ->
            cm.removeAllCookies { cont.resume(Unit) }
        }
        cm.flush()
        for (cookie in dao.cookiesForProfile(to)) {
            val url = "https://${cookie.domain}/"
            cookie.value.split("; ").forEach { pair ->
                val name = pair.substringBefore("=").trim()
                val value = pair.substringAfter("=", "").trim()
                if (name.isNotBlank() && value.isNotBlank()) cm.setCookie(url, "$name=$value")
            }
        }
        cm.flush()
    }
}
