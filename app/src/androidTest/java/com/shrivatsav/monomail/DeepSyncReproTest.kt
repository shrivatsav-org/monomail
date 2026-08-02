package com.shrivatsav.monomail

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.network.provider.EmailFolder
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.core.network.provider.imap.ImapProvider
import com.shrivatsav.monomail.security.SecurityUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Reproduces the deep-sync code path (listThreads with sinceDate → SEARCH)
 * in-process with the real account, logging each step so a hang is pinpointed.
 * Also probes concurrent getThread calls (the parallel backfill shape).
 */
@RunWith(AndroidJUnit4::class)
class DeepSyncReproTest {

    @Test
    fun reproDeepSyncSearchPath() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accountManager = AccountManager(context)
        val profile = accountManager.getAccounts().firstOrNull { it.provider == "imap" }
        check(profile != null) { "No IMAP account configured" }
        Log.i("DeepSyncRepro", "account: ${profile.email} id=${profile.id}")

        val configJson = SecurityUtil.decryptString(profile.accessToken)
            ?: error("cannot decrypt config")
        val password = SecurityUtil.decryptString(profile.refreshToken)
            ?: error("cannot decrypt password")
        val config = ImapAccountConfig.fromJson(configJson)
        val provider = ImapProvider(config, password, context)
        Log.i("DeepSyncRepro", "provider created, host=${config.imapHost}")

        val sinceDate = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val folders = listOf(
            EmailFolder.INBOX, EmailFolder.SENT, EmailFolder.ARCHIVE,
            EmailFolder.SPAM, EmailFolder.TRASH, EmailFolder.DRAFT
        )
        var failed = false
        for (folder in folders) {
            val t0 = System.currentTimeMillis()
            Log.i("DeepSyncRepro", "--- listThreads($folder) sinceDate path starting")
            val result = withTimeoutOrNull(90_000) {
                provider.listThreads(
                    folder = folder,
                    maxResults = 50,
                    pageToken = null,
                    bodyFetchLimitMs = null,
                    sinceDate = sinceDate,
                    onProgress = null
                )
            }
            val elapsed = System.currentTimeMillis() - t0
            if (result == null) {
                Log.e("DeepSyncRepro", "!!! listThreads($folder) HUNG >90s (elapsed=${elapsed}ms)")
                failed = true
            } else {
                Log.i("DeepSyncRepro", "listThreads($folder) OK in ${elapsed}ms, threads=${result.threads.size} nextPage=${result.nextPageToken}")
            }
        }

        // Parallel probe: 5 concurrent getThread calls (backfill shape) on distinct ids.
        Log.i("DeepSyncRepro", "--- parallel getThread probe (5 concurrent)")
        val threadIds = listOf(
            "<CAP-CLOCK-MAILBOXES@mail.gmail.com>",
            "<CAGbKPjJ4LrX9tYwq8mZ3vQ@mail.gmail.com>",
            "<CAOm4aDxyzX7U8Jq2bN5eR6T@mail.gmail.com>",
            "<CACk1P2sLmQoWnVbXcZdFgH@mail.gmail.com>",
            "<CALf9GdRtyUoPzXwVnBmQsE4@mail.gmail.com>"
        )
        val t0 = System.currentTimeMillis()
        coroutineScope {
            val jobs = threadIds.map { id ->
                async {
                    val r = withTimeoutOrNull(90_000) {
                        try {
                            provider.getThread(id, listOf("INBOX"))
                        } catch (e: Exception) {
                            Log.w("DeepSyncRepro", "getThread($id) threw: ${e.javaClass.simpleName}: ${e.message}")
                            null
                        }
                    }
                    Log.i("DeepSyncRepro", "getThread($id) -> ${if (r == null) "HUNG/FAILED" else "ok msgs=${r.messages.size}"}")
                    r
                }
            }
            jobs.forEach { it.await() }
        }
        Log.i("DeepSyncRepro", "parallel probe done in ${System.currentTimeMillis() - t0}ms")
        check(!failed) { "Deep sync search path HUNG — see DeepSyncRepro logs" }
    }
}
