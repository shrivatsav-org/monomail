package com.shrivatsav.monomail.feature.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.Outbound
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.animation.fadeIn
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.material3.LinearProgressIndicator
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val PROVIDERS = listOf("Gmail", "Outlook", "Yahoo", "Zoho", "Custom")


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderDropdown(
    selectedProvider: String,
    onProviderSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            "Provider",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            shape = cornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedProvider, style = MaterialTheme.typography.bodyLarge)
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PROVIDERS.forEach { provider ->
                DropdownMenuItem(
                    text = { Text(provider) },
                    onClick = {
                        onProviderSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

private enum class TlsMode { NONE, SSL, STARTTLS }

@Composable
private fun ServerSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    host: String,
    port: String,
    ssl: Boolean,
    startTls: Boolean,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSslChange: (Boolean) -> Unit,
    onStartTlsChange: (Boolean) -> Unit,
    portImeAction: ImeAction
) {
    Spacer(modifier = Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
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
                Text("Signing in...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(cornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round
                )
                Text("Please wait, fetching your inbox", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ImapSetupForm(
    viewModel: ImapSetupViewModel,
    isBusy: Boolean,
    testState: ImapTestState,
    onTestCredentials: () -> Unit,
    onProceed: () -> Unit
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
    val isGmailMode by viewModel.isGmailMode.collectAsState()
    val selectedProvider by viewModel.selectedProvider.collectAsState()

    var showPassword by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    var showAdvanced by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Provider dropdown
        ProviderDropdown(
            selectedProvider = selectedProvider,
            onProviderSelected = { viewModel.selectProvider(it) }
        )

        // Email
        OutlinedTextField(
            value = username,
            onValueChange = { viewModel.setUsername(it) },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true
        )

        // Password / App Password
        OutlinedTextField(
            value = password,
            onValueChange = { viewModel.setPassword(it) },
            label = { Text(if (isGmailMode) "Gmail App Password" else "Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            supportingText = if (isGmailMode) {
                { Text("16-character app password from myaccount.google.com/apppasswords") }
            } else null
        )

        // Gmail App Password help link
        if (isGmailMode) {
            TextButton(
                onClick = { uriHandler.openUri("https://myaccount.google.com/apppasswords") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Generate Gmail App Password",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Advanced Settings accordion
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { showAdvanced = !showAdvanced },
            shape = cornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Advanced Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = showAdvanced,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { viewModel.setDisplayName(it) },
                    label = { Text("Display Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ServerSection(
                    title = "Incoming Server (IMAP)",
                    icon = Icons.Rounded.MoveToInbox,
                    host = imapHost,
                    port = imapPort,
                    ssl = imapSsl,
                    startTls = imapStartTls,
                    onHostChange = { viewModel.setImapHost(it) },
                    onPortChange = { viewModel.setImapPort(it) },
                    onSslChange = { viewModel.setImapSsl(it) },
                    onStartTlsChange = { viewModel.setImapStartTls(it) },
                    portImeAction = ImeAction.Next
                )

                ServerSection(
                    title = "Outgoing Server (SMTP)",
                    icon = Icons.AutoMirrored.Rounded.Outbound,
                    host = smtpHost,
                    port = smtpPort,
                    ssl = smtpSsl,
                    startTls = smtpStartTls,
                    onHostChange = { viewModel.setSmtpHost(it) },
                    onPortChange = { viewModel.setSmtpPort(it) },
                    onSslChange = { viewModel.setSmtpSsl(it) },
                    onStartTlsChange = { viewModel.setSmtpStartTls(it) },
                    portImeAction = ImeAction.Done
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onTestCredentials,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isBusy
        ) {
            if (testState is ImapTestState.Testing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Testing...")
            } else {
                Text("Test Credentials")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = testState is ImapTestState.Verified && !isBusy
        ) {
            Text("Proceed")
        }

        if (testState is ImapTestState.Verified) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Credentials verified — proceed to sign in",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (testState is ImapTestState.Error) {
            ErrorText(message = testState.message)
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}


@Composable
private fun ErrorText(message: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImapSetupScreen(
    viewModel: ImapSetupViewModel,
    prefillEmail: String? = null,
    prefillProvider: String? = null,
    onSetupComplete: () -> Unit,
    onBack: () -> Unit
) {
    val testState by viewModel.testState.collectAsState()
    val context = LocalContext.current
    val isBusy = testState is ImapTestState.Testing || testState is ImapTestState.Syncing

    // Pre-fill for Gmail if email was passed via navigation
    LaunchedEffect(prefillEmail) {
        if (!prefillEmail.isNullOrBlank()) {
            viewModel.prefillForGmail(prefillEmail)
        }
    }

    // Pre-select a provider if passed via navigation
    LaunchedEffect(prefillProvider) {
        if (!prefillProvider.isNullOrBlank()) {
            viewModel.selectProvider(prefillProvider)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.blur(if (testState is ImapTestState.Syncing) 10.dp else 0.dp),
            topBar = {
                TopAppBar(
                    title = { Text("Add Account") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding)) {
                ImapSetupForm(
                    viewModel = viewModel,
                    isBusy = isBusy,
                    testState = testState,
                    onTestCredentials = { viewModel.testCredentials(context) },
                    onProceed = { viewModel.proceedToSignIn() }
                )
            }
        }

        if (testState is ImapTestState.ShowSyncPrompt) {
            var pendingDays by remember { mutableStateOf<Int?>(null) }
            val days = pendingDays
            if (days == null) {
                SyncWindowDialog(
                    onConfirm = { pendingDays = it },
                    onCancel = { viewModel.cancelInitialSync() }
                )
            } else {
                SyncWarningDialog(
                    days = days,
                    onProceed = { viewModel.startInitialSync(it, onSetupComplete) },
                    onBack = { pendingDays = null },
                    onCancel = { viewModel.cancelInitialSync() }
                )
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
