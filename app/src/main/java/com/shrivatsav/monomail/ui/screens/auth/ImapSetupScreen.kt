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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

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
    val suggestedConfig by viewModel.suggestedConfig.collectAsState()

    val context = LocalContext.current

    val isFormValid = imapHost.isNotBlank() && imapPort.isNotBlank() &&
                      smtpHost.isNotBlank() && smtpPort.isNotBlank() &&
                      username.isNotBlank() && password.isNotBlank()

    val isSaveEnabled = testState is ImapTestState.Success

    Scaffold(
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

            AnimatedVisibility(visible = suggestedConfig != null) {
                val config = suggestedConfig
                if (config != null) {
                    SuggestionChip(
                        onClick = { viewModel.applySuggestion(config) },
                        label = { Text("Auto-fill server settings") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Text("Account Info", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            OutlinedTextField(
                value = username,
                onValueChange = { viewModel.setUsername(it) },
                label = { Text("Email Address") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { viewModel.setPassword(it) },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                singleLine = true
            )

            OutlinedTextField(
                value = displayName,
                onValueChange = { viewModel.setDisplayName(it) },
                label = { Text("Display Name") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Incoming Server (IMAP)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = imapHost,
                    onValueChange = { viewModel.setImapHost(it) },
                    label = { Text("Host") },
                    modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    singleLine = true
                )
                OutlinedTextField(
                    value = imapPort,
                    onValueChange = { viewModel.setImapPort(it) },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use SSL")
                Switch(
                    checked = imapSsl,
                    onCheckedChange = { 
                        viewModel.setImapSsl(it)
                        if (it) viewModel.setImapStartTls(false)
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use STARTTLS")
                Switch(
                    checked = imapStartTls,
                    onCheckedChange = { 
                        viewModel.setImapStartTls(it)
                        if (it) viewModel.setImapSsl(false)
                    },
                    enabled = !imapSsl
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Outgoing Server (SMTP)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = smtpHost,
                    onValueChange = { viewModel.setSmtpHost(it) },
                    label = { Text("Host") },
                    modifier = Modifier.weight(2f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    singleLine = true
                )
                OutlinedTextField(
                    value = smtpPort,
                    onValueChange = { viewModel.setSmtpPort(it) },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use SSL")
                Switch(
                    checked = smtpSsl,
                    onCheckedChange = { 
                        viewModel.setSmtpSsl(it)
                        if (it) viewModel.setSmtpStartTls(false)
                    }
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Use STARTTLS")
                Switch(
                    checked = smtpStartTls,
                    onCheckedChange = { 
                        viewModel.setSmtpStartTls(it)
                        if (it) viewModel.setSmtpSsl(false)
                    },
                    enabled = !smtpSsl
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.testConnection(context) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = isFormValid && testState !is ImapTestState.Testing
            ) {
                if (testState is ImapTestState.Testing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Testing...")
                } else if (testState is ImapTestState.Success) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connection Successful")
                } else {
                    Text("Test Connection")
                }
            }

            if (testState is ImapTestState.Error) {
                Text(
                    text = (testState as ImapTestState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Button(
                onClick = { viewModel.saveAccount(onSetupComplete) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = isSaveEnabled
            ) {
                Text("Save Account")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
