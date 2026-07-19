package com.shrivatsav.monomail.feature.auth

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import com.shrivatsav.monomail.ui.theme.cornerShape
import com.shrivatsav.monomail.ui.components.SlideSheet
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource

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
    var showVerificationModal by remember { mutableStateOf(false) }

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

    if (showVerificationModal) {
        VerificationModal(onDismiss = { showVerificationModal = false })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SignInContent(
            scale = scale.value,
            alpha = alpha.value,
            onContinueWithEmail = { showProviderSheet = true },
            onNavigateToLegal = onNavigateToLegal,
            context = context,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Snackbar(
                snackbarData = it,
                shape = com.shrivatsav.monomail.ui.theme.cornerShape(12.dp),
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionContentColor = MaterialTheme.colorScheme.inverseOnSurface
            )
        }

        if (showProviderSheet) {
            ProviderSheet(
                state = state,
                onDismiss = { if (state !is SignInState.Loading) showProviderSheet = false },
                onGoogleSignIn = { handleGoogleSignIn(onGithub = { showProviderSheet = false; showVerificationModal = true }, onOther = { viewModel.signIn(context) }) },
                onMicrosoftSignIn = { handleMicrosoftSignIn(context) { activity -> viewModel.signInMicrosoft(activity) } },
                onImapClick = onNavigateToImapSetup,
            )
        }
    }
}

private fun handleGoogleSignIn(
    onGithub: () -> Unit,
    onOther: () -> Unit
) {
    if (com.shrivatsav.monomail.feature.auth.BuildConfig.IS_GITHUB_BUILD) {
        onGithub()
    } else {
        onOther()
    }
}

private fun handleMicrosoftSignIn(context: Context, onSignIn: (android.app.Activity) -> Unit) {
    context.findActivity()?.let { onSignIn(it) }
        ?: Toast.makeText(context, "Activity not found", Toast.LENGTH_SHORT).show()
}

@Composable
private fun SignInContent(
    scale: Float,
    alpha: Float,
    onContinueWithEmail: () -> Unit,
    onNavigateToLegal: (String) -> Unit,
    context: Context,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .scale(scale)
            .alpha(alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(com.shrivatsav.monomail.feature.auth.R.drawable.ic_signin_mark),
            contentDescription = "Monomail",
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
            onClick = onContinueWithEmail,
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
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "monomail.millosaurs.me",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                try {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://monomail.millosaurs.me"))
                    )
                } catch (e: Exception) { android.util.Log.w("SignIn", "Failed to open URL", e) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSheet(
    state: SignInState,
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onMicrosoftSignIn: () -> Unit,
    onImapClick: () -> Unit,
) {
    SlideSheet(
        onDismiss = onDismiss,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Choose your provider",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.height(24.dp))
        ProviderButtons(
            state = state,
            onGoogleSignIn = onGoogleSignIn,
            onMicrosoftSignIn = onMicrosoftSignIn,
            onImapClick = onImapClick,
        )
    }
}

// =============================================================================
// ProviderSelectionDialog  (used when adding a second account from settings)
// =============================================================================

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProviderSelectionDialog(
    viewModel: SignInViewModel,
    onSuccess: () -> Unit,
    onNavigateToImapSetup: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showVerificationModal by remember { mutableStateOf(false) }

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

    if (showVerificationModal) {
        VerificationModal(onDismiss = { showVerificationModal = false })
    }

    Surface(
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = cornerShape(28.dp),
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
            ProviderButtons(
                state = state,
                onGoogleSignIn = {
                    handleGoogleSignIn(
                        onGithub = { showVerificationModal = true },
                        onOther = { viewModel.signIn(context) }
                    )
                },
                onMicrosoftSignIn = {
                    context.findActivity()?.let { activity ->
                        viewModel.signInMicrosoft(activity)
                    } ?: Toast.makeText(context, "Activity not found", Toast.LENGTH_SHORT).show()
                },
                onImapClick = onNavigateToImapSetup
            )
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

@Composable
private fun VerificationModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Verification in Progress",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "Google's OAuth verification is in progress and will be enabled once that completes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderButtons(
    state: SignInState,
    onGoogleSignIn: () -> Unit,
    onMicrosoftSignIn: () -> Unit,
    onImapClick: () -> Unit,
) {
    val isLoading = state is SignInState.Loading
    Button(
        onClick = onGoogleSignIn,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (isLoading) {
            LoadingIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(
                text = "Sign in with Google",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Button(
        onClick = onMicrosoftSignIn,
        enabled = !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = MaterialTheme.shapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ),
    ) {
        if (isLoading) {
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
        onClick = onImapClick,
        enabled = !isLoading,
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