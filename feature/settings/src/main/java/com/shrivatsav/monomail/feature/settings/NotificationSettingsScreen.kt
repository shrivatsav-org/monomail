package com.shrivatsav.monomail.feature.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.feature.settings.BuildConfig
import com.shrivatsav.monomail.core.data.settings.*
import com.shrivatsav.monomail.core.data.worker.NotificationActionReceiver

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsScreen(
    authManager: AuthManager,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val accounts by authManager.accountsFlow.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val isPlayStoreBuild = !BuildConfig.IS_GITHUB_BUILD

    ScrollableSettingsScaffold(title = "Notifications", onBack = onBack) {
        if (!isPlayStoreBuild) {
            SettingsCard {
                BottomSheetPickerRow(
                    icon = Icons.Rounded.Sync,
                    title = "Sync Frequency",
                    currentValue = settings.syncFrequency.displayName(),
                    options = SyncFrequency.entries.map { it.displayName() },
                    onSelected = { idx -> viewModel.setSyncFrequency(SyncFrequency.entries[idx]) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        if (accounts.isNotEmpty()) {
            GroupLabel(text = "Accounts", modifier = Modifier.padding(horizontal = 16.dp))
            SettingsCard {
                accounts.forEachIndexed { index, account ->
                    val isEnabled = !settings.disabledNotificationAccounts.contains(account.id)
                    SettingsToggleRow(
                        icon = Icons.Rounded.AccountCircle,
                        title = account.email,
                        subtitle = "Enable notifications",
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            val newSet = settings.disabledNotificationAccounts.toMutableSet()
                            if (checked) newSet.remove(account.id) else newSet.add(account.id)
                            viewModel.setDisabledNotificationAccounts(newSet)
                        }
                    )
                    
                    if (isEnabled) {
                        val profile by viewModel.notificationProfileFlow(account.id)
                            .collectAsState(initial = NotificationProfile.defaults())
                        CardDivider()
                        BottomSheetPickerRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "Alert style",
                            currentValue = profile.importance.displayName(),
                            options = NotificationImportance.entries.map { it.displayName() },
                            onSelected = { idx ->
                                viewModel.setNotificationProfile(
                                    account.id, profile.copy(importance = NotificationImportance.entries[idx])
                                )
                            }
                        )
                        CardDivider()
                        BottomSheetPickerRow(
                            icon = Icons.Rounded.VolumeUp,
                            title = "Sound",
                            currentValue = profile.sound.displayName(),
                            options = NotificationSound.entries.map { it.displayName() },
                            onSelected = { idx ->
                                viewModel.setNotificationProfile(
                                    account.id, profile.copy(sound = NotificationSound.entries[idx])
                                )
                            }
                        )
                        CardDivider()
                        SettingsToggleRow(
                            icon = Icons.Rounded.Vibration,
                            title = "Vibrate",
                            subtitle = "Vibrate when a notification arrives",
                            checked = profile.vibrate,
                            onCheckedChange = { checked ->
                                viewModel.setNotificationProfile(account.id, profile.copy(vibrate = checked))
                            }
                        )
                        CardDivider()
                        BottomSheetPickerRow(
                            icon = Icons.Rounded.Lock,
                            title = "Lock screen preview",
                            currentValue = profile.preview.displayName(),
                            options = NotificationPreview.entries.map { it.displayName() },
                            onSelected = { idx ->
                                viewModel.setNotificationProfile(
                                    account.id, profile.copy(preview = NotificationPreview.entries[idx])
                                )
                            }
                        )
                        CardDivider()
                        SettingsToggleRow(
                            icon = Icons.Rounded.Badge,
                            title = "App icon badge",
                            subtitle = "Show the app icon badge with a count",
                            checked = profile.badge,
                            onCheckedChange = { checked ->
                                viewModel.setNotificationProfile(account.id, profile.copy(badge = checked))
                            }
                        )
                        CardDivider()
                        InfoRow(
                            icon = Icons.Rounded.Tune,
                            title = "System channel settings",
                            value = "",
                            onClick = {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, "monomail_${account.id}")
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }

                    if (index < accounts.lastIndex) {
                        CardDivider()
                    }
                }
            }
        }

        GroupLabel(text = "Quick Actions", modifier = Modifier.padding(horizontal = 16.dp))
        SettingsCard {
            val quickActions = settings.notificationQuickActions
            val rows = listOf(
                Triple(Icons.Rounded.Reply, "Reply", "reply"),
                Triple(Icons.Rounded.Archive, "Archive", "archive"),
                Triple(Icons.Rounded.Delete, "Trash", "delete"),
                Triple(Icons.Rounded.Snooze, "Snooze", "snooze")
            )
            rows.forEachIndexed { index, (icon, title, id) ->
                SettingsToggleRow(
                    icon = icon,
                    title = title,
                    subtitle = "Show on new-email notification",
                    checked = id in quickActions,
                    onCheckedChange = { checked ->
                        val newSet = quickActions.toMutableSet()
                        if (checked) newSet.add(id) else newSet.remove(id)
                        viewModel.setNotificationQuickActions(newSet)
                    }
                )
                if (index < rows.lastIndex) {
                    CardDivider()
                }
            }
            Text(
                text = "Android shows up to 3 actions on a notification — turn off the ones you don't use.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            CardDivider()
            SettingsActionRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Send test notification",
                subtitle = "Preview the actions above",
                onClick = {
                    context.sendBroadcast(
                        Intent(context, NotificationActionReceiver::class.java).apply {
                            action = NotificationActionReceiver.ACTION_TEST_NOTIFICATION
                        }
                    )
                }
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}
