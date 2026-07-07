package com.shrivatsav.monomail.data.mapper
import android.text.Html
import android.util.Base64
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.remote.GmailMessage
import com.shrivatsav.monomail.data.remote.GmailThread
import com.shrivatsav.monomail.data.remote.MessagePart
import com.shrivatsav.monomail.util.cleanSubject
object EmailMapper {

    /** Result of extracting a body from a MIME part tree. */
    private data class BodyResult(
        val text: String,
        val isHtml: Boolean
    )
    fun GmailMessage.toEmail(): Email {
        val headers = payload?.headers.orEmpty()
        val subject  = headers.firstOrNull { it.name.equals("Subject", true) }?.value ?: "(no subject)"
        val fromRaw  = headers.firstOrNull { it.name.equals("From", true) }?.value ?: ""
        val toRaw    = headers.firstOrNull { it.name.equals("To", true) }?.value ?: ""
        val ccRaw    = headers.firstOrNull { it.name.equals("Cc", true) }?.value ?: ""
        val bccRaw   = headers.firstOrNull { it.name.equals("Bcc", true) }?.value ?: ""
        val (fromName, fromEmail) = parseFrom(fromRaw)
        val labels   = labelIds.orEmpty()
        val isRead   = "UNREAD" !in labels
        val isStarred = "STARRED" in labels
        val bodyInfo = extractAndInjectImages(payload)
        return Email(
            id        = id,
            threadId  = threadId,
            subject   = subject,
            from      = fromName,
            fromEmail = fromEmail,
            to        = toRaw,
            cc        = ccRaw,
            bcc       = bccRaw,
            snippet   = snippet?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() } ?: "",
            body      = bodyInfo.text,
            bodyIsHtml = bodyInfo.isHtml,
            date      = internalDate?.toLongOrNull() ?: 0L,
            isRead    = isRead,
            isStarred = isStarred,
            labels    = labels,
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
        val isRead = messages.all { msg ->
            val labels = msg.labelIds.orEmpty()
            "UNREAD" !in labels
        }
        val isStarred = messages.any { msg ->
            val labels = msg.labelIds.orEmpty()
            "STARRED" in labels
        }
        val participants = messages.mapNotNull { msg ->
            val raw = msg.payload?.headers?.firstOrNull { it.name.equals("From", true) }?.value
            raw?.let { parseFrom(it).first }
        }.distinct()
        return EmailThread(
            threadId        = id,
            subject         = subject.cleanSubject(),
            from            = fromName,
            fromEmail       = fromEmail,
            snippet         = latest?.snippet?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString() } ?: "",
            date            = latest?.internalDate?.toLongOrNull() ?: 0L,
            messageCount    = messages.size,
            isRead          = isRead,
            isStarred       = isStarred,
            latestMessageId = latest?.id ?: "",
            participants    = participants
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
    private fun extractBody(part: MessagePart?): BodyResult {
        if (part == null) return BodyResult("", isHtml = true)
        if (part.body?.data != null && part.parts.isNullOrEmpty()) {
            val decoded = decodeBase64Url(part.body.data)
            if (part.mimeType == "text/html") {
                return BodyResult(decoded, isHtml = true)
            }
            if (part.mimeType == "text/plain") {
                return BodyResult(decoded, isHtml = false)
            }
        }
        val children = part.parts.orEmpty()
        for (child in children) {
            if (child.mimeType == "text/html" && child.body?.data != null) {
                return BodyResult(decodeBase64Url(child.body.data), isHtml = true)
            }
        }
        for (child in children) {
            if (child.mimeType == "text/plain" && child.body?.data != null) {
                return BodyResult(decodeBase64Url(child.body.data), isHtml = false)
            }
        }
        for (child in children) {
            val result = extractBody(child)
            if (result.text.isNotEmpty()) return result
        }
        return BodyResult("", isHtml = true)
    }
    private fun extractAndInjectImages(payload: MessagePart?): BodyResult {
        val bodyInfo = extractBody(payload)
        val cidMap = mutableMapOf<String, String>()
        extractInlineImages(payload, cidMap)
        var htmlBody = bodyInfo.text
        cidMap.forEach { (cid, dataUri) ->
            htmlBody = htmlBody.replace("cid:$cid", dataUri)
        }
        return BodyResult(htmlBody, bodyInfo.isHtml)
    }
    private fun extractInlineImages(part: MessagePart?, map: MutableMap<String, String>) {
        if (part == null) return
        val contentId = part.headers?.firstOrNull { it.name.equals("Content-ID", true) }?.value?.removeSurrounding("<", ">")
        if (contentId != null && part.mimeType?.startsWith("image/") == true && !part.body?.data.isNullOrEmpty()) {
            val base64 = part.body!!.data!!.replace("-", "+").replace("_", "/")
            map[contentId] = "data:${part.mimeType};base64,$base64"
        }
        part.parts?.forEach { extractInlineImages(it, map) }
    }
    private fun extractAttachments(part: MessagePart?, messageId: String): List<com.shrivatsav.monomail.data.model.EmailAttachmentInfo> {
        if (part == null) return emptyList()
        val attachments = mutableListOf<com.shrivatsav.monomail.data.model.EmailAttachmentInfo>()
        if (!part.filename.isNullOrEmpty() && part.body?.attachmentId != null) {
            attachments.add(
                com.shrivatsav.monomail.data.model.EmailAttachmentInfo(
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
            val bytes = Base64.decode(data, Base64.URL_SAFE or Base64.NO_WRAP)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }
}
