package com.shrivatsav.monomail.data.provider.imap

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.provider.EmailFolder
import com.shrivatsav.monomail.data.provider.EmailProvider
import com.shrivatsav.monomail.data.provider.ProviderMessage
import com.shrivatsav.monomail.data.provider.ProviderThread
import com.shrivatsav.monomail.data.provider.ProviderThreadListResult
import jakarta.mail.Flags
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.Session
import jakarta.mail.Store
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.search.FlagTerm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class ImapProvider(
    private val config: ImapAccountConfig,
    private val password: String,
    private val context: Context
) : EmailProvider {

    override val providerName: String = "imap"

    private var store: Store? = null
    private val storeMutex = Mutex()
    private val folderNamesCache = ConcurrentHashMap<EmailFolder, String>()

    private suspend fun getStore(): Store = storeMutex.withLock {
        val currentStore = store
        if (currentStore != null && currentStore.isConnected) {
            return currentStore
        }

        val props = Properties()
        val protocol = if (config.imapSsl) "imaps" else "imap"
        props["mail.store.protocol"] = protocol
        props["mail.$protocol.host"] = config.imapHost
        props["mail.$protocol.port"] = config.imapPort.toString()
        if (config.imapStartTls) {
            props["mail.$protocol.starttls.enable"] = "true"
        }
        props["mail.$protocol.connectiontimeout"] = "10000"
        props["mail.$protocol.timeout"] = "10000"
        props["mail.mime.multipart.ignoreexistingboundaryparameter"] = "true"
        props["mail.mime.multipart.ignoremissingboundaryparameter"] = "true"
        props["mail.mime.base64.ignoreerrors"] = "true"
        props["mail.mime.decodetext.strict"] = "false"

        val session = Session.getInstance(props)
        val newStore = session.getStore(protocol)
        newStore.connect(config.username, password)
        store = newStore
        
        // Populate folder cache
        folderNamesCache.clear()
        val defaultFolder = newStore.defaultFolder
        val folders = defaultFolder.list("*")
        for (f in folders) {
            val name = f.fullName
            val lower = name.lowercase()
            if (lower.endsWith("inbox") || lower == "inbox") folderNamesCache[EmailFolder.INBOX] = name
            else if (lower.contains("sent") || lower == "sent items" || lower == "sent messages") folderNamesCache[EmailFolder.SENT] = name
            else if (lower.contains("archive") || lower == "all mail") folderNamesCache[EmailFolder.ARCHIVE] = name
            else if (lower.contains("trash") || lower == "deleted messages" || lower == "deleted items" || lower == "bin") folderNamesCache[EmailFolder.TRASH] = name
            else if (lower.contains("spam") || lower == "junk") folderNamesCache[EmailFolder.SPAM] = name
        }
        if (!folderNamesCache.containsKey(EmailFolder.INBOX)) folderNamesCache[EmailFolder.INBOX] = "INBOX"

        return newStore
    }

    private fun getFolderName(folder: EmailFolder): String? {
        if (folder == EmailFolder.STARRED) return null // Handled via search or all folders
        val cached = folderNamesCache[folder]
        if (cached != null) return cached
        
        return when (folder) {
            EmailFolder.INBOX -> "INBOX"
            EmailFolder.SENT -> "Sent"
            EmailFolder.ARCHIVE -> "Archive"
            EmailFolder.TRASH -> "Trash"
            EmailFolder.SPAM -> "Spam"
            else -> null
        }
    }

    override suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int,
        pageToken: String?,
        query: String?
    ): ProviderThreadListResult = withContext(Dispatchers.IO) {
        val store = getStore()
        val imapFolder = if (folder == EmailFolder.STARRED) {
            store.getFolder(getFolderName(EmailFolder.INBOX) ?: "INBOX")
        } else {
            val folderName = getFolderName(folder) ?: return@withContext ProviderThreadListResult(emptyList(), null)
            store.getFolder(folderName)
        }

        if (!imapFolder.exists()) {
            return@withContext ProviderThreadListResult(emptyList(), null)
        }

        imapFolder.open(Folder.READ_ONLY)
        try {
            val messages = if (folder == EmailFolder.STARRED) {
                imapFolder.search(FlagTerm(Flags(Flags.Flag.FLAGGED), true))
            } else {
                imapFolder.messages
            }

            val total = messages.size
            if (total == 0) return@withContext ProviderThreadListResult(emptyList(), null)

            // Very basic pagination: fetch last maxResults
            val offset = pageToken?.toIntOrNull() ?: total
            val start = maxOf(1, offset - maxResults + 1)
            val end = offset
            if (start > end) return@withContext ProviderThreadListResult(emptyList(), null)

            val toFetch = if (folder == EmailFolder.STARRED) {
                messages.takeLast(maxResults).toTypedArray()
            } else {
                imapFolder.getMessages(start, end)
            }

            // Fetch envelope, flags, and content info for body parsing
            val profile = jakarta.mail.FetchProfile().apply {
                add(jakarta.mail.FetchProfile.Item.ENVELOPE)
                add(jakarta.mail.FetchProfile.Item.FLAGS)
                add(jakarta.mail.FetchProfile.Item.CONTENT_INFO)
                add("Message-ID")
                add("References")
                add("In-Reply-To")
            }
            imapFolder.fetch(toFetch, profile)

            val rawMessages = toFetch.mapNotNull { msg ->
                val messageId = msg.getHeader("Message-ID")?.firstOrNull() ?: return@mapNotNull null
                val references = msg.getHeader("References")?.joinToString(" ") ?: ""
                val inReplyTo = msg.getHeader("In-Reply-To")?.firstOrNull() ?: ""
                val date = msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L

                val fromAddrs = msg.from?.mapNotNull { it as? InternetAddress } ?: emptyList()
                val toAddrs = msg.getRecipients(Message.RecipientType.TO)?.mapNotNull { it as? InternetAddress } ?: emptyList()
                val ccAddrs = msg.getRecipients(Message.RecipientType.CC)?.mapNotNull { it as? InternetAddress } ?: emptyList()

                val fromName = fromAddrs.firstOrNull()?.personal ?: fromAddrs.firstOrNull()?.address ?: ""
                val fromEmail = fromAddrs.firstOrNull()?.address ?: ""
                val to = toAddrs.joinToString(", ") { it.address }
                val cc = ccAddrs.joinToString(", ") { it.address }

                val isRead = msg.isSet(Flags.Flag.SEEN)
                val isStarred = msg.isSet(Flags.Flag.FLAGGED)

                // Parse body and attachments inline for instant display
                val attachments = mutableListOf<EmailAttachmentInfo>()
                var htmlBody = ""
                var plainBody = ""

                try {
                    // Snapshot the message so all parts are read synchronously and we don't hit lazy eval errors
                    val rawStream = java.io.ByteArrayOutputStream()
                    msg.writeTo(rawStream)
                    
                    val tempProps = java.util.Properties().apply {
                        put("mail.mime.multipart.ignoreexistingboundaryparameter", "true")
                        put("mail.mime.multipart.ignoremissingboundaryparameter", "true")
                        put("mail.mime.base64.ignoreerrors", "true")
                        put("mail.mime.decodetext.strict", "false")
                    }
                    val tempSession = jakarta.mail.Session.getInstance(tempProps)
                    val snapshot = jakarta.mail.internet.MimeMessage(tempSession, java.io.ByteArrayInputStream(rawStream.toByteArray()))

                    fun processPart(part: Part) {
                        try {
                            val disposition = part.disposition
                            val contentType = part.contentType.lowercase()

                            if (Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
                                (disposition == null && contentType.contains("application/"))) {
                                val name = part.fileName ?: "attachment"
                                attachments.add(
                                    EmailAttachmentInfo(
                                        id = name,
                                        messageId = messageId,
                                        mimeType = contentType.substringBefore(";"),
                                        name = name,
                                        size = part.size
                                    )
                                )
                            } else if (part.isMimeType("text/plain") && plainBody.isEmpty()) {
                                plainBody = part.getBodyText() ?: ""
                            } else if (part.isMimeType("text/html") && htmlBody.isEmpty()) {
                                htmlBody = part.getBodyText() ?: ""
                            } else if (part.isMimeType("multipart/*")) {
                                val content = part.content
                                val mp = when (content) {
                                    is Multipart -> content
                                    is java.io.InputStream -> jakarta.mail.internet.MimeMultipart(
                                        jakarta.mail.util.ByteArrayDataSource(content, part.contentType)
                                    )
                                    else -> null
                                }
                                if (mp != null) {
                                    for (i in 0 until mp.count) {
                                        processPart(mp.getBodyPart(i))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("ImapProvider", "listThreads processPart error: ${e.message}")
                        }
                    }
                    processPart(snapshot)
                } catch (e: Exception) {
                    android.util.Log.w("ImapProvider", "listThreads body parse error: ${e.message}")
                }

                val body = htmlBody.ifEmpty { plainBody.replace("\n", "<br>") }
                val cleanPlain = plainBody
                    .replace(Regex("https?://\\S+"), "")
                    .replace(Regex("={3,}"), "")
                    .replace(Regex("_{3,}"), "")
                    .replace(Regex("\\[image:[^\\]]+\\]", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val cleanHtml = htmlBody
                    .replace(Regex("<style[^>]*>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<script[^>]*>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("&[a-zA-Z0-9#]+;"), " ")
                    .replace(Regex("https?://\\S+"), "")
                    .replace(Regex("\\s+"), " ")
                    .trim()

                val snippetSource = if (cleanPlain.length > 15) cleanPlain else cleanHtml.ifEmpty { cleanPlain }
                val snippet = snippetSource.take(150).trim()

                val providerMsg = ProviderMessage(
                    id = messageId,
                    threadId = messageId,
                    subject = msg.subject ?: "",
                    from = fromName,
                    fromEmail = fromEmail,
                    to = to,
                    cc = cc,
                    bcc = "",
                    snippet = snippet,
                    body = body,
                    date = date,
                    isRead = isRead,
                    isStarred = isStarred,
                    folders = setOf(folder),
                    attachments = attachments
                )

                ImapRawMessage(messageId, references, inReplyTo, date, providerMsg)
            }

            val threadsMap = ImapThreader.groupByReferences(rawMessages)
            val threads = threadsMap.map { (threadId, msgs) ->
                ProviderThread(threadId, msgs)
            }.sortedByDescending { t -> t.messages.maxOfOrNull { it.date } ?: 0L }

            val nextToken = if (start > 1) (start - 1).toString() else null
            ProviderThreadListResult(threads, nextToken)

        } finally {
            if (imapFolder.isOpen) imapFolder.close(false)
        }
    }

    override suspend fun getThread(threadId: String, folderHints: List<String>): ProviderThread = withContext(Dispatchers.IO) {
        val store = getStore()
        
        // Use folder hints if provided to avoid searching all folders.
        // IMAP folder names are exactly the strings in the folderHints list (since they come from local DB which stores standard EmailFolder names)
        val validFolderNames = folderNamesCache.values.toList()
        val searchFolders = if (folderHints.isNotEmpty()) {
            val mappedHints = folderHints.mapNotNull { hint -> 
                try {
                    val enumVal = com.shrivatsav.monomail.data.provider.EmailFolder.valueOf(hint)
                    folderNamesCache[enumVal]
                } catch (e: Exception) {
                    null
                }
            }
            mappedHints.ifEmpty { validFolderNames }
        } else {
            listOfNotNull(
                folderNamesCache[EmailFolder.INBOX],
                folderNamesCache[EmailFolder.SENT],
                folderNamesCache[EmailFolder.ARCHIVE]
            )
        }.distinct()

        val matchingMessages = mutableListOf<Message>()
        val imapFolders = mutableListOf<Folder>()

        try {
            for (folderName in searchFolders) {
                val f = store.getFolder(folderName)
                if (f.exists()) {
                    f.open(Folder.READ_ONLY)
                    imapFolders.add(f)
                    
                    // Header search term for Message-ID or References
                    val msgIdTerm = jakarta.mail.search.HeaderTerm("Message-ID", threadId)
                    val refTerm = jakarta.mail.search.HeaderTerm("References", threadId)
                    val inReplyTerm = jakarta.mail.search.HeaderTerm("In-Reply-To", threadId)
                    
                    val orTerm = jakarta.mail.search.OrTerm(
                        msgIdTerm,
                        jakarta.mail.search.OrTerm(refTerm, inReplyTerm)
                    )
                    
                    matchingMessages.addAll(f.search(orTerm))
                }
            }

            if (matchingMessages.isEmpty()) {
                return@withContext ProviderThread(threadId, emptyList())
            }

            // Fetch FULL contents
            val profile = jakarta.mail.FetchProfile().apply {
                add(jakarta.mail.FetchProfile.Item.ENVELOPE)
                add(jakarta.mail.FetchProfile.Item.FLAGS)
                add(jakarta.mail.FetchProfile.Item.CONTENT_INFO)
            }

            val rawMessages = mutableListOf<ImapRawMessage>()

            for (msg in matchingMessages) {
                // Determine folder set
                val folderSet = mutableSetOf<EmailFolder>()
                val fname = msg.folder.name
                folderNamesCache.entries.find { it.value == fname }?.key?.let { folderSet.add(it) }

                val messageId = msg.getHeader("Message-ID")?.firstOrNull() ?: continue
                val references = msg.getHeader("References")?.joinToString(" ") ?: ""
                val inReplyTo = msg.getHeader("In-Reply-To")?.firstOrNull() ?: ""
                val date = msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L

                val fromAddrs = msg.from?.mapNotNull { it as? InternetAddress } ?: emptyList()
                val toAddrs = msg.getRecipients(Message.RecipientType.TO)?.mapNotNull { it as? InternetAddress } ?: emptyList()
                val ccAddrs = msg.getRecipients(Message.RecipientType.CC)?.mapNotNull { it as? InternetAddress } ?: emptyList()

                val isRead = msg.isSet(Flags.Flag.SEEN)
                val isStarred = msg.isSet(Flags.Flag.FLAGGED)
                if (isStarred) folderSet.add(EmailFolder.STARRED)

                // Extract body and attachments
                val attachments = mutableListOf<EmailAttachmentInfo>()
                var htmlBody = ""
                var plainBody = ""

                // Snapshot the message so all parts are read synchronously and we don't hit lazy eval errors
                val rawStream = java.io.ByteArrayOutputStream()
                msg.writeTo(rawStream)
                
                val tempProps = java.util.Properties().apply {
                    put("mail.mime.multipart.ignoreexistingboundaryparameter", "true")
                    put("mail.mime.multipart.ignoremissingboundaryparameter", "true")
                    put("mail.mime.base64.ignoreerrors", "true")
                    put("mail.mime.decodetext.strict", "false")
                }
                val tempSession = jakarta.mail.Session.getInstance(tempProps)
                val snapshot = jakarta.mail.internet.MimeMessage(tempSession, java.io.ByteArrayInputStream(rawStream.toByteArray()))

                fun processPart(part: Part) {
                    val disposition = part.disposition
                    val contentType = part.contentType.lowercase()

                    if (Part.ATTACHMENT.equals(disposition, ignoreCase = true) || 
                        (disposition == null && contentType.contains("application/"))) {
                        val name = part.fileName ?: "attachment"
                        attachments.add(
                            EmailAttachmentInfo(
                                id = name, // Use filename as ID for IMAP
                                messageId = messageId,
                                mimeType = contentType.substringBefore(";"),
                                name = name,
                                size = part.size
                            )
                        )
                    } else if (part.isMimeType("text/plain") && plainBody.isEmpty()) {
                        plainBody = part.getBodyText() ?: ""
                    } else if (part.isMimeType("text/html") && htmlBody.isEmpty()) {
                        htmlBody = part.getBodyText() ?: ""
                    } else if (part.isMimeType("multipart/*")) {
                        try {
                            val content = part.content
                            val mp = if (content is Multipart) {
                                content
                            } else if (content is java.io.InputStream) {
                                jakarta.mail.internet.MimeMultipart(jakarta.mail.util.ByteArrayDataSource(content, part.contentType))
                            } else {
                                null
                            }
                            if (mp != null) {
                                for (i in 0 until mp.count) {
                                    processPart(mp.getBodyPart(i))
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ImapProvider", "Error parsing multipart", e)
                        }
                    }
                }

                processPart(snapshot)

                val body = htmlBody.ifEmpty { plainBody.replace("\n", "<br>") }
                val snippet = plainBody.take(150).replace(Regex("\\s+"), " ").trim().ifEmpty {
                    // Very crude HTML to text
                    htmlBody.replace(Regex("<[^>]+>"), " ").take(150).replace(Regex("\\s+"), " ").trim()
                }

                val providerMsg = ProviderMessage(
                    id = messageId,
                    threadId = threadId,
                    subject = msg.subject ?: "",
                    from = fromAddrs.firstOrNull()?.personal ?: fromAddrs.firstOrNull()?.address ?: "",
                    fromEmail = fromAddrs.firstOrNull()?.address ?: "",
                    to = toAddrs.joinToString(", ") { it.address },
                    cc = ccAddrs.joinToString(", ") { it.address },
                    bcc = "",
                    snippet = snippet,
                    body = body,
                    date = date,
                    isRead = isRead,
                    isStarred = isStarred,
                    folders = folderSet,
                    attachments = attachments
                )

                rawMessages.add(ImapRawMessage(messageId, references, inReplyTo, date, providerMsg))
            }

            val threadsMap = ImapThreader.groupByReferences(rawMessages)
            // Just return the first thread since we queried for it specifically
            return@withContext threadsMap.values.firstOrNull()?.let { msgs ->
                ProviderThread(threadId, msgs)
            } ?: ProviderThread(threadId, emptyList())

        } finally {
            imapFolders.forEach { if (it.isOpen) it.close(false) }
        }
    }

    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? = withContext(Dispatchers.IO) {
        val store = getStore()
        val searchFolders = listOfNotNull(
            folderNamesCache[EmailFolder.INBOX],
            folderNamesCache[EmailFolder.SENT],
            folderNamesCache[EmailFolder.ARCHIVE]
        ).distinct()

        for (folderName in searchFolders) {
            val f = store.getFolder(folderName)
            if (f.exists()) {
                f.open(Folder.READ_ONLY)
                try {
                    val messages = f.search(jakarta.mail.search.HeaderTerm("Message-ID", messageId))
                    if (messages.isNotEmpty()) {
                        val msg = messages[0]
                        var result: ByteArray? = null
                        
                        fun searchPart(part: Part) {
                            if (result != null) return
                            if (part.fileName == attachmentId) {
                                val input = part.inputStream
                                val buffer = ByteArrayOutputStream()
                                input.copyTo(buffer)
                                result = buffer.toByteArray()
                            } else if (part.isMimeType("multipart/*")) {
                                val mp = part.content as? Multipart
                                if (mp != null) {
                                    for (i in 0 until mp.count) {
                                        searchPart(mp.getBodyPart(i))
                                    }
                                }
                            }
                        }
                        searchPart(msg)
                        if (result != null) return@withContext result
                    }
                } finally {
                    f.close(false)
                }
            }
        }
        return@withContext null
    }

    private suspend fun moveThread(threadId: String, targetFolder: EmailFolder) = withContext(Dispatchers.IO) {
        val store = getStore()
        val targetName = getFolderName(targetFolder) ?: return@withContext
        val destFolder = store.getFolder(targetName)
        if (!destFolder.exists()) destFolder.create(Folder.HOLDS_MESSAGES)

        val srcName = getFolderName(EmailFolder.INBOX) ?: return@withContext
        val srcFolder = store.getFolder(srcName)
        if (!srcFolder.exists()) return@withContext

        srcFolder.open(Folder.READ_WRITE)
        try {
            val messages = srcFolder.search(jakarta.mail.search.HeaderTerm("Message-ID", threadId))
            if (messages.isNotEmpty()) {
                srcFolder.copyMessages(messages, destFolder)
                srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                srcFolder.expunge()
            }
        } finally {
            srcFolder.close(true)
        }
    }

    override suspend fun archiveThread(threadId: String) {
        moveThread(threadId, EmailFolder.ARCHIVE)
    }

    override suspend fun unarchiveThread(threadId: String) = withContext(Dispatchers.IO) {
        // Move back to INBOX, simplified since we only search INBOX for moving
        val store = getStore()
        val srcName = getFolderName(EmailFolder.ARCHIVE) ?: return@withContext
        val destName = getFolderName(EmailFolder.INBOX) ?: return@withContext
        val srcFolder = store.getFolder(srcName)
        val destFolder = store.getFolder(destName)
        if (!srcFolder.exists()) return@withContext

        srcFolder.open(Folder.READ_WRITE)
        try {
            val messages = srcFolder.search(jakarta.mail.search.HeaderTerm("Message-ID", threadId))
            if (messages.isNotEmpty()) {
                srcFolder.copyMessages(messages, destFolder)
                srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                srcFolder.expunge()
            }
        } finally {
            srcFolder.close(true)
        }
    }

    override suspend fun trashThread(threadId: String) {
        moveThread(threadId, EmailFolder.TRASH)
    }

    override suspend fun restoreThread(threadId: String) = withContext(Dispatchers.IO) {
        val store = getStore()
        val srcName = getFolderName(EmailFolder.TRASH) ?: return@withContext
        val destName = getFolderName(EmailFolder.INBOX) ?: return@withContext
        val srcFolder = store.getFolder(srcName)
        val destFolder = store.getFolder(destName)
        if (!srcFolder.exists()) return@withContext

        srcFolder.open(Folder.READ_WRITE)
        try {
            val messages = srcFolder.search(jakarta.mail.search.HeaderTerm("Message-ID", threadId))
            if (messages.isNotEmpty()) {
                srcFolder.copyMessages(messages, destFolder)
                srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                srcFolder.expunge()
            }
        } finally {
            srcFolder.close(true)
        }
    }

    override suspend fun permanentlyDeleteThread(threadId: String) = withContext(Dispatchers.IO) {
        val store = getStore()
        val srcName = getFolderName(EmailFolder.TRASH) ?: return@withContext
        val srcFolder = store.getFolder(srcName)
        if (!srcFolder.exists()) return@withContext

        srcFolder.open(Folder.READ_WRITE)
        try {
            val messages = srcFolder.search(jakarta.mail.search.HeaderTerm("Message-ID", threadId))
            if (messages.isNotEmpty()) {
                srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                srcFolder.expunge()
            }
        } finally {
            srcFolder.close(true)
        }
    }

    private suspend fun updateFlag(threadId: String, flag: Flags.Flag, set: Boolean) = withContext(Dispatchers.IO) {
        val store = getStore()
        val searchFolders = listOfNotNull(
            folderNamesCache[EmailFolder.INBOX],
            folderNamesCache[EmailFolder.ARCHIVE]
        ).distinct()

        for (folderName in searchFolders) {
            val f = store.getFolder(folderName)
            if (f.exists()) {
                f.open(Folder.READ_WRITE)
                try {
                    val messages = f.search(jakarta.mail.search.HeaderTerm("Message-ID", threadId))
                    if (messages.isNotEmpty()) {
                        f.setFlags(messages, Flags(flag), set)
                    }
                } finally {
                    f.close(false)
                }
            }
        }
    }

    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        updateFlag(threadId, Flags.Flag.FLAGGED, starred)
    }

    override suspend fun markRead(threadId: String, read: Boolean) {
        updateFlag(threadId, Flags.Flag.SEEN, read)
    }

    override suspend fun batchMarkRead(messageIds: List<String>) = withContext(Dispatchers.IO) {
        val store = getStore()
        val searchFolders = listOfNotNull(
            folderNamesCache[EmailFolder.INBOX],
            folderNamesCache[EmailFolder.ARCHIVE]
        ).distinct()

        for (folderName in searchFolders) {
            val f = store.getFolder(folderName)
            if (f.exists()) {
                f.open(Folder.READ_WRITE)
                try {
                    val terms = messageIds.map { jakarta.mail.search.HeaderTerm("Message-ID", it) }.toTypedArray()
                    val orTerm = jakarta.mail.search.OrTerm(terms)
                    val messages = f.search(orTerm)
                    if (messages.isNotEmpty()) {
                        f.setFlags(messages, Flags(Flags.Flag.SEEN), true)
                    }
                } finally {
                    f.close(false)
                }
            }
        }
    }

    override suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        cc: String,
        bcc: String,
        threadId: String?,
        attachments: List<EmailAttachment>
    ): String? = withContext(Dispatchers.IO) {
        val props = Properties()
        val protocol = if (config.smtpSsl) "smtps" else "smtp"
        props["mail.transport.protocol"] = protocol
        props["mail.$protocol.host"] = config.smtpHost
        props["mail.$protocol.port"] = config.smtpPort.toString()
        props["mail.$protocol.auth"] = "true"
        if (config.smtpStartTls) {
            props["mail.$protocol.starttls.enable"] = "true"
        }
        props["mail.$protocol.connectiontimeout"] = "10000"
        props["mail.$protocol.timeout"] = "10000"

        val session = Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication() = jakarta.mail.PasswordAuthentication(config.username, password)
        })

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(from))
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        if (cc.isNotBlank()) message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc))
        if (bcc.isNotBlank()) message.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bcc))
        message.subject = subject

        if (threadId != null) {
            message.setHeader("In-Reply-To", threadId)
            message.setHeader("References", threadId)
        }

        if (attachments.isEmpty()) {
            message.setContent(body, "text/html; charset=utf-8")
        } else {
            val multipart = MimeMultipart()
            val textPart = MimeBodyPart()
            textPart.setContent(body, "text/html; charset=utf-8")
            multipart.addBodyPart(textPart)

            for (att in attachments) {
                val bytes = context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                if (bytes != null) {
                    val attPart = MimeBodyPart()
                    val source = jakarta.mail.util.ByteArrayDataSource(bytes, att.mimeType)
                    attPart.dataHandler = jakarta.activation.DataHandler(source)
                    attPart.fileName = att.name
                    multipart.addBodyPart(attPart)
                }
            }
            message.setContent(multipart)
        }

        message.saveChanges()
        val generatedMessageId = message.messageID

        Transport.send(message)

        // Save to SENT folder best-effort
        try {
            val store = getStore()
            val sentName = getFolderName(EmailFolder.SENT)
            if (sentName != null) {
                val sentFolder = store.getFolder(sentName)
                if (sentFolder.exists()) {
                    sentFolder.open(Folder.READ_WRITE)
                    message.setFlag(Flags.Flag.SEEN, true)
                    sentFolder.appendMessages(arrayOf(message))
                    sentFolder.close(false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext generatedMessageId
    }

    suspend fun disconnect() = withContext(Dispatchers.IO) {
        storeMutex.withLock {
            store?.takeIf { it.isConnected }?.close()
            store = null
        }
    }
}

private fun Part.getBodyText(): String? {
    return try {
        when (val c = content) {
            is String -> c
            is java.io.InputStream -> {
                val charset = java.nio.charset.Charset.forName(contentType.charsetOrUtf8())
                // Decode Content-Transfer-Encoding (base64, quoted-printable, etc.)
                val encoding = (this as? jakarta.mail.internet.MimePart)?.encoding
                val decodedStream = if (encoding != null) {
                    try {
                        jakarta.mail.internet.MimeUtility.decode(c, encoding)
                    } catch (e: Exception) {
                        c
                    }
                } else {
                    c
                }
                decodedStream.bufferedReader(charset).readText()
            }
            else -> {
                android.util.Log.e("ImapProvider", "Unknown content type: ${c?.javaClass?.name}")
                null
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("ImapProvider", "Error getting body text: ${e.message}", e)
        null
    }
}

private fun String.charsetOrUtf8(): String =
    Regex("charset=[\"']?([\\w-]+)[\"']?", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.get(1) ?: "UTF-8"
