package com.shrivatsav.monomail.ui.screens.inbox

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrivatsav.monomail.auth.UserProfile

internal enum class ModalType { PROFILE, SWITCH_ACCOUNT, ADD_ACCOUNT }

internal data class ModalCallbacks(
    val onDismiss: () -> Unit,
    val onSignOut: () -> Unit,
    val onSwitchAccount: (String) -> Unit,
    val onAddAccount: () -> Unit,
    val onShowSwitchAccount: () -> Unit,
    val onBackToProfile: () -> Unit,
    val onCycleAccount: (String) -> Unit,
    val onSettings: () -> Unit,
    val onNavigateToImapSetup: () -> Unit,
    val onToggleUnified: (Boolean) -> Unit = {},
)

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
internal fun ModalOverlay(
    activeModal: ModalType?,
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    callbacks: ModalCallbacks,
    unifiedInboxEnabled: Boolean = false,
) {
    var displayed by remember { mutableStateOf<ModalType?>(null) }
    displayed = activeModal ?: displayed

    if (activeModal != null) {
        BackHandler {
            when (activeModal) {
                ModalType.SWITCH_ACCOUNT -> callbacks.onBackToProfile()
                ModalType.PROFILE -> callbacks.onDismiss()
                ModalType.ADD_ACCOUNT -> callbacks.onDismiss()
            }
        }
    }

    AnimatedVisibility(
        visible = activeModal != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { callbacks.onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = displayed,
                transitionSpec = {
                    if (initialState == null) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (scaleIn(
                            tween(200, easing = FastOutSlowInEasing),
                            initialScale = 0.85f
                        ) + fadeIn(tween(200))) togetherWith
                                (scaleOut(tween(200), targetScale = 0.85f) +
                                        fadeOut(tween(180)))
                    }
                },
                label = "ModalContent"
            ) { modal ->
                ModalContentBody(
                    modal = modal,
                    userProfile = userProfile,
                    accounts = accounts,
                    callbacks = callbacks,
                    unifiedInboxEnabled = unifiedInboxEnabled
                )
            }
        }
    }
}

@Composable
private fun ModalContentBody(
    modal: ModalType?,
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    callbacks: ModalCallbacks,
    unifiedInboxEnabled: Boolean,
) {
    Box(
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ) {},
        contentAlignment = Alignment.Center
    ) {
        when (modal) {
            ModalType.ADD_ACCOUNT -> {
                val vm: com.shrivatsav.monomail.ui.screens.auth.SignInViewModel = hiltViewModel()
                com.shrivatsav.monomail.ui.screens.auth.ProviderSelectionDialog(
                    viewModel = vm,
                    onSuccess = { callbacks.onDismiss() },
                    onNavigateToImapSetup = callbacks.onNavigateToImapSetup
                )
            }
            ModalType.PROFILE -> {
                if (userProfile != null) {
                    ProfileCard(
                        userProfile = userProfile,
                        accounts = accounts,
                        callbacks = ProfileCardCallbacks(
                            onSignOut = callbacks.onSignOut,
                            onShowSwitchAccount = callbacks.onShowSwitchAccount,
                            onCycleAccount = callbacks.onCycleAccount,
                            onSettings = callbacks.onSettings,
                            onAddAccount = callbacks.onAddAccount,
                            onToggleUnified = callbacks.onToggleUnified,
                        ),
                        unifiedInboxEnabled = unifiedInboxEnabled,
                    )
                }
            }
            ModalType.SWITCH_ACCOUNT -> {
                if (userProfile != null) {
                    SwitchAccountCard(
                        userProfile = userProfile,
                        accounts = accounts,
                        onSwitchAccount = callbacks.onSwitchAccount,
                        onAddAccount = callbacks.onAddAccount,
                        onBack = callbacks.onBackToProfile,
                    )
                }
            }
            null -> { /* no modal */ }
        }
    }
}
