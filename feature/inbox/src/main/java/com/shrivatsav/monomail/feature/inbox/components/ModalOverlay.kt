package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrivatsav.monomail.core.data.auth.UserProfile

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
        enter = fadeIn(tween(220)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { callbacks.onDismiss() },
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedContent(
                targetState = displayed,
                transitionSpec = {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(180))
                },
                label = "ModalContent",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 16.dp)
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
                val vm: com.shrivatsav.monomail.feature.auth.SignInViewModel = hiltViewModel()
                com.shrivatsav.monomail.feature.auth.ProviderSelectionDialog(
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
