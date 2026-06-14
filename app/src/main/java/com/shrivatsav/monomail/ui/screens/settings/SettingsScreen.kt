package com.shrivatsav.monomail.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.settings.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── Appearance ──────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Palette, title = "Appearance")

            // Theme
            ThemeSelectorRow(
                currentTheme = settings.themeMode,
                onThemeSelected = { viewModel.setThemeMode(it) }
            )

            // Font Size
            FontSizeRow(
                currentScale = settings.fontScale,
                onScaleChanged = { viewModel.setFontScale(it) }
            )

            // Dividers
            SettingsToggleRow(
                icon = Icons.Outlined.HorizontalRule,
                title = "Show Dividers",
                subtitle = "Show lines between emails in list",
                checked = settings.showDividers,
                onCheckedChange = { viewModel.setShowDividers(it) }
            )

            // Compact List
            SettingsToggleRow(
                icon = Icons.Outlined.DensitySmall,
                title = "Compact List",
                subtitle = "Reduce spacing in email list",
                checked = settings.compactList,
                onCheckedChange = { viewModel.setCompactList(it) }
            )

            // Snippet Preview
            SettingsToggleRow(
                icon = Icons.Outlined.ShortText,
                title = "Show Snippet Preview",
                subtitle = "Display preview text below sender",
                checked = settings.showSnippet,
                onCheckedChange = { viewModel.setShowSnippet(it) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Behavior ────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.TouchApp, title = "Behavior")

            // Unified Inbox
            SettingsToggleRow(
                icon = Icons.Outlined.Inbox,
                title = "Unified Inbox",
                subtitle = "Show emails from all accounts in one tab",
                checked = settings.unifiedInboxEnabled,
                onCheckedChange = { viewModel.setUnifiedInboxEnabled(it) }
            )

            // Swipe Left Action
            PickerRow(
                icon = Icons.Outlined.SwipeLeft,
                title = "Swipe Left Action",
                currentValue = settings.swipeLeftAction.displayName(),
                options = SwipeAction.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setSwipeLeftAction(SwipeAction.entries[idx]) }
            )

            // Swipe Right Action
            PickerRow(
                icon = Icons.Outlined.SwipeRight,
                title = "Swipe Right Action",
                currentValue = settings.swipeRightAction.displayName(),
                options = SwipeAction.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setSwipeRightAction(SwipeAction.entries[idx]) }
            )

            // Confirm Before Sending
            SettingsToggleRow(
                icon = Icons.Outlined.CheckCircle,
                title = "Confirm Before Sending",
                subtitle = "Show confirmation dialog before send",
                checked = settings.confirmBeforeSending,
                onCheckedChange = { viewModel.setConfirmBeforeSending(it) }
            )

            // Default Reply
            PickerRow(
                icon = Icons.Outlined.Reply,
                title = "Default Reply Action",
                currentValue = settings.defaultReply.displayName(),
                options = DefaultReply.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setDefaultReply(DefaultReply.entries[idx]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Notifications ───────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Notifications, title = "Notifications")

            SettingsToggleRow(
                icon = Icons.Outlined.NotificationsActive,
                title = "Email Notifications",
                subtitle = "Receive push notifications for new emails",
                checked = settings.emailNotifications,
                onCheckedChange = { viewModel.setEmailNotifications(it) }
            )

            PickerRow(
                icon = Icons.Outlined.Sync,
                title = "Sync Frequency",
                currentValue = settings.syncFrequency.displayName(),
                options = SyncFrequency.entries.map { it.displayName() },
                onSelected = { idx -> viewModel.setSyncFrequency(SyncFrequency.entries[idx]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── About ───────────────────────────────────────────────
            SectionHeader(icon = Icons.Outlined.Info, title = "About")

            InfoRow(
                icon = Icons.Outlined.Info,
                title = "Version",
                value = "1.0.4"
            )

            InfoRow(
                icon = Icons.Outlined.PrivacyTip,
                title = "Privacy Policy",
                value = "Read our privacy policy",
                onClick = { onNavigateToLegal("privacy") }
            )

            InfoRow(
                icon = Icons.Outlined.Gavel,
                title = "Terms of Service",
                value = "Read our terms of service",
                onClick = { onNavigateToLegal("tos") }
            )

            InfoRow(
                icon = Icons.Outlined.Description,
                title = "Open Source Licenses",
                value = ""
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

// ── Components ──────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            )
        )
    }
}

@Composable
private fun ThemeSelectorRow(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = currentTheme == mode
                val bgColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = tween(250)
                )
                val textColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    animationSpec = tween(250)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(bgColor)
                        .clickable { onThemeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FontSizeRow(
    currentScale: FontScale,
    onScaleChanged: (FontScale) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.FormatSize,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = currentScale.displayName(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Slider(
                value = currentScale.ordinal.toFloat(),
                onValueChange = { onScaleChanged(FontScale.entries[it.toInt()]) },
                valueRange = 0f..4f,
                steps = 3,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(
                text = "A",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<String>,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontWeight = if (option == currentValue) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelected(index)
                        expanded = false
                    },
                    trailingIcon = if (option == currentValue) {
                        {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Extension display names ─────────────────────────────────────────────────

private fun ThemeMode.displayName() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun FontScale.displayName() = when (this) {
    FontScale.EXTRA_SMALL -> "Extra Small"
    FontScale.SMALL -> "Small"
    FontScale.DEFAULT -> "Default"
    FontScale.LARGE -> "Large"
    FontScale.EXTRA_LARGE -> "Extra Large"
}

private fun SwipeAction.displayName() = when (this) {
    SwipeAction.ARCHIVE -> "Archive"
    SwipeAction.STAR -> "Star"
    SwipeAction.DELETE -> "Delete"
}

private fun DefaultReply.displayName() = when (this) {
    DefaultReply.REPLY -> "Reply"
    DefaultReply.REPLY_ALL -> "Reply All"
}

private fun SyncFrequency.displayName() = when (this) {
    SyncFrequency.MIN_15 -> "15 minutes"
    SyncFrequency.MIN_30 -> "30 minutes"
    SyncFrequency.HOUR_1 -> "1 hour"
    SyncFrequency.MANUAL -> "Manual"
}
