$filePath = 'app/src/main/java/com/shrivatsav/monomail/ui/screens/inbox/InboxScreen.kt'
$content = Get-Content -Raw -Encoding UTF8 $filePath

# 1. Add BackHandler import
$content = $content.Replace(
    "import androidx.compose.animation.core.tween",
    "import androidx.activity.compose.BackHandler`r`nimport androidx.compose.animation.core.tween"
)

# 2. Hoist activeModal state
$targetHoist = @"
    val isRefreshing = (state as? InboxState.Success)?.isRefreshing == true

    if (showDonationPrompt) {
"@
$replacementHoist = @"
    val isRefreshing = (state as? InboxState.Success)?.isRefreshing == true

    var activeModal by remember { mutableStateOf<ModalType?>(null) }

    if (showDonationPrompt) {
"@
$content = $content.Replace($targetHoist, $replacementHoist)

# 3. Add activeModal to blur condition
$targetBlur = @"
                    .then(
                        if (longPressedThread != null) Modifier.blur(12.dp)
                        else Modifier
                    )
"@
$replacementBlur = @"
                    .then(
                        if (longPressedThread != null || activeModal != null) Modifier.blur(12.dp)
                        else Modifier
                    )
"@
$content = $content.Replace($targetBlur, $replacementBlur)

# 4. Modify InboxSearchBar definition
$targetSearchBarDef = @"
    isRefreshing: Boolean,
    toastState: InboxViewModel.ToastState?,
    onUndo: () -> Unit,
    onSettings: () -> Unit = {}
) {
    var activeModal by remember { mutableStateOf<ModalType?>(null) }

    val containerColor by androidx.compose.animation.animateColorAsState(
"@
$replacementSearchBarDef = @"
    isRefreshing: Boolean,
    toastState: InboxViewModel.ToastState?,
    onUndo: () -> Unit,
    onAvatarClick: () -> Unit,
    onSettings: () -> Unit = {}
) {
    val containerColor by androidx.compose.animation.animateColorAsState(
"@
$content = $content.Replace($targetSearchBarDef, $replacementSearchBarDef)

# 5. Modify InboxSearchBar call in InboxScreen
$targetSearchBarCall = @"
                isRefreshing = isRefreshing,
                toastState = toastState,
                onUndo = { viewModel.undoAction() },
                onSettings = onSettings
            )
"@
$replacementSearchBarCall = @"
                isRefreshing = isRefreshing,
                toastState = toastState,
                onUndo = { viewModel.undoAction() },
                onAvatarClick = { activeModal = ModalType.PROFILE },
                onSettings = onSettings
            )
"@
$content = $content.Replace($targetSearchBarCall, $replacementSearchBarCall)

# 6. Modify AvatarButton onClick
$targetAvatarClick = @"
                                    },
                                    onClick = { activeModal = ModalType.PROFILE }
                                )
"@
$replacementAvatarClick = @"
                                    },
                                    onClick = onAvatarClick
                                )
"@
$content = $content.Replace($targetAvatarClick, $replacementAvatarClick)

# 7. Remove ModalOverlay from InboxSearchBar
$targetRemoveModal = @"
        windowInsets = WindowInsets(0.dp)
    ) {}

    ModalOverlay(
        activeModal = activeModal,
        userProfile = userProfile,
        accounts = accounts,
        onDismiss = { activeModal = null },
        onSignOut = { activeModal = null; onSignOut() },
        onSwitchAccount = onSwitchAccount,
        onAddAccount = onAddAccount,
        onShowSwitchAccount = { activeModal = ModalType.SWITCH_ACCOUNT },
        onTrashClick = { activeModal = null; onTrashClick() },
        onStarredClick = { activeModal = null; onStarredClick() },
        onSettings = { activeModal = null; onSettings() },
    )
}
"@
$replacementRemoveModal = @"
        windowInsets = WindowInsets(0.dp)
    ) {}
}
"@
$content = $content.Replace($targetRemoveModal, $replacementRemoveModal)

# 8. Add ModalOverlay to the end of InboxScreen
$targetAddModal = @"
                }
            }
        }
    }

    if (threadToDelete != null) {
"@
$replacementAddModal = @"
                }
            }

            // Modal Overlay
            ModalOverlay(
                activeModal = activeModal,
                userProfile = userProfile,
                accounts = viewModel.accounts.collectAsState().value,
                onDismiss = { activeModal = null },
                onSignOut = { activeModal = null; onSignOut() },
                onSwitchAccount = { accountId -> viewModel.switchAccount(accountId) },
                onAddAccount = {
                    val accountsList = viewModel.accounts.value
                    if (accountsList.size >= 10) {
                        android.widget.Toast.makeText(context, "Maximum limit of 10 accounts reached.", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        onAddAccount()
                    }
                },
                onShowSwitchAccount = { activeModal = ModalType.SWITCH_ACCOUNT },
                onBackToProfile = { activeModal = ModalType.PROFILE },
                onTrashClick = { activeModal = null; viewModel.switchTab(com.shrivatsav.monomail.ui.screens.inbox.InboxTab.TRASH) },
                onStarredClick = { activeModal = null; viewModel.switchTab(com.shrivatsav.monomail.ui.screens.inbox.InboxTab.STARRED) },
                onSettings = { activeModal = null; onSettings() },
            )
        }
    }

    if (threadToDelete != null) {
"@
$content = $content.Replace($targetAddModal, $replacementAddModal)

# 9. Modify ModalOverlay signature and background
$targetModalSig = @"
    onShowSwitchAccount: () -> Unit,
    onTrashClick: () -> Unit,
    onStarredClick: () -> Unit,
    onSettings: () -> Unit,
) {
    // Keep last non-null modal so the exit animation has content to show
    var displayedModal by remember { mutableStateOf<ModalType?>(null) }
    LaunchedEffect(activeModal) {
        if (activeModal != null) displayedModal = activeModal
    }

    AnimatedVisibility(
        visible = activeModal != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
"@
$replacementModalSig = @"
    onShowSwitchAccount: () -> Unit,
    onBackToProfile: () -> Unit,
    onTrashClick: () -> Unit,
    onStarredClick: () -> Unit,
    onSettings: () -> Unit,
) {
    // Keep last non-null modal so the exit animation has content to show
    var displayedModal by remember { mutableStateOf<ModalType?>(null) }
    LaunchedEffect(activeModal) {
        if (activeModal != null) displayedModal = activeModal
    }

    BackHandler(enabled = activeModal != null) {
        if (activeModal == ModalType.SWITCH_ACCOUNT) {
            onBackToProfile()
        } else {
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = activeModal != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                .clickable(
"@
$content = $content.Replace($targetModalSig, $replacementModalSig)

[IO.File]::WriteAllText($filePath, $content, [System.Text.Encoding]::UTF8)
Write-Output "Done"
