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
internal fun AboutSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToLegal: (String) -> Unit,
    onBack: () -> Unit
) {
    val buildFlavorName = if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD) "GitHub" else "Play Store"
    val buildTypeName = if (com.shrivatsav.monomail.BuildConfig.DEBUG) "Debug" else "Release"
    val isFcmCompiled = remember {
        try {
            Class.forName("com.google.firebase.messaging.FirebaseMessaging")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
    val updateState by viewModel.updateState.collectAsState()
    val latestUrl by viewModel.latestVersionUrl.collectAsState()
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current

    ScrollableSettingsScaffold(title = "About", onBack = onBack) {
        SettingsCard {
            InfoRow(
                icon = Icons.Rounded.Info,
                title = "Version",
                value = com.shrivatsav.monomail.BuildConfig.VERSION_NAME
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.Layers,
                title = "Build Info",
                value = "$buildFlavorName · $buildTypeName"
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.CloudQueue,
                title = "Push",
                value = if (isFcmCompiled) "FCM Enabled" else "FCM Disabled"
            )
            CardDivider()
            UpdateInfoRow(updateState, latestUrl, viewModel, uriHandler)
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.Language,
                title = "Website",
                value = "",
                onClick = { uriHandler.openUri("https://monomail.millosaurs.me") }
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.PrivacyTip,
                title = "Privacy Policy",
                value = "",
                onClick = { onNavigateToLegal("privacy") }
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.Gavel,
                title = "Terms of Service",
                value = "",
                onClick = { onNavigateToLegal("tos") }
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.Description,
                title = "Open Source Licenses",
                value = ""
            )
            CardDivider()
            InfoRow(
                icon = Icons.Rounded.Code,
                title = "License",
                value = "GNU GPL v3.0"
            )
        }
    }
}

@Composable
private fun UpdateInfoRow(
    updateState: UpdateState,
    latestUrl: String?,
    viewModel: SettingsViewModel,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val updateText = when (updateState) {
        UpdateState.IDLE -> "Check for Updates"
        UpdateState.CHECKING -> "Checking…"
        UpdateState.UP_TO_DATE -> "You are up to date!"
        UpdateState.UPDATE_AVAILABLE -> "Update Available — tap to download"
        UpdateState.ERROR -> "Error checking for updates"
    }
    InfoRow(
        icon = if (updateState == UpdateState.UPDATE_AVAILABLE) Icons.Rounded.Download else Icons.Rounded.Refresh,
        title = updateText,
        value = "",
        onClick = {
            if (updateState == UpdateState.UPDATE_AVAILABLE && latestUrl != null) {
                uriHandler.openUri(latestUrl)
            } else {
                viewModel.checkForUpdates(com.shrivatsav.monomail.BuildConfig.VERSION_NAME)
            }
        }
    )
}
