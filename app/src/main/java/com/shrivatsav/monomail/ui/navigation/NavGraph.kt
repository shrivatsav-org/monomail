package com.shrivatsav.monomail.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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

    // Restore persisted session before deciding start destination
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(400))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(400))
        }
    ) {
        // ── Sign In ──────────────────────────────────────────────────
        composable(Screen.SignIn.route) {
            val vm: SignInViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return SignInViewModel(authManager) as T
                    }
                }
            )
            SignInScreen(
                viewModel      = vm,
                onSignInSuccess = {
                    navController.navigate(Screen.Inbox.route) {
                        popUpTo(Screen.SignIn.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Inbox ────────────────────────────────────────────────────
        composable(Screen.Inbox.route) {
            val vm: InboxViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return InboxViewModel(emailRepository, contactProvider) as T
                    }
                }
            )
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            InboxScreen(
                viewModel    = vm,
                userProfile  = authManager.currentUser,
                onEmailClick = { threadId ->
                    navController.navigate(Screen.ThreadDetail.createRoute(threadId))
                },
                onSignOut = {
                    scope.launch {
                        authManager.signOut()
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                },
                onCompose = {
                    navController.navigate(Screen.Compose.createRoute())
                }
            )
        }

        // ── Thread Detail (conversation view) ────────────────────────
        composable(
            route = Screen.ThreadDetail.route,
            arguments = listOf(navArgument("threadId") { type = NavType.StringType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getString("threadId") ?: return@composable
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

        // ── Compose / Reply / Forward ────────────────────────────────
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