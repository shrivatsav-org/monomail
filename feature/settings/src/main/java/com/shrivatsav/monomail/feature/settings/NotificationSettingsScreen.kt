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
                    
                    if (isEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        InfoRow(
                            icon = Icons.Rounded.Tune,
                            title = "Customize Sound & Vibration",
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
    }
}
