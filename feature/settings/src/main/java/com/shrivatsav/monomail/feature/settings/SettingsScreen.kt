package com.shrivatsav.monomail.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.ui.theme.MonoSpring
import com.shrivatsav.monomail.ui.theme.MonoTween
import androidx.compose.ui.platform.LocalContext

enum class SettingsSection(val icon: ImageVector, val title: String, val subtitle: String) {
    ACCOUNTS(Icons.Rounded.ManageAccounts, "Accounts", "Manage accounts, providers, connection status"),
    APPEARANCE(Icons.Rounded.Palette, "Appearance", "Theme, font size, colors"),
    INBOX(Icons.Rounded.Inbox, "Inbox", "Threading, grouping, list density, swipe actions"),
    READING(Icons.Rounded.MenuBook, "Reading", "Inline attachments, markdown renderer"),
    PRIVACY(Icons.Rounded.Shield, "Privacy & Security", "PGP encryption keys, remote images"),
    COMPOSE(Icons.AutoMirrored.Rounded.Send, "Compose", "Reply defaults, confirm send, undo send"),
    NAVIGATION(Icons.Rounded.Explore, "Navigation", "Gestures & actions"),
    NOTIFICATIONS(Icons.Rounded.Notifications, "Notifications", "Alerts & sync"),
    DEVELOPER(Icons.Rounded.DeveloperMode, "Developer", "Developer mode & debug tools"),
    SUPPORT(Icons.Rounded.Favorite, "Support", "Donate & community")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    authManager: com.shrivatsav.monomail.core.data.auth.AuthManager,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToPgpKeys: () -> Unit = {},
    onNavigateToSampleCompose: () -> Unit = {}
) {
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }
    BackHandler(currentSection != null) { currentSection = null }

    AnimatedContent(
        targetState = currentSection,
        transitionSpec = {
            val isDrill = targetState != null
            if (isDrill) {
                (slideInHorizontally { width -> width } + fadeIn(animationSpec = MonoTween.fadeIn))
                    .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut(animationSpec = MonoTween.fadeOut))
            } else {
                (slideInHorizontally { width -> -width / 4 } + fadeIn(animationSpec = MonoTween.fadeIn))
                    .togetherWith(slideOutHorizontally { width -> width } + fadeOut(animationSpec = MonoTween.fadeOut))
            }
        },
        label = "settingsNav"
    ) { section ->
        when (section) {
            null -> SettingsHubScreen(
                viewModel = viewModel,
                onSectionClick = { currentSection = it },
                onNavigateBack = onNavigateBack,
                onNavigateToLegal = onNavigateToLegal
            )
            SettingsSection.ACCOUNTS -> AccountsSettingsScreen(
                authManager = authManager,
                onBack = { currentSection = null }
            )
            SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.INBOX -> InboxSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.READING -> ReadingSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.PRIVACY -> PrivacySettingsScreen(
                viewModel = viewModel,
                onNavigateToPgpKeys = onNavigateToPgpKeys,
                onBack = { currentSection = null }
            )
            SettingsSection.COMPOSE -> ComposeSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.NAVIGATION -> NavigationSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.DEVELOPER -> DeveloperSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null },
                onNavigateToSampleCompose = onNavigateToSampleCompose
            )
            SettingsSection.NOTIFICATIONS -> NotificationSettingsScreen(
                authManager = authManager,
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.SUPPORT -> SupportSettingsScreen(
                onBack = { currentSection = null }
            )
        }
    }
}

// ── HUB SCREEN ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHubScreen(
    viewModel: SettingsViewModel,
    onSectionClick: (SettingsSection) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val buildFlavorName = if (com.shrivatsav.monomail.feature.settings.BuildConfig.IS_GITHUB_BUILD) "GitHub" else "Play Store"
    val buildTypeName = if (com.shrivatsav.monomail.feature.settings.BuildConfig.DEBUG) "Debug" else "Release"
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val settings by viewModel.settings.collectAsState()
    var devTapCount by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
        topBar = {
            SettingsDetailTopBar(title = "Settings", onNavigateBack = onNavigateBack)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SettingsSection.entries.filter { it != SettingsSection.DEVELOPER || settings.isDeveloperMode }.forEach { section ->
                CategoryCard(
                    icon = section.icon,
                    title = section.title,
                    subtitle = section.subtitle,
                    onClick = { onSectionClick(section) }
                )
            }

            SettingsCard {
                InfoRow(
                    icon = Icons.Rounded.Info,
                    title = "Version",
                    value = "$versionName ($buildFlavorName $buildTypeName)",
                    onClick = {
                        if (!settings.isDeveloperMode) {
                            devTapCount++
                            if (devTapCount >= 3) {
                                viewModel.setDeveloperMode(true)
                                devTapCount = 0
                                android.widget.Toast.makeText(context, "Developer Mode unlocked!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
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
            }
            Spacer(Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Made with ❤️",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeveloperSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onNavigateToSampleCompose: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    ScrollableSettingsScaffold(
        title = "Developer",
        onBack = onBack
    ) {
        SettingsCard {
            SettingsToggleRow(
                icon = Icons.Rounded.BugReport,
                title = "Developer Mode",
                subtitle = "Enable share options for raw HTML/MD/plain text email body",
                checked = settings.isDeveloperMode,
                onCheckedChange = { viewModel.setDeveloperMode(it) }
            )
            CardDivider()
            SettingsToggleRow(
                icon = Icons.Rounded.FolderSpecial,
                title = "Demo Smart Folders",
                subtitle = "Enable smart folders demonstration",
                checked = settings.demoSmartFolders,
                onCheckedChange = { viewModel.setDemoSmartFolders(it) }
            )
        }
        Spacer(Modifier.height(8.dp))
        SettingsCard {
            // Sample Attachments
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToSampleCompose)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachFile,
                    contentDescription = "Sample Attachments",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Sample Attachments",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Open compose with sample attachments for testing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            CardDivider()
            // Preview Welcome button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        viewModel.resetWelcomePrompt()
                        android.widget.Toast.makeText(
                            context,
                            "Welcome prompt will show on next app launch",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Preview,
                    contentDescription = "Preview Welcome",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Preview Welcome",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Reset welcome prompt and show modal on next app launch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
            CardDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        throw RuntimeException("Intentional crash for testing purposes")
                    })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Test Crash",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Test App Crash",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Intentionally crash the app to verify crash handler",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = MonoSpring.bouncy(),
        label = "cardScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = cornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(
                scaleX = cardScale,
                scaleY = cardScale
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
