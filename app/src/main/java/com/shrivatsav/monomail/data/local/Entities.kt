package com.shrivatsav.monomail.data.local
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import com.google.gson.Gson
@Entity(
    tableName = "threads",
    primaryKeys = ["accountId", "threadId"],
    indices = [
        Index(value = ["accountId", "inInbox", "date"]),
        Index(value = ["accountId", "inSent", "date"]),
        Index(value = ["accountId", "inArchived", "date"]),
        Index(value = ["accountId", "isStarred", "date"]),
        Index(value = ["accountId", "inTrash", "date"]),
        Index(value = ["accountId", "inSpam", "date"]),
        Index(value = ["accountId", "isSnoozed", "snoozedUntil"]),
        Index(value = ["isSnoozed", "snoozedUntil"])
    ]
)
data class ThreadEntity(
    val threadId: String,
    val accountId: String,
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
    val inInbox: Boolean,
    val inSent: Boolean,
    val inArchived: Boolean,
    val inTrash: Boolean,
    val isSnoozed: Boolean = false,
    val snoozedUntil: Long = 0L,
    val inSpam: Boolean = false
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
@Entity(
    tableName = "emails",
    indices = [
        Index(value = ["accountId", "threadId"]),
        Index(value = ["accountId", "inInbox", "date"]),
        Index(value = ["accountId", "inSent", "date"]),
        Index(value = ["accountId", "inArchived", "date"]),
        Index(value = ["accountId", "isStarred", "date"]),
        Index(value = ["accountId", "inTrash", "date"]),
        Index(value = ["accountId", "inSpam", "date"]),
        Index(value = ["accountId", "isSnoozed", "snoozedUntil"]),
        Index(value = ["isSnoozed", "snoozedUntil"]),
        Index(value = ["threadId", "accountId"])
    ]
)
data class EmailEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val threadId: String,
    val subject: String,
    val fromName: String,
    val fromEmail: String,
    val toEmail: String,
    val ccEmail: String = "",
    val bccEmail: String = "",
    val snippet: String,
    val body: String,
    val bodyIsHtml: Boolean = true,
    val date: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val labels: List<String>,
    val attachmentsJson: String = "[]",
    val inInbox: Boolean = false,
    val inSent: Boolean = false,
    val inArchived: Boolean = false,
    val inTrash: Boolean = false,
    val isSnoozed: Boolean = false,
    val snoozedUntil: Long = 0L,
    val inSpam: Boolean = false
) {
    fun toDomainModel() = Email(
        id = id,
        threadId = threadId,
        subject = subject,
        from = fromName,
        fromEmail = fromEmail,
        to = toEmail,
        cc = ccEmail,
        bcc = bccEmail,
        snippet = snippet,
        body = body,
        bodyIsHtml = bodyIsHtml,
        date = date,
        isRead = isRead,
        isStarred = isStarred,
        labels = labels,
        attachments = try {
            gson.fromJson(attachmentsJson, Array<com.shrivatsav.monomail.data.model.EmailAttachmentInfo>::class.java)?.toList() ?: emptyList()
        } catch(e: Exception) { emptyList() }
    )
}
fun EmailThread.toEntity(
    accountId: String,
    inInbox: Boolean = false,
    inSent: Boolean = false,
    inArchived: Boolean = false,
    inTrash: Boolean = false,
    isSnoozed: Boolean = false,
    snoozedUntil: Long = 0L,
    inSpam: Boolean = false
) = ThreadEntity(
    threadId = threadId,
    accountId = accountId,
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
    inTrash = inTrash,
    isSnoozed = isSnoozed,
    snoozedUntil = snoozedUntil,
    inSpam = inSpam
)
@Entity(tableName = "scheduled_messages")
data class ScheduledMessageEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val fromEmail: String,
    val to: String,
    val cc: String = "",
    val bcc: String = "",
    val subject: String,
    val body: String,
    val attachmentsJson: String = "[]",
    val scheduledAt: Long,
    val isSent: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val fromAlias: String? = null
)

enum class PendingActionType {
    TOGGLE_STAR, MARK_READ, MARK_UNREAD, ARCHIVE, UNARCHIVE, DELETE, RESTORE, SNOOZE, UNSNOOZE
}

enum class PendingActionStatus {
    PENDING, IN_FLIGHT, FAILED
}

@Entity(tableName = "pending_actions", indices = [Index(value = ["status"])])
data class PendingActionEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val actionType: PendingActionType,
    val threadId: String,
    val payload: String = "",
    val emailIdsJson: String = "",
    val status: PendingActionStatus = PendingActionStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val errorMessage: String = ""
)

private val gson = Gson()
fun Email.toEntity(accountId: String) = EmailEntity(
    id = id,
    accountId = accountId,
    threadId = threadId,
    subject = subject,
    fromName = from,
    fromEmail = fromEmail,
    toEmail = to,
    ccEmail = cc,
    bccEmail = bcc,
    snippet = snippet,
    body = body,
    bodyIsHtml = bodyIsHtml,
    date = date,
    isRead = isRead,
    isStarred = isStarred,
    labels = labels,
    attachmentsJson = gson.toJson(attachments),
    inInbox = labels.contains("INBOX"),
    inSent = labels.contains("SENT"),
    inArchived = !labels.contains("INBOX") && !labels.contains("TRASH") && !labels.contains("SENT") && !labels.contains("SPAM"),
    inTrash = labels.contains("TRASH"),
    inSpam = labels.contains("SPAM")
)
