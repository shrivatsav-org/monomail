package com.shrivatsav.monomail
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shrivatsav.monomail.data.settings.AppSettings
import com.shrivatsav.monomail.ui.navigation.NavGraph
import com.shrivatsav.monomail.ui.theme.MonoMailTheme
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
    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
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
                com.shrivatsav.monomail.data.settings.FontScale.EXTRA_SMALL -> 0.8f
                com.shrivatsav.monomail.data.settings.FontScale.SMALL       -> 0.9f
                com.shrivatsav.monomail.data.settings.FontScale.DEFAULT     -> 1.0f
                com.shrivatsav.monomail.data.settings.FontScale.LARGE       -> 1.15f
                com.shrivatsav.monomail.data.settings.FontScale.EXTRA_LARGE -> 1.3f
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    density = density.density,
                    fontScale = density.fontScale * fontScaleMultiplier
                )
            ) {
                MonoMailTheme(themeMode = settings.themeMode.name) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
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
    private fun requestNotificationPermissionAndScheduleSync() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            when {
                androidx.core.content.ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    scheduleBackgroundSync()
                }
                else -> {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            scheduleBackgroundSync()
        }
    }
    private fun scheduleBackgroundSync() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.shrivatsav.monomail.worker.EmailSyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(
                androidx.work.Constraints.Builder()
                    .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                    .build()
            )
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "EmailSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}