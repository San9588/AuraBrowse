package com.example.aurabrowse.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aurabrowse.core.CookieStore
import com.example.aurabrowse.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class BrowserTab(val id: String, var url: String, var title: String, val profileId: String = "default", var groupId: String? = null, val createdAt: Long = System.currentTimeMillis())

class BrowserViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDatabase.get(app).browserDao()
    private val cookieStore = CookieStore(dao)
    private val prefs = app.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val _tabs = MutableStateFlow<List<BrowserTab>>(listOf(BrowserTab("first", "about:home", "New tab")))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()
    private val _active = MutableStateFlow("first")
    val activeId: StateFlow<String> = _active.asStateFlow()
    private val _profiles = MutableStateFlow(listOf(ProfileEntity("default", "personal", 0xFF536DFE.toInt(), "●", true)))
    val profiles = _profiles.asStateFlow()
    private val _profileId = MutableStateFlow(prefs.getString("last_profile", "default") ?: "default")
    val profileId = _profileId.asStateFlow()
    private val _groups = MutableStateFlow<List<GroupEntity>>(emptyList())
    val groups = _groups.asStateFlow()
    private val _closedTabs = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val closedTabs: SharedFlow<String> = _closedTabs.asSharedFlow()

    init {
        viewModelScope.launch {
            dao.saveProfile(_profiles.value.first())
            dao.profiles().collect { if (it.isNotEmpty()) _profiles.value = it }
        }
        viewModelScope.launch {
            val saved = dao.tabsSnapshot().filter { it.profileId == _profileId.value }
            _groups.value = dao.groupsSnapshot().filter { it.profileId == _profileId.value }
            if (saved.isNotEmpty()) {
                _tabs.value = saved.map { it.toBrowserTab() }
                _active.value = saved.first().id
            } else {
                addTab()
            }
            cookieStore.switchTo(_profileId.value, _profileId.value)
        }
    }

    fun active(): BrowserTab = _tabs.value.firstOrNull { it.id == _active.value } ?: _tabs.value.first()
    fun select(id: String) { _active.value = id }

    fun switchProfile(id: String) {
        if (id == _profileId.value) return
        val from = _profileId.value
        _profileId.value = id
        prefs.edit().putString("last_profile", id).apply()
        viewModelScope.launch { loadProfile(from, id) }
    }

    fun moveToProfile(tabId: String, targetProfileId: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        if (tab.profileId == targetProfileId) return
        val moved = BrowserTab(UUID.randomUUID().toString(), tab.url, tab.title, targetProfileId, null, System.currentTimeMillis())
        val from = _profileId.value
        _profileId.value = targetProfileId
        prefs.edit().putString("last_profile", targetProfileId).apply()
        viewModelScope.launch {
            dao.saveTab(moved.toEntity())
            _closedTabs.tryEmit(tabId)
            dao.deleteTab(tab.toEntity())
            loadProfile(from, targetProfileId)
        }
    }

    fun openInProfile(tabId: String, targetProfileId: String) {
        val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return
        if (tab.profileId == targetProfileId) return
        val opened = BrowserTab(UUID.randomUUID().toString(), tab.url, tab.title, targetProfileId, null, System.currentTimeMillis())
        val from = _profileId.value
        _profileId.value = targetProfileId
        prefs.edit().putString("last_profile", targetProfileId).apply()
        viewModelScope.launch {
            dao.saveTab(opened.toEntity())
            loadProfile(from, targetProfileId)
        }
    }

    private suspend fun loadProfile(from: String?, id: String) {
        cookieStore.switchTo(from, id)
        val saved = dao.tabsSnapshot().filter { it.profileId == id }
        _groups.value = dao.groupsSnapshot().filter { it.profileId == id }
        if (saved.isNotEmpty()) {
            _tabs.value = saved.map { it.toBrowserTab() }
            _active.value = saved.first().id
        } else {
            addTab()
        }
    }

    fun addProfile(name: String) { val cleanName = name.trim().ifBlank { "profile ${_profiles.value.size + 1}" }; val profile = ProfileEntity(UUID.randomUUID().toString(), cleanName, 0xFF536DFE.toInt(), "●", false); _profiles.value += profile; viewModelScope.launch { dao.saveProfile(profile) } }

    fun deleteProfile(profile: ProfileEntity) {
        if (profile.isDefault || profile.id == _profileId.value) return
        _profiles.value = _profiles.value.filterNot { it.id == profile.id }
        _groups.value = _groups.value.filterNot { it.profileId == profile.id }
        viewModelScope.launch {
            dao.deleteProfile(profile)
            dao.deleteTabsForProfile(profile.id)
            dao.deleteGroupsForProfile(profile.id)
            dao.deleteHistoryForProfile(profile.id)
            dao.deleteBookmarksForProfile(profile.id)
            dao.deleteDownloadsForProfile(profile.id)
            dao.deleteCookiesForProfile(profile.id)
        }
    }

    fun addGroup(name: String) { val cleanName = name.trim().ifBlank { "group ${_groups.value.size + 1}" }; val group = GroupEntity(UUID.randomUUID().toString(), cleanName, 0xFF536DFE.toInt(), _profileId.value, false, _groups.value.size); _groups.value += group; viewModelScope.launch { dao.saveGroup(group) } }

    fun deleteGroup(group: GroupEntity) {
        _groups.value = _groups.value.filterNot { it.id == group.id }
        _tabs.value = _tabs.value.map { if (it.groupId == group.id) it.copy(groupId = null) else it }
        viewModelScope.launch {
            dao.deleteGroup(group)
            _tabs.value.forEach { dao.saveTab(it.toEntity()) }
        }
    }

    fun assignToGroup(tabId: String, groupId: String?) {
        _tabs.value = _tabs.value.map { if (it.id == tabId) it.copy(groupId = groupId) else it }
        viewModelScope.launch { _tabs.value.firstOrNull { it.id == tabId }?.let { dao.saveTab(it.toEntity()) } }
    }

    fun addTab(url: String = "about:home"): String {
        val tab = BrowserTab(UUID.randomUUID().toString(), url, "New tab", _profileId.value, null, System.currentTimeMillis())
        _tabs.value = _tabs.value + tab
        _active.value = tab.id
        viewModelScope.launch { dao.saveTab(tab.toEntity()) }
        return tab.id
    }

    fun addDevToolsTab() {
        val tab = BrowserTab(UUID.randomUUID().toString(), "about:devtools", "Developer tools", _profileId.value, null, System.currentTimeMillis())
        _tabs.value = _tabs.value + tab
        _active.value = tab.id
        viewModelScope.launch { dao.saveTab(tab.toEntity()) }
    }

    fun navigate(url: String) {
        _tabs.value = _tabs.value.map { if (it.id == _active.value) it.copy(url = url) else it }
        viewModelScope.launch { _tabs.value.firstOrNull { it.id == _active.value }?.let { dao.saveTab(it.toEntity()) } }
    }

    fun close(id: String) {
        if (_tabs.value.size == 1) return
        val closed = _tabs.value.firstOrNull { it.id == id }
        val remaining = _tabs.value.filterNot { it.id == id }
        _tabs.value = remaining
        if (_active.value == id) _active.value = remaining.last().id
        _closedTabs.tryEmit(id)
        if (closed != null) viewModelScope.launch { dao.deleteTab(closed.toEntity()) }
    }

    fun updatePage(tabId: String, url: String, title: String) {
        val cleanTitle = title.ifBlank { url }
        _tabs.value = _tabs.value.map { if (it.id == tabId) it.copy(url = url, title = cleanTitle) else it }
        viewModelScope.launch {
            val tab = _tabs.value.firstOrNull { it.id == tabId } ?: return@launch
            dao.saveTab(tab.toEntity())
            if (!url.startsWith("about:") && !url.startsWith("data:") && !url.startsWith("file:")) {
                cookieStore.snapshot(url, tab.profileId)
                val existing = dao.historyByUrl(url, tab.profileId)
                if (existing != null) {
                    dao.saveHistory(existing.copy(title = cleanTitle, timestamp = System.currentTimeMillis(), visitCount = existing.visitCount + 1))
                } else {
                    dao.saveHistory(HistoryEntity(UUID.randomUUID().toString(), url, cleanTitle, System.currentTimeMillis(), tab.profileId, 1))
                }
            }
        }
    }

    fun bookmarkActive() {
        val tab = active()
        if (tab.url == "about:home" || tab.url == "about:devtools") return
        viewModelScope.launch { dao.saveBookmark(BookmarkEntity(UUID.randomUUID().toString(), tab.url, tab.title, null, tab.profileId, System.currentTimeMillis())) }
    }

    private fun BrowserTab.toEntity(): TabEntity = TabEntity(id, url, title, profileId, groupId, _tabs.value.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: 0, createdAt)
    private fun TabEntity.toBrowserTab(): BrowserTab = BrowserTab(id, url, title, profileId, groupId, createdAt)
}
