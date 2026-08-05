package com.shrivatsav.monomail.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.security.SecurityUtil
import com.shrivatsav.monomail.core.network.provider.imap.EffectiveSmtp
import com.shrivatsav.monomail.ui.theme.MonoOpacity
import com.shrivatsav.monomail.ui.theme.cornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsSettingsScreen(
    authManager: AuthManager,
    onBack: () -> Unit
) {
    val accounts by authManager.accountsFlow.collectAsState(initial = emptyList())
    val reauthInfo by authManager.reauthNeeded.collectAsState()
    var selectedImapAccount by remember { mutableStateOf<UserProfile?>(null) }

    ScrollableSettingsScaffold(title = "Accounts", onBack = onBack) {
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No accounts found", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            SettingsCard {
                accounts.forEachIndexed { index, account ->
                    val isReauthNeeded = reauthInfo?.email == account.email
                    val statusText = if (isReauthNeeded) "Session Expired" else "Connected"
                    val statusColor = if (isReauthNeeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val isImap = account.provider.lowercase() == "imap"

                    val providerIcon = when (account.provider.lowercase()) {
                        "gmail" -> Icons.Rounded.Email
                        "outlook" -> Icons.Rounded.ForwardToInbox
                        "imap" -> Icons.Rounded.Storage
                        else -> Icons.Rounded.AccountCircle
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isImap) { selectedImapAccount = account }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = providerIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = account.provider.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (isImap) {
                                    Text(
                                        text = " • tap for details",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Text(
                                        text = " • ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor
                                )
                            }
                        }
                    }
                    if (index < accounts.size - 1) {
                        CardDivider()
                    }
                }
            }
        }
    }

    // Resolve the selected account against the live list so the dialog
    // re-reads the config after a save (the tap-time snapshot would stay stale).
    selectedImapAccount?.let { selected ->
        val current = accounts.firstOrNull { it.id == selected.id } ?: selected
        ImapAccountDetailDialog(
            account = current,
            authManager = authManager,
            onDismiss = { selectedImapAccount = null }
        )
    }
}

