package com.shrivatsav.monomail.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Reading", onBack = onBack) {
        SettingsCard {
            SettingsToggleRow(
                icon = Icons.Rounded.Code,
                title = "Render Markdown",
                subtitle = "Convert markdown formatting in plain text emails",
                checked = settings.renderMarkdown,
                onCheckedChange = { viewModel.setRenderMarkdown(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.AttachFile,
                title = "Inline Attachments",
                subtitle = "Show attachment cards within the email body, or as a collapsible summary above it",
                checked = settings.showInlineAttachments,
                onCheckedChange = { viewModel.setShowInlineAttachments(it) }
            )
        }
    }
}
