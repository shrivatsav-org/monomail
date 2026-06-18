package com.shrivatsav.monomail.shared.platform

/**
 * Background sync scheduling. Android actual: WorkManager. iOS actual: BGTaskScheduler.
 * (Auth moved to the shared Ktor PKCE flow in com.shrivatsav.monomail.shared.auth.)
 */
expect class BackgroundScheduler {
    fun scheduleSync()
    fun cancelSync()
}
