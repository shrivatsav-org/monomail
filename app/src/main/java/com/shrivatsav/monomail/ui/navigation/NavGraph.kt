package com.shrivatsav.monomail.ui.navigation
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import com.shrivatsav.monomail.ui.screens.auth.ImapSetupScreen
import com.shrivatsav.monomail.ui.screens.auth.ImapSetupViewModel
import com.shrivatsav.monomail.ui.screens.auth.SignInScreen
import com.shrivatsav.monomail.ui.screens.auth.SignInViewModel
import com.shrivatsav.monomail.ui.screens.compose.ComposeMode
import com.shrivatsav.monomail.ui.screens.compose.ComposeScreen
import com.shrivatsav.monomail.ui.screens.compose.ComposeViewModel
import com.shrivatsav.monomail.ui.screens.detail.EmailDetailScreen
import com.shrivatsav.monomail.ui.screens.detail.EmailDetailViewModel
import com.shrivatsav.monomail.ui.screens.inbox.InboxScreen
import com.shrivatsav.monomail.ui.screens.inbox.InboxViewModel
import com.shrivatsav.monomail.ui.screens.scheduled.ScheduledMessagesScreen
import com.shrivatsav.monomail.ui.screens.scheduled.ScheduledMessagesViewModel
import com.shrivatsav.monomail.ui.screens.pgp.PgpKeyManagementScreen
import com.shrivatsav.monomail.ui.screens.settings.SettingsScreen
import com.shrivatsav.monomail.ui.screens.settings.SettingsViewModel

sealed class Screen(val route: String) {
    object Onboarding   : Screen("onboarding")
    object SignIn       : Screen("sign_in")
    object ImapSetup    : Screen("imap_setup")
    object Inbox        : Screen("inbox")
    object ThreadDetail : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
    object Compose      : Screen("compose?mode={mode}&to={to}&subject={subject}&threadId={threadId}&messageId={messageId}&scheduledId={scheduledId}") {
        fun createRoute(
            mode: ComposeMode = ComposeMode.NEW,
            to: String = "",
            subject: String = "",
            threadId: String = "",
            messageId: String = "",
            scheduledId: String = ""
        ): String {
            val enc = { s: String -> Uri.encode(s) }
            return "compose?mode=${mode.name}&to=${enc(to)}&subject=${enc(subject)}&threadId=${enc(threadId)}&messageId=${enc(messageId)}&scheduledId=${enc(scheduledId)}"
        }
    }
    object Scheduled : Screen("scheduled")
    object Settings : Screen("settings")
    object Legal : Screen("legal/{type}") {
        fun createRoute(type: String) = "legal/$type"
    }
    object PgpKeys : Screen("pgp_keys")
}

