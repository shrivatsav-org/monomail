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
import com.shrivatsav.monomail.auth.UserProfile

internal enum class ModalType { PROFILE, SWITCH_ACCOUNT, ADD_ACCOUNT }

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
internal fun ModalOverlay(
    activeModal: ModalType?,
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onShowSwitchAccount: () -> Unit,
    onBackToProfile: () -> Unit,
    onCycleAccount: (String) -> Unit,
    onSettings: () -> Unit,
    onNavigateToImapSetup: () -> Unit
) {
    var displayed by remember { mutableStateOf<ModalType?>(null) }
    displayed = activeModal ?: displayed

    if (activeModal != null) {
        BackHandler {
            when (activeModal) {
                ModalType.SWITCH_ACCOUNT -> onBackToProfile()
                ModalType.PROFILE -> onDismiss()
                ModalType.ADD_ACCOUNT -> onDismiss()
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
                ) { onDismiss() },
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
                Box(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                    contentAlignment = Alignment.Center
                ) {
                    when (modal) {
                        ModalType.ADD_ACCOUNT -> {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val a = ctx.applicationContext as com.shrivatsav.monomail.MonoMailApp
                            val vm: com.shrivatsav.monomail.ui.screens.auth.SignInViewModel =
                                androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = object :
                                        androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(
                                            modelClass: Class<T>
                                        ): T =
                                            com.shrivatsav.monomail.ui.screens.auth.SignInViewModel(
                                                a.authManager,
                                                a.emailRepository
                                            ) as T
                                    }
                                )
                            com.shrivatsav.monomail.ui.screens.auth.ProviderSelectionDialog(
                                viewModel = vm,
                                onDismiss = { onDismiss() },
                                onSuccess = { onDismiss() },
                                onNavigateToImapSetup = onNavigateToImapSetup
                            )
                        }

                        ModalType.PROFILE -> {
                            if (userProfile != null) {
                                ProfileCard(
                                    userProfile = userProfile,
                                    accounts = accounts,
                                    onSignOut = onSignOut,
                                    onShowSwitchAccount = onShowSwitchAccount,
                                    onCycleAccount = onCycleAccount,
                                    onSettings = onSettings,
                                    onAddAccount = onAddAccount,
                                )
                            }
                        }

                        ModalType.SWITCH_ACCOUNT -> {
                            if (userProfile != null) {
                                SwitchAccountCard(
                                    userProfile = userProfile,
                                    accounts = accounts,
                                    onSwitchAccount = onSwitchAccount,
                                    onAddAccount = onAddAccount,
                                    onBack = onBackToProfile,
                                )
                            }
                        }

                        null -> {}
                    }
                }
            }
        }
    }
}
