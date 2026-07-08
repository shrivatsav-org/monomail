package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ComposeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    ScrollableSettingsScaffold(title = "Compose", onBack = onBack) {
        SettingsCard {
            BottomSheetPickerRow(
                icon = Icons.AutoMirrored.Rounded.Reply,
                title = "Default Reply",
                currentValue = settings.defaultReply.displayName(),
                options = DefaultReply.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setDefaultReply(DefaultReply.entries[idx]) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.CheckCircle,
                title = "Confirm Before Sending",
                subtitle = "Show confirmation before send",
                checked = settings.confirmBeforeSending,
                onCheckedChange = { viewModel.setConfirmBeforeSending(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.HistoryToggleOff,
                title = "Undo Send",
                subtitle = "Hold email for ${settings.undoSendWindow.seconds}s before sending",
                checked = settings.undoSendEnabled,
                onCheckedChange = { viewModel.setUndoSendEnabled(it) }
            )
            AnimatedVisibility(
                visible = settings.undoSendEnabled,
                enter = expandVertically() + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
            ) {
                Column {
                    CardDivider()
                    BottomSheetPickerRow(
                        icon = Icons.Rounded.Timer,
                        title = "Undo Window",
                        currentValue = settings.undoSendWindow.displayName(),
                        options = UndoSendWindow.entries.map { it.displayName() },
                        onSelected = { idx -> viewModel.setUndoSendWindow(UndoSendWindow.entries[idx]) },
                        indented = true
                    )
                }
            }
        }
        TemplatesCard(viewModel = viewModel)
    }
}
