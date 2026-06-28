package com.shrivatsav.monomail.ui.screens.settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shrivatsav.monomail.data.settings.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    accountCount: Int = 0
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
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
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.Palette, title = "Appearance")
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
                    icon = Icons.Outlined.HorizontalRule,
                    title = "Show Dividers",
                    subtitle = "Show lines between emails",
                    checked = settings.showDividers,
                    onCheckedChange = { viewModel.setShowDividers(it) }
                )
                CardDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.DensitySmall,
                    title = "Compact List",
                    subtitle = "Reduce spacing in email list",
                    checked = settings.compactList,
                    onCheckedChange = { viewModel.setCompactList(it) }
                )
                CardDivider()
                SettingsToggleRow(
                    icon = Icons.AutoMirrored.Outlined.ShortText,
                    title = "Show Snippet Preview",
                    subtitle = "Display preview text below sender",
                    checked = settings.showSnippet,
                    onCheckedChange = { viewModel.setShowSnippet(it) }
                )
            }
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.TouchApp, title = "Behavior")
                SettingsToggleRow(
                    icon = Icons.Outlined.Forum,
                    title = if (settings.organizeByThread) "Conversation View" else "Message Chain",
                    subtitle = if (settings.organizeByThread) "Collapsible grouped threads in detail view" else "All messages expanded as a chain",
                    checked = settings.organizeByThread,
                    onCheckedChange = { viewModel.setOrganizeByThread(it) }
                )
                CardDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.FolderSpecial,
                    title = "Smart Grouping",
                    subtitle = "Group frequent senders into folders",
                    checked = settings.smartGroupingEnabled,
                    onCheckedChange = { viewModel.setSmartGroupingEnabled(it) }
                )
                AnimatedVisibility(
                    visible = settings.smartGroupingEnabled,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150))
                ) {
                    Column {
                        CardDivider()
                        SettingsToggleRow(
                            icon = Icons.Outlined.Schedule,
                            title = "Group Recent Only",
                            subtitle = "Only group emails from the last 24 hours",
                            checked = settings.smartGroupingRecentOnly,
                            onCheckedChange = { viewModel.setSmartGroupingRecentOnly(it) },
                            indented = true
                        )
                    }
                }
                CardDivider()
                BottomSheetPickerRow(
                    icon = Icons.Outlined.SwipeLeft,
                    title = "Swipe Left",
                    currentValue = settings.swipeLeftAction.displayName(),
                    options = SwipeAction.entries.map { it.displayName() },
                    onSelected = { idx -> viewModel.setSwipeLeftAction(SwipeAction.entries[idx]) }
                )
                CardDivider()
                BottomSheetPickerRow(
                    icon = Icons.Outlined.SwipeRight,
                    title = "Swipe Right",
                    currentValue = settings.swipeRightAction.displayName(),
                    options = SwipeAction.entries.map { it.displayName() },
                    onSelected = { idx -> viewModel.setSwipeRightAction(SwipeAction.entries[idx]) }
                )
                CardDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.CheckCircle,
                    title = "Confirm Before Sending",
                    subtitle = "Show confirmation before send",
                    checked = settings.confirmBeforeSending,
                    onCheckedChange = { viewModel.setConfirmBeforeSending(it) }
                )
                CardDivider()
                BottomSheetPickerRow(
                    icon = Icons.AutoMirrored.Outlined.Reply,
                    title = "Default Reply",
                    currentValue = settings.defaultReply.displayName(),
                    options = DefaultReply.entries.map { it.displayName() },
                    onSelected = { idx -> viewModel.setDefaultReply(DefaultReply.entries[idx]) }
                )
                CardDivider()
                SettingsToggleRow(
                    icon = Icons.Outlined.HistoryToggleOff,
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
                            icon = Icons.Outlined.Timer,
                            title = "Undo Window",
                            currentValue = settings.undoSendWindow.displayName(),
                            options = UndoSendWindow.entries.map { it.displayName() },
                            onSelected = { idx -> viewModel.setUndoSendWindow(UndoSendWindow.entries[idx]) },
                            indented = true
                        )
                    }
                }
            }
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.SpaceDashboard, title = "Dock Bar")
                SettingsToggleRow(
                    icon = Icons.Outlined.Inbox,
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
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.Notifications, title = "Notifications")
                SettingsToggleRow(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Email Notifications",
                    subtitle = "Receive push notifications for new emails",
                    checked = settings.emailNotifications,
                    onCheckedChange = { viewModel.setEmailNotifications(it) }
                )
                CardDivider()
                BottomSheetPickerRow(
                    icon = Icons.Outlined.Sync,
                    title = "Sync Frequency",
                    currentValue = settings.syncFrequency.displayName(),
                    options = SyncFrequency.entries.map { it.displayName() },
                    onSelected = { idx -> viewModel.setSyncFrequency(SyncFrequency.entries[idx]) }
                )
                CardDivider()
                val isFcmCompiled = remember {
                    try {
                        Class.forName("com.google.firebase.messaging.FirebaseMessaging")
                        true
                    } catch (e: ClassNotFoundException) {
                        false
                    }
                }
                val buildFlavorName = if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD) "GitHub Release" else "Play Store Release"
                val fcmStatusText = if (isFcmCompiled) "FCM Push Enabled" else "FCM Push Disabled"
                InfoRow(
                    icon = Icons.Outlined.AppSettingsAlt,
                    title = "Build Distribution",
                    value = "$buildFlavorName • $fcmStatusText"
                )
            }
            TemplatesCard(viewModel = viewModel)
            val updateState by viewModel.updateState.collectAsState()
            val latestUrl by viewModel.latestVersionUrl.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.SystemUpdate, title = "Updates")
                val updateText = when (updateState) {
                    UpdateState.IDLE -> "Check for Updates"
                    UpdateState.CHECKING -> "Checking..."
                    UpdateState.UP_TO_DATE -> "You are up to date!"
                    UpdateState.UPDATE_AVAILABLE -> "Update Available! Tap to download."
                    UpdateState.ERROR -> "Error checking for updates."
                }
                InfoRow(
                    icon = if (updateState == UpdateState.UPDATE_AVAILABLE) Icons.Outlined.Download else Icons.Outlined.Refresh,
                    title = updateText,
                    value = "",
                    onClick = {
                        if (updateState == UpdateState.UPDATE_AVAILABLE && latestUrl != null) {
                            uriHandler.openUri(latestUrl!!)
                        } else {
                            viewModel.checkForUpdates(com.shrivatsav.monomail.BuildConfig.VERSION_NAME)
                        }
                    }
                )
            }
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.Info, title = "About")
                InfoRow(
                    icon = Icons.Outlined.Info,
                    title = "Version",
                    value = com.shrivatsav.monomail.BuildConfig.VERSION_NAME
                )
                CardDivider()
                InfoRow(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    value = "",
                    onClick = { onNavigateToLegal("privacy") }
                )
                CardDivider()
                InfoRow(
                    icon = Icons.Outlined.Gavel,
                    title = "Terms of Service",
                    value = "",
                    onClick = { onNavigateToLegal("tos") }
                )
                CardDivider()
                InfoRow(
                    icon = Icons.Outlined.Description,
                    title = "Open Source Licenses",
                    value = ""
                )
                CardDivider()
                InfoRow(
                    icon = Icons.Outlined.Code,
                    title = "License",
                    value = "GNU GPL v3.0"
                )
            }
            SettingsCard {
                SectionHeader(icon = Icons.Outlined.FavoriteBorder, title = "Support")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    val kofiIcon = remember {
                        val bmp = android.graphics.BitmapFactory.decodeStream(
                            context.resources.openRawResource(com.shrivatsav.monomail.R.raw.kofi)
                        )
                        if (bmp != null) androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
                        else null
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SupportButton(label = "Buy me a coffee", onClick = { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }) { modifier ->
                            if (kofiIcon != null) Icon(painter = kofiIcon, contentDescription = null, modifier = modifier, tint = Color.Unspecified)
                            else Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Pay with UPI", onClick = { uriHandler.openUri("upi://pay?pa=shrivatsav@slc&pn=Sharan%20Shrivatsav&mode=02") }) { modifier ->
                            Icon(Icons.Outlined.Payments, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Star on GitHub", onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") }) { modifier ->
                            Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Join Discord Server", onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") }) { modifier ->
                            Icon(Icons.Outlined.HeadsetMic, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Share Monomail", onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out Monomail - a private, open-source email client: https://github.com/shrivatsav-0/monomail")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Monomail"))
                        }) { modifier ->
                            Icon(Icons.Outlined.Share, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Report Issue", onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail/issues") }) { modifier ->
                            Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = modifier)
                        }
                        SupportButton(label = "Donate Crypto (BASE)", onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", "0xB27Ba9241de81F6DBCB322aDd76a9d9686462e9E"))
                            android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }) { modifier ->
                            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = modifier)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
    ) {
        Column(content = content)
    }
}
@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}
@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(
                start = if (indented) 32.dp else 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = enabled
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = "Theme",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Theme",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = currentTheme == mode
                val bgColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    animationSpec = tween(250), label = "themeBg"
                )
                val textColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.surface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(250), label = "themeText"
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
    }
}
@Composable
private fun FontSizeRow(
    currentScale: FontScale,
    onScaleChanged: (FontScale) -> Unit
) {
    val previewSize by animateFloatAsState(
        targetValue = when (currentScale) {
            FontScale.EXTRA_SMALL -> 11f
            FontScale.SMALL       -> 13f
            FontScale.DEFAULT     -> 15f
            FontScale.LARGE       -> 17f
            FontScale.EXTRA_LARGE -> 20f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fontSize"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.FormatSize,
                contentDescription = "Font Size",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentScale.displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "The quick brown fox jumps over the lazy dog",
                fontSize = previewSize.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = currentScale.ordinal.toFloat(),
                onValueChange = { onScaleChanged(FontScale.entries[it.toInt()]) },
                valueRange = 0f..4f,
                steps = 3,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            Text(
                text = "A",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
@Composable
private fun NavSizeRow(
    scale: Float,
    onScaleChanged: (Float) -> Unit
) {
    val previewScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "navScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.SpaceDashboard,
                contentDescription = "Navigation Size",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Navigation Size",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp * previewScale),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp * previewScale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp * previewScale, vertical = 4.dp * previewScale),
                        horizontalArrangement = Arrangement.spacedBy(4.dp * previewScale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp * previewScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp * previewScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        )
                        Box(
                            modifier = Modifier
                                .size(20.dp * previewScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp * previewScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
            Slider(
                value = scale,
                onValueChange = onScaleChanged,
                valueRange = 0.6f..1.4f,
                steps = 7,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottomSheetPickerRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    indented: Boolean = false
) {
    var showSheet by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(start = if (indented) 32.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEachIndexed { index, option ->
                    val isSelected = option == currentValue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected(index)
                                showSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
private fun ThemeMode.displayName() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT  -> "Light"
    ThemeMode.DARK   -> "Dark"
}
private fun FontScale.displayName() = when (this) {
    FontScale.EXTRA_SMALL -> "XS"
    FontScale.SMALL       -> "Small"
    FontScale.DEFAULT     -> "Default"
    FontScale.LARGE       -> "Large"
    FontScale.EXTRA_LARGE -> "XL"
}
private fun SwipeAction.displayName() = when (this) {
    SwipeAction.ARCHIVE    -> "Archive"
    SwipeAction.STAR       -> "Star"
    SwipeAction.DELETE     -> "Delete"
    SwipeAction.READ_UNREAD -> "Mark Read/Unread"
}
private fun DefaultReply.displayName() = when (this) {
    DefaultReply.REPLY     -> "Reply"
    DefaultReply.REPLY_ALL -> "Reply All"
}
private fun SyncFrequency.displayName() = when (this) {
    SyncFrequency.MIN_15  -> "15 minutes"
    SyncFrequency.MIN_30  -> "30 minutes"
    SyncFrequency.HOUR_1  -> "1 hour"
    SyncFrequency.MANUAL  -> "Manual"
}
private fun UndoSendWindow.displayName() = "${seconds}s"

@Composable
private fun TemplatesCard(viewModel: SettingsViewModel) {
    val templates by viewModel.templates.collectAsState()
    var editingIndex by remember { mutableStateOf(-1) }
    var showEditor by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    SettingsCard {
        SectionHeader(icon = Icons.Outlined.Description, title = "Templates")
        if (templates.isEmpty()) {
            Text(
                text = "No templates yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            templates.forEachIndexed { index, template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = template.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = template.subject,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = {
                        editingIndex = index
                        nameInput = template.name
                        subjectInput = template.subject
                        bodyInput = template.body
                        showEditor = true
                    }) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = {
                        val updated = templates.toMutableList().apply { removeAt(index) }
                        viewModel.saveTemplates(updated)
                    }) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
                    }
                }
                if (index < templates.lastIndex) CardDivider()
            }
        }
        CardDivider()
        TextButton(
            onClick = {
                editingIndex = -1
                nameInput = ""
                subjectInput = ""
                bodyInput = ""
                showEditor = true
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Template")
        }
    }
    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingIndex >= 0) "Edit Template" else "New Template", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bodyInput,
                        onValueChange = { bodyInput = it },
                        label = { Text("Body") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nameInput.isNotBlank()) {
                            val template = EmailTemplate(nameInput, subjectInput, bodyInput)
                            val updated = templates.toMutableList()
                            if (editingIndex >= 0) updated[editingIndex] = template
                            else updated.add(template)
                            viewModel.saveTemplates(updated)
                            showEditor = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DockBarEditor(
    dockConfig: DockConfig,
    maxSlots: Int,
    unifiedInboxEnabled: Boolean,
    onConfigChanged: (DockConfig) -> Unit
) {
    val allTabs = DockTabId.values().filter { it != DockTabId.UNIFIED || unifiedInboxEnabled }
    val availableTabs = allTabs.filter { it !in dockConfig.primaryTabs }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Dock tabs (${dockConfig.primaryTabs.size}/$maxSlots)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        dockConfig.primaryTabs.forEachIndexed { index, tabId ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = dockTabIcon(tabId),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = dockTabLabel(tabId),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        val list = dockConfig.primaryTabs.toMutableList()
                        if (index > 0) {
                            list[index] = list[index - 1].also { list[index - 1] = list[index] }
                            onConfigChanged(DockConfig(primaryTabs = list))
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = index > 0
                ) {
                    Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        val list = dockConfig.primaryTabs.toMutableList()
                        if (index < list.lastIndex) {
                            list[index] = list[index + 1].also { list[index + 1] = list[index] }
                            onConfigChanged(DockConfig(primaryTabs = list))
                        }
                    },
                    modifier = Modifier.size(32.dp),
                    enabled = index < dockConfig.primaryTabs.lastIndex
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                }
                if (dockConfig.primaryTabs.size > 1) {
                    IconButton(
                        onClick = {
                            val list = dockConfig.primaryTabs.toMutableList()
                            list.removeAt(index)
                            onConfigChanged(DockConfig(primaryTabs = list))
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp),
                             tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Spacer(Modifier.size(32.dp))
                }
            }
        }

        if (availableTabs.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 0.5.dp
            )
            Text(
                text = "Available",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            availableTabs.forEach { tabId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = dockTabIcon(tabId),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = dockTabLabel(tabId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (dockConfig.primaryTabs.size < maxSlots) {
                        IconButton(onClick = {
                            val list = dockConfig.primaryTabs.toMutableList()
                            list.add(tabId)
                            onConfigChanged(DockConfig(primaryTabs = list))
                        }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.AddCircleOutline, contentDescription = "Add", modifier = Modifier.size(18.dp),
                                 tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun dockTabIcon(tab: DockTabId): ImageVector = when (tab) {
    DockTabId.UNIFIED -> Icons.Outlined.Inbox
    DockTabId.INBOX -> Icons.Outlined.Inbox
    DockTabId.SENT -> Icons.AutoMirrored.Outlined.Send
    DockTabId.ARCHIVED -> Icons.Outlined.Archive
    DockTabId.SNOOZED -> Icons.Outlined.Schedule
    DockTabId.STARRED -> Icons.Outlined.StarOutline
    DockTabId.TRASH -> Icons.Outlined.Delete
    DockTabId.SPAM -> Icons.Outlined.Report
}

private fun dockTabLabel(tab: DockTabId): String = when (tab) {
    DockTabId.UNIFIED -> "Unified"
    DockTabId.INBOX -> "Inbox"
    DockTabId.SENT -> "Sent"
    DockTabId.ARCHIVED -> "Archived"
    DockTabId.SNOOZED -> "Snoozed"
    DockTabId.STARRED -> "Starred"
    DockTabId.TRASH -> "Trash"
    DockTabId.SPAM -> "Spam"
}

@Composable
private fun SupportButton(
    label: String,
    onClick: () -> Unit,
    icon: @Composable (Modifier) -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        icon(Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}