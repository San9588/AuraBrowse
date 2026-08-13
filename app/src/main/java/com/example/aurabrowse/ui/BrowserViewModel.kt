package com.example.aurabrowse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aurabrowse.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class BrowserTab(val id: String, var url: String, var title: String, var groupId: String? = null)
class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).browserDao()
    private val _tabs = MutableStateFlow(listOf(BrowserTab("first", "about:home", "New tab")))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()
    private val _active = MutableStateFlow("first")
    val activeId = _active.asStateFlow()
    private val _profiles = MutableStateFlow(listOf(ProfileEntity("default", "personal", 0xFF536DFE.toInt(), "●", true)))
    val profiles = _profiles.asStateFlow()
    private val _profileId = MutableStateFlow("default")
    val profileId = _profileId.asStateFlow()
    init { viewModelScope.launch { dao.saveProfile(_profiles.value.first()); dao.profiles().collect { if (it.isNotEmpty()) _profiles.value = it } } }
    fun active() = _tabs.value.first { it.id == _active.value }
    fun select(id: String) { _active.value = id }
    fun switchProfile(id: String) { _profileId.value = id }
    fun addProfile() { val profile = ProfileEntity(UUID.randomUUID().toString(), "profile ${_profiles.value.size + 1}", 0xFF536DFE.toInt(), "●", false); _profiles.value += profile; viewModelScope.launch { dao.saveProfile(profile) } }
    fun addTab(url: String = "about:home") { val tab = BrowserTab(UUID.randomUUID().toString(), url, "New tab"); _tabs.value = _tabs.value + tab; _active.value = tab.id }
    fun navigate(url: String) { _tabs.value = _tabs.value.map { if (it.id == _active.value) it.copy(url = url) else it } }
    fun close(id: String) { if (_tabs.value.size == 1) return; val remaining = _tabs.value.filterNot { it.id == id }; _tabs.value = remaining; if (_active.value == id) _active.value = remaining.last().id }
    fun updatePage(url: String, title: String) { _tabs.value = _tabs.value.map { if (it.id == _active.value) it.copy(url = url, title = title.ifBlank { url }) else it }; viewModelScope.launch { dao.saveHistory(HistoryEntity(UUID.randomUUID().toString(), url, title.ifBlank { url }, System.currentTimeMillis(), _profileId.value, 1)) } }
    fun bookmarkActive() { val tab = active(); viewModelScope.launch { dao.saveBookmark(BookmarkEntity(UUID.randomUUID().toString(), tab.url, tab.title, null, _profileId.value, System.currentTimeMillis())) } }
}
