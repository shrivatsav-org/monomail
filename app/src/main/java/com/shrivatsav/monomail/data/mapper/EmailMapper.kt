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

    private const val MIME_TEXT_HTML = "text/html"
    private const val MIME_TEXT_PLAIN = "text/plain"

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
        val attachments = extractAttachments(payload, id)
        val attachmentCids = extractFilenamedImageCids(payload)
        val bodyInfo = extractAndInjectImages(payload, attachmentCids)
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
            attachments = attachments
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
            if (part.mimeType == MIME_TEXT_HTML) return BodyResult(decoded, isHtml = true)
            if (part.mimeType == MIME_TEXT_PLAIN) return BodyResult(decoded, isHtml = false)
        }
        val children = part.parts.orEmpty()
        findChildWithData(children, MIME_TEXT_HTML)?.let { return it }
        findChildWithData(children, MIME_TEXT_PLAIN)?.let { return it }
        for (child in children) {
            val result = extractBody(child)
            if (result.text.isNotEmpty()) return result
        }
        return BodyResult("", isHtml = true)
    }

    private fun findChildWithData(children: List<MessagePart>, mimeType: String): BodyResult? {
        for (child in children) {
            if (child.mimeType == mimeType && child.body?.data != null) {
                return BodyResult(decodeBase64Url(child.body.data), isHtml = mimeType == MIME_TEXT_HTML)
            }
        }
        return null
    }
    private fun extractFilenamedImageCids(part: MessagePart?): Set<String> {
        if (part == null) return emptySet()
        val cids = mutableSetOf<String>()
        val contentId = part.headers?.firstOrNull { it.name.equals("Content-ID", true) }?.value?.removeSurrounding("<", ">")
        if (contentId != null && !part.filename.isNullOrEmpty()) {
            cids.add(contentId)
        }
        part.parts?.forEach { cids.addAll(extractFilenamedImageCids(it)) }
        return cids
    }
    private fun extractAndInjectImages(payload: MessagePart?, attachmentCids: Set<String> = emptySet()): BodyResult {
        val bodyInfo = extractBody(payload)
        val cidMap = mutableMapOf<String, String>()
        val attachmentCidList = mutableListOf<Pair<String, String>>()
        extractInlineImages(payload, cidMap, attachmentCidList, attachmentCids)
        var htmlBody = bodyInfo.text
        cidMap.forEach { (cid, dataUri) ->
            htmlBody = htmlBody.replace("cid:$cid", dataUri)
        }
        attachmentCidList.forEach { (cid, filename) ->
            htmlBody = htmlBody.replace(
                "cid:$cid",
                """<div style="padding:8px;margin:8px 0;background:#f0f0f0;border-radius:6px;text-align:center;font-family:sans-serif;font-size:13px;color:#666;">📎 <em>$filename</em> (inline image — see attachments)</div>"""
            )
        }
        return BodyResult(htmlBody, bodyInfo.isHtml)
    }
    private fun extractInlineImages(
        part: MessagePart?,
        map: MutableMap<String, String>,
        attachmentCids: MutableList<Pair<String, String>> = mutableListOf(),
        skipCids: Set<String> = emptySet()
    ) {
        if (part == null) return
        val contentId = part.headers?.firstOrNull { it.name.equals("Content-ID", true) }?.value?.removeSurrounding("<", ">")
        val bodyData = part.body?.data
        if (contentId != null && part.mimeType?.startsWith("image/") == true && !bodyData.isNullOrEmpty()) {
            if (contentId in skipCids) {
                attachmentCids.add(contentId to (part.filename ?: "inline image"))
            } else {
                val base64 = bodyData.replace("-", "+").replace("_", "/")
                map[contentId] = "data:${part.mimeType};base64,$base64"
            }
        }
        part.parts?.forEach { extractInlineImages(it, map, attachmentCids, skipCids) }
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