@Composable
private fun ImapAccountDetailDialog(
    account: UserProfile,
    authManager: AuthManager,
    onDismiss: () -> Unit
) {
    val config: ImapAccountConfig? = remember(account) {
        try {
            val configJson = SecurityUtil.decryptString(account.accessToken)
            if (configJson != null) ImapAccountConfig.fromJson(configJson) else null
        } catch (e: Exception) { null }
    }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val reauthInfo by authManager.reauthNeeded.collectAsState()
    val isReauthNeeded = reauthInfo?.email == account.email
    var editing by remember { mutableStateOf(false) }

    // Field state is keyed on (config, editing) so entering edit mode always
    // starts from the saved config — cancelling or saving discards stale edits.
    var imapHost by remember(config, editing) { mutableStateOf(config?.imapHost ?: "") }
    var imapPortStr by remember(config, editing) { mutableStateOf(config?.imapPort?.toString() ?: "") }
    var imapMode by remember(config, editing) { mutableStateOf(config?.let(::imapSecurityMode) ?: SecurityMode.SSL) }
    var smtpHost by remember(config, editing) { mutableStateOf(config?.smtpHost ?: "") }
    var smtpPortStr by remember(config, editing) { mutableStateOf(config?.smtpPort?.takeIf { it > 0 }?.toString() ?: "") }
    var smtpMode by remember(config, editing) { mutableStateOf(config?.let(::smtpSecurityMode) ?: SecurityMode.SSL) }

    val imapPort = imapPortStr.toIntOrNull()
    val smtpPort = smtpPortStr.toIntOrNull()
    val imapPortError = imapPortStr.isNotEmpty() && (imapPort == null || imapPort !in 1..65535)
    val smtpPortError = smtpPortStr.isNotEmpty() && (smtpPort == null || smtpPort !in 1..65535)
    val portsValid = !imapPortError && !smtpPortError

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            when {
                config == null -> Text(
                    "Could not decrypt IMAP configuration",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                else -> AnimatedContent(
                    targetState = editing,
                    transitionSpec = {
                        (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                            slideInVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 })
                            .togetherWith(fadeOut(tween(150)))
                    },
                    label = "account-detail-mode"
                ) { isEditing ->
                    if (isEditing) {
                        AccountEditContent(
                            account = account,
                            isReauthNeeded = isReauthNeeded,
                            imapHost = imapHost,
                            onImapHostChange = { imapHost = it },
                            imapPortStr = imapPortStr,
                            onImapPortChange = { imapPortStr = it.filter(Char::isDigit) },
                            imapPortError = imapPortError,
                            imapMode = imapMode,
                            onImapMode = { imapMode = it; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            smtpHost = smtpHost,
                            onSmtpHostChange = { smtpHost = it },
                            smtpPortStr = smtpPortStr,
                            onSmtpPortChange = { smtpPortStr = it.filter(Char::isDigit) },
                            smtpPortError = smtpPortError,
                            smtpMode = smtpMode,
                            onSmtpMode = { smtpMode = it; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                        )
                    } else {
                        AccountDetailContent(account = account, isReauthNeeded = isReauthNeeded, config = config)
                    }
                }
            }
        },
        confirmButton = {
            if (editing) {
                TextButton(onClick = { editing = false }) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        val saved = config ?: return@TextButton
                        val (imapSsl, imapStartTls) = imapMode.toFlags()
                        val (smtpSsl, smtpStartTls) = smtpMode.toFlags()
                        val newConfig = saved.copy(
                            imapHost = imapHost.trim().ifBlank { saved.imapHost },
                            imapPort = imapPort ?: saved.imapPort,
                            imapSsl = imapSsl,
                            imapStartTls = imapStartTls,
                            smtpHost = smtpHost.trim(),
                            smtpPort = smtpPort ?: 0,
                            smtpSsl = smtpSsl,
                            smtpStartTls = smtpStartTls
                        )
                        scope.launch {
                            val encrypted = SecurityUtil.encryptString(newConfig.toJson())
                            if (encrypted != null) {
                                authManager.updateAccessToken(account.copy(accessToken = encrypted))
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                editing = false
                            }
                        }
                    },
                    enabled = portsValid
                ) {
                    Text("Save")
                }
            } else {
                TextButton(onClick = { editing = true }, enabled = config != null) {
                    Text("Edit")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/** Identity: initials avatar, name/email, connection status. */
@Composable
private fun AccountIdentityHeader(account: UserProfile, isReauthNeeded: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initialsOf(account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName.ifBlank { account.email },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .then(
                            if (isReauthNeeded) {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.onSurface)
                            }
                        )
                )
                Text(
                    text = if (isReauthNeeded) "Session expired" else "Connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary)
                )
                Text(
                    text = "· ${account.provider.replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary)
                )
            }
        }
    }
}

private fun initialsOf(account: UserProfile): String {
    val name = account.displayName.trim()
    if (name.isNotEmpty()) {
        val parts = name.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.size >= 2) return (parts[0].take(1) + parts[1].take(1)).uppercase()
        return name.take(1).uppercase()
    }
    return account.email.take(1).uppercase()
}

/** Read-only view: identity + IMAP/SMTP section cards. */
@Composable
private fun AccountDetailContent(
    account: UserProfile,
    isReauthNeeded: Boolean,
    config: ImapAccountConfig
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccountIdentityHeader(account, isReauthNeeded)

        ConfigSectionCard(title = "IMAP", icon = Icons.Rounded.MoveToInbox) {
            ConfigRow("Host", config.imapHost, mono = true)
            ConfigRow("Port", config.imapPort.toString(), mono = true)
            ConfigRow("Security", imapSecurityMode(config).label)
            if (config.username.isNotBlank() && config.username != account.email) {
                ConfigRow("Server user", config.username, mono = true)
            }
        }

        ConfigSectionCard(title = "SMTP", icon = Icons.Rounded.Send) {
            ConfigRow("Host", config.smtpHost.ifBlank { "Uses IMAP host" }, mono = true)
            ConfigRow("Port", if (config.smtpPort > 0) config.smtpPort.toString() else "Auto", mono = true)
            ConfigRow("Security", smtpSecurityMode(config).label)
            val eff = config.effectiveSmtp()
            Text(
                text = "Effective: ${eff.host}:${eff.port} · ${eff.protocolLabel}",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)
            )
        }
    }
}

