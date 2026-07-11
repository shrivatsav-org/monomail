package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrivacySettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToPgpKeys: () -> Unit,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Privacy & Security", onBack = onBack) {
        SettingsCard {
            SettingsToggleRow(
                icon = Icons.Rounded.ImageNotSupported,
                title = "Load Remote Images",
                subtitle = "When off, external images in emails are blocked until you tap to load them",
                checked = settings.loadRemoteImages,
                onCheckedChange = { viewModel.setLoadRemoteImages(it) }
            )
            CardDivider()
            SettingsActionRow(
                icon = Icons.Rounded.Lock,
                title = "PGP Encryption",
                subtitle = "Manage encryption keys",
                onClick = onNavigateToPgpKeys
            )
        }
    }
}
