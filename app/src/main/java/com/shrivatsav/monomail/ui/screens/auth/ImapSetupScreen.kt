package com.shrivatsav.monomail.ui.screens.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Outbound
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import com.shrivatsav.monomail.data.provider.imap.ImapAccountConfig
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val CUSTOM_CONFIG = "Custom Configuration"

@Composable
private fun AccountSettingsSection(
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    onApplyPreset: (ImapAccountConfig) -> Unit
) {
    var expandedProvider by remember { mutableStateOf(false) }
    var selectedProvider by remember { mutableStateOf(CUSTOM_CONFIG) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("Account Settings", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expandedProvider,
        onExpandedChange = { expandedProvider = !expandedProvider }
    ) {
        OutlinedTextField(
            value = selectedProvider,
            onValueChange = {},
            readOnly = true,
            label = { Text("Provider Setup") },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvider) },
            colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
        )
        ExposedDropdownMenu(
            expanded = expandedProvider,
            onDismissRequest = { expandedProvider = false }
        ) {
            listOf("Gmail", "Outlook", "Yahoo", "Zoho", CUSTOM_CONFIG).forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider) },
                    onClick = {
                        selectedProvider = provider
                        expandedProvider = false
                        if (provider != CUSTOM_CONFIG) onApplyPreset(ImapAccountConfig.presetForHost(provider)!!)
                    }
                )
            }
        }
    }

    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        label = { Text("Name (Optional)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Email Address") },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        singleLine = true
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("App Password / Password") },
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
        singleLine = true
    )
}

@Composable
private fun ServerSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    ssl: Boolean,
    onSslChange: (Boolean) -> Unit,
    startTls: Boolean,
    onStartTlsChange: (Boolean) -> Unit,
    portImeAction: ImeAction
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = host,
            onValueChange = onHostChange,
            label = { Text("Host") },
            modifier = Modifier.weight(0.7f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            singleLine = true
        )
        OutlinedTextField(
            value = port,
            onValueChange = onPortChange,
            label = { Text("Port") },
            modifier = Modifier.weight(0.3f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = portImeAction),
            singleLine = true
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Use SSL")
        Switch(
            checked = ssl,
            onCheckedChange = { onSslChange(it) }
        )
    }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Use STARTTLS")
        Switch(
            checked = startTls,
            onCheckedChange = { onStartTlsChange(it) },
            enabled = !ssl
        )
    }
}

@Composable
private fun SyncingOverlay() {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0.95f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 15000,
                easing = androidx.compose.animation.core.LinearOutSlowInEasing
            )
        )
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text("Syncing emails...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round
                )
                Text("Please wait, fetching your inbox", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImapSetupScreen(
    viewModel: ImapSetupViewModel,
    onSetupComplete: () -> Unit,
    onBack: () -> Unit
) {
    val imapHost by viewModel.imapHost.collectAsState()
    val imapPort by viewModel.imapPort.collectAsState()
    val imapSsl by viewModel.imapSsl.collectAsState()
    val imapStartTls by viewModel.imapStartTls.collectAsState()

    val smtpHost by viewModel.smtpHost.collectAsState()
    val smtpPort by viewModel.smtpPort.collectAsState()
    val smtpSsl by viewModel.smtpSsl.collectAsState()
    val smtpStartTls by viewModel.smtpStartTls.collectAsState()

    val username by viewModel.username.collectAsState()
    val password by viewModel.password.collectAsState()
    val displayName by viewModel.displayName.collectAsState()

    val testState by viewModel.testState.collectAsState()
    val context = LocalContext.current

    val isFormValid = imapHost.isNotBlank() && imapPort.isNotBlank() &&
                      smtpHost.isNotBlank() && smtpPort.isNotBlank() &&
                      username.isNotBlank() && password.isNotBlank()

    val isBusy = testState is ImapTestState.Testing || testState is ImapTestState.Syncing

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.blur(if (testState is ImapTestState.Syncing) 10.dp else 0.dp),
            topBar = {
                TopAppBar(
                    title = { Text("Add IMAP Account") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AccountSettingsSection(
                    displayName = displayName,
                    onDisplayNameChange = { viewModel.setDisplayName(it) },
                    username = username,
                    onUsernameChange = { viewModel.setUsername(it) },
                    password = password,
                    onPasswordChange = { viewModel.setPassword(it) },
                    onApplyPreset = { viewModel.applySuggestion(it) }
                )

                ServerSection(
                    title = "Incoming Server (IMAP)",
                    icon = Icons.Rounded.MoveToInbox,
                    host = imapHost,
                    onHostChange = { viewModel.setImapHost(it) },
                    port = imapPort,
                    onPortChange = { viewModel.setImapPort(it) },
                    ssl = imapSsl,
                    onSslChange = { viewModel.setImapSsl(it); if (it) viewModel.setImapStartTls(false) },
                    startTls = imapStartTls,
                    onStartTlsChange = { viewModel.setImapStartTls(it); if (it) viewModel.setImapSsl(false) },
                    portImeAction = ImeAction.Next
                )

                ServerSection(
                    title = "Outgoing Server (SMTP)",
                    icon = Icons.AutoMirrored.Rounded.Outbound,
                    host = smtpHost,
                    onHostChange = { viewModel.setSmtpHost(it) },
                    port = smtpPort,
                    onPortChange = { viewModel.setSmtpPort(it) },
                    ssl = smtpSsl,
                    onSslChange = { viewModel.setSmtpSsl(it); if (it) viewModel.setSmtpStartTls(false) },
                    startTls = smtpStartTls,
                    onStartTlsChange = { viewModel.setSmtpStartTls(it); if (it) viewModel.setSmtpSsl(false) },
                    portImeAction = ImeAction.Done
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.testAndSaveAccount(context, onSetupComplete) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = isFormValid && !isBusy
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (testState is ImapTestState.Testing) "Signing In..." else "Syncing Emails...")
                    } else {
                        Text("Sign In")
                    }
                }

                if (testState is ImapTestState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = (testState as ImapTestState.Error).message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        AnimatedVisibility(
            visible = testState is ImapTestState.Syncing,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            SyncingOverlay()
        }
    }
}
