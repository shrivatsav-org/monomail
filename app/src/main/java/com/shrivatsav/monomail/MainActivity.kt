package com.shrivatsav.monomail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity as LocalDensityComposable
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.shrivatsav.monomail.data.settings.AppSettings
import com.shrivatsav.monomail.data.settings.FontScale
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
import com.shrivatsav.monomail.worker.EmailSyncWorker
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
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleBackgroundSync()
        }
    }

    private val requestContactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(initial = AppSettings())
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
                MonoMailTheme(themeMode = settings.themeMode.name) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavGraph(
                            authManager = authManager,
                            emailRepository = emailRepository,
                            settingsDataStore = settingsDataStore
                        )
                    }
                }
            }
        }
        requestNotificationPermissionAndScheduleSync()
        requestContactsPermission()
    }

    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
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

    private fun requestNotificationPermissionAndScheduleSync() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    scheduleBackgroundSync()
                }
                else -> {
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            scheduleBackgroundSync()
        }
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
    }
}