/** Edit view: same cards, host/port fields + security mode chips. */
@Composable
private fun AccountEditContent(
    account: UserProfile,
    isReauthNeeded: Boolean,
    imapHost: String,
    onImapHostChange: (String) -> Unit,
    imapPortStr: String,
    onImapPortChange: (String) -> Unit,
    imapPortError: Boolean,
    imapMode: SecurityMode,
    onImapMode: (SecurityMode) -> Unit,
    smtpHost: String,
    onSmtpHostChange: (String) -> Unit,
    smtpPortStr: String,
    onSmtpPortChange: (String) -> Unit,
    smtpPortError: Boolean,
    smtpMode: SecurityMode,
    onSmtpMode: (SecurityMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AccountIdentityHeader(account, isReauthNeeded)

        ConfigSectionCard(title = "IMAP", icon = Icons.Rounded.MoveToInbox) {
            EditField(value = imapHost, onValueChange = onImapHostChange, label = "IMAP Host")
            EditField(
                value = imapPortStr,
                onValueChange = onImapPortChange,
                label = "IMAP Port",
                number = true,
                isError = imapPortError,
                supportingText = if (imapPortError) "Enter a port from 1 to 65535" else null
            )
            SecurityModeChips(label = "Security", mode = imapMode, onSelect = onImapMode)
        }

        ConfigSectionCard(title = "SMTP", icon = Icons.Rounded.Send) {
            EditField(
                value = smtpHost,
                onValueChange = onSmtpHostChange,
                label = "SMTP Host",
                placeholder = "Use IMAP host"
            )
            EditField(
                value = smtpPortStr,
                onValueChange = onSmtpPortChange,
                label = "SMTP Port",
                number = true,
                placeholder = "Auto",
                isError = smtpPortError,
                supportingText = if (smtpPortError) "Enter a port from 1 to 65535" else null
            )
            SecurityModeChips(label = "Security", mode = smtpMode, onSelect = onSmtpMode)
            Text(
                "587 usually pairs with STARTTLS, 465 with SSL. Leaving the port blank auto-derives it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            )
        }
    }
}

/** Grouped card matching the settings screens' visual language. */
@Composable
private fun ConfigSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = cornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

/** Label left, value right — technical values in monospace. */
@Composable
private fun ConfigRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
            modifier = Modifier.weight(0.45f)
        )
        Text(
            text = value,
            style = if (mono) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (mono) FontWeight.Normal else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun EditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    number: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        keyboardOptions = if (number) {
            KeyboardOptions(keyboardType = KeyboardType.Number)
        } else {
            KeyboardOptions.Default
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** One concept, three choices — replaces the old SSL/STARTTLS switch pair. */
@Composable
private fun SecurityModeChips(
    label: String,
    mode: SecurityMode,
    onSelect: (SecurityMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecurityMode.entries.forEach { candidate ->
                FilterChip(
                    selected = mode == candidate,
                    onClick = { onSelect(candidate) },
                    label = { Text(candidate.label) }
                )
            }
        }
    }
}

private enum class SecurityMode(val label: String) {
    NONE("None"),
    STARTTLS("STARTTLS"),
    SSL("SSL");

    fun toFlags(): Pair<Boolean, Boolean> = when (this) {
        NONE -> false to false
        STARTTLS -> false to true
        SSL -> true to false
    }
}

private fun imapSecurityMode(config: ImapAccountConfig): SecurityMode = when {
    config.imapSsl && !config.imapStartTls -> SecurityMode.SSL
    config.imapStartTls -> SecurityMode.STARTTLS
    else -> SecurityMode.NONE
}

private fun smtpSecurityMode(config: ImapAccountConfig): SecurityMode = when {
    config.smtpSsl && !config.smtpStartTls -> SecurityMode.SSL
    config.smtpStartTls -> SecurityMode.STARTTLS
    else -> SecurityMode.NONE
}

private val EffectiveSmtp.protocolLabel: String
    get() = when {
        useSsl -> "SSL"
        startTls -> "STARTTLS"
        else -> "none"
    }
