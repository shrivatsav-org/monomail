package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    ScrollableSettingsScaffold(
        title = "Appearance",
        onBack = onBack
    ) {
        SettingsCard {
            ThemeSelectorRow(
                currentTheme = settings.themeMode,
                onThemeSelected = { viewModel.setThemeMode(it) }
            )
            CardDivider()
            FontSizeRow(
                currentScale = settings.fontScale,
                onScaleChanged = { viewModel.setFontScale(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.HorizontalRule,
                title = "Show Dividers",
                subtitle = "Show lines between emails",
                checked = settings.showDividers,
                onCheckedChange = { viewModel.setShowDividers(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.DensitySmall,
                title = "Compact List",
                subtitle = "Reduce spacing in email list",
                checked = settings.compactList,
                onCheckedChange = { viewModel.setCompactList(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.AutoMirrored.Rounded.ShortText,
                title = "Show Snippet Preview",
                subtitle = "Display preview text below sender",
                checked = settings.showSnippet,
                onCheckedChange = { viewModel.setShowSnippet(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.ImageNotSupported,
                title = "Load Remote Images",
                subtitle = "When off, external images in emails are blocked until you tap to load them",
                checked = settings.loadRemoteImages,
                onCheckedChange = { viewModel.setLoadRemoteImages(it) }
            )
            CardDivider()
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
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.TextFields,
                title = "System Font",
                subtitle = "Use system font instead of Google Sans",
                checked = settings.useSystemFont,
                onCheckedChange = { viewModel.setUseSystemFont(it) }
            )
            CardDivider()
            EmailColorsRow(
                currentTheme = settings.emailTheme,
                onThemeSelected = { viewModel.setEmailTheme(it) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        SettingsCard {
            SectionHeader(icon = Icons.Rounded.DeveloperMode, title = "Developer")
            SettingsToggleRow(
                icon = Icons.Rounded.BugReport,
                title = "Developer Mode",
                subtitle = "Enable share options for raw HTML/MD/plain text email body",
                checked = settings.isDeveloperMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollableSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}
