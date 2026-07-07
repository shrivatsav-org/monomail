package com.shrivatsav.monomail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity as LocalDensityComposable
import androidx.compose.ui.unit.Density
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.FontScale
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
import com.shrivatsav.monomail.worker.EmailSyncWorker
import com.shrivatsav.monomail.worker.GraphSubscriptionRenewalWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var authManager: AuthManager
    @Inject lateinit var emailRepository: EmailRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var accountManager: AccountManager

    /** Tracks whether content is ready for the SplashScreen transition. */
    @Volatile
    var isContentReady: Boolean = false
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition {
            // Keep splash visible until content is ready
            !isContentReady
        }
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState()
            val fontScaleMultiplier = when (settings.fontScale) {
                FontScale.EXTRA_SMALL -> 0.8f
                FontScale.SMALL       -> 0.9f
                FontScale.DEFAULT     -> 1.0f
                FontScale.LARGE       -> 1.15f
                FontScale.EXTRA_LARGE -> 1.3f
            }
            val density = LocalDensityComposable.current
            androidx.compose.runtime.CompositionLocalProvider(
                LocalDensityComposable provides Density(
                    density = density.density,
                    fontScale = density.fontScale * fontScaleMultiplier
                )
            ) {
                MonoMailTheme(
                    themeMode = settings.themeMode.name,
                    useSystemFont = settings.useSystemFont
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavGraph(
                            authManager = authManager,
                            emailRepository = emailRepository,
                            settingsDataStore = settingsDataStore,
                            onContentReady = { isContentReady = true }
                        )
                    }
                }
            }
            // Background sync is triggered from onStop; no permission requests at launch
            // — permissions are handled during onboarding.
            LaunchedEffect(Unit) {
                scheduleBackgroundSync()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch {
            accountManager.setLastActiveTime(System.currentTimeMillis())
        }
    }

    override fun onStop() {
        super.onStop()
        scheduleAdaptiveSync()
    }

    private fun scheduleAdaptiveSync() {
        val workRequest = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "adaptive_email_sync",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun scheduleBackgroundSync() {
        val workRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EmailSyncWork",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        val renewalWorkRequest = PeriodicWorkRequestBuilder<GraphSubscriptionRenewalWorker>(
            24, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "GraphSubscriptionRenewalWork",
            ExistingPeriodicWorkPolicy.KEEP,
            renewalWorkRequest
        )
    }
}
