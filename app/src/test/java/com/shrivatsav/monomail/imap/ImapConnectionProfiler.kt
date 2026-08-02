package com.shrivatsav.monomail.imap

import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.core.network.provider.imap.ImapConnectionPool
import jakarta.mail.Folder
import jakarta.mail.FetchProfile
import jakarta.mail.Flags
import jakarta.mail.Session
import jakarta.mail.search.FlagTerm
import kotlinx.coroutines.runBlocking
import java.util.Properties

/**
 * Standalone profiling harness for IMAP connection performance.
 * Run with: gradlew :app:testPlaystoreDebugUnitTest --tests 'com.shrivatsav.monomail.imap.ImapConnectionProfiler'
 *
 * This is NOT a pass/fail test — it outputs timing metrics.
 */
class ImapConnectionProfiler {

    private val config = ImapAccountConfig.presetForHost(System.getenv("IMAP_TEST_EMAIL") ?: "test@gmail.com")!!
        .copy(username = System.getenv("IMAP_TEST_EMAIL") ?: "test@gmail.com")
    private val password = System.getenv("IMAP_TEST_PASSWORD") ?: ""

    // Session reused across tests
    private val pool = ImapConnectionPool(
        config = config,
        credentialProvider = { password },
        tag = "Profiler"
    )

    @org.junit.Test
    fun profileConnectionTimes() = runBlocking {
        // 1. Cold connection
        var start = System.nanoTime()
        pool.withStore { store ->
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("COLD CONNECT: ${elapsed}ms (SSL handshake + auth)")
        }

        // 2. Warm connection (reuse)
        repeat(3) {
            start = System.nanoTime()
            pool.withStore { store ->
                val elapsed = (System.nanoTime() - start) / 1_000_000
                println("WARM CONNECT #${it+1}: ${elapsed}ms (should be ~0)")
            }
        }

        // 3. Folder open
        start = System.nanoTime()
        pool.withFolder("INBOX") { folder ->
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("FOLDER OPEN INBOX: ${elapsed}ms")
        }

        // 4. Batch fetch + body parse (full)
        var totalMsgs = 0
        var totalFetchMs = 0L
        var totalBodyMs = 0L
        pool.withFolder("INBOX", Folder.READ_ONLY) { folder ->
            val total = folder.messageCount
            println("INBOX MESSAGE COUNT: $total")
            val batch = folder.getMessages(maxOf(1, total - 19), total).toList()
            totalMsgs = batch.size

            val profile = FetchProfile().apply {
                add(FetchProfile.Item.ENVELOPE)
                add(FetchProfile.Item.FLAGS)
                add(FetchProfile.Item.CONTENT_INFO)
                add("Message-ID")
                add("References")
                add("In-Reply-To")
            }

            start = System.nanoTime()
            folder.fetch(batch.toTypedArray(), profile)
            totalFetchMs = (System.nanoTime() - start) / 1_000_000

            start = System.nanoTime()
            for (msg in batch) {
                try {
                    @Suppress("UNUSED_EXPRESSION")
                    msg.content // Force full MIME download
                } catch (_: Exception) {}
            }
            totalBodyMs = (System.nanoTime() - start) / 1_000_000

            println("BATCH FETCH (${totalMsgs} msgs): ${totalFetchMs}ms (avg ${totalFetchMs/totalMsgs}ms/msg)")
            println("BODY DOWNLOAD (${totalMsgs} msgs): ${totalBodyMs}ms (avg ${totalBodyMs/totalMsgs}ms/msg)")
            println("TOTAL LIST TIME: ${totalFetchMs + totalBodyMs}ms for $totalMsgs msgs")
        }

        // 5. SinceDate search (incremental)
        start = System.nanoTime()
        pool.withFolder("INBOX") { folder ->
            // search for messages newer than 1 hour ago
            val oneHourAgo = java.util.Date(System.currentTimeMillis() - 3_600_000)
            val searchTerm = jakarta.mail.search.ReceivedDateTerm(
                jakarta.mail.search.ComparisonTerm.GE, oneHourAgo
            )
            val results = folder.search(searchTerm)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("SINCE_DATE SEARCH (1hr): ${elapsed}ms, found ${results.size} messages")
        }

        // 6. SinceDate 1 day
        start = System.nanoTime()
        pool.withFolder("INBOX") { folder ->
            val yesterday = java.util.Date(System.currentTimeMillis() - 86_400_000)
            val results = folder.search(
                jakarta.mail.search.ReceivedDateTerm(jakarta.mail.search.ComparisonTerm.GE, yesterday)
            )
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("SINCE_DATE SEARCH (1d): ${elapsed}ms, found ${results.size} messages")
        }

        // 7. SinceDate 7 days
        start = System.nanoTime()
        pool.withFolder("INBOX") { folder ->
            val lastWeek = java.util.Date(System.currentTimeMillis() - 7 * 86_400_000)
            val results = folder.search(
                jakarta.mail.search.ReceivedDateTerm(jakarta.mail.search.ComparisonTerm.GE, lastWeek)
            )
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("SINCE_DATE SEARCH (7d): ${elapsed}ms, found ${results.size} messages")
        }

        // 8. Starred search
        start = System.nanoTime()
        pool.withFolder("INBOX") { folder ->
            val starred = folder.search(FlagTerm(Flags(Flags.Flag.FLAGGED), true))
            val elapsed = (System.nanoTime() - start) / 1_000_000
            println("STARRED SEARCH: ${elapsed}ms, found ${starred.size} messages")
        }

        pool.close()
        println("\n=== PROFILE COMPLETE ===")
    }
}
