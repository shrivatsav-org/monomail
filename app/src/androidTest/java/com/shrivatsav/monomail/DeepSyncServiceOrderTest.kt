package com.shrivatsav.monomail

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.worker.DeepSyncService
import com.shrivatsav.monomail.core.database.local.AppDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device diagnostic for the notification contract:
 *  - the body-backfill channel is silent (IMPORTANCE_LOW) — a long download
 *    must never alert, on start or on folder switches,
 *  - the persistent "All synced" (0xDE) notification is only posted AFTER
 *    the body backfill has finished (its 0xBB notification is dismissed),
 *    not right after the header sync.
 * Runs the real DeepSyncService FGS end to end against the live account.
 */
@RunWith(AndroidJUnit4::class)
class DeepSyncServiceOrderTest {

    private val backfillNotificationId = 0xBB // BODY_BACKFILL_NOTIFICATION_ID
    private val deepSyncNotificationId = 0xDE

    @Test
    fun doneNotificationPostedOnlyAfterBackfillFinished(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val accountManager = AccountManager(context)
        val profile = accountManager.getAccounts().firstOrNull { it.provider == "imap" }
            ?: error("No IMAP account")
        Log.i("DeepOrder", "account=${profile.email} id=${profile.id}")
        val db = AppDatabase.getDatabase(context)
        val missingBefore = db.emailDao().getEmailsMissingBody(profile.id, 500).size
        Log.i("DeepOrder", "missing bodies before: $missingBefore")

        // Force a real body backfill: clear the 10 newest bodies so the
        // download sweep has work to do. The sweep re-downloads them from the
        // server, so the DB self-heals by the end of the test.
        val cleared = db.openHelper.writableDatabase.execSQL(
            "UPDATE emails SET body = '' WHERE id IN " +
                "(SELECT id FROM emails WHERE accountId = ? AND body IS NOT NULL AND body != '' ORDER BY date DESC LIMIT 10)",
            arrayOf(profile.id)
        )
        Log.i("DeepOrder", "cleared $cleared bodies to force backfill work")

        // Bring the app to the foreground so the FGS start is allowed on
        // Android 12+ (foreground-service-start restrictions).
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        delay(2000)

        val t0 = System.currentTimeMillis()
        DeepSyncService.start(context, profile.id, 1)

        var sawBackfillNotif = false
        var doneObserved = false
        var backfillActiveAtDone = false
        var firstBackfillSeenAt = -1L
        var lastBackfillSeenAt = -1L
        val observed = withTimeoutOrNull(5 * 60_000) {
            while (true) {
                val active = nm.activeNotifications
                val backfill = active.firstOrNull { it.id == backfillNotificationId }
                if (backfill != null) {
                    if (!sawBackfillNotif) firstBackfillSeenAt = System.currentTimeMillis() - t0
                    sawBackfillNotif = true
                    lastBackfillSeenAt = System.currentTimeMillis() - t0
                }
                val done = active.firstOrNull {
                    it.id == deepSyncNotificationId &&
                        it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() == "All synced"
                }
                if (done != null) {
                    doneObserved = true
                    backfillActiveAtDone = backfill != null
                    Log.i(
                        "DeepOrder",
                        "done notification observed at +${System.currentTimeMillis() - t0}ms, backfill active=$backfillActiveAtDone"
                    )
                    return@withTimeoutOrNull true
                }
                delay(200)
            }
        }
        Log.i(
            "DeepOrder",
            "done observed: $doneObserved; backfill notification seen: $sawBackfillNotif " +
                "(first +${firstBackfillSeenAt}ms, last +${lastBackfillSeenAt}ms)"
        )
        assertTrue("All synced (0xDE) notification never appeared", observed == true && doneObserved)
        assertTrue(
            "test setup: body backfill notification never appeared — nothing to prove the ordering",
            sawBackfillNotif
        )
        assertFalse(
            "done notification posted while body backfill was still active — it must wait for the content download",
            backfillActiveAtDone
        )

        // The backfill must have re-downloaded the cleared bodies.
        val missingAfter = db.emailDao().getEmailsMissingBody(profile.id, 500).size
        assertEquals("backfill should have restored all cleared bodies", 0, missingAfter)
        Log.i("DeepOrder", "missing bodies after: $missingAfter")

        // The channel must be silent: a long-running download must never alert.
        val chan = nm.getNotificationChannel("body_backfill")
        assertEquals(
            "body_backfill channel must be IMPORTANCE_LOW (silent)",
            NotificationManager.IMPORTANCE_LOW,
            chan?.importance
        )
        Log.i("DeepOrder", "body_backfill channel importance=${chan?.importance} (LOW=2)")
    }
}