private fun openLegalUrl(context: android.content.Context, type: String) {
    val url = if (type == "privacy") "https://monomail.millosaurs.me/pp" else "https://monomail.millosaurs.me/tos"
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
        .apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
    try { context.startActivity(intent) } catch (e: Exception) { android.util.Log.w("NavGraph", "Failed to open legal URL", e) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReauthDialog(
    authManager: AuthManager,
    navController: androidx.navigation.NavHostController,
    scope: CoroutineScope
) {
    val reauthInfo by authManager.reauthNeeded.collectAsState()
    val reauth = reauthInfo
    if (reauth == null) return

    AlertDialog(
        onDismissRequest = { authManager.dismissReauth() },
        title = { Text("Session Expired") },
        text = {
            Text(
                "Your ${reauth.provider.replaceFirstChar { it.uppercase() }} account " +
                "(${reauth.email}) needs to be re-authenticated.\n\n" +
                "You can sign out this account and continue with your other accounts."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    authManager.dismissReauth()
                    val accounts = authManager.getAccounts()
                    val targetId = accounts.find { it.email == reauth.email }?.id ?: return@launch
                    authManager.removeAccount(targetId)
                    val remaining = authManager.getAccounts()
                    if (remaining.isEmpty()) {
                        authManager.signOutAll()
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }) {
                Text("Sign out this account")
            }
        },
        dismissButton = {
            TextButton(onClick = { authManager.dismissReauth() }) {
                Text("Later")
            }
        }
    )
}

private fun routeType(route: String?): String = when {
    route?.startsWith("compose") == true -> "compose"
    route?.startsWith("thread") == true || route?.startsWith("settings") == true -> "thread"
    else -> ""
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.slideInForRoute() = when (routeType(targetState.destination.route)) {
    "compose" -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
    "thread" -> slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
    else -> fadeIn(animationSpec = tween(300))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.slideOutForRoute() = when (routeType(initialState.destination.route)) {
    "compose" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    "thread" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    else -> fadeOut(animationSpec = tween(300))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun AnimatedContentTransitionScope<androidx.navigation.NavBackStackEntry>.popExitForRoute() = when (routeType(initialState.destination.route)) {
    "compose" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    "thread" -> slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
    else -> fadeOut(animationSpec = tween(300))
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavGraph(
    authManager: AuthManager,
    emailRepository: EmailRepository,
    settingsDataStore: SettingsDataStore,
    onContentReady: () -> Unit = {}
) {
    val navController = rememberNavController()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var hasSeenWelcomePrompt by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAuthenticated = authManager.restoreSessionQuick()
        hasSeenWelcomePrompt = settingsDataStore.settingsFlow.first().hasSeenWelcomePrompt
        isLoading = false
        onContentReady()
        if (isAuthenticated) {
            scope.launch { authManager.restoreSession() }
        }
    }
    if (isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        return
    }
    val startDestination = when {
        isAuthenticated -> Screen.Inbox.route
        !hasSeenWelcomePrompt -> Screen.Onboarding.route
        else -> Screen.SignIn.route
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ReauthDialog(authManager, navController, scope)

        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = ::slideInForRoute,
            exitTransition = ::slideOutForRoute,
            popEnterTransition = { fadeIn(animationSpec = tween(300)) },
            popExitTransition = ::popExitForRoute
        ) {
            composable(Screen.Onboarding.route) {
                val onboardingScope = androidx.compose.runtime.rememberCoroutineScope()
                com.shrivatsav.monomail.ui.screens.auth.OnboardingScreen(
                    onFinishOnboarding = {
                        onboardingScope.launch {
                            settingsDataStore.setHasSeenWelcomePrompt(true)
                            navController.navigate(Screen.SignIn.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    }
                )
            }
            composable(Screen.SignIn.route) {
                val vm: SignInViewModel = hiltViewModel()
                val ctx = LocalContext.current
                SignInScreen(
                    viewModel      = vm,
                    onSignInSuccess = {
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    },
                    onNavigateToLegal = { type -> openLegalUrl(ctx, type) },
                    onNavigateToImapSetup = {
                        navController.navigate(Screen.ImapSetup.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.ImapSetup.route) {
                val vm: ImapSetupViewModel = hiltViewModel()
                ImapSetupScreen(
                    viewModel = vm,
                    onSetupComplete = {
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Inbox.route) {
                val vm: InboxViewModel = hiltViewModel()
                val inboxScope = androidx.compose.runtime.rememberCoroutineScope()
                val activeAccount by authManager.activeAccountFlow.collectAsState(initial = authManager.currentUser)
                InboxScreen(
                    viewModel    = vm,
                    userProfile  = activeAccount,
                    onEmailClick = { threadId ->
                        navController.navigate(Screen.ThreadDetail.createRoute(threadId)) { launchSingleTop = true }
                    },
                    onSignOut = {
                        inboxScope.launch {
                            authManager.signOutActiveAccount()
                            val accounts = authManager.getAccounts()
                            if (accounts.isEmpty()) {
                                navController.navigate(Screen.SignIn.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                                emailRepository.clearLocalData()
                            }
                        }
                    },
                    onCompose = {
                        navController.navigate(Screen.Compose.createRoute()) { launchSingleTop = true }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                    },
                    onScheduledClick = {
                        navController.navigate(Screen.Scheduled.route) { launchSingleTop = true }
                    },
                    onNavigateToImapSetup = {
                        navController.navigate(Screen.ImapSetup.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                val ctx = LocalContext.current
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLegal = { type -> openLegalUrl(ctx, type) },
                    onNavigateToPgpKeys = {
                        navController.navigate(Screen.PgpKeys.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.PgpKeys.route) {
                PgpKeyManagementScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.Legal.route,
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "privacy"
                com.shrivatsav.monomail.ui.screens.settings.LegalScreen(
                    type = type,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Screen.ThreadDetail.route,
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) { _ ->
                val vm: EmailDetailViewModel = hiltViewModel()
                EmailDetailScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                    onReply   = { to, subject, _, tid, messageId ->
                        navController.navigate(
                            Screen.Compose.createRoute(
                                mode = ComposeMode.REPLY,
                                to = to,
                                subject = subject,
                                threadId = tid,
                                messageId = messageId
                            )
                        )
                    },
                    onForward = { subject, _, tid, messageId ->
                        navController.navigate(
                            Screen.Compose.createRoute(
                                mode = ComposeMode.FORWARD,
                                to = "",
                                subject = subject,
                                threadId = tid,
                                messageId = messageId
                            )
                        )
                    },
                    onFetchAttachment = { messageId, attachmentId ->
                        vm.fetchAttachmentBytes(messageId, attachmentId)
                    }
                )
            }
            composable(
                route = "compose?mode={mode}&to={to}&subject={subject}&threadId={threadId}&messageId={messageId}&scheduledId={scheduledId}",
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType; defaultValue = "NEW" },
                    navArgument("to") { type = NavType.StringType; defaultValue = "" },
                    navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                    navArgument("threadId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("messageId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("scheduledId") { type = NavType.StringType; defaultValue = "" }
                )
            ) { _ ->
                val vm: ComposeViewModel = hiltViewModel()
                ComposeScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() }
                )
            }
            composable(Screen.Scheduled.route) {
                val vm: ScheduledMessagesViewModel = hiltViewModel()
                ScheduledMessagesScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onCompose = {
                        navController.navigate(Screen.Compose.createRoute())
                    },
                    onEdit = { scheduled ->
                        navController.navigate(
                            Screen.Compose.createRoute(
                                to = scheduled.to,
                                subject = scheduled.subject,
                                scheduledId = scheduled.id
                            )
                        )
                    }
                )
            }
        }
    }
}
