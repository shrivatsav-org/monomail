package com.shrivatsav.monomail

import android.os.Bundle
import android.os.Build
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
import com.shrivatsav.monomail.core.data.repository.dismissSyncCompletionNotifications
import com.shrivatsav.monomail.core.data.settings.FontScale
import com.shrivatsav.monomail.core.data.settings.SettingsDataStore
import com.shrivatsav.monomail.core.data.settings.SyncFrequency
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
import com.shrivatsav.monomail.core.data.worker.EmailSyncWorker
import com.shrivatsav.monomail.core.data.licensing.LicenseManager
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

    /** One notice per app session when a Gmail API account is blocked. */
    private var gmailBlockNoticeShown = false

    /** Tracks whether content is ready for the SplashScreen transition. */
    @Volatile
    var isContentReady: Boolean = false
        private set

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            android.util.Log.d("MainActivity", "Notification permission granted")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (CrashHandler.getLastError(this) != null) {
            startActivity(android.content.Intent(this, CrashActivity::class.java))
            finish()
            return
        }
        // Request notification permission on Android 13+ for body backfill and sync progress
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
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
        dismissSyncCompletionNotifications(this)
        checkGmailLicense()
        CoroutineScope(Dispatchers.IO).launch {
            accountManager.setLastActiveTime(System.currentTimeMillis())
        }
    }

    /** Re-validates the cached license when a Gmail API account exists and
     *  notifies once when it is blocked (the repository refuses to resolve a
     *  provider for unlicensed Gmail accounts, so sync silently stops). */
    private fun checkGmailLicense() {
        if (!LicenseManager.gmailApiAvailable) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hasGmailApiAccount = accountManager.getAccounts().any { it.provider == "gmail" }
                if (!hasGmailApiAccount) return@launch
                val licenseManager = LicenseManager(applicationContext)
                val licensed = licenseManager.checkLicense()
                if (!licensed && !gmailBlockNoticeShown) {
                    gmailBlockNoticeShown = true
                    notifyGmailApiBlocked()
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "License check failed", e)
            }
        }
    }

    private fun notifyGmailApiBlocked() {
        try {
            val nm = getSystemService(android.app.NotificationManager::class.java)
            val channelId = "license_required"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    android.app.NotificationChannel(
                        channelId,
                        "License required",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
            val intent = android.content.Intent(this, MainActivity::class.java)
            val pi = android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(com.shrivatsav.monomail.core.data.R.drawable.ic_notification_leaf)
                .setContentTitle("Gmail sync paused")
                .setContentText("A Gmail API account could not verify its Play Store license. Reinstall from Google Play or use IMAP.")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(0xCF, notification)
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Gmail license notice failed", e)
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
                // Manual: no frequent background checks, but keep a quiet 6h
                // safety-net sync so accounts never go fully stale (GitHub builds
                // have no push to fall back on).
                6 * 60L
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
