package com.shrivatsav.monomail.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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

enum class SettingsSection(val icon: ImageVector, val title: String, val subtitle: String) {
    APPEARANCE(Icons.Rounded.Palette, "Appearance", "Theme, font size, display preferences"),
    INBOX(Icons.Rounded.Inbox, "Inbox", "Threading, smart grouping, swipe gestures"),
    COMPOSE(Icons.AutoMirrored.Rounded.Send, "Compose", "Reply defaults, confirm send, undo send"),
    NAVIGATION(Icons.Rounded.SpaceDashboard, "Navigation", "Dock tabs, unified inbox"),
    NOTIFICATIONS(Icons.Rounded.Notifications, "Notifications", "Push alerts, sync frequency"),
    ABOUT(Icons.Rounded.Info, "About", "Version, updates, legal, licenses")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToPgpKeys: () -> Unit = {},
    accountCount: Int = 0
) {
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    BackHandler(currentSection != null) { currentSection = null }

    AnimatedContent(
        targetState = currentSection,
        transitionSpec = {
            val isDrill = targetState != null
            if (isDrill) {
                (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(250)))
                    .togetherWith(slideOutHorizontally { width -> -width / 4 } + fadeOut(animationSpec = tween(200)))
            } else {
                (slideInHorizontally { width -> -width / 4 } + fadeIn(animationSpec = tween(250)))
                    .togetherWith(slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(200)))
            }
        },
        label = "settingsNav"
    ) { section ->
        when (section) {
            null -> SettingsHubScreen(
                viewModel = viewModel,
                accountCount = accountCount,
                onSectionClick = { currentSection = it },
                onNavigateToPgpKeys = onNavigateToPgpKeys,
                onNavigateToLegal = onNavigateToLegal,
                onBack = onNavigateBack
            )
            SettingsSection.APPEARANCE -> AppearanceSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.INBOX -> InboxSettingsScreen(
                viewModel = viewModel,
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
            SettingsSection.NOTIFICATIONS -> NotificationSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSection = null }
            )
            SettingsSection.ABOUT -> AboutSettingsScreen(
                viewModel = viewModel,
                onNavigateToLegal = onNavigateToLegal,
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
    accountCount: Int,
    onSectionClick: (SettingsSection) -> Unit,
    onNavigateToPgpKeys: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                },
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            SettingsSection.entries.forEach { section ->
                CategoryCard(
                    icon = section.icon,
                    title = section.title,
                    subtitle = section.subtitle,
                    onClick = { onSectionClick(section) }
                )
            }

            // ── PGP (direct nav to existing screen) ──────────────────────
            CategoryCard(
                icon = Icons.Rounded.Lock,
                title = "PGP Encryption",
                subtitle = "Manage encryption keys",
                onClick = onNavigateToPgpKeys
            )

            Spacer(Modifier.height(6.dp))
            SupportSection()
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardScale"
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(16.dp),
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
