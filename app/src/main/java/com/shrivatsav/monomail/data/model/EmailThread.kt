package com.shrivatsav.monomail.data.model
data class EmailThread(
    val threadId: String,
    val subject: String,
    val from: String,              
    val fromEmail: String,         
    val snippet: String,           
    val date: Long,                
    val messageCount: Int,
    val isRead: Boolean,           
    val isStarred: Boolean,
    val latestMessageId: String,
    val participants: List<String> 
)
