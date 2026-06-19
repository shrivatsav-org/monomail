package com.shrivatsav.monomail.ui.navigation
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import kotlinx.coroutines.launch
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shrivatsav.monomail.MonoMailApp
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.ui.screens.auth.SignInScreen
import com.shrivatsav.monomail.ui.screens.auth.SignInViewModel
import com.shrivatsav.monomail.ui.screens.compose.ComposeMode
import com.shrivatsav.monomail.ui.screens.compose.ComposeScreen
import com.shrivatsav.monomail.ui.screens.compose.ComposeViewModel
import com.shrivatsav.monomail.ui.screens.detail.EmailDetailScreen
import com.shrivatsav.monomail.ui.screens.detail.EmailDetailViewModel
import com.shrivatsav.monomail.ui.screens.inbox.InboxScreen
import com.shrivatsav.monomail.ui.screens.inbox.InboxViewModel
import com.shrivatsav.monomail.ui.screens.settings.SettingsScreen
import com.shrivatsav.monomail.ui.screens.settings.SettingsViewModel
import java.net.URLDecoder
import java.net.URLEncoder
sealed class Screen(val route: String) {
    object SignIn       : Screen("sign_in")
    object Inbox        : Screen("inbox")
    object ThreadDetail : Screen("thread/{threadId}") {
        fun createRoute(threadId: String) = "thread/$threadId"
    }
    object Compose      : Screen("compose?mode={mode}&to={to}&subject={subject}&threadId={threadId}&messageId={messageId}") {
        fun createRoute(
            mode: ComposeMode = ComposeMode.NEW,
            to: String = "",
            subject: String = "",
            threadId: String = "",
            messageId: String = ""
        ): String {
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            return "compose?mode=${mode.name}&to=${enc(to)}&subject=${enc(subject)}&threadId=${enc(threadId)}&messageId=${enc(messageId)}"
        }
    }
    object Settings : Screen("settings")
    object Legal : Screen("legal/{type}") {
        fun createRoute(type: String) = "legal/$type"
    }
}
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavGraph(
    authManager: AuthManager,
    emailRepository: EmailRepository
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as MonoMailApp
    val contactProvider = app.contactSuggestionProvider
    var isLoading by remember { mutableStateOf(true) }
    var isAuthenticated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isAuthenticated = authManager.restoreSession()
        isLoading = false
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
    } else {
        Screen.SignIn.route
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
            composable(Screen.SignIn.route) {
                val vm: SignInViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SignInViewModel(authManager, emailRepository) as T
                        }
                    }
                )
                SignInScreen(
                    viewModel      = vm,
                    onSignInSuccess = {
                        navController.navigate(Screen.Inbox.route) {
                            popUpTo(Screen.SignIn.route) { inclusive = true }
                        }
                    },
                    onNavigateToLegal = { type ->
                        navController.navigate(Screen.Legal.createRoute(type)) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Inbox.route) {
                val vm: InboxViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return InboxViewModel(emailRepository, contactProvider, authManager, app.settingsDataStore, app) as T
                        }
                    }
                )
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
                                emailRepository.clearLocalData()
                                navController.navigate(Screen.SignIn.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        }
                    },
                    onCompose = {
                        navController.navigate(Screen.Compose.createRoute()) { launchSingleTop = true }
                    },
                    onSettings = {
                        navController.navigate(Screen.Settings.route) { launchSingleTop = true }
                    }
                )
            }
            composable(Screen.Settings.route) {
                val app = context.applicationContext as MonoMailApp
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return SettingsViewModel(app.settingsDataStore) as T
                        }
                    }
                )
                val accounts by authManager.accountsFlow.collectAsState(initial = emptyList())
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToLegal = { type ->
                        navController.navigate(Screen.Legal.createRoute(type)) { launchSingleTop = true }
                    },
                    accountCount = accounts.size
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
                val appSettings by app.settingsDataStore.settingsFlow.collectAsState(
                    initial = com.shrivatsav.monomail.data.settings.AppSettings()
                )
                val vm: EmailDetailViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return EmailDetailViewModel(emailRepository, threadId) as T
                        }
                    }
                )
                EmailDetailScreen(
                    viewModel = vm,
                    onBack    = { navController.popBackStack() },
                    isConversationView = appSettings.organizeByThread,
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
                route = "compose?mode={mode}&to={to}&subject={subject}&threadId={threadId}&messageId={messageId}",
                arguments = listOf(
                    navArgument("mode") { type = NavType.StringType; defaultValue = "NEW" },
                    navArgument("to") { type = NavType.StringType; defaultValue = "" },
                    navArgument("subject") { type = NavType.StringType; defaultValue = "" },
                    navArgument("threadId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("messageId") { type = NavType.StringType; defaultValue = "" }
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
                val fromEmail = authManager.currentUser?.email ?: ""
                val vm: ComposeViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return ComposeViewModel(
                                repository = emailRepository,
                                contactProvider = contactProvider,
                                fromEmail = fromEmail,
                                app = app,
                                mode = mode,
                                replyTo = to,
                                originalSubject = subject,
                                threadId = threadId,
                                messageId = messageId
                            ) as T
                        }
                    }
                )
                ComposeScreen(
                    viewModel = vm,
                    onBack = { navController.popBackStack() },
                    onSent = { navController.popBackStack() }
                )
            }
        }
    }
}
