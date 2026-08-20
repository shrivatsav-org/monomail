package com.shrivatsav.monomail.core.data.settings
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")
enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CornerStyle { SQUARE, ROUNDED, EXTRA_ROUNDED }
/**
 * Controls how email HTML bodies are colored when rendered in the detail WebView.
 * - [AUTO]: adapt plain-ish mail to the app theme; render richly-styled mail as-is.
 * - [ORIGINAL]: never adapt — show the sender's original colors on a light card.
 */
enum class EmailTheme { AUTO, ORIGINAL }
enum class FontScale { EXTRA_SMALL, SMALL, DEFAULT, LARGE, EXTRA_LARGE }
enum class SwipeAction { ARCHIVE, STAR, DELETE, READ_UNREAD }
enum class DefaultReply { REPLY, REPLY_ALL }
enum class SyncFrequency { MIN_15, MIN_30, HOUR_1, MANUAL }
enum class UndoSendWindow(val seconds: Int) { SEC_5(5), SEC_10(10), SEC_20(20), SEC_30(30) }
/** Sound behavior for a per-account notification channel. */
enum class NotificationSound { DEFAULT, SILENT }
/** Alert level for a per-account notification channel (maps to channel importance). */
enum class NotificationImportance { DEFAULT, URGENT, SILENT }
/** How much email content appears on the lock screen (maps to Notification visibility). */
enum class NotificationPreview { FULL, PRIVATE, NONE }
/**
 * Per-account notification behavior. Stored as one Gson JSON string per account
 * under a `notification_profile_<accountId>` preference key.
 *
 * Defaults preserve the pre-customization behavior: system sound + vibration,
 * default importance, full content preview, badge shown.
 */
data class NotificationProfile(
    val sound: NotificationSound = NotificationSound.DEFAULT,
    val vibrate: Boolean = true,
    val importance: NotificationImportance = NotificationImportance.DEFAULT,
    val preview: NotificationPreview = NotificationPreview.FULL,
    val badge: Boolean = true,
) {
    companion object {
        fun defaults() = NotificationProfile()
    }
}
enum class DockTabId { UNIFIED, INBOX, SENT, ARCHIVED, SNOOZED, STARRED, TRASH, SPAM }
data class DockConfig(
    val primaryTabs: List<DockTabId> = listOf(
        DockTabId.INBOX, DockTabId.SENT, DockTabId.ARCHIVED
    )
) {
    companion object {
        const val MAX_SLOTS = 4
        fun defaults() = DockConfig()
    }
}
data class EmailTemplate(
    val name: String,
    val subject: String,
    val body: String
)
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val fontScale: FontScale = FontScale.DEFAULT,
    val useSystemFont: Boolean = false,
    val showDividers: Boolean = false,
    val compactList: Boolean = true,
    val showSnippet: Boolean = false,
    val swipeLeftAction: SwipeAction = SwipeAction.READ_UNREAD,
    val swipeRightAction: SwipeAction = SwipeAction.ARCHIVE,
    val confirmBeforeSending: Boolean = false,
    val defaultReply: DefaultReply = DefaultReply.REPLY,
    val notificationQuickActions: Set<String> = setOf("reply", "archive", "delete", "snooze"),
    val disabledNotificationAccounts: Set<String> = emptySet(),
    val syncFrequency: SyncFrequency = SyncFrequency.MIN_15,
    val unifiedInboxEnabled: Boolean = false,
    val hasSeenWelcomePrompt: Boolean = false,
    val smartGroupingEnabled: Boolean = true,
    val smartGroupingRecentOnly: Boolean = true,
    val organizeByThread: Boolean = true,
    val loadRemoteImages: Boolean = false,
    val renderMarkdown: Boolean = false,
    val emailTheme: EmailTheme = EmailTheme.AUTO,
    val navScale: Float = 1f,
    val undoSendEnabled: Boolean = true,
    val undoSendWindow: UndoSendWindow = UndoSendWindow.SEC_10,
    val dockConfig: DockConfig = DockConfig.defaults(),
    val isDeveloperMode: Boolean = false,
    val showInlineImages: Boolean = true,
    val showInlineAttachments: Boolean = true,
    val cornerStyle: CornerStyle = CornerStyle.ROUNDED,
    val showMarkAllRead: Boolean = true,
    val monochromeTheme: Boolean = false,
    val swipeThreshold: Float = 0.40f,
    val addSignature: Boolean = true,
    val use24HourTime: Boolean = false,
)
class SettingsDataStore(private val context: Context) {
    private val gson = Gson()

