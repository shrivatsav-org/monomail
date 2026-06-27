package com.shrivatsav.monomail.ui.screens.auth

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// =============================================================================
// SignInScreen
// =============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    viewModel: SignInViewModel,
    onSignInSuccess: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    onNavigateToImapSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showProviderSheet by remember { mutableStateOf(false) }
    var showApiKeyScreen by remember { mutableStateOf(false) }
    var hasApiKey by remember { mutableStateOf(viewModel.getCustomGoogleClientId(context).isNotBlank()) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResult(context)
        }
    }

    val scale = remember { Animatable(0.9f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        )
    }
    LaunchedEffect(Unit) {
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        )
    }
    LaunchedEffect(state) {
        when (state) {
            is SignInState.Success -> onSignInSuccess()
            is SignInState.NeedsConsent -> {
                consentLauncher.launch((state as SignInState.NeedsConsent).intent)
            }
            is SignInState.Error -> {
                showProviderSheet = false
                snackbarHostState.showSnackbar((state as SignInState.Error).message)
            }
            else -> {}
        }
    }

    // Show the OAuth setup screen as a full-screen overlay
    if (showApiKeyScreen) {
        ApiKeyConfigurationScreen(
            viewModel = viewModel,
            onDismiss = { showApiKeyScreen = false },
            onKeySaved = {
                hasApiKey = viewModel.getCustomGoogleClientId(context).isNotBlank()
                showApiKeyScreen = false
            },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .scale(scale.value)
                .alpha(alpha.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = com.shrivatsav.monomail.R.mipmap.ic_launcher,
                contentDescription = "Monomail Icon",
                modifier = Modifier.size(96.dp),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Mono Mail",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your inbox, distilled.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = { showProviderSheet = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Continue with Email",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToLegal("privacy") },
                )
                Text(
                    text = " • ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
                Text(
                    text = "Terms of Service",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onNavigateToLegal("tos") },
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Snackbar(snackbarData = it)
        }

        if (showProviderSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (state !is SignInState.Loading) showProviderSheet = false
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Choose your provider",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && !hasApiKey) {
                                showProviderSheet = false
                                showApiKeyScreen = true
                            } else {
                                viewModel.signIn(context)
                            }
                        },
                        enabled = state !is SignInState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        if (state is SignInState.Loading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && !hasApiKey) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "Configure",
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Configure Google API Key",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            }
                        } else {
                            Text(
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && hasApiKey) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Change Client ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    showProviderSheet = false
                                    showApiKeyScreen = true
                                },
                            )
                            Text(
                                text = "  •  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                text = "Reset Client ID",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    viewModel.clearCustomGoogleClientId(context)
                                    hasApiKey = viewModel.getCustomGoogleClientId(context).isNotBlank()
                                    Toast.makeText(context, "Google Client ID reset", Toast.LENGTH_SHORT).show()
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            context.findActivity()?.let { activity ->
                                viewModel.signInMicrosoft(activity)
                            } ?: Toast.makeText(context, "Activity not found", Toast.LENGTH_SHORT).show()
                        },
                        enabled = state !is SignInState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                        ),
                    ) {
                        if (state is SignInState.Loading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                        } else {
                            Text(
                                text = "Sign in with Microsoft",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { onNavigateToImapSetup() },
                        enabled = state !is SignInState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                    ) {
                        Text(
                            text = "Other (IMAP/SMTP)",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

// =============================================================================
// ProviderSelectionDialog  (used when adding a second account from settings)
// =============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionDialog(
    viewModel: SignInViewModel,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit,
    onNavigateToImapSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showApiKeyScreen by remember { mutableStateOf(false) }
    var hasApiKey by remember { mutableStateOf(viewModel.getCustomGoogleClientId(context).isNotBlank()) }

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onConsentResult(context)
        }
    }

    LaunchedEffect(state) {
        when (state) {
            is SignInState.Success -> onSuccess()
            is SignInState.NeedsConsent -> {
                consentLauncher.launch((state as SignInState.NeedsConsent).intent)
            }
            else -> {}
        }
    }

    if (showApiKeyScreen) {
        ApiKeyConfigurationScreen(
            viewModel = viewModel,
            onDismiss = { showApiKeyScreen = false },
            onKeySaved = {
                hasApiKey = viewModel.getCustomGoogleClientId(context).isNotBlank()
                showApiKeyScreen = false
            },
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 32.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Add Account",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Choose your provider",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && !hasApiKey) {
                        showApiKeyScreen = true
                    } else {
                        viewModel.signIn(context)
                    }
                },
                enabled = state !is SignInState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                if (state is SignInState.Loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && !hasApiKey) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Configure",
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Configure Google API Key",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    Text(
                        text = "Sign in with Google",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (com.shrivatsav.monomail.BuildConfig.IS_GITHUB_BUILD && hasApiKey) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Change Client ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            showApiKeyScreen = true
                        },
                    )
                    Text(
                        text = "  •  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = "Reset Client ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.clickable {
                            viewModel.clearCustomGoogleClientId(context)
                            hasApiKey = viewModel.getCustomGoogleClientId(context).isNotBlank()
                            Toast.makeText(context, "Google Client ID reset", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    context.findActivity()?.let { activity ->
                        viewModel.signInMicrosoft(activity)
                    } ?: Toast.makeText(context, "Activity not found", Toast.LENGTH_SHORT).show()
                },
                enabled = state !is SignInState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                if (state is SignInState.Loading) {
                    LoadingIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSecondary,
                    )
                } else {
                    Text(
                        text = "Sign in with Microsoft",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onNavigateToImapSetup() },
                enabled = state !is SignInState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = MaterialTheme.shapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text(
                    text = "Other (IMAP/SMTP)",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            if (state is SignInState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = (state as SignInState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// =============================================================================
// Helpers
// =============================================================================

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// =============================================================================
// ApiKeyConfigurationScreen  — full 5-step M3 Expressive setup wizard
// =============================================================================

private data class SetupStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val SETUP_STEPS = listOf(
    SetupStep(
        title = "Create GCP Project",
        subtitle = "Set up your Google Cloud project and enable the Gmail API",
        icon = Icons.Rounded.Shield,
    ),
    SetupStep(
        title = "OAuth Consent Screen",
        subtitle = "Configure scopes, app info, and test users",
        icon = Icons.Rounded.Policy,
    ),
    SetupStep(
        title = "Android Client ID",
        subtitle = "Register your APK signing key with Google",
        icon = Icons.Rounded.Smartphone,
    ),
    SetupStep(
        title = "Web Client ID",
        subtitle = "Create the credential used by Credential Manager",
        icon = Icons.Rounded.Web,
    ),
    SetupStep(
        title = "Paste & Save",
        subtitle = "Enter the Web Client ID into Monomail",
        icon = Icons.Rounded.Key,
    ),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ApiKeyConfigurationScreen(
    viewModel: SignInViewModel,
    onDismiss: () -> Unit,
    onKeySaved: () -> Unit,
) {
    val context = LocalContext.current
    var currentStep by remember { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf(viewModel.getCustomGoogleClientId(context)) }
    val shaKey = remember { com.shrivatsav.monomail.security.SecurityUtil.getAppSigningSha1(context) }

    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(currentStep) {
        progressAnim.animateTo(
            targetValue = (currentStep + 1f) / SETUP_STEPS.size,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Google OAuth Setup",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Step ${currentStep + 1} of ${SETUP_STEPS.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentStep > 0) currentStep-- else onDismiss()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Animated linear progress
            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            // Segmented pill strip
            StepPills(currentStep = currentStep, total = SETUP_STEPS.size)

            // Sliding step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(
                        initialOffsetX = { it * dir },
                        animationSpec = tween(300),
                    ) + fadeIn(tween(300))) togetherWith
                            (slideOutHorizontally(
                                targetOffsetX = { -it * dir },
                                animationSpec = tween(300),
                            ) + fadeOut(tween(200)))
                },
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                label = "step_content",
            ) { step ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    StepHeaderCard(step = SETUP_STEPS[step], stepIndex = step)
                    Spacer(modifier = Modifier.height(24.dp))
                    when (step) {
                        0 -> Step1Body()
                        1 -> Step2Body()
                        2 -> Step3Body(shaKey = shaKey, packageName = context.packageName, context = context)
                        3 -> Step4Body(context = context)
                        4 -> Step5Body(apiKey = apiKey, onApiKeyChange = { apiKey = it })
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // Sticky bottom nav
            BottomNavBar(
                currentStep = currentStep,
                totalSteps = SETUP_STEPS.size,
                onBack = { if (currentStep > 0) currentStep-- else onDismiss() },
                onNext = { currentStep++ },
                onFinish = {
                    val trimmed = apiKey.trim()
                    if (trimmed.isNotBlank()) {
                        viewModel.saveCustomGoogleClientId(context, trimmed)
                        onKeySaved()
                        onDismiss()
                    } else {
                        Toast.makeText(context, "Please enter a Web Client ID first", Toast.LENGTH_SHORT).show()
                    }
                },
                canFinish = apiKey.trim().isNotBlank(),
            )
        }
    }
}

// =============================================================================
// Step pills
// =============================================================================

@Composable
private fun StepPills(currentStep: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(total) { index ->
            val active = index == currentStep
            val done = index < currentStep
            Box(
                modifier = Modifier
                    .weight(if (active) 2.5f else 1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            done -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                            active -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
            )
        }
    }
}

// =============================================================================
// Step header card
// =============================================================================

@Composable
private fun StepHeaderCard(step: SetupStep, stepIndex: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = step.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step ${stepIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// =============================================================================
// Shared atoms
// =============================================================================

@Composable
private fun InstructionItem(number: Int, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun InfoBox(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun CopyableChip(label: String, value: String, context: Context) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp),
            )
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ContentCopy,
                contentDescription = "Copy",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ScopeBadge(scope: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = scope,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

// =============================================================================
// Step bodies
// =============================================================================

@Composable
private fun Step1Body() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Instructions")
        InstructionItem(1, "Open Google Cloud Console at console.cloud.google.com")
        InstructionItem(2, "Click the project dropdown at the top and select New Project")
        InstructionItem(3, "Name it something like \"MonoMail App\" and click Create")
        InstructionItem(4, "Make sure the new project is selected in the top bar")
        InstructionItem(5, "Go to APIs & Services > Library")
        InstructionItem(6, "Search for \"Gmail API\", open it, and click Enable")
        Spacer(modifier = Modifier.height(8.dp))
        FilledTonalButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/library/gmail.googleapis.com"))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Gmail API in Console")
        }
    }
}

@Composable
private fun Step2Body() {
    val context = LocalContext.current
    val scopes = listOf(
        "https://mail.google.com/",
        "email",
        "profile",
        "openid",
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Instructions")
        InstructionItem(1, "Go to APIs & Services > OAuth consent screen")
        InstructionItem(2, "Select External (or Internal if on Workspace), then click Create")
        InstructionItem(3, "Fill in App name as \"MonoMail\", your support email, and developer contact")
        InstructionItem(4, "Click Save and Continue, then on Scopes click Add or Remove Scopes")
        InstructionItem(5, "Add the four scopes below, click Update, then Save and Continue")
        InstructionItem(6, "On Test Users, add your Gmail address(es), then Save and Continue")
        Spacer(modifier = Modifier.height(4.dp))
        SectionLabel("Required Scopes")
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            scopes.forEach { ScopeBadge(it) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        FilledTonalButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/credentials/consent"))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open OAuth Consent Screen")
        }
    }
}

@Composable
private fun Step3Body(shaKey: String, packageName: String, context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Instructions")
        InstructionItem(1, "Go to APIs & Services > Credentials")
        InstructionItem(2, "Click Create Credentials > OAuth client ID")
        InstructionItem(3, "Under Application type, select Android")
        InstructionItem(4, "Enter any name, e.g. \"MonoMail Android Client\"")
        InstructionItem(5, "Copy the package name and SHA-1 fingerprint below and paste them in")
        InstructionItem(6, "Click Create — you don't need to save the resulting ID anywhere")
        Spacer(modifier = Modifier.height(4.dp))
        SectionLabel("Your App Credentials")
        CopyableChip(label = "Package Name", value = packageName, context = context)
        Spacer(modifier = Modifier.height(6.dp))
        CopyableChip(label = "SHA-1 Fingerprint — tap to copy", value = shaKey, context = context)
        Spacer(modifier = Modifier.height(4.dp))
        InfoBox {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "The SHA-1 above is computed live from your installed APK, so it exactly matches the key Google will validate during sign-in.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/credentials"))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Credentials Page")
        }
    }
}

