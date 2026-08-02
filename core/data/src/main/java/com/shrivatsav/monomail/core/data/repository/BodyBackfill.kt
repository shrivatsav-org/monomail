package com.shrivatsav.monomail.core.data.repository

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Tracks body-backfill progress displayed in the notification area.
 *
 * Shows a persistent system notification while the repository downloads email
 * body content (text, HTML, inline images) from newest to oldest, after the
 * fast header/subject sync has already populated the inbox.
 */
data class BodyBackfillState(
    val total: Int,
    val completed: Int,
    val accountEmail: String,
    /** Display name of the folder (tab) whose content is currently downloading. */
    val folder: String? = null
) {
    val progress: Float get() = if (total > 0) completed.toFloat() / total else 0f
    val finished: Boolean get() = completed >= total
}

/** DB-derived download state for the sync-status modal: how much of the
 *  account's email content has been downloaded so far. */
data class BodyDownloadStats(
    val total: Int,
    val downloaded: Int
) {
    val progress: Float get() = if (total > 0) downloaded.toFloat() / total else 0f
}

internal const val BODY_BACKFILL_CHANNEL_ID = "body_backfill"
internal const val BODY_BACKFILL_NOTIFICATION_ID = 0xBB

/**
 * Creates the body-backfill channel. Silent (IMPORTANCE_LOW) so the long
 * download never alerts: no sound on start, on folder switches, or on
 * progress updates. Recreates the channel once if it exists with an older
 * importance, because channel importance is immutable after creation.
 */
internal fun ensureBodyBackfillChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (nm.getNotificationChannel(BODY_BACKFILL_CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_LOW) return
    nm.deleteNotificationChannel(BODY_BACKFILL_CHANNEL_ID)
    val channel = NotificationChannel(
        BODY_BACKFILL_CHANNEL_ID,
        "Email content download",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Shows progress while email body content downloads in the background"
    }
    nm.createNotificationChannel(channel)
}

/**
 * Builds the live body-download notification.
 *
 * Live Update (promoted ongoing) on Android 16+; classic progress bar on all
 * levels. (ProgressStyle segments were tried on 37 but its setProgress()
 * semantics filled the bar regardless of the counter — dropped for correctness.)
 */
internal fun buildBodyBackfillNotification(context: Context, completed: Int, total: Int, folder: String? = null): Notification {
    val builder = Notification.Builder(context, BODY_BACKFILL_CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(if (folder != null) "Downloading $folder content" else "Downloading email content")
        .setContentText(if (total > 0) "$completed of $total" else "Starting...")
        .setOngoing(true)
        .setCategory(Notification.CATEGORY_PROGRESS)
        // Fully silent: the channel is IMPORTANCE_LOW, so no sound on the
        // first post or on any progress update.
        .setOnlyAlertOnce(true)

    // Live Updates opt-in (Android 16+): promotes the ongoing notification to
    // the top of the shade / lock screen / status-bar chip.
    if (Build.VERSION.SDK_INT >= 36) {
        builder.setRequestPromotedOngoing(true)
    }

    builder.setProgress(total, completed, total == 0)
    return builder.build()
}

internal class BodyBackfillNotificationHelper(private val context: Context) {

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureBodyBackfillChannel(context)
    }

    fun showProgress(state: BodyBackfillState) {
        nm.notify(BODY_BACKFILL_NOTIFICATION_ID, buildBodyBackfillNotification(context, state.completed, state.total, state.folder))
    }

    fun dismiss() {
        nm.cancel(BODY_BACKFILL_NOTIFICATION_ID)
    }
}
