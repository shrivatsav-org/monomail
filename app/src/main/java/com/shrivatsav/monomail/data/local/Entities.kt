package com.shrivatsav.monomail.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailThread

@Entity(tableName = "threads")
data class ThreadEntity(
    @PrimaryKey val threadId: String,
    val subject: String,
    val fromName: String,
    val fromEmail: String,
    val snippet: String,
    val date: Long,
    val messageCount: Int,
    val isRead: Boolean,
    val isStarred: Boolean,
    val latestMessageId: String,
    val participants: List<String>,
    // These boolean flags represent which tab the thread belongs to
    val inInbox: Boolean,
    val inSent: Boolean,
    val inArchived: Boolean,
    val inTrash: Boolean
) {
    fun toDomainModel() = EmailThread(
        threadId = threadId,
        subject = subject,
        from = fromName,
        fromEmail = fromEmail,
        snippet = snippet,
        date = date,
        messageCount = messageCount,
        isRead = isRead,
        isStarred = isStarred,
        latestMessageId = latestMessageId,
        participants = participants
    )
}

@Entity(tableName = "emails")
data class EmailEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val subject: String,
    val fromName: String,
    val fromEmail: String,
    val toEmail: String,
    val snippet: String,
    val body: String,
    val date: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val labels: List<String>,
    val attachmentsJson: String = "[]"
) {
    fun toDomainModel() = Email(
        id = id,
        threadId = threadId,
        subject = subject,
        from = fromName,
        fromEmail = fromEmail,
        to = toEmail,
        snippet = snippet,
        body = body,
        date = date,
        isRead = isRead,
        isStarred = isStarred,
        labels = labels,
        attachments = try {
            com.google.gson.Gson().fromJson(attachmentsJson, Array<com.shrivatsav.monomail.data.model.EmailAttachmentInfo>::class.java)?.toList() ?: emptyList()
        } catch(e: Exception) { emptyList() }
    )
}

fun EmailThread.toEntity(
    inInbox: Boolean = false,
    inSent: Boolean = false,
    inArchived: Boolean = false,
    inTrash: Boolean = false
) = ThreadEntity(
    threadId = threadId,
    subject = subject,
    fromName = from,
    fromEmail = fromEmail,
    snippet = snippet,
    date = date,
    messageCount = messageCount,
    isRead = isRead,
    isStarred = isStarred,
    latestMessageId = latestMessageId,
    participants = participants,
    inInbox = inInbox,
    inSent = inSent,
    inArchived = inArchived,
    inTrash = inTrash
)

fun Email.toEntity() = EmailEntity(
    id = id,
    threadId = threadId,
    subject = subject,
    fromName = from,
    fromEmail = fromEmail,
    toEmail = to,
    snippet = snippet,
    body = body,
    date = date,
    isRead = isRead,
    isStarred = isStarred,
    labels = labels,
    attachmentsJson = com.google.gson.Gson().toJson(attachments)
)
