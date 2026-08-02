package com.shrivatsav.monomail

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.core.database.local.AppDatabase
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.core.network.provider.imap.ImapProvider
import com.shrivatsav.monomail.security.SecurityUtil
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the exact DeepSyncService sequence — startBackgroundDeepSync(days, id)
 * then startBodyBackfill(id) — through the real repository, logging every
 * syncProgress/bodyBackfillProgress emission. This is the ground truth for
 * "does the deep sync complete and does the notification progress advance".
 */
@RunWith(AndroidJUnit4::class)
class DeepSyncPipelineTest {

    @Test
    fun fullPipeline() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accountManager = AccountManager(context)
        val profile = accountManager.getAccounts().firstOrNull { it.provider == "imap" }
            ?: error("No IMAP account")
        Log.i("DeepPipe", "account=${profile.email} id=${profile.id}")

        val db = AppDatabase.getDatabase(context)
        val repo = EmailRepository(
            providerFactory = { p ->
                val config = ImapAccountConfig.fromJson(SecurityUtil.decryptString(p.accessToken)!!)
                val password = SecurityUtil.decryptString(p.refreshToken)!!
                ImapProvider(config, password, context)
            },
            database = db,
            context = context,
            accountManager = accountManager,
            pendingActionDao = db.pendingActionDao(),
            pendingSendDao = db.pendingSendDao()
        )

        val progressJob = launch {
            repo.syncProgress.collect { sp ->
                Log.i("DeepPipe", "syncProgress: ${sp?.fraction} folder=${sp?.folder}")
            }
        }
        val backfillJob = launch {
            repo.bodyBackfillProgress.collect { bp ->
                Log.i("DeepPipe", "backfill: ${bp?.completed}/${bp?.total} folder=${bp?.folder}")
            }
        }

        val t0 = System.currentTimeMillis()
        val deepOk = withTimeoutOrNull(15 * 60_000) {
            repo.startBackgroundDeepSync(7, profile.id)
        }
        Log.i("DeepPipe", "startBackgroundDeepSync returned ${if (deepOk == null) "HUNG>15min" else "ok"} in ${System.currentTimeMillis() - t0}ms")

        val t1 = System.currentTimeMillis()
        val backfillOk = withTimeoutOrNull(15 * 60_000) {
            repo.startBodyBackfill(profile.id)
        }
        Log.i("DeepPipe", "startBodyBackfill returned ${if (backfillOk == null) "HUNG>15min" else "ok"} in ${System.currentTimeMillis() - t1}ms")

        progressJob.cancel()
        backfillJob.cancel()
    }
}
