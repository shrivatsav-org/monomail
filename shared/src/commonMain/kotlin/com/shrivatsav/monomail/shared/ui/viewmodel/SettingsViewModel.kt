package com.shrivatsav.monomail.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.shared.data.remote.createJsonHttpClient
import com.shrivatsav.monomail.shared.data.settings.AppSettings
import com.shrivatsav.monomail.shared.data.settings.DefaultReply
import com.shrivatsav.monomail.shared.data.settings.FontScale
import com.shrivatsav.monomail.shared.data.settings.SettingsRepository
import com.shrivatsav.monomail.shared.data.settings.SwipeAction
import com.shrivatsav.monomail.shared.data.settings.SyncFrequency
import com.shrivatsav.monomail.shared.data.settings.ThemeMode
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class UpdateState { IDLE, CHECKING, UP_TO_DATE, UPDATE_AVAILABLE, ERROR }

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String
)

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repository.settingsFlow

    fun setThemeMode(mode: ThemeMode) = repository.setThemeMode(mode)
    fun setFontScale(scale: FontScale) = repository.setFontScale(scale)
    fun setShowDividers(v: Boolean) = repository.setShowDividers(v)
    fun setCompactList(v: Boolean) = repository.setCompactList(v)
    fun setShowSnippet(v: Boolean) = repository.setShowSnippet(v)
    fun setSwipeLeftAction(a: SwipeAction) = repository.setSwipeLeftAction(a)
    fun setSwipeRightAction(a: SwipeAction) = repository.setSwipeRightAction(a)
    fun setConfirmBeforeSending(v: Boolean) = repository.setConfirmBeforeSending(v)
    fun setDefaultReply(r: DefaultReply) = repository.setDefaultReply(r)
    fun setEmailNotifications(v: Boolean) = repository.setEmailNotifications(v)
    fun setSyncFrequency(f: SyncFrequency) = repository.setSyncFrequency(f)
    fun setUnifiedInboxEnabled(v: Boolean) = repository.setUnifiedInboxEnabled(v)
    fun setSmartGroupingEnabled(v: Boolean) = repository.setSmartGroupingEnabled(v)
    fun setSmartGroupingRecentOnly(v: Boolean) = repository.setSmartGroupingRecentOnly(v)
    fun setOrganizeByThread(v: Boolean) = repository.setOrganizeByThread(v)
    fun setNavScale(v: Float) = repository.setNavScale(v)

    private val _updateState = MutableStateFlow(UpdateState.IDLE)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()
    private val _latestVersionUrl = MutableStateFlow<String?>(null)
    val latestVersionUrl: StateFlow<String?> = _latestVersionUrl.asStateFlow()

    fun checkForUpdates(currentVersion: String) {
        if (_updateState.value == UpdateState.CHECKING) return
        _updateState.value = UpdateState.CHECKING
        viewModelScope.launch {
            try {
                val client = createJsonHttpClient()
                val release: GithubRelease =
                    client.get("https://api.github.com/repos/shrivatsav-0/monomail/releases/latest").body()
                client.close()
                val tag = release.tagName.removePrefix("v")
                if (isVersionGreater(tag, currentVersion)) {
                    _latestVersionUrl.value = release.htmlUrl
                    _updateState.value = UpdateState.UPDATE_AVAILABLE
                } else {
                    _updateState.value = UpdateState.UP_TO_DATE
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.ERROR
            }
        }
    }

    private fun isVersionGreater(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
