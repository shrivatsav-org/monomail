package com.shrivatsav.monomail.feature.settings

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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.security.SecurityUtil

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

    selectedImapAccount?.let { account ->
        ImapAccountDetailDialog(
            account = account,
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
    var editing by remember { mutableStateOf(false) }

    // Field state is keyed on (config, editing) so entering edit mode always
    // starts from the saved config — cancelling or saving discards stale edits.
    var imapHost by remember(config, editing) { mutableStateOf(config?.imapHost ?: "") }
    var imapPortStr by remember(config, editing) { mutableStateOf(config?.imapPort?.toString() ?: "") }
    var imapSsl by remember(config, editing) { mutableStateOf(config?.imapSsl ?: true) }
    var imapStartTls by remember(config, editing) { mutableStateOf(config?.imapStartTls ?: false) }
    var smtpHost by remember(config, editing) { mutableStateOf(config?.smtpHost ?: "") }
    var smtpPortStr by remember(config, editing) { mutableStateOf(config?.smtpPort?.takeIf { it > 0 }?.toString() ?: "") }
    var smtpSsl by remember(config, editing) { mutableStateOf(config?.smtpSsl ?: true) }
    var smtpStartTls by remember(config, editing) { mutableStateOf(config?.smtpStartTls ?: false) }

    val imapPort = imapPortStr.toIntOrNull()
    val smtpPort = smtpPortStr.toIntOrNull()
    val portsValid = imapPort != null && imapPort in 1..65535 &&
        (smtpPortStr.isBlank() || (smtpPort != null && smtpPort in 1..65535))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Storage, contentDescription = null)
        },
        title = {
            Text(if (editing) "Edit IMAP Account" else "IMAP Account")
        },
        text = {
            if (config == null) {
                Text(
                    "Could not decrypt IMAP configuration",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (editing) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("IMAP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = imapHost,
                        onValueChange = { imapHost = it },
                        label = { Text("IMAP Host") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = imapPortStr,
                        onValueChange = { imapPortStr = it.filter(Char::isDigit) },
                        label = { Text("IMAP Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("IMAP SSL", modifier = Modifier.weight(1f))
                        Switch(checked = imapSsl, onCheckedChange = { imapSsl = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("IMAP STARTTLS", modifier = Modifier.weight(1f))
                        Switch(checked = imapStartTls, onCheckedChange = { imapStartTls = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text("SMTP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP Host (blank = use IMAP host)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpPortStr,
                        onValueChange = { smtpPortStr = it.filter(Char::isDigit) },
                        label = { Text("SMTP Port (blank = auto)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("SMTP SSL", modifier = Modifier.weight(1f))
                        Switch(checked = smtpSsl, onCheckedChange = { smtpSsl = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("SMTP STARTTLS", modifier = Modifier.weight(1f))
                        Switch(checked = smtpStartTls, onCheckedChange = { smtpStartTls = it })
                    }
                    Text(
                        "Tip: port 587 normally uses STARTTLS, port 465 uses SSL.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailRow("Email", account.email)
                    DetailRow("Display Name", account.displayName.ifBlank { "—" })

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("IMAP Host", config.imapHost)
                    DetailRow("IMAP Port", config.imapPort.toString())
                    DetailRow("IMAP SSL", if (config.imapSsl) "Yes" else "No")
                    DetailRow("IMAP STARTTLS", if (config.imapStartTls) "Yes" else "No")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("SMTP Host", config.smtpHost.ifBlank { "auto (IMAP host)" })
                    DetailRow("SMTP Port", if (config.smtpPort > 0) config.smtpPort.toString() else "auto")
                    DetailRow("SMTP SSL", if (config.smtpSsl) "Yes" else "No")
                    DetailRow("SMTP STARTTLS", if (config.smtpStartTls) "Yes" else "No")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("Username", config.username.ifBlank { account.email })
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

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
