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
internal fun NavigationSettingsScreen(
    viewModel: SettingsViewModel,
    accountCount: Int,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Navigation", onBack = onBack) {
        SettingsCard {
            SectionHeader(icon = Icons.Rounded.SpaceDashboard, title = "Navigation")
            SettingsToggleRow(
                icon = Icons.Rounded.Inbox,
                title = "Unified Inbox",
                subtitle = if (accountCount > 1) "Show emails from all accounts in one tab"
                           else "Add another account to enable",
                checked = settings.unifiedInboxEnabled,
                onCheckedChange = { viewModel.setUnifiedInboxEnabled(it) },
                enabled = accountCount > 1
            )
            CardDivider()
            NavSizeRow(
                scale = settings.navScale,
                onScaleChanged = { viewModel.setNavScale(it) }
            )
            CardDivider()
            DockBarEditor(
                dockConfig = settings.dockConfig,
                maxSlots = DockConfig.MAX_SLOTS,
                unifiedInboxEnabled = settings.unifiedInboxEnabled,
                onConfigChanged = { viewModel.setDockConfig(it) }
            )
        }
    }
}
