package com.shrivatsav.monomail.shared.data.settings

import com.shrivatsav.monomail.shared.platform.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class FontScale { EXTRA_SMALL, SMALL, DEFAULT, LARGE, EXTRA_LARGE }
enum class SwipeAction { ARCHIVE, STAR, DELETE, READ_UNREAD }
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
    val unifiedInboxEnabled: Boolean = false,
    val hasSeenDonationPrompt: Boolean = false,
    val smartGroupingEnabled: Boolean = true,
    val smartGroupingRecentOnly: Boolean = false,
    val organizeByThread: Boolean = true,
    val navScale: Float = 1f
)

/**
 * Multiplatform settings store backed by [KeyValueStore] (NSUserDefaults / SharedPreferences),
 * replacing the Android DataStore original. Reactive via an in-memory StateFlow.
 */
class SettingsRepository(private val store: KeyValueStore) {
    private object Keys {
        const val THEME_MODE = "theme_mode"
        const val FONT_SCALE = "font_scale"
        const val SHOW_DIVIDERS = "show_dividers"
        const val COMPACT_LIST = "compact_list"
        const val SHOW_SNIPPET = "show_snippet"
        const val SWIPE_LEFT = "swipe_left_action"
        const val SWIPE_RIGHT = "swipe_right_action"
        const val CONFIRM_SEND = "confirm_before_sending"
        const val DEFAULT_REPLY = "default_reply"
        const val EMAIL_NOTIFICATIONS = "email_notifications"
        const val SYNC_FREQUENCY = "sync_frequency"
        const val UNIFIED_INBOX = "unified_inbox_enabled"
        const val DONATION_PROMPT = "has_seen_donation_prompt"
        const val SMART_GROUPING = "smart_grouping_enabled"
        const val SMART_GROUPING_RECENT = "smart_grouping_recent_only"
        const val ORGANIZE_BY_THREAD = "organize_by_thread"
        const val NAV_SCALE = "nav_scale"
    }

    private val _settings = MutableStateFlow(load())
    val settingsFlow: StateFlow<AppSettings> = _settings.asStateFlow()

    private inline fun <reified T : Enum<T>> enumOf(raw: String?, default: T): T =
        raw?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private fun load(): AppSettings = AppSettings(
        themeMode = enumOf(store.getString(Keys.THEME_MODE), ThemeMode.SYSTEM),
        fontScale = enumOf(store.getString(Keys.FONT_SCALE), FontScale.DEFAULT),
        showDividers = store.getBoolean(Keys.SHOW_DIVIDERS) ?: false,
        compactList = store.getBoolean(Keys.COMPACT_LIST) ?: false,
        showSnippet = store.getBoolean(Keys.SHOW_SNIPPET) ?: true,
        swipeLeftAction = enumOf(store.getString(Keys.SWIPE_LEFT), SwipeAction.STAR),
        swipeRightAction = enumOf(store.getString(Keys.SWIPE_RIGHT), SwipeAction.ARCHIVE),
        confirmBeforeSending = store.getBoolean(Keys.CONFIRM_SEND) ?: false,
        defaultReply = enumOf(store.getString(Keys.DEFAULT_REPLY), DefaultReply.REPLY),
        emailNotifications = store.getBoolean(Keys.EMAIL_NOTIFICATIONS) ?: true,
        syncFrequency = enumOf(store.getString(Keys.SYNC_FREQUENCY), SyncFrequency.MIN_15),
        unifiedInboxEnabled = store.getBoolean(Keys.UNIFIED_INBOX) ?: false,
        hasSeenDonationPrompt = store.getBoolean(Keys.DONATION_PROMPT) ?: false,
        smartGroupingEnabled = store.getBoolean(Keys.SMART_GROUPING) ?: true,
        smartGroupingRecentOnly = store.getBoolean(Keys.SMART_GROUPING_RECENT) ?: false,
        organizeByThread = store.getBoolean(Keys.ORGANIZE_BY_THREAD) ?: true,
        navScale = store.getFloat(Keys.NAV_SCALE) ?: 1f
    )

    private fun reload() { _settings.value = load() }

    fun setThemeMode(mode: ThemeMode) { store.putString(Keys.THEME_MODE, mode.name); reload() }
    fun setFontScale(scale: FontScale) { store.putString(Keys.FONT_SCALE, scale.name); reload() }
    fun setShowDividers(v: Boolean) { store.putBoolean(Keys.SHOW_DIVIDERS, v); reload() }
    fun setCompactList(v: Boolean) { store.putBoolean(Keys.COMPACT_LIST, v); reload() }
    fun setShowSnippet(v: Boolean) { store.putBoolean(Keys.SHOW_SNIPPET, v); reload() }
    fun setSwipeLeftAction(a: SwipeAction) { store.putString(Keys.SWIPE_LEFT, a.name); reload() }
    fun setSwipeRightAction(a: SwipeAction) { store.putString(Keys.SWIPE_RIGHT, a.name); reload() }
    fun setConfirmBeforeSending(v: Boolean) { store.putBoolean(Keys.CONFIRM_SEND, v); reload() }
    fun setDefaultReply(r: DefaultReply) { store.putString(Keys.DEFAULT_REPLY, r.name); reload() }
    fun setEmailNotifications(v: Boolean) { store.putBoolean(Keys.EMAIL_NOTIFICATIONS, v); reload() }
    fun setSyncFrequency(f: SyncFrequency) { store.putString(Keys.SYNC_FREQUENCY, f.name); reload() }
    fun setUnifiedInboxEnabled(v: Boolean) { store.putBoolean(Keys.UNIFIED_INBOX, v); reload() }
    fun setHasSeenDonationPrompt(v: Boolean) { store.putBoolean(Keys.DONATION_PROMPT, v); reload() }
    fun setSmartGroupingEnabled(v: Boolean) { store.putBoolean(Keys.SMART_GROUPING, v); reload() }
    fun setSmartGroupingRecentOnly(v: Boolean) { store.putBoolean(Keys.SMART_GROUPING_RECENT, v); reload() }
    fun setOrganizeByThread(v: Boolean) { store.putBoolean(Keys.ORGANIZE_BY_THREAD, v); reload() }
    fun setNavScale(v: Float) { store.putFloat(Keys.NAV_SCALE, v); reload() }
}
