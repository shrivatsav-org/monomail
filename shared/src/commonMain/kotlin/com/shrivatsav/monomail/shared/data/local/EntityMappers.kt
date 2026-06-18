package com.shrivatsav.monomail.shared.data.local

import com.shrivatsav.monomail.shared.data.model.Email
import com.shrivatsav.monomail.shared.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.shared.data.model.EmailThread
import com.shrivatsav.monomail.shared.database.EmailEntity
import com.shrivatsav.monomail.shared.database.ThreadEntity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val entityJson = Json { ignoreUnknownKeys = true }

fun ThreadEntity.toDomain(): EmailThread = EmailThread(
    threadId = threadId,
    subject = subject,
    from = fromName,
    fromEmail = fromEmail,
    snippet = snippet,
    date = date,
    messageCount = messageCount.toInt(),
    isRead = isRead == 1L,
    isStarred = isStarred == 1L,
    latestMessageId = latestMessageId,
    participants = decodeStringList(participants)
)

fun EmailEntity.toDomain(): Email = Email(
    id = id,
    threadId = threadId,
    subject = subject,
    from = fromName,
    fromEmail = fromEmail,
    to = toEmail,
    snippet = snippet,
    body = body,
    date = date,
    isRead = isRead == 1L,
    isStarred = isStarred == 1L,
    labels = decodeStringList(labels),
    attachments = decodeAttachments(attachmentsJson)
)

fun encodeStringList(list: List<String>): String = entityJson.encodeToString(list)

fun encodeAttachments(list: List<EmailAttachmentInfo>): String = entityJson.encodeToString(list)

private fun decodeStringList(raw: String): List<String> =
    try { entityJson.decodeFromString(raw) } catch (e: Exception) { emptyList() }

private fun decodeAttachments(raw: String): List<EmailAttachmentInfo> =
    try { entityJson.decodeFromString(raw) } catch (e: Exception) { emptyList() }
