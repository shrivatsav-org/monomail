package com.shrivatsav.monomail.feature.settings

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
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.DataArray,
                title = "Show Dividers",
                subtitle = "Show horizontal lines between emails",
                checked = settings.showDividers,
                onCheckedChange = { viewModel.setShowDividers(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.ViewHeadline,
                title = "Compact List",
                subtitle = "Reduce email list spacing",
                checked = settings.compactList,
                onCheckedChange = { viewModel.setCompactList(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.ShortText,
                title = "Show Snippet",
                subtitle = "Preview email body text in the list",
                checked = settings.showSnippet,
                onCheckedChange = { viewModel.setShowSnippet(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.CheckCircle,
                title = "Mark All Read Button",
                subtitle = "Show mark all as read button in the search bar",
                checked = settings.showMarkAllRead,
                onCheckedChange = { viewModel.setShowMarkAllRead(it) }
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