    /** Scope that starts reading DataStore during singleton construction. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val USE_SYSTEM_FONT = booleanPreferencesKey("use_system_font")
        val SHOW_DIVIDERS = booleanPreferencesKey("show_dividers")
        val COMPACT_LIST = booleanPreferencesKey("compact_list")
        val SHOW_SNIPPET = booleanPreferencesKey("show_snippet")
        val SWIPE_LEFT = stringPreferencesKey("swipe_left_action")
        val SWIPE_RIGHT = stringPreferencesKey("swipe_right_action")
        val CONFIRM_SEND = booleanPreferencesKey("confirm_before_sending")
        val DEFAULT_REPLY = stringPreferencesKey("default_reply")
        val NOTIFICATION_QUICK_ACTIONS = stringSetPreferencesKey("notification_quick_actions")
        val DISABLED_NOTIF_ACCOUNTS = stringSetPreferencesKey("disabled_notif_accounts")
        val SYNC_FREQUENCY = stringPreferencesKey("sync_frequency")
        val UNIFIED_INBOX_ENABLED = booleanPreferencesKey("unified_inbox_enabled")
        val HAS_SEEN_WELCOME_PROMPT = booleanPreferencesKey("has_seen_welcome_prompt")
        val SMART_GROUPING_ENABLED = booleanPreferencesKey("smart_grouping_enabled")
        val SMART_GROUPING_RECENT_ONLY = booleanPreferencesKey("smart_grouping_recent_only")
        val ORGANIZE_BY_THREAD = booleanPreferencesKey("organize_by_thread")
        val LOAD_REMOTE_IMAGES = booleanPreferencesKey("load_remote_images")
        val RENDER_MARKDOWN = booleanPreferencesKey("render_markdown")
        val EMAIL_THEME = stringPreferencesKey("email_theme")
        val NAV_SCALE = floatPreferencesKey("nav_scale")
        val UNDO_SEND_ENABLED = booleanPreferencesKey("undo_send_enabled")
        val UNDO_SEND_WINDOW = stringPreferencesKey("undo_send_window")
        val TEMPLATES = stringPreferencesKey("email_templates")
        val DOCK_CONFIG = stringPreferencesKey("dock_config")
        val IS_DEVELOPER_MODE = booleanPreferencesKey("is_developer_mode")
        val SHOW_INLINE_ATTACHMENTS = booleanPreferencesKey("show_inline_attachments")
        val SHOW_MARK_ALL_READ = booleanPreferencesKey("show_mark_all_read")
        val CORNER_STYLE = stringPreferencesKey("corner_style")
        val SHOW_INLINE_IMAGES = booleanPreferencesKey("show_inline_images")
        val MONOCHROME_THEME = booleanPreferencesKey("monochrome_theme")
        val SWIPE_THRESHOLD = floatPreferencesKey("swipe_threshold")
        val ADD_SIGNATURE = booleanPreferencesKey("add_signature")
        val USE_24_HOUR_TIME = booleanPreferencesKey("use_24_hour_time")
        fun notificationProfileKey(accountId: String) =
            stringPreferencesKey("notification_profile_$accountId")
    }
    private fun mapToSettings(prefs: Preferences): AppSettings {
        val dockConfigJson = prefs[Keys.DOCK_CONFIG]
        return AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { ThemeMode.valueOf(it) } ?: ThemeMode.SYSTEM,
            fontScale = prefs[Keys.FONT_SCALE]?.let { FontScale.valueOf(it) } ?: FontScale.DEFAULT,
            useSystemFont = prefs[Keys.USE_SYSTEM_FONT] ?: false,
            showDividers = prefs[Keys.SHOW_DIVIDERS] ?: false,
            compactList = prefs[Keys.COMPACT_LIST] ?: true,
            showSnippet = prefs[Keys.SHOW_SNIPPET] ?: false,
            swipeLeftAction = prefs[Keys.SWIPE_LEFT]?.let { SwipeAction.valueOf(it) } ?: SwipeAction.READ_UNREAD,
            swipeRightAction = prefs[Keys.SWIPE_RIGHT]?.let { SwipeAction.valueOf(it) } ?: SwipeAction.ARCHIVE,
            confirmBeforeSending = prefs[Keys.CONFIRM_SEND] ?: false,
            defaultReply = prefs[Keys.DEFAULT_REPLY]?.let { DefaultReply.valueOf(it) } ?: DefaultReply.REPLY,
            notificationQuickActions = prefs[Keys.NOTIFICATION_QUICK_ACTIONS]
                ?: setOf("reply", "archive", "delete", "snooze"),
            disabledNotificationAccounts = prefs[Keys.DISABLED_NOTIF_ACCOUNTS] ?: emptySet(),
            syncFrequency = prefs[Keys.SYNC_FREQUENCY]?.let { SyncFrequency.valueOf(it) } ?: SyncFrequency.MIN_15,
            unifiedInboxEnabled = prefs[Keys.UNIFIED_INBOX_ENABLED] ?: false,
            hasSeenWelcomePrompt = prefs[Keys.HAS_SEEN_WELCOME_PROMPT] ?: false,
            smartGroupingEnabled = prefs[Keys.SMART_GROUPING_ENABLED] ?: true,
            smartGroupingRecentOnly = prefs[Keys.SMART_GROUPING_RECENT_ONLY] ?: true,
            organizeByThread = prefs[Keys.ORGANIZE_BY_THREAD] ?: true,
            loadRemoteImages = prefs[Keys.LOAD_REMOTE_IMAGES] ?: true,
            renderMarkdown = prefs[Keys.RENDER_MARKDOWN] ?: false,
            emailTheme = prefs[Keys.EMAIL_THEME]?.let {
                try { EmailTheme.valueOf(it) } catch (e: Exception) { EmailTheme.AUTO }
            } ?: EmailTheme.AUTO,
            navScale = prefs[Keys.NAV_SCALE] ?: 1f,
            undoSendEnabled = prefs[Keys.UNDO_SEND_ENABLED] ?: true,
            undoSendWindow = prefs[Keys.UNDO_SEND_WINDOW]?.let { UndoSendWindow.valueOf(it) } ?: UndoSendWindow.SEC_10,
            dockConfig = dockConfigJson?.let { json ->
                try {
                    val raw = gson.fromJson(json, DockConfig::class.java)
                    // Gson may silently produce broken objects (null/wrong-type primaryTabs)
                    // when deserializing Kotlin data classes via Unsafe. Validate the result.
                    val tabs = raw?.primaryTabs
                    @Suppress("SENSELESS_COMPARISON") // Gson Unsafe may produce wrong-type elements at runtime
                    if (tabs != null && tabs.isNotEmpty() && tabs.all { it is DockTabId }) raw
                    else DockConfig.defaults()
                } catch (e: Exception) { DockConfig.defaults() }
            } ?: DockConfig.defaults(),
            isDeveloperMode = prefs[Keys.IS_DEVELOPER_MODE] ?: false,
            showInlineImages = prefs[Keys.SHOW_INLINE_IMAGES] ?: true,
            showInlineAttachments = prefs[Keys.SHOW_INLINE_ATTACHMENTS] ?: true,
            cornerStyle = prefs[Keys.CORNER_STYLE]?.let { CornerStyle.valueOf(it) } ?: CornerStyle.ROUNDED,
            monochromeTheme = prefs[Keys.MONOCHROME_THEME] ?: false,
            swipeThreshold = prefs[Keys.SWIPE_THRESHOLD]?.coerceIn(0.20f, 0.60f) ?: 0.40f,
            addSignature = prefs[Keys.ADD_SIGNATURE] ?: true,
            use24HourTime = prefs[Keys.USE_24_HOUR_TIME] ?: false,
        )
    }
    private fun parseNotificationProfile(json: String?): NotificationProfile {
        if (json == null) return NotificationProfile.defaults()
        return try {
            val raw = gson.fromJson(json, NotificationProfile::class.java)
            val sound = raw?.sound
            val vibrate = raw?.vibrate
            val importance = raw?.importance
            val preview = raw?.preview
            val badge = raw?.badge
            if (sound != null && vibrate != null && importance != null && preview != null && badge != null) {
                NotificationProfile(sound, vibrate, importance, preview, badge)
            } else {
                NotificationProfile.defaults()
            }
        } catch (e: Exception) {
            NotificationProfile.defaults()
        }
    }

    /** Per-account notification profile as a reactive flow (for the settings UI). */
    fun notificationProfileFlow(accountId: String): Flow<NotificationProfile> =
        context.dataStore.data.map { prefs ->
            parseNotificationProfile(prefs[Keys.notificationProfileKey(accountId)])
        }

