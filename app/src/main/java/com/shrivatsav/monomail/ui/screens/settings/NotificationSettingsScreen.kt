package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NotificationSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Notifications", onBack = onBack) {
        SettingsCard {
            SectionHeader(icon = Icons.Rounded.Notifications, title = "Notifications")
            SettingsToggleRow(
                icon = Icons.Rounded.NotificationsActive,
                title = "Email Notifications",
                subtitle = "Receive push notifications for new emails",
                checked = settings.emailNotifications,
                onCheckedChange = { viewModel.setEmailNotifications(it) }
            )
            CardDivider()
            BottomSheetPickerRow(
                icon = Icons.Rounded.Sync,
                title = "Sync Frequency",
                currentValue = settings.syncFrequency.displayName(),
                options = SyncFrequency.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setSyncFrequency(SyncFrequency.entries[idx]) }
            )
        }
    }
}
