package com.shrivatsav.monomail.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
//import androidx.compose.material.icons.rounded.License
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.core.data.licensing.LicenseManager
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.VerifiedUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensingScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    val isLicensed by licenseManager.isLicensed.collectAsState()
    val cachedEmail = licenseManager.getCachedEmail()
    val cachedKey = licenseManager.getCachedLicenseKey()
    val cachedPlan = licenseManager.getCachedPlan()

    val maskedKey = cachedKey?.let { key ->
        val prefix = key.take(5) // "MONO-"
        prefix + "-XXXX-XXXX-XXXX-XXXX"
    }

    Scaffold(
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
        topBar = {
            TopAppBar(
                title = { Text("Licensing") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Icon(
                imageVector = if (isLicensed) Icons.Rounded.CheckCircle else Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = if (isLicensed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (isLicensed) "Licensed" else "Free Tier",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (isLicensed) "Gmail API access is active" else "Standard IMAP/SMTP",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            SettingsCard {
                InfoRow(
                    icon = Icons.Rounded.Key,
                    title = "License Key",
                    value = maskedKey ?: "N/A",
                    onClick = {}
                )
                if (isLicensed) {
                    CardDivider()
                    InfoRow(
                        icon = Icons.Rounded.VerifiedUser,
                        title = "Plan",
                        value = cachedPlan?.replaceFirstChar { it.uppercase() } ?: "Premium",
                        onClick = {}
                    )
                    CardDivider()
                    InfoRow(
                        icon = Icons.Rounded.Email,
                        title = "Registered to",
                        value = cachedEmail ?: "Unknown",
                        onClick = {}
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Gmail API requires a \$800/year CASA verification fee.\nContact the developer on Discord or email for access.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
