package com.shrivatsav.monomail.shared.platform

actual class BackgroundScheduler {
    actual fun scheduleSync() {
        // TODO: register a BGAppRefreshTask (best-effort background sync).
    }

    actual fun cancelSync() {
        // TODO: cancel the pending BGAppRefreshTask.
    }
}