@Composable
private fun Step4Body(context: Context) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("Instructions")
        InstructionItem(1, "On the same Credentials page, click Create Credentials > OAuth client ID again")
        InstructionItem(2, "Under Application type, select Web application")
        InstructionItem(3, "Enter any name, e.g. \"MonoMail Web Client\"")
        InstructionItem(4, "Leave Authorized redirect URIs blank — not needed for mobile OAuth")
        InstructionItem(5, "Click Create")
        InstructionItem(6, "In the dialog that appears, copy the Client ID ending in .apps.googleusercontent.com")
        Spacer(modifier = Modifier.height(4.dp))
        InfoBox {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Web,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 1.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Credential Manager on Android uses this Web Client ID to request OpenID tokens — even though the app runs on a phone. This is by design.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FilledTonalButton(
            onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://console.cloud.google.com/apis/credentials"))
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Open Credentials Page")
        }
    }
}

@Composable
private fun Step5Body(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoBox {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(top = 2.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Almost done! Paste the Web Application Client ID you copied in Step 4 below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text("Web Application Client ID") },
            placeholder = {
                Text(
                    "xxxxxx.apps.googleusercontent.com",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = {
                Icon(Icons.Rounded.Key, contentDescription = null, modifier = Modifier.size(20.dp))
            },
            supportingText = {
                Text(
                    text = "Should end in .apps.googleusercontent.com",
                    style = MaterialTheme.typography.labelSmall,
                )
            },
        )
        AnimatedVisibility(visible = apiKey.trim().isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Client ID entered. Tap Save & Sign In below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

// =============================================================================
// Bottom nav bar
// =============================================================================

@Composable
private fun BottomNavBar(
    currentStep: Int,
    totalSteps: Int,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    canFinish: Boolean,
) {
    val isLast = currentStep == totalSteps - 1
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentStep > 0) {
                FilledTonalButton(
                    onClick = onBack,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Back")
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Button(
                onClick = if (isLast) onFinish else onNext,
                enabled = if (isLast) canFinish else true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.large,
            ) {
                Text(if (isLast) "Save & Sign In" else "Next")
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (isLast) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}