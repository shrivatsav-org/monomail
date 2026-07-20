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
import androidx.compose.ui.platform.LocalDensity as LocalDensityComposable
import androidx.compose.ui.unit.Density
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.core.data.settings.FontScale
import com.shrivatsav.monomail.core.data.settings.SettingsDataStore
import com.shrivatsav.monomail.core.data.settings.SyncFrequency
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
import com.shrivatsav.monomail.core.data.worker.EmailSyncWorker
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
        if (CrashHandler.getLastError(this) != null) {
            startActivity(android.content.Intent(this, CrashActivity::class.java))
            finish()
            return
        }
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
                    useSystemFont = settings.useSystemFont,
                    cornerStyle = settings.cornerStyle.name,
                    monochrome = settings.monochromeTheme
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
            // Re-schedule background sync whenever sync frequency changes
            LaunchedEffect(settings.syncFrequency) {
                scheduleBackgroundSync(settings.syncFrequency)
            }
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

    private fun scheduleBackgroundSync(frequency: SyncFrequency) {
        val intervalMinutes = when (frequency) {
            SyncFrequency.MIN_15 -> 15L
            SyncFrequency.MIN_30 -> 30L
            SyncFrequency.HOUR_1 -> 60L
            SyncFrequency.MANUAL -> {
                // Cancel periodic sync when user selects Manual
                WorkManager.getInstance(this).cancelUniqueWork("EmailSyncWork")
                // Still schedule push subscription renewal (independent of notification frequency)
                scheduleRenewalWorker()
                return
            }
        }
        val workRequest = PeriodicWorkRequestBuilder<EmailSyncWorker>(
            intervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EmailSyncWork",
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            workRequest
        )
        scheduleRenewalWorker()
    }
    
    private fun scheduleRenewalWorker() {
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