    /** One-shot profile read for workers (settingsFlow is pre-heated but per-account keys aren't). */
    suspend fun getNotificationProfile(accountId: String): NotificationProfile =
        parseNotificationProfile(context.dataStore.data.first()[Keys.notificationProfileKey(accountId)])

    suspend fun setNotificationProfile(accountId: String, profile: NotificationProfile) {
        context.dataStore.edit { it[Keys.notificationProfileKey(accountId)] = gson.toJson(profile) }
    }

    /**
     * Pre-heated settings flow — subscribes to DataStore immediately during singleton
     * construction so that the first collector in [MainActivity] gets the cached value
     * instead of [AppSettings] defaults.
     */
    val settingsFlow: StateFlow<AppSettings> = context.dataStore.data
        .map { mapToSettings(it) }
        .stateIn(scope, SharingStarted.Eagerly, AppSettings())
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }
    suspend fun setFontScale(scale: FontScale) {
        context.dataStore.edit { it[Keys.FONT_SCALE] = scale.name }
    }
    suspend fun setUseSystemFont(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_SYSTEM_FONT] = enabled }
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
    suspend fun setAddSignature(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ADD_SIGNATURE] = enabled }
    }
    suspend fun setDefaultReply(reply: DefaultReply) {
        context.dataStore.edit { it[Keys.DEFAULT_REPLY] = reply.name }
    }
    suspend fun setDisabledNotificationAccounts(accounts: Set<String>) {
        context.dataStore.edit { it[Keys.DISABLED_NOTIF_ACCOUNTS] = accounts }
    }
    suspend fun setNotificationQuickActions(actions: Set<String>) {
        context.dataStore.edit { it[Keys.NOTIFICATION_QUICK_ACTIONS] = actions }
    }
    suspend fun setSyncFrequency(freq: SyncFrequency) {
        context.dataStore.edit { it[Keys.SYNC_FREQUENCY] = freq.name }
    }
    suspend fun setUnifiedInboxEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNIFIED_INBOX_ENABLED] = enabled }
    }
    suspend fun setHasSeenWelcomePrompt(seen: Boolean) {
        context.dataStore.edit { it[Keys.HAS_SEEN_WELCOME_PROMPT] = seen }
    }
    suspend fun setSmartGroupingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_GROUPING_ENABLED] = enabled }
    }
    suspend fun setSmartGroupingRecentOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SMART_GROUPING_RECENT_ONLY] = enabled }
    }
    suspend fun setOrganizeByThread(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ORGANIZE_BY_THREAD] = enabled }
    }
    suspend fun setLoadRemoteImages(enabled: Boolean) {
        context.dataStore.edit { it[Keys.LOAD_REMOTE_IMAGES] = enabled }
    }
    suspend fun setRenderMarkdown(enabled: Boolean) {
        context.dataStore.edit { it[Keys.RENDER_MARKDOWN] = enabled }
    }
    suspend fun setEmailTheme(theme: EmailTheme) {
        context.dataStore.edit { it[Keys.EMAIL_THEME] = theme.name }
    }
    suspend fun setNavScale(scale: Float) {
        context.dataStore.edit { it[Keys.NAV_SCALE] = scale }
    }
    suspend fun setUndoSendEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UNDO_SEND_ENABLED] = enabled }
    }
    suspend fun setUndoSendWindow(window: UndoSendWindow) {
        context.dataStore.edit { it[Keys.UNDO_SEND_WINDOW] = window.name }
    }
    suspend fun setDockConfig(config: DockConfig) {
        context.dataStore.edit { it[Keys.DOCK_CONFIG] = gson.toJson(config) }
    }
    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_DEVELOPER_MODE] = enabled }
    }
    suspend fun setShowInlineImages(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_INLINE_IMAGES] = enabled }
    }
    suspend fun setShowInlineAttachments(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_INLINE_ATTACHMENTS] = enabled }
    }
    suspend fun setShowMarkAllRead(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_MARK_ALL_READ] = enabled }
    }
    suspend fun setCornerStyle(style: CornerStyle) {
        context.dataStore.edit { it[Keys.CORNER_STYLE] = style.name }
    }

    suspend fun setMonochromeTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MONOCHROME_THEME] = enabled }
    }
    suspend fun setSwipeThreshold(threshold: Float) {
        context.dataStore.edit { it[Keys.SWIPE_THRESHOLD] = threshold.coerceIn(0.20f, 0.60f) }
    }
    suspend fun setUse24HourTime(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_24_HOUR_TIME] = enabled }
    }
    suspend fun getTemplates(): List<EmailTemplate> {
        val prefs = context.dataStore.data.first()
        val json = prefs[Keys.TEMPLATES] ?: return emptyList()
        return try {
            val type = object : TypeToken<Array<EmailTemplate>>() {}.type
            gson.fromJson<Array<EmailTemplate>>(json, type).toList()
        } catch (e: Exception) { emptyList() }
    }
    suspend fun saveTemplates(templates: List<EmailTemplate>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEMPLATES] = gson.toJson(templates)
        }
    }
    val templatesFlow: Flow<List<EmailTemplate>> = context.dataStore.data.map { prefs ->
        val json = prefs[Keys.TEMPLATES] ?: return@map emptyList()
        try {
            val type = object : TypeToken<Array<EmailTemplate>>() {}.type
            gson.fromJson<Array<EmailTemplate>>(json, type).toList()
        } catch (e: Exception) { emptyList() }
    }
}
