package com.shrivatsav.monomail.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
            onDismiss = { selectedImapAccount = null }
        )
    }
}

@Composable
private fun ImapAccountDetailDialog(
    account: UserProfile,
    onDismiss: () -> Unit
) {
    val config: ImapAccountConfig? = remember(account.id) {
        try {
            val configJson = SecurityUtil.decryptString(account.accessToken)
            if (configJson != null) ImapAccountConfig.fromJson(configJson) else null
        } catch (e: Exception) { null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Storage, contentDescription = null)
        },
        title = {
            Text("IMAP Account")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Email", account.email)
                DetailRow("Display Name", account.displayName.ifBlank { "—" })

                if (config != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("IMAP Host", config.imapHost)
                    DetailRow("IMAP Port", config.imapPort.toString())
                    DetailRow("IMAP SSL", if (config.imapSsl) "Yes" else "No")
                    DetailRow("IMAP STARTTLS", if (config.imapStartTls) "Yes" else "No")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("SMTP Host", config.smtpHost)
                    DetailRow("SMTP Port", config.smtpPort.toString())
                    DetailRow("SMTP SSL", if (config.smtpSsl) "Yes" else "No")
                    DetailRow("SMTP STARTTLS", if (config.smtpStartTls) "Yes" else "No")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DetailRow("Username", config.username.ifBlank { account.email })
                } else {
                    Text(
                        "Could not decrypt IMAP configuration",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
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
