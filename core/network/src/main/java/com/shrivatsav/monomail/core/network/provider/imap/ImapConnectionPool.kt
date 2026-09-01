package com.shrivatsav.monomail.core.network.provider.imap

import android.util.Log
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import com.shrivatsav.monomail.core.network.provider.EmailFolder

/**
 * Persistent IMAP connection pool for a single account.
 *
 * Maintains up to [poolSize] Store connections so body backfill (and other
 * concurrent operations) can run in parallel. Gmail allows 15 simultaneous
 * connections per account, so 5 leaves comfortable headroom for other devices.
 *
 * Connections are created lazily on concurrent demand, reused while idle, and
 * recreated on demand when a connection drops.
 *
 * Thread-safe: leasing/returning goes through a bounded channel, so the
 * pool never hands out more stores than [poolSize].
 */
class ImapConnectionPool(
    private val config: ImapAccountConfig,
    private val credentialProvider: suspend () -> String,  // Returns password/app-password
    private val tag: String = "ImapPool",
    private val poolSize: Int = DEFAULT_POOL_SIZE,
) {
    /** Idle, connected stores waiting to be leased. */
    private val idleStores = Channel<Store>(capacity = poolSize)

    /** Number of stores created (idle + leased); never exceeds [poolSize]. */
    private val createdCount = AtomicInteger(0)

    private val closeMutex = Mutex()

    /**
     * Execute a block with a connected Store, leasing one of the pooled
     * connections. Creates a new connection when demand exceeds supply (up to
     * [poolSize]) and reconnects automatically if the connection dropped.
     *
     * Reconnects are bounded and back off exponentially: a server that keeps
     * kicking us (Gmail sends `* BYE` and closes connections under connection
     * pressure) must not be hammered with instant reconnects — that turns a
     * single dropped connection into a reconnect storm.
     */
    suspend fun <T> withStore(block: suspend (Store) -> T): T = withContext(Dispatchers.IO) {
        var attempt = 0
        while (true) {
            var active: Store? = null
            try {
                active = leaseStore()
                val result = block(active)
                returnStore(active)
                return@withContext result
            } catch (e: Exception) {
                val connectionFailure = active?.let { !it.isConnected || isConnectionFailure(e) }
                    ?: isConnectionFailure(e)
                active?.let {
                    if (connectionFailure) {
                        discardStore(it)
                    } else {
                        returnStore(it)
                    }
                }
                if (connectionFailure && attempt < STORE_RECONNECT_ATTEMPTS) {
                    attempt++
                    val delayMs = STORE_RECONNECT_BASE_DELAY_MS * attempt
                    Log.w(tag, "Store disconnected, retrying in ${delayMs}ms (attempt $attempt/$STORE_RECONNECT_ATTEMPTS)")
                    delay(delayMs)
                    continue
                }
                throw e
            }
        }
        // Unreachable: the loop above only exits via return@withContext or
        // throw. This Nothing-typed tail keeps the lambda's inferred type T
        // instead of Unit (a bare while loop would fix T = Unit).
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    /**
     * Execute a block with an opened Folder.
     * Opens folder, executes block, then closes folder (but keeps Store alive).
     */
    suspend fun <T> withFolder(
        folderName: String,
        mode: Int = Folder.READ_ONLY,
        block: suspend (Folder) -> T
    ): T {
        return withStore { store ->
            val folder = store.getFolder(folderName)
            if (!folder.exists()) {
                throw IllegalArgumentException("Folder does not exist: $folderName")
            }
            folder.open(mode)
            try {
                block(folder)
            } finally {
                if (folder.isOpen) {
                    folder.close(false)
                }
            }
        }
    }

    /**
     * Take an idle store, or create a new one while under the pool cap,
     * or wait for one to be returned when the pool is saturated.
     */
    private suspend fun leaseStore(): Store {
        while (true) {
            idleStores.tryReceive().getOrNull()?.let { return it }
            val created = createdCount.get()
            if (created < poolSize && createdCount.compareAndSet(created, created + 1)) {
                return try {
                    createStore()
                } catch (e: Exception) {
                    createdCount.decrementAndGet()
                    throw e
                }
            }
            // Pool saturated — wait for a store to be returned
            val store = idleStores.receiveCatching().getOrNull()
            if (store != null) return store
            throw IllegalStateException("IMAP connection pool closed")
        }
    }

    /**
     * Return a healthy store to the idle pool, or drop a dead one.
     */
    private suspend fun returnStore(store: Store) {
        if (store.isConnected) {
            // Capacity == poolSize and leased + idle <= poolSize, so this never blocks.
            idleStores.send(store)
        } else {
            dropStore(store)
        }
    }

    /** Close a dead store and free its pool slot. */
    private fun dropStore(store: Store) {
        try {
            store.close()
        } catch (e: Exception) {
            Log.d(tag, "Error closing dead store: ${e.message}")
        }
        createdCount.decrementAndGet()
    }

    private fun discardStore(store: Store) = dropStore(store)

    private fun isConnectionFailure(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            val message = cause.message.orEmpty()
            if (cause is SocketTimeoutException || cause is SocketException ||
                cause is EOFException || cause is ConnectException ||
                message.contains("BYE", ignoreCase = true) ||
                message.contains("connection closed", ignoreCase = true)
            ) return true
            cause = cause.cause
        }
        return false
    }

    /**
     * Create a new IMAP Store with optimized settings.
     */
    private fun createStore(): Store {
        val props = Properties()
        val protocol = if (config.imapSsl) "imaps" else "imap"

        // Connection settings
        props["mail.store.protocol"] = protocol
        props["mail.$protocol.host"] = config.imapHost
        props["mail.$protocol.port"] = config.imapPort.toString()

        // Timeouts
        props["mail.$protocol.connectiontimeout"] = "10000"  // 10s connect
        props["mail.$protocol.timeout"] = "10000"            // 10s read
        props["mail.$protocol.fetchsize"] = "65536"   // fetch content in 64KB chunks so a capped read stops the download early

        // Keep-alive
        props["mail.$protocol.keepalive"] = "true"
        props["mail.$protocol.keepalive.interval"] = "180"   // 3 min

        // TLS
        if (config.imapStartTls) {
            props["mail.$protocol.starttls.enable"] = "true"
        }
        props["mail.$protocol.ssl.protocols"] = "TLSv1.2 TLSv1.3"
        props["mail.$protocol.ssl.checkserveridentity"] = "true"
        if (config.imapStartTls) {
            props["mail.$protocol.starttls.required"] = "true"
        }

        // MIME handling
        props["mail.mime.multipart.ignoreexistingboundaryparameter"] = "true"
        props["mail.mime.multipart.ignoremissingboundaryparameter"] = "true"
        props["mail.mime.base64.ignoreerrors"] = "true"
        props["mail.mime.decodetext.strict"] = "false"

        val session = Session.getInstance(props)
        val newStore = session.getStore(protocol)

        // Use runBlocking-like approach for sync connect in IO thread
        val password = kotlinx.coroutines.runBlocking {
            credentialProvider()
        }
        newStore.connect(config.username, password)

        Log.d(tag, "Connected to ${config.imapHost}:${config.imapPort}")
        return newStore
    }

    /**
     * Get folder names cache from connected store.
     * Call after first connect to populate folder mappings.
     */
    suspend fun getFolderNames(): Map<EmailFolder, String> = withStore { store ->
        val cache = mutableMapOf<EmailFolder, String>()
        val defaultFolder = store.defaultFolder
        val folders = defaultFolder.list("*")
        for (f in folders) {
            matchFolder(f.fullName.lowercase())?.let { cache[it] = f.fullName }
        }
        if (!cache.containsKey(EmailFolder.INBOX)) {
            cache[EmailFolder.INBOX] = "INBOX"
        }
        cache
    }

    /**
     * Check if the pool holds any connection.
     */
    fun isConnected(): Boolean = createdCount.get() > 0

    /**
     * Close all idle connections. Leased connections are closed when they are
     * returned; the pool lazily reconnects on next use.
     */
    suspend fun close() = closeMutex.withLock {
        while (true) {
            val s = idleStores.tryReceive().getOrNull() ?: break
            try {
                s.close()
            } catch (e: Exception) {
                Log.d(tag, "Error closing store: ${e.message}")
            }
            createdCount.decrementAndGet()
        }
    }

    /**
     * Map folder name to EmailFolder enum.
     */
    private fun matchFolder(lower: String): EmailFolder? {
        // Strip [Gmail]/ prefix for Gmail accounts
        val normalized = lower.removePrefix("[gmail]/").trim()
        return when {
            normalized.endsWith("inbox") || normalized == "inbox" -> EmailFolder.INBOX
            // Gmail: "[Gmail]/Sent Mail" → Sent
            normalized.contains("sent") || normalized == "sent items" || normalized == "sent messages" -> EmailFolder.SENT
            // Gmail: "[Gmail]/All Mail" → Archive
            normalized.contains("archive") || normalized.contains("all mail") -> EmailFolder.ARCHIVE
            // Gmail: "[Gmail]/Trash" → Trash
            normalized.contains("trash") || normalized == "deleted messages" || normalized == "deleted items" || normalized == "bin" -> EmailFolder.TRASH
            // Gmail: "[Gmail]/Spam" → Spam
            normalized.contains("spam") || normalized == "junk" -> EmailFolder.SPAM
            // Gmail: "[Gmail]/Drafts" → Drafts
            normalized.contains("draft") -> EmailFolder.DRAFT
            // Gmail: "[Gmail]/Starred" → Starred
            normalized.contains("starred") || normalized.contains("flagged") -> EmailFolder.STARRED
            else -> null
        }
    }

    companion object {
        /** Gmail allows 15 simultaneous IMAP connections per account; 5 leaves
         *  headroom for other clients/devices while giving backfill ~5x speedup. */
        const val DEFAULT_POOL_SIZE = 5

        /** Bounded reconnect attempts and linear backoff (2s, 4s, 6s) after a
         *  connection is dropped, so a server that is kicking us (Gmail `* BYE`)
         *  is not hammered with an instant-reconnect storm. */
        private const val STORE_RECONNECT_ATTEMPTS = 3
        private const val STORE_RECONNECT_BASE_DELAY_MS = 2000L
    }
}
