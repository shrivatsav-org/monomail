package com.shrivatsav.monomail.ui.navigation
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
import androidx.navigation.compose.dialog
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
import java.net.URLDecoder
import java.net.URLEncoder
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
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
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
        // Phase A — fast: read cached auth state so the first frame renders immediately
        isAuthenticated = authManager.restoreSessionQuick()
        // One-time read: avoid subscribing to the full settings flow for just hasSeenWelcomePrompt
        hasSeenWelcomePrompt = settingsDataStore.settingsFlow.first().hasSeenWelcomePrompt
        isLoading = false
        onContentReady()
        // Phase B — background: refresh tokens and register push without blocking the UI
        if (isAuthenticated) {
            scope.launch {
                authManager.restoreSession()
            }
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
    val startDestination = if (isAuthenticated) {
        Screen.Inbox.route
    } else if (!hasSeenWelcomePrompt) {
        Screen.Onboarding.route
    } else {
        Screen.SignIn.route
    }
    val reauthInfo by authManager.reauthNeeded.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val reauth = reauthInfo
        if (reauth != null) {
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

        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = {
                when {
                    targetState.destination.route?.startsWith("compose") == true -> {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                    }
                    targetState.destination.route?.startsWith("thread") == true || targetState.destination.route?.startsWith("settings") == true -> {
                        slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300))
                    }
                    else -> fadeIn(animationSpec = tween(300))
                }
            },
            exitTransition = {
                when {
                    initialState.destination.route?.startsWith("compose") == true -> {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    }
                    initialState.destination.route?.startsWith("thread") == true || initialState.destination.route?.startsWith("settings") == true -> {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    }
                    else -> fadeOut(animationSpec = tween(300))
                }
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                when {
                    initialState.destination.route?.startsWith("compose") == true -> {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    }
                    initialState.destination.route?.startsWith("thread") == true || initialState.destination.route?.startsWith("settings") == true -> {
                        slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
                    }
                    else -> fadeOut(animationSpec = tween(300))
                }
            }
        ) {
            composable(Screen.Onboarding.route) {
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                com.shrivatsav.monomail.ui.screens.auth.OnboardingScreen(
                    onFinishOnboarding = {
                        scope.launch {
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
                SignInScreen(
                    viewModel      = vm,
                    onSignInSuccess = {
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    },
                    onNavigateToLegal = { type ->
                        navController.navigate(Screen.Legal.createRoute(type)) { launchSingleTop = true }
                    },
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
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                val activeAccount by authManager.activeAccountFlow.collectAsState(initial = authManager.currentUser)
                InboxScreen(
                    viewModel    = vm,
                    userProfile  = activeAccount,
                    onEmailClick = { threadId ->
                        navController.navigate(Screen.ThreadDetail.createRoute(threadId)) { launchSingleTop = true }
                    },
                    onSignOut = {
                        scope.launch {
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
                val accounts by authManager.accountsFlow.collectAsState(initial = emptyList())
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLegal = { type ->
                        navController.navigate(Screen.Legal.createRoute(type)) { launchSingleTop = true }
                    },
                    onNavigateToPgpKeys = {
                        navController.navigate(Screen.PgpKeys.route) { launchSingleTop = true }
                    },
                    accountCount = accounts.size
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
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable
                val vm: EmailDetailViewModel = hiltViewModel()
                EmailDetailScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                    onReply   = { to, subject, body, tid, messageId ->
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
                    onForward = { subject, body, tid, messageId ->
                        navController.navigate(
                            Screen.Compose.createRoute(
                                mode = ComposeMode.FORWARD,
                                to = "",
                                subject = subject,
                                threadId = tid,
                                messageId = messageId
                            )
                        )
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
            ) { backStackEntry ->
                val dec = { s: String -> URLDecoder.decode(s, "UTF-8") }
                val mode = ComposeMode.valueOf(
                    backStackEntry.arguments?.getString("mode") ?: "NEW"
                )
                val to = dec(backStackEntry.arguments?.getString("to") ?: "")
                val subject = dec(backStackEntry.arguments?.getString("subject") ?: "")
                val threadId = dec(backStackEntry.arguments?.getString("threadId") ?: "").takeIf { it.isNotEmpty() }
                val messageId = dec(backStackEntry.arguments?.getString("messageId") ?: "").takeIf { it.isNotEmpty() }
                val scheduledId = dec(backStackEntry.arguments?.getString("scheduledId") ?: "").takeIf { it.isNotEmpty() }
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
