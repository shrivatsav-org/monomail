package com.shrivatsav.monomail.shared.data.mapper

import com.shrivatsav.monomail.shared.data.model.Email
import com.shrivatsav.monomail.shared.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.shared.data.model.EmailThread
import com.shrivatsav.monomail.shared.data.remote.GmailMessage
import com.shrivatsav.monomail.shared.data.remote.GmailThread
import com.shrivatsav.monomail.shared.data.remote.MessagePart
import com.shrivatsav.monomail.shared.util.Base64Util

object EmailMapper {
    private fun cleanSubject(subject: String): String {
        return subject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")
    }

    fun GmailMessage.toEmail(): Email {
        val headers = payload?.headers.orEmpty()
        val subject = headers.firstOrNull { it.name.equals("Subject", true) }?.value ?: "(no subject)"
        val fromRaw = headers.firstOrNull { it.name.equals("From", true) }?.value ?: ""
        val toRaw = headers.firstOrNull { it.name.equals("To", true) }?.value ?: ""
        val (fromName, fromEmail) = parseFrom(fromRaw)
        val labels = labelIds.orEmpty()
        val isRead = "UNREAD" !in labels
        val isStarred = "STARRED" in labels
        return Email(
            id = id,
            threadId = threadId,
            subject = subject,
            from = fromName,
            fromEmail = fromEmail,
            to = toRaw,
            snippet = snippet?.decodeHtmlEntities() ?: "",
            body = extractAndInjectImages(payload),
            date = internalDate?.toLongOrNull() ?: 0L,
            isRead = isRead,
            isStarred = isStarred,
            labels = labels,
            attachments = extractAttachments(payload, id)
        )
    }

    fun GmailThread.toEmailThread(): EmailThread {
        val messages = messages.orEmpty()
        val sorted = messages.sortedByDescending { it.internalDate?.toLongOrNull() ?: 0L }
        val latest = sorted.firstOrNull()
        val latestHeaders = latest?.payload?.headers.orEmpty()
        val subject = latestHeaders.firstOrNull { it.name.equals("Subject", true) }?.value
            ?: messages.firstOrNull()?.payload?.headers?.firstOrNull { it.name.equals("Subject", true) }?.value
            ?: "(no subject)"
        val fromRaw = latestHeaders.firstOrNull { it.name.equals("From", true) }?.value ?: ""
        val (fromName, fromEmail) = parseFrom(fromRaw)
        val isRead = messages.all { msg -> "UNREAD" !in msg.labelIds.orEmpty() }
        val isStarred = messages.any { msg -> "STARRED" in msg.labelIds.orEmpty() }
        val participants = messages.mapNotNull { msg ->
            val raw = msg.payload?.headers?.firstOrNull { it.name.equals("From", true) }?.value
            raw?.let { parseFrom(it).first }
        }.distinct()
        return EmailThread(
            threadId = id,
            subject = cleanSubject(subject),
            from = fromName,
            fromEmail = fromEmail,
            snippet = latest?.snippet?.decodeHtmlEntities() ?: "",
            date = latest?.internalDate?.toLongOrNull() ?: 0L,
            messageCount = messages.size,
            isRead = isRead,
            isStarred = isStarred,
            latestMessageId = latest?.id ?: "",
            participants = participants
        )
    }

    fun GmailThread.toEmailList(): List<Email> {
        return messages.orEmpty()
            .sortedBy { it.internalDate?.toLongOrNull() ?: 0L }
            .map { it.toEmail() }
    }

    private fun parseFrom(raw: String): Pair<String, String> {
        val match = Regex("""^(.*?)\s*<(.+?)>$""").find(raw.trim())
        return if (match != null) {
            val name = match.groupValues[1].trim().removeSurrounding("\"")
            val email = match.groupValues[2].trim()
            Pair(name.ifEmpty { email }, email)
        } else {
            Pair(raw.trim(), raw.trim())
        }
    }

    private fun extractBody(part: MessagePart?): String {
        if (part == null) return ""
        if (part.body?.data != null && part.parts.isNullOrEmpty()) {
            val decoded = decodeBase64Url(part.body.data)
            if (part.mimeType == "text/plain" || part.mimeType == "text/html") {
                return decoded
            }
        }
        val children = part.parts.orEmpty()
        for (child in children) {
            if (child.mimeType == "text/html" && child.body?.data != null) {
                return decodeBase64Url(child.body.data)
            }
        }
        for (child in children) {
            if (child.mimeType == "text/plain" && child.body?.data != null) {
                return decodeBase64Url(child.body.data)
            }
        }
        for (child in children) {
            val result = extractBody(child)
            if (result.isNotEmpty()) return result
        }
        return ""
    }

    private fun extractAndInjectImages(payload: MessagePart?): String {
        var htmlBody = extractBody(payload)
        val cidMap = mutableMapOf<String, String>()
        extractInlineImages(payload, cidMap)
        cidMap.forEach { (cid, dataUri) ->
            htmlBody = htmlBody.replace("cid:$cid", dataUri)
        }
        return htmlBody
    }

    private fun extractInlineImages(part: MessagePart?, map: MutableMap<String, String>) {
        if (part == null) return
        val contentId = part.headers?.firstOrNull { it.name.equals("Content-ID", true) }
            ?.value?.removeSurrounding("<", ">")
        if (contentId != null && part.mimeType?.startsWith("image/") == true && !part.body?.data.isNullOrEmpty()) {
            val base64 = part.body!!.data!!.replace("-", "+").replace("_", "/")
            map[contentId] = "data:${part.mimeType};base64,$base64"
        }
        part.parts?.forEach { extractInlineImages(it, map) }
    }

    private fun extractAttachments(part: MessagePart?, messageId: String): List<EmailAttachmentInfo> {
        if (part == null) return emptyList()
        val attachments = mutableListOf<EmailAttachmentInfo>()
        if (!part.filename.isNullOrEmpty() && part.body?.attachmentId != null) {
            attachments.add(
                EmailAttachmentInfo(
                    id = part.body.attachmentId,
                    messageId = messageId,
                    mimeType = part.mimeType ?: "application/octet-stream",
                    name = part.filename,
                    size = part.body.size ?: 0
                )
            )
        }
        part.parts?.forEach { attachments.addAll(extractAttachments(it, messageId)) }
        return attachments
    }

    private fun decodeBase64Url(data: String): String {
        return try {
            Base64Util.decodeUrl(data).decodeToString()
        } catch (e: Exception) {
            ""
        }
    }

    private fun String.decodeHtmlEntities(): String {
        return this
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }
}
