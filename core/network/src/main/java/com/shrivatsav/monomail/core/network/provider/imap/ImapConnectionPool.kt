package com.shrivatsav.monomail.core.network.provider.imap

import android.util.Log
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean
import com.shrivatsav.monomail.core.network.provider.EmailFolder

/**
 * Persistent IMAP connection pool for a single account.
 * 
 * Maintains a single Store connection that is reused across operations.
 * Automatically reconnects on connection drops.
 * 
 * Thread-safe via Mutex — only one reconnection attempt at a time.
 */
class ImapConnectionPool(
    private val config: ImapAccountConfig,
    private val credentialProvider: suspend () -> String,  // Returns password/app-password
    private val tag: String = "ImapPool"
) {
    @Volatile private var store: Store? = null
    private val mutex = Mutex()
    private val isConnecting = AtomicBoolean(false)
    
    /**
     * Execute a block with a connected Store.
     * Reconnects automatically if connection dropped.
     */
    suspend fun <T> withStore(block: suspend (Store) -> T): T {
        return withContext(Dispatchers.IO) {
            val s = getOrCreateStore()
            try {
                block(s)
            } catch (e: Exception) {
                // Connection may have dropped — try once more after reconnect
                if (!s.isConnected) {
                    Log.w(tag, "Store disconnected, reconnecting")
                    val newStore = reconnect()
                    block(newStore)
                } else {
                    throw e
                }
            }
        }
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
     * Get or create the Store connection.
     */
    private suspend fun getOrCreateStore(): Store {
        store?.let { s ->
            if (s.isConnected) return s
            // Connection dropped — fall through to reconnect
        }
        return reconnect()
    }
    
    /**
     * Create a new Store connection.
     * Mutex prevents concurrent reconnection attempts.
     */
    private suspend fun reconnect(): Store = mutex.withLock {
        // Double-check after acquiring lock
        store?.let { s ->
            if (s.isConnected) return s
        }
        
        // Close existing connection if any
        try {
            store?.close()
        } catch (e: Exception) {
            Log.d(tag, "Error closing old store: ${e.message}")
        }
        
        val newStore = createStore()
        store = newStore
        newStore
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
        
        // Keep-alive
        props["mail.$protocol.keepalive"] = "true"
        props["mail.$protocol.keepalive.interval"] = "180"   // 3 min
        
        // TLS
        if (config.imapStartTls) {
            props["mail.$protocol.starttls.enable"] = "true"
        }
        props["mail.$protocol.ssl.protocols"] = "TLSv1.2 TLSv1.3"
        
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
     * Check if store is currently connected.
     */
    fun isConnected(): Boolean = store?.isConnected == true
    
    /**
     * Close the connection pool.
     */
    suspend fun close() = mutex.withLock {
        try {
            store?.close()
        } catch (e: Exception) {
            Log.d(tag, "Error closing store: ${e.message}")
        }
        store = null
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
}
