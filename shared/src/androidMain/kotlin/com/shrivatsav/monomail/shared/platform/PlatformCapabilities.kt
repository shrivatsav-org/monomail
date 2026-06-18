package com.shrivatsav.monomail.shared.platform

actual class BackgroundScheduler {
    actual fun scheduleSync() {
        // TODO: enqueue a periodic WorkManager sync request.
    }

    actual fun cancelSync() {
        // TODO: cancel the WorkManager sync request.
    }
}
