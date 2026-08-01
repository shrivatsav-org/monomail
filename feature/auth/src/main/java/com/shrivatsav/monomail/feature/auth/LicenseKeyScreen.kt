package com.shrivatsav.monomail.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.core.data.licensing.LicenseManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicenseKeyScreen(
    licenseManager: LicenseManager,
    onKeyValidated: () -> Unit,
    onLicenseActivated: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Enter License Key") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Enter your license key",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Gmail API access requires a $800/year CASA verification fee the developer cannot cover. Keys are granted individually — contact the developer on Discord or email to request access.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = key,
                onValueChange = {
                    key = it.trim().uppercase()
                    error = null
                },
                label = { Text("License Key") },
                placeholder = { Text("MONO-XXXX-XXXX-XXXX-XXXX") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (key.isNotBlank() && !isLoading) {
                            focusManager.clearFocus()
                            scope.launch {
                                isLoading = true
                                val valid = licenseManager.activateLicense(key)
                                if (valid) {
                                    success = true
                                } else {
                                    error = "Invalid license key. Please check and try again."
                                    isLoading = false
                                }
                            }
                        }
                    }
                ),
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                enabled = !isLoading && !success
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (success) {
                val licensedEmail = licenseManager.getCachedEmail() ?: "your email"
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("License Activated") },
                    text = {
                        Text("Your license is registered to $licensedEmail.\n\nPlease sign in with this exact email address.")
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            onLicenseActivated?.invoke() ?: onKeyValidated()
                        }) {
                            Text("OK, Sign In")
                        }
                    },
                    dismissButton = null
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "License activated!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            isLoading = true
                            val valid = licenseManager.activateLicense(key)
                            if (valid) {
                                success = true
                            } else {
                                error = "Invalid license key. Please check and try again."
                            }
                            isLoading = false
                        }
                    },
                    enabled = key.isNotBlank() && !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Validate License")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onBack,
                enabled = !isLoading
            ) {
                Text("Use App Password instead")
            }
        }
    }
}
