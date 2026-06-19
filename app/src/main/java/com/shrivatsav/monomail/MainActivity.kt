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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalDensity as LocalDensityComposable
import androidx.compose.ui.unit.Density
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shrivatsav.monomail.data.settings.AppSettings
import com.shrivatsav.monomail.data.settings.FontScale
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
import com.shrivatsav.monomail.worker.EmailSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val authManager by lazy {
        (application as MonoMailApp).authManager
    }
    private val emailRepository by lazy {
        (application as MonoMailApp).emailRepository
    }
    private val settingsDataStore by lazy {
        (application as MonoMailApp).settingsDataStore
    }
    private val accountManager by lazy {
        (application as MonoMailApp).accountManager
    }
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            scheduleBackgroundSync()
        }
    }

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
                            emailRepository = emailRepository
                        )
                    }
                }
            }
        }
        requestNotificationPermissionAndScheduleSync()
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
