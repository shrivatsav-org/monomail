package com.shrivatsav.monomail.core.network.provider.imap

import android.content.Context
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.core.network.provider.EmailFolder
import com.shrivatsav.monomail.core.network.provider.EmailProvider
import com.shrivatsav.monomail.core.network.provider.ProviderMessage
import com.shrivatsav.monomail.core.network.provider.SendEmailOptions
import com.shrivatsav.monomail.core.network.provider.ProviderThread
import com.shrivatsav.monomail.core.network.provider.ProviderThreadListResult
import com.shrivatsav.monomail.core.network.provider.SendAsAlias
import com.shrivatsav.monomail.core.network.provider.SendEmailResult
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
import kotlinx.coroutines.withContext
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class ImapProvider(
    private val config: ImapAccountConfig,
    private val password: String,
    private val context: Context
) : EmailProvider {

    companion object {
        private const val HEADER_MESSAGE_ID = "Message-ID"
        private const val HEADER_IN_REPLY_TO = "In-Reply-To"
        private const val HEADER_REFERENCES = "References"
        private const val MIME_MULTIPART = "multipart/*"
        const val MIME_TEXT_PLAIN = "text/plain"
        const val MIME_TEXT_HTML = "text/html"
        private val HTML_TAG_REGEX = Regex("<[^>]+>")
    }

    override val providerName: String = "imap"

    private val folderNamesCache = ConcurrentHashMap<EmailFolder, String>()
    @Volatile private var cachedStore: Store? = null

    private suspend fun getStore(): Store {
        cachedStore?.let { s ->
            if (s.isConnected) return s
            // Connection dropped — fall through to reconnect
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

        // Populate folder cache only on first connection (avoid the clear+repopulate race)
        if (folderNamesCache.isEmpty()) {
            val defaultFolder = newStore.defaultFolder
            val folders = defaultFolder.list("*")
            for (f in folders) {
                matchFolder(f.fullName.lowercase())?.let { folderNamesCache[it] = f.fullName }
            }
            if (!folderNamesCache.containsKey(EmailFolder.INBOX)) folderNamesCache[EmailFolder.INBOX] = "INBOX"
        }

        cachedStore = newStore
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
        try {
            val imapFolder = openListFolder(store, folder)
                ?: return@withContext ProviderThreadListResult(emptyList(), null)

            imapFolder.open(Folder.READ_ONLY)
            try {
                val fetchResult = fetchMessagesForListing(imapFolder, folder, maxResults, pageToken)
                    ?: return@withContext ProviderThreadListResult(emptyList(), null)

                val rawMessages = fetchResult.messages.mapNotNull { msg ->
                    envelopeToRawMessage(msg, folder)
                }

                val threads = ImapThreader.groupByReferences(rawMessages)
                    .map { (threadId, msgs) -> ProviderThread(threadId, msgs) }
                    .sortedByDescending { t -> t.messages.maxOfOrNull { it.date } ?: 0L }

                val nextToken = if (fetchResult.fetchStart > 1) (fetchResult.fetchStart - 1).toString() else null
                ProviderThreadListResult(threads, nextToken)
            } finally {
                if (imapFolder.isOpen) imapFolder.close(false)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    private fun openListFolder(store: Store, folder: EmailFolder): Folder? {
        return if (folder == EmailFolder.STARRED) {
            store.getFolder(getFolderName(EmailFolder.INBOX) ?: "INBOX")
        } else {
            val folderName = getFolderName(folder) ?: return null
            val f = store.getFolder(folderName)
            if (!f.exists()) null else f
        }
    }

    private data class FetchResult(val messages: List<jakarta.mail.Message>, val fetchStart: Int)

    private fun fetchMessagesForListing(
        folder: jakarta.mail.Folder,
        emailFolder: EmailFolder,
        maxResults: Int,
        pageToken: String?
    ): FetchResult? {
        val messages: List<jakarta.mail.Message>
        val start: Int

        if (emailFolder == EmailFolder.STARRED) {
            val allStarred = folder.search(FlagTerm(Flags(Flags.Flag.FLAGGED), true))
            if (allStarred.isEmpty()) return null
            messages = allStarred.takeLast(maxResults).toList()
            start = 1
        } else {
            val total = folder.messageCount
            if (total == 0) return null

            val offset = pageToken?.toIntOrNull() ?: total
            start = maxOf(1, offset - maxResults + 1)
            val end = offset
            if (start > end) return null
            messages = folder.getMessages(start, end).toList()
        }

        val profile = jakarta.mail.FetchProfile().apply {
            add(jakarta.mail.FetchProfile.Item.ENVELOPE)
            add(jakarta.mail.FetchProfile.Item.FLAGS)
            add(jakarta.mail.FetchProfile.Item.CONTENT_INFO)
            add(HEADER_MESSAGE_ID)
            add(HEADER_REFERENCES)
            add(HEADER_IN_REPLY_TO)
        }
        folder.fetch(messages.toTypedArray(), profile)

        return FetchResult(messages, start)
    }

    private fun envelopeToRawMessage(msg: jakarta.mail.Message, folder: EmailFolder): ImapRawMessage? {
        val messageId = msg.getHeader(HEADER_MESSAGE_ID)?.firstOrNull() ?: return null
        val references = msg.getHeader(HEADER_REFERENCES)?.joinToString(" ") ?: ""
        val inReplyTo = msg.getHeader(HEADER_IN_REPLY_TO)?.firstOrNull() ?: ""
        val date = msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L

        val fromAddrs = msg.from?.mapNotNull { it as? InternetAddress } ?: emptyList()
        val toAddrs = msg.getRecipients(Message.RecipientType.TO)?.mapNotNull { it as? InternetAddress } ?: emptyList()
        val ccAddrs = msg.getRecipients(Message.RecipientType.CC)?.mapNotNull { it as? InternetAddress } ?: emptyList()

        val attachments = mutableListOf<EmailAttachmentInfo>()
        val snippet = try { extractSnippet(msg) } catch (_: Exception) { "" }
        try { collectAttachments(msg, attachments, messageId) } catch (e: Exception) {
            android.util.Log.w("ImapProvider", "listThreads attachment parse error: ${e.message}")
        }

        val providerMsg = ProviderMessage(
            id = messageId, threadId = messageId,
            subject = msg.subject ?: "",
            from = fromAddrs.firstOrNull()?.personal ?: fromAddrs.firstOrNull()?.address ?: "",
            fromEmail = fromAddrs.firstOrNull()?.address ?: "",
            to = toAddrs.joinToString(", ") { it.address },
            cc = ccAddrs.joinToString(", ") { it.address },
            bcc = "", snippet = snippet, body = "", bodyIsHtml = false,
            date = date, isRead = msg.isSet(Flags.Flag.SEEN), isStarred = msg.isSet(Flags.Flag.FLAGGED),
            folders = setOf(folder), attachments = attachments
        )
        return ImapRawMessage(messageId, references, inReplyTo, date, providerMsg)
    }

    override suspend fun getThread(threadId: String, folderHints: List<String>): ProviderThread = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val searchFolders = resolveSearchFolders(folderHints)
            val rawMessages = mutableListOf<ImapRawMessage>()

            try {
                for (folderName in searchFolders) {
                    collectMessagesFromFolder(store, folderName, threadId, rawMessages)
                }

                if (rawMessages.isEmpty()) return@withContext ProviderThread(threadId, emptyList())

                val threadsMap = ImapThreader.groupByReferences(rawMessages)
                return@withContext threadsMap.values.firstOrNull()?.let { msgs ->
                    ProviderThread(threadId, msgs)
                } ?: ProviderThread(threadId, emptyList())
            } catch (e: Exception) {
                android.util.Log.e("ImapProvider", "getThread error", e)
                return@withContext ProviderThread(threadId, emptyList())
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    private fun collectMessagesFromFolder(
        store: Store,
        folderName: String,
        threadId: String,
        rawMessages: MutableList<ImapRawMessage>
    ) {
        val f = store.getFolder(folderName)
        if (!f.exists()) return

        f.open(Folder.READ_ONLY)
        try {
            val orTerm = jakarta.mail.search.OrTerm(
                jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId),
                jakarta.mail.search.OrTerm(
                    jakarta.mail.search.HeaderTerm(HEADER_REFERENCES, threadId),
                    jakarta.mail.search.HeaderTerm(HEADER_IN_REPLY_TO, threadId)
                )
            )
            val matchingMessages = f.search(orTerm)
            if (matchingMessages.isEmpty()) return

            val profile = jakarta.mail.FetchProfile().apply {
                add(jakarta.mail.FetchProfile.Item.ENVELOPE)
                add(jakarta.mail.FetchProfile.Item.FLAGS)
                add(jakarta.mail.FetchProfile.Item.CONTENT_INFO)
            }
            f.fetch(matchingMessages, profile)

            for (msg in matchingMessages) {
                val raw = threadMessageToRaw(msg, threadId) ?: continue
                rawMessages.add(raw)
            }
        } finally {
            if (f.isOpen) f.close(false)
        }
    }

    private fun threadMessageToRaw(msg: jakarta.mail.Message, threadId: String): ImapRawMessage? {
        val messageId = msg.getHeader(HEADER_MESSAGE_ID)?.firstOrNull() ?: return null
        val references = msg.getHeader(HEADER_REFERENCES)?.joinToString(" ") ?: ""
        val inReplyTo = msg.getHeader(HEADER_IN_REPLY_TO)?.firstOrNull() ?: ""
        val date = msg.sentDate?.time ?: msg.receivedDate?.time ?: 0L

        val fromAddrs = msg.from?.mapNotNull { it as? InternetAddress } ?: emptyList()
        val toAddrs = msg.getRecipients(Message.RecipientType.TO)?.mapNotNull { it as? InternetAddress } ?: emptyList()
        val ccAddrs = msg.getRecipients(Message.RecipientType.CC)?.mapNotNull { it as? InternetAddress } ?: emptyList()

        val folderSet = mutableSetOf<EmailFolder>()
        val fname = msg.folder.name
        folderNamesCache.entries.find { it.value == fname }?.key?.let { folderSet.add(it) }

        val isRead = msg.isSet(Flags.Flag.SEEN)
        val isStarred = msg.isSet(Flags.Flag.FLAGGED)
        if (isStarred) folderSet.add(EmailFolder.STARRED)

        val state = BodyParseState(messageId = messageId)
        processPart(msg, state)

        var body = state.htmlBody.ifEmpty { state.plainBody.replace("\n", "<br>") }
        state.cidMap.forEach { (cid, dataUri) -> body = body.replace("cid:$cid", dataUri) }

        val snippet = state.plainBody.take(150).replace(Regex("\\s+"), " ").trim().ifEmpty {
            state.htmlBody.replace(HTML_TAG_REGEX, " ").take(150).replace(Regex("\\s+"), " ").trim()
        }

        val providerMsg = ProviderMessage(
            id = messageId, threadId = threadId,
            subject = msg.subject ?: "",
            from = fromAddrs.firstOrNull()?.personal ?: fromAddrs.firstOrNull()?.address ?: "",
            fromEmail = fromAddrs.firstOrNull()?.address ?: "",
            to = toAddrs.joinToString(", ") { it.address },
            cc = ccAddrs.joinToString(", ") { it.address },
            bcc = "", snippet = snippet, body = body, bodyIsHtml = state.bodyIsHtml,
            date = date, isRead = isRead, isStarred = isStarred,
            folders = folderSet, attachments = state.attachments
        )
        return ImapRawMessage(messageId, references, inReplyTo, date, providerMsg)
    }

    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val searchFolders = listOfNotNull(
                folderNamesCache[EmailFolder.INBOX],
                folderNamesCache[EmailFolder.SENT],
                folderNamesCache[EmailFolder.ARCHIVE]
            ).distinct()

            for (folderName in searchFolders) {
                val result = findAttachmentInFolder(store, folderName, messageId, attachmentId)
                if (result != null) return@withContext result
            }
            return@withContext null
        } finally {
            if (store.isConnected) store.close()
        }
    }

    private fun findAttachmentInFolder(store: Store, folderName: String, messageId: String, attachmentId: String): ByteArray? {
        val f = store.getFolder(folderName)
        if (!f.exists()) return null

        f.open(Folder.READ_ONLY)
        try {
            val messages = f.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, messageId))
            if (messages.isEmpty()) return null
            return searchPart(messages[0], attachmentId)
        } finally {
            f.close(false)
        }
    }

    private suspend fun moveThread(threadId: String, targetFolder: EmailFolder) = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val targetName = getFolderName(targetFolder) ?: return@withContext
            val destFolder = store.getFolder(targetName)
            if (!destFolder.exists()) destFolder.create(Folder.HOLDS_MESSAGES)

            // Search all cached folders for this thread, not just INBOX
            for (srcName in folderNamesCache.values) {
                val srcFolder = store.getFolder(srcName)
                if (!srcFolder.exists()) continue

                srcFolder.open(Folder.READ_WRITE)
                try {
                    val messages = srcFolder.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId))
                    if (messages.isNotEmpty()) {
                        srcFolder.copyMessages(messages, destFolder)
                        srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                        srcFolder.expunge()
                    }
                } finally {
                    srcFolder.close(true)
                }
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    override suspend fun archiveThread(threadId: String) {
        moveThread(threadId, EmailFolder.ARCHIVE)
    }

    override suspend fun unarchiveThread(threadId: String) = withContext(Dispatchers.IO) {
        // Move back to INBOX, simplified since we only search INBOX for moving
        val store = getStore()
        try {
            val srcName = getFolderName(EmailFolder.ARCHIVE) ?: return@withContext
            val destName = getFolderName(EmailFolder.INBOX) ?: return@withContext
            val srcFolder = store.getFolder(srcName)
            val destFolder = store.getFolder(destName)
            if (!srcFolder.exists()) return@withContext

            srcFolder.open(Folder.READ_WRITE)
            try {
                val messages = srcFolder.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId))
                if (messages.isNotEmpty()) {
                    srcFolder.copyMessages(messages, destFolder)
                    srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                    srcFolder.expunge()
                }
            } finally {
                srcFolder.close(true)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    override suspend fun trashThread(threadId: String) {
        moveThread(threadId, EmailFolder.TRASH)
    }

    override suspend fun restoreThread(threadId: String) = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val srcName = getFolderName(EmailFolder.TRASH) ?: return@withContext
            val destName = getFolderName(EmailFolder.INBOX) ?: return@withContext
            val srcFolder = store.getFolder(srcName)
            val destFolder = store.getFolder(destName)
            if (!srcFolder.exists()) return@withContext

            srcFolder.open(Folder.READ_WRITE)
            try {
                val messages = srcFolder.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId))
                if (messages.isNotEmpty()) {
                    srcFolder.copyMessages(messages, destFolder)
                    srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                    srcFolder.expunge()
                }
            } finally {
                srcFolder.close(true)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    override suspend fun permanentlyDeleteThread(threadId: String) = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val srcName = getFolderName(EmailFolder.TRASH) ?: return@withContext
            val srcFolder = store.getFolder(srcName)
            if (!srcFolder.exists()) return@withContext

            srcFolder.open(Folder.READ_WRITE)
            try {
                val messages = srcFolder.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId))
                if (messages.isNotEmpty()) {
                    srcFolder.setFlags(messages, Flags(Flags.Flag.DELETED), true)
                    srcFolder.expunge()
                }
            } finally {
                srcFolder.close(true)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    private suspend fun updateFlag(threadId: String, flag: Flags.Flag, set: Boolean) = withContext(Dispatchers.IO) {
        val store = getStore()
        try {
            val searchFolders = listOfNotNull(
                folderNamesCache[EmailFolder.INBOX],
                folderNamesCache[EmailFolder.ARCHIVE]
            ).distinct()

            for (folderName in searchFolders) {
                val f = store.getFolder(folderName)
                if (f.exists()) {
                    f.open(Folder.READ_WRITE)
                    try {
                        val messages = f.search(jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, threadId))
                        if (messages.isNotEmpty()) {
                            f.setFlags(messages, Flags(flag), set)
                        }
                    } finally {
                        f.close(false)
                    }
                }
            }
        } finally {
            if (store.isConnected) store.close()
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
        try {
            val searchFolders = listOfNotNull(
                folderNamesCache[EmailFolder.INBOX],
                folderNamesCache[EmailFolder.ARCHIVE]
            ).distinct()

            for (folderName in searchFolders) {
                val f = store.getFolder(folderName)
                if (f.exists()) {
                    f.open(Folder.READ_WRITE)
                    try {
                        messageIds.chunked(100).forEach { chunk ->
                            val terms = chunk.map { jakarta.mail.search.HeaderTerm(HEADER_MESSAGE_ID, it) }.toTypedArray()
                            val orTerm = jakarta.mail.search.OrTerm(terms)
                            val messages = f.search(orTerm)
                            if (messages.isNotEmpty()) {
                                f.setFlags(messages, Flags(Flags.Flag.SEEN), true)
                            }
                        }
                    } finally {
                        f.close(false)
                    }
                }
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    override suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        options: SendEmailOptions
    ): SendEmailResult? = withContext(Dispatchers.IO) {
        val props = buildSmtpProps(from)
        val session = Session.getInstance(props, object : jakarta.mail.Authenticator() {
            override fun getPasswordAuthentication() = jakarta.mail.PasswordAuthentication(config.username, password)
        })

        val message = buildMimeMessage(session, from, to, subject, body, options)
        Transport.send(message)
        saveToSentFolder(message)
        SendEmailResult(messageId = message.messageID, threadId = message.messageID)
    }

    private fun buildMimeMessage(
        session: Session,
        from: String,
        to: String,
        subject: String,
        body: String,
        options: SendEmailOptions
    ): MimeMessage = MimeMessage(session).apply {
        setFrom(InternetAddress(from))
        setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
        if (options.cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(options.cc))
        if (options.bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, InternetAddress.parse(options.bcc))
        this.subject = subject
        if (options.inReplyToMessageId != null) {
            setHeader(HEADER_IN_REPLY_TO, options.inReplyToMessageId)
        }
        val refs = options.references?.takeIf { it.isNotBlank() }
            ?: options.inReplyToMessageId
        if (refs != null) {
            setHeader(HEADER_REFERENCES, refs)
        }
        if (options.attachments.isEmpty()) {
            setContent(body, "text/html; charset=utf-8")
        } else {
            val multipart = MimeMultipart().apply {
                addBodyPart(MimeBodyPart().also { it.setContent(body, "text/html; charset=utf-8") })
                for (att in options.attachments) {
                    val bytes = context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() } ?: continue
                    addBodyPart(MimeBodyPart().also {
                        it.dataHandler = jakarta.activation.DataHandler(jakarta.mail.util.ByteArrayDataSource(bytes, att.mimeType))
                        it.fileName = att.name
                    })
                }
            }
            setContent(multipart)
        }
        saveChanges()
    }

    override suspend fun getSendAsAliases(): List<SendAsAlias> {
        // IMAP/SMTP doesn't have a "send-as" alias API
        return emptyList()
    }

    private fun matchFolder(lower: String): EmailFolder? = when {
        lower.endsWith("inbox") || lower == "inbox" -> EmailFolder.INBOX
        lower.contains("sent") || lower == "sent items" || lower == "sent messages" -> EmailFolder.SENT
        lower.contains("archive") || lower == "all mail" -> EmailFolder.ARCHIVE
        lower.contains("trash") || lower == "deleted messages" || lower == "deleted items" || lower == "bin" -> EmailFolder.TRASH
        lower.contains("spam") || lower == "junk" -> EmailFolder.SPAM
        else -> null
    }

    private fun collectAttachments(part: Part, attachments: MutableList<EmailAttachmentInfo>, messageId: String) {
        try {
            val disposition = part.disposition
            val contentType = part.contentType.lowercase()
            val contentId = part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">")

            if (Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
                (disposition == null && contentType.contains("application/") && contentId == null)) {
                val name = part.fileName ?: "attachment"
                attachments.add(EmailAttachmentInfo(id = name, messageId = messageId, mimeType = contentType.substringBefore(";"), name = name, size = part.size))
            } else if (part.isMimeType(MIME_MULTIPART)) {
                val mp = toMultipart(part) ?: return
                for (i in 0 until mp.count) {
                    collectAttachments(mp.getBodyPart(i), attachments, messageId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ImapProvider", "collectAttachments error: ${e.message}")
        }
    }

    private fun processPart(part: Part, state: BodyParseState) {
        val disposition = part.disposition
        val contentType = part.contentType.lowercase()
        val contentId = part.getHeader("Content-ID")?.firstOrNull()?.removeSurrounding("<", ">")

        collectInlineImage(part, contentType, contentId, state)

        val isAttachment = Part.ATTACHMENT.equals(disposition, ignoreCase = true) ||
            (disposition == null && contentType.contains("application/") && contentId == null)
        val mimeType = contentType.substringBefore(";")

        when {
            isAttachment -> {
                val name = part.fileName ?: "attachment"
                state.attachments.add(EmailAttachmentInfo(id = name, messageId = state.messageId, mimeType = mimeType, name = name, size = part.size))
            }
            part.isMimeType(MIME_TEXT_PLAIN) && state.plainBody.isEmpty() -> {
                state.plainBody = part.getBodyText() ?: ""
            }
            part.isMimeType(MIME_TEXT_HTML) && state.htmlBody.isEmpty() -> {
                state.htmlBody = part.getBodyText() ?: ""
                state.bodyIsHtml = true
            }
            part.isMimeType(MIME_MULTIPART) -> {
                try {
                    val mp = toMultipart(part) ?: return
                    for (i in 0 until mp.count) {
                        processPart(mp.getBodyPart(i), state)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImapProvider", "Error parsing multipart", e)
                }
            }
        }
    }

    private fun collectInlineImage(part: Part, contentType: String, contentId: String?, state: BodyParseState) {
        if (contentId == null || !contentType.startsWith("image/")) return
        try {
            val base64 = android.util.Base64.encodeToString(part.inputStream.readBytes(), android.util.Base64.NO_WRAP)
            state.cidMap[contentId] = "data:$contentType;base64,$base64"
        } catch (e: Exception) {
            android.util.Log.w("ImapProvider", "Failed to read inline image: ${e.message}")
        }
    }

    private fun toMultipart(part: Part): Multipart? {
        val content = part.content
        return when (content) {
            is Multipart -> content
            is java.io.InputStream -> try {
                jakarta.mail.internet.MimeMultipart(jakarta.mail.util.ByteArrayDataSource(content, part.contentType))
            } catch (e: Exception) { null }
            else -> null
        }
    }

    private fun searchPart(part: Part, attachmentId: String): ByteArray? {
        if (part.fileName == attachmentId) {
            return part.inputStream.use { it.readBytes() }
        }
        if (part.isMimeType(MIME_MULTIPART)) {
            val mp = part.content as? Multipart ?: return null
            for (i in 0 until mp.count) {
                val result = searchPart(mp.getBodyPart(i), attachmentId)
                if (result != null) return result
            }
        }
        return null
    }

    private fun buildSmtpProps(from: String): Properties {
        val props = Properties()
        val protocol = if (config.smtpSsl) "smtps" else "smtp"
        props["mail.transport.protocol"] = protocol
        props["mail.$protocol.host"] = config.smtpHost
        props["mail.$protocol.port"] = config.smtpPort.toString()
        props["mail.$protocol.auth"] = "true"
        if (config.smtpStartTls) props["mail.$protocol.starttls.enable"] = "true"
        props["mail.$protocol.connectiontimeout"] = "15000"
        props["mail.$protocol.timeout"] = "15000"
        props["mail.$protocol.writetimeout"] = "15000"
        props["mail.smtp.localhost"] = from.substringAfterLast("@").ifBlank { config.smtpHost }
        props["mail.smtp.quitwait"] = "false"
        props["mail.$protocol.ssl.protocols"] = "TLSv1.2 TLSv1.3"
        if (config.smtpSsl || config.smtpStartTls) props["mail.$protocol.checkserveridentity"] = "true"
        if (config.smtpStartTls) props["mail.$protocol.starttls.required"] = "true"
        return props
    }

    private fun resolveSearchFolders(hints: List<String>): List<String> {
        val allFolders = folderNamesCache.values.toList()
        if (hints.isEmpty()) {
            return listOfNotNull(
                folderNamesCache[EmailFolder.INBOX],
                folderNamesCache[EmailFolder.SENT],
                folderNamesCache[EmailFolder.ARCHIVE]
            ).distinct()
        }
        val mapped = hints.mapNotNull { hint ->
            try { folderNamesCache[EmailFolder.valueOf(hint)] } catch (_: Exception) { null }
        }
        return mapped.ifEmpty { allFolders }.distinct()
    }

    private suspend fun saveToSentFolder(message: jakarta.mail.Message) {
        val sentName = getFolderName(EmailFolder.SENT) ?: return
        try {
            val imapStore = getStore()
            val sentFolder = imapStore.getFolder(sentName)
            if (sentFolder.exists()) {
                sentFolder.open(Folder.READ_WRITE)
                try {
                    message.setFlag(Flags.Flag.SEEN, true)
                    sentFolder.appendMessages(arrayOf(message))
                } finally {
                    sentFolder.close(false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ImapProvider", "Failed to save to Sent folder", e)
        }
    }

    private data class BodyParseState(
        val messageId: String,
        var plainBody: String = "",
        var htmlBody: String = "",
        var bodyIsHtml: Boolean = false,
        val attachments: MutableList<EmailAttachmentInfo> = mutableListOf(),
        val cidMap: MutableMap<String, String> = mutableMapOf()
    )
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

private val EXTRACT_SNIPPET_HTML_REGEX = Regex("<[^>]+>")

private fun extractSnippet(part: jakarta.mail.Part): String {
    return try {
        val content = part.content
        val text = when {
            part.isMimeType(ImapProvider.MIME_TEXT_PLAIN) -> (content as? String) ?: ""
            part.isMimeType(ImapProvider.MIME_TEXT_HTML) -> (content as? String)?.replace(EXTRACT_SNIPPET_HTML_REGEX, " ") ?: ""
            content is Multipart -> {
                var result = ""
                for (i in 0 until content.count) {
                    val bp = content.getBodyPart(i)
                    val partContent = try { bp.content as? String } catch (_: Exception) { null }
                    when {
                        bp.isMimeType(ImapProvider.MIME_TEXT_PLAIN) -> { result = partContent ?: ""; break }
                        bp.isMimeType(ImapProvider.MIME_TEXT_HTML) && result.isEmpty() ->
                            result = partContent?.replace(EXTRACT_SNIPPET_HTML_REGEX, " ") ?: ""
                        bp.isMimeType("multipart/*") && result.isEmpty() ->
                            result = extractSnippet(bp)
                    }
                }
                result
            }
            else -> ""
        }
        text.replace(Regex("\\s+"), " ").trim().take(150)
    } catch (_: Exception) { "" }
}

private fun String.charsetOrUtf8(): String =
    Regex("charset=[\"']?([\\w-]+)[\"']?", RegexOption.IGNORE_CASE)
        .find(this)?.groupValues?.get(1) ?: "UTF-8"
