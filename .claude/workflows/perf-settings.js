export const meta = {
  name: 'fix-settings-polling',
  description: 'Convert settingsFlow to StateFlow, fix foreground polling to respect sync frequency',
  phases: [{ title: 'Settings', detail: 'Settings + polling fixes' }],
}

phase('Settings')
await agent(
  "Fix two issues across two files:\n\n" +
  "FILE 1: SettingsDataStore.kt\n" +
  "Path: app/src/main/java/com/shrivatsav/monomail/data/settings/SettingsDataStore.kt\n\n" +
  "Problem: settingsFlow returns a plain Flow, not a StateFlow. Each collector gets a fresh \n" +
  "subscription. No caching of the last known value.\n\n" +
  "Fix:\n" +
  "1. Add these imports at the top:\n" +
  "   import kotlinx.coroutines.flow.SharingStarted\n" +
  "   import kotlinx.coroutines.flow.stateIn\n" +
  "   import kotlinx.coroutines.CoroutineScope\n" +
  "   import kotlinx.coroutines.Dispatchers\n" +
  "   import kotlinx.coroutines.SupervisorJob\n" +
  "   import kotlinx.coroutines.flow.StateFlow\n" +
  "2. Change val settingsFlow: Flow<AppSettings> to val settingsFlow: StateFlow<AppSettings>\n" +
  "3. Add a scope field: private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())\n" +
  "4. Append .stateIn(...) at the end of the flow definition:\n" +
  "   .stateIn(\n" +
  "       scope = scope,\n" +
  "       started = SharingStarted.Eagerly,\n" +
  "       initialValue = AppSettings()\n" +
  "   )\n" +
  "5. Remove unused import: kotlinx.coroutines.flow.Flow (replace with StateFlow)\n\n" +
  "FILE 2: InboxViewModel.kt\n" +
  "Path: app/src/main/java/com/shrivatsav/monomail/ui/screens/inbox/InboxViewModel.kt\n\n" +
  "Problem: startForegroundPolling() hardcodes 120_000ms = 2 minutes, ignoring the user's \n" +
  "syncFrequency setting.\n\n" +
  "Fix:\n" +
  "1. Add a field:\n" +
  "   private var pollingIntervalMs = 120_000L\n" +
  "2. In the settings collection coroutine (where settingsFlow is collected), add:\n" +
  "   pollingIntervalMs = when (settings.syncFrequency) {\n" +
  "       SyncFrequency.MIN_15 -> 15 * 60 * 1000L\n" +
  "       SyncFrequency.MIN_30 -> 30 * 60 * 1000L\n" +
  "       SyncFrequency.HOUR_1 -> 60 * 60 * 1000L\n" +
  "       SyncFrequency.MANUAL -> Long.MAX_VALUE\n" +
  "   }\n" +
  "3. If SyncFrequency import is not present, add:\n" +
  "   import com.shrivatsav.monomail.data.settings.SyncFrequency\n" +
  "4. Update startForegroundPolling to use pollingIntervalMs:\n" +
  "   private fun startForegroundPolling() {\n" +
  "       viewModelScope.launch {\n" +
  "           while (true) {\n" +
  "               delay(pollingIntervalMs)\n" +
  "               if (pollingIntervalMs == Long.MAX_VALUE) continue\n" +
  "               repository.refreshInbox(InboxTab.INBOX)\n" +
  "               if (_currentTab.value != InboxTab.INBOX && _currentTab.value != InboxTab.UNIFIED) {\n" +
  "                   repository.refreshInbox(_currentTab.value)\n" +
  "               }\n" +
  "           }\n" +
  "       }\n" +
  "   }\n\n" +
  "Read each file first before making changes.",
  { label: 'Settings + Polling', phase: 'Settings', isolation: 'worktree' }
)
