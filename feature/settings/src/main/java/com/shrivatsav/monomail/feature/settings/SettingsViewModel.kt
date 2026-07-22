package com.shrivatsav.monomail.feature.settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.core.data.settings.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import javax.inject.Inject
enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, ERROR }
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    fun setFontScale(scale: FontScale) = viewModelScope.launch { settingsDataStore.setFontScale(scale) }
    fun setUseSystemFont(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setUseSystemFont(enabled) }
    fun setShowDividers(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowDividers(show) }
    fun setCompactList(compact: Boolean) = viewModelScope.launch { settingsDataStore.setCompactList(compact) }
    fun setShowSnippet(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowSnippet(show) }
    fun setSwipeLeftAction(action: SwipeAction) = viewModelScope.launch { settingsDataStore.setSwipeLeftAction(action) }
    fun setSwipeRightAction(action: SwipeAction) = viewModelScope.launch { settingsDataStore.setSwipeRightAction(action) }
    fun setConfirmBeforeSending(confirm: Boolean) = viewModelScope.launch { settingsDataStore.setConfirmBeforeSending(confirm) }
    fun setDefaultReply(reply: DefaultReply) = viewModelScope.launch { settingsDataStore.setDefaultReply(reply) }
    fun setDisabledNotificationAccounts(accounts: Set<String>) = viewModelScope.launch { settingsDataStore.setDisabledNotificationAccounts(accounts) }
    fun setSyncFrequency(freq: SyncFrequency) = viewModelScope.launch { settingsDataStore.setSyncFrequency(freq) }
    fun setUnifiedInboxEnabled(enabled: Boolean) = viewModelScope.launch {
        settingsDataStore.setUnifiedInboxEnabled(enabled)
        if (!enabled) {
            val currentConfig = settings.value.dockConfig
            val filtered = currentConfig.primaryTabs.filter { it != DockTabId.UNIFIED }
            if (filtered != currentConfig.primaryTabs) {
                settingsDataStore.setDockConfig(DockConfig(primaryTabs = filtered))
            }
        }
    }
    fun setSmartGroupingEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setSmartGroupingEnabled(enabled) }
    fun setSmartGroupingRecentOnly(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setSmartGroupingRecentOnly(enabled) }
    fun setOrganizeByThread(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setOrganizeByThread(enabled) }
    fun setLoadRemoteImages(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setLoadRemoteImages(enabled) }
    fun setRenderMarkdown(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setRenderMarkdown(enabled) }
    fun setEmailTheme(theme: EmailTheme) = viewModelScope.launch { settingsDataStore.setEmailTheme(theme) }
    fun setNavScale(scale: Float) = viewModelScope.launch { settingsDataStore.setNavScale(scale) }
    fun setUndoSendEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setUndoSendEnabled(enabled) }
    fun setUndoSendWindow(window: UndoSendWindow) = viewModelScope.launch { settingsDataStore.setUndoSendWindow(window) }
    fun setDockConfig(config: DockConfig) = viewModelScope.launch { settingsDataStore.setDockConfig(config) }
    fun setDeveloperMode(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDeveloperMode(enabled) }
    fun setDemoSmartFolders(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setDemoSmartFolders(enabled) }
    fun setShowInlineImages(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setShowInlineImages(enabled) }
    fun setShowInlineAttachments(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setShowInlineAttachments(enabled) }
    fun setShowMarkAllRead(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setShowMarkAllRead(enabled) }
    fun setMonochromeTheme(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setMonochromeTheme(enabled) }
    fun setCornerStyle(style: com.shrivatsav.monomail.core.data.settings.CornerStyle) = viewModelScope.launch { settingsDataStore.setCornerStyle(style) }
    fun setSwipeThreshold(threshold: Float) = viewModelScope.launch { settingsDataStore.setSwipeThreshold(threshold) }
    fun resetWelcomePrompt() = viewModelScope.launch {
        settingsDataStore.setHasSeenWelcomePrompt(false)
    }
    val templates = settingsDataStore.templatesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveTemplates(templates: List<EmailTemplate>) = viewModelScope.launch {
        settingsDataStore.saveTemplates(templates)
    }
    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState = _updateState.asStateFlow()
    private val _latestVersionUrl = MutableStateFlow<String?>(null)
    val latestVersionUrl = _latestVersionUrl.asStateFlow()
    fun checkForUpdates(currentVersion: String) {
        if (_updateState.value == UpdateState.CHECKING) return
        _updateState.value = UpdateState.CHECKING
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = URI("https://api.github.com/repos/shrivatsav-0/monomail/releases/latest").toURL().readText()
                val json = JSONObject(response)
                val tagName = json.getString("tag_name").removePrefix("v")
                val htmlUrl = json.getString("html_url")
                withContext(Dispatchers.Main) {
                    if (isVersionGreater(tagName, currentVersion)) {
                        _latestVersionUrl.value = htmlUrl
                        _updateState.value = UpdateState.UPDATE_AVAILABLE
                    } else {
                        _updateState.value = UpdateState.UP_TO_DATE
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _updateState.value = UpdateState.ERROR
                }
            }
        }
    }
    private fun isVersionGreater(latest: String, current: String): Boolean {
        val lParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val l = lParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
