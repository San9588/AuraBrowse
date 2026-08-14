package com.example.aurabrowse.core

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

class Settings(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val changes = MutableSharedFlow<String>(extraBufferCapacity = 16)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> key?.let { changes.tryEmit(it) } }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun stringFlow(key: String, default: String): Flow<String> = changes
        .filter { it == key }
        .map { prefs.getString(key, default) ?: default }
        .onStart { emit(prefs.getString(key, default) ?: default) }

    fun boolFlow(key: String, default: Boolean): Flow<Boolean> = changes
        .filter { it == key }
        .map { prefs.getBoolean(key, default) }
        .onStart { emit(prefs.getBoolean(key, default)) }

    fun getString(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    fun set(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    fun set(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }

    val theme: Flow<String> = stringFlow("theme", "system")
    val accent: Flow<String> = stringFlow("accent", "blue")
    val pagesLayout: Flow<String> = stringFlow("pages_layout", "card")
    val adblockEnabled: Flow<Boolean> = boolFlow("adblock_enabled", true)
    val searchEngine: Flow<String> = stringFlow("search_engine", "google")
    val devtools: Flow<Boolean> = boolFlow("enable_devtools", false)
    val javascriptEnabled: Flow<Boolean> = boolFlow("javascript_enabled", true)

    fun isJavaScriptEnabled(): Boolean = getBoolean("javascript_enabled", true)
}
