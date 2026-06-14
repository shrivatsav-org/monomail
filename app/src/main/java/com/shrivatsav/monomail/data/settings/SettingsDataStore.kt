package com.shrivatsav.monomail.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class FontScale { EXTRA_SMALL, SMALL, DEFAULT, LARGE, EXTRA_LARGE }
enum class SwipeAction { ARCHIVE, STAR, DELETE }
enum class DefaultReply { REPLY, REPLY_ALL }
enum class SyncFrequency { MIN_15, MIN_30, HOUR_1, MANUAL }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.DEFAULT,
    val showDividers: Boolean = false,
    val compactList: Boolean = false,
    val showSnippet: Boolean = true,
    val swipeLeftAction: SwipeAction = SwipeAction.STAR,
    val swipeRightAction: SwipeAction = SwipeAction.ARCHIVE,
    val confirmBeforeSending: Boolean = false,
    val defaultReply: DefaultReply = DefaultReply.REPLY,
    val emailNotifications: Boolean = true,
    val syncFrequency: SyncFrequency = SyncFrequency.MIN_15,
    val unifiedInboxEnabled: Boolean = false
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val SHOW_DIVIDERS = booleanPreferencesKey("show_dividers")
        val COMPACT_LIST = booleanPreferencesKey("compact_list")
        val SHOW_SNIPPET = booleanPreferencesKey("show_snippet")
        val SWIPE_LEFT = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val CONFIRM_SEND = booleanPreferencesKey("confirm_before_sending")
        val DEFAULT_REPLY = stringPreferencesKey("default_reply")
        val EMAIL_NOTIFICATIONS = booleanPreferencesKey("email_notifications")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val UNIFIED_INBOX_ENABLED = booleanPreferencesKey("unified_inbox_enabled")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            fontScale = prefs[Keys.FONT_SCALE]?.let { FontScale.valueOf(it) } ?: FontScale.DEFAULT,
            showDividers = prefs[Keys.SHOW_DIVIDERS] ?: false,
            compactList = prefs[Keys.COMPACT_LIST] ?: false,
            showSnippet = prefs[Keys.SHOW_SNIPPET] ?: true,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT]?.let { SwipeAction.valueOf(it) } ?: SwipeAction.STAR,
            swipeRightAction = prefs[Keys.SWIPE_RIGHT]?.let { SwipeAction.valueOf(it) } ?: SwipeAction.ARCHIVE,
            confirmBeforeSending = prefs[Keys.CONFIRM_SEND] ?: false,
            defaultReply = prefs[Keys.DEFAULT_REPLY]?.let { DefaultReply.valueOf(it) } ?: DefaultReply.REPLY,
            emailNotifications = prefs[Keys.EMAIL_NOTIFICATIONS] ?: true,
            syncFrequency = prefs[Keys.SYNC_FREQUENCY]?.let { SyncFrequency.valueOf(it) } ?: SyncFrequency.MIN_15,
            unifiedInboxEnabled = prefs[Keys.UNIFIED_INBOX_ENABLED] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setFontScale(scale: FontScale) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.name }
    }

    suspend fun setShowDividers(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DIVIDERS] = show }
    }

    suspend fun setCompactList(compact: Boolean) {
        context.dataStore.edit { it[Keys.COMPACT_LIST] = compact }
    }

    suspend fun setShowSnippet(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SNIPPET] = show }
    }

    suspend fun setSwipeLeftAction(action: SwipeAction) {
        context.dataStore.edit { it[Keys.SWIPE_LEFT] = action.name }
    }

    suspend fun setSwipeRightAction(action: SwipeAction) {
        context.dataStore.edit { it[Keys.SWIPE_RIGHT] = action.name }
    }

    suspend fun setConfirmBeforeSending(confirm: Boolean) {
        context.dataStore.edit { it[Keys.CONFIRM_SEND] = confirm }
    }

    suspend fun setDefaultReply(reply: DefaultReply) {
        context.dataStore.edit { it[Keys.DEFAULT_REPLY] = reply.name }
    }

    suspend fun setEmailNotifications(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EMAIL_NOTIFICATIONS] = enabled }
    }

    suspend fun setSyncFrequency(freq: SyncFrequency) {
        context.dataStore.edit { it[Keys.SYNC_FREQUENCY] = freq.name }
    }

    suspend fun setUnifiedInboxEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNIFIED_INBOX_ENABLED] = enabled }
    }
}
