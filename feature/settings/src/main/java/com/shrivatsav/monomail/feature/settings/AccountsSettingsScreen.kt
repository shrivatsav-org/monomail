package com.shrivatsav.monomail.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.auth.AuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsSettingsScreen(
    authManager: AuthManager,
    onBack: () -> Unit
) {
    val accounts by authManager.accountsFlow.collectAsState(initial = emptyList())
    val reauthInfo by authManager.reauthNeeded.collectAsState()

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
                    
                    val providerIcon = when (account.provider.lowercase()) {
                        "gmail" -> Icons.Rounded.Email
                        "outlook" -> Icons.Rounded.ForwardToInbox
                        "imap" -> Icons.Rounded.Storage
                        else -> Icons.Rounded.AccountCircle
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
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
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
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
}
