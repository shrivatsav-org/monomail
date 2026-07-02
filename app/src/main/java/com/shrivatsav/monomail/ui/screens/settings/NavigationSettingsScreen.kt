package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NavigationSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Navigation", onBack = onBack) {
        SettingsCard {
            SectionHeader(icon = Icons.Rounded.SpaceDashboard, title = "Navigation")
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
