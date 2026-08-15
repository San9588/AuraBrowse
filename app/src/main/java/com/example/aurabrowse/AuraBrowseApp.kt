package com.example.aurabrowse

import android.app.Application
import android.content.Context
import android.webkit.WebView

class AuraBrowseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("enable_devtools", false)) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}
