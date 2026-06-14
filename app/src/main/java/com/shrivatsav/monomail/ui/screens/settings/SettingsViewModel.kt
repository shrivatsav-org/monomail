package com.shrivatsav.monomail.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.settings.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsDataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { settingsDataStore.setThemeMode(mode) }
    fun setFontScale(scale: FontScale) = viewModelScope.launch { settingsDataStore.setFontScale(scale) }
    fun setShowDividers(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowDividers(show) }
    fun setCompactList(compact: Boolean) = viewModelScope.launch { settingsDataStore.setCompactList(compact) }
    fun setShowSnippet(show: Boolean) = viewModelScope.launch { settingsDataStore.setShowSnippet(show) }
    fun setSwipeLeftAction(action: SwipeAction) = viewModelScope.launch { settingsDataStore.setSwipeLeftAction(action) }
    fun setSwipeRightAction(action: SwipeAction) = viewModelScope.launch { settingsDataStore.setSwipeRightAction(action) }
    fun setConfirmBeforeSending(confirm: Boolean) = viewModelScope.launch { settingsDataStore.setConfirmBeforeSending(confirm) }
    fun setDefaultReply(reply: DefaultReply) = viewModelScope.launch { settingsDataStore.setDefaultReply(reply) }
    fun setEmailNotifications(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setEmailNotifications(enabled) }
    fun setSyncFrequency(freq: SyncFrequency) = viewModelScope.launch { settingsDataStore.setSyncFrequency(freq) }
    fun setUnifiedInboxEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.setUnifiedInboxEnabled(enabled) }
}
