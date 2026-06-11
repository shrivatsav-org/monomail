package com.shrivatsav.monomail.data.model

/**
 * Domain model for a thread in the inbox.
 * Groups multiple messages into a single conversation entry.
 */
data class EmailThread(
    val threadId: String,
    val subject: String,
    val from: String,              // latest sender display name
    val fromEmail: String,         // latest sender email
    val snippet: String,           // latest snippet
    val date: Long,                // latest message date (epoch millis)
    val messageCount: Int,
    val isRead: Boolean,           // true only if ALL messages are read
    val isStarred: Boolean,
    val latestMessageId: String,
    val participants: List<String> // unique sender display names
)
