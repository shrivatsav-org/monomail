package com.shrivatsav.monomail

data class SentEmailEvent(
    val threadId: String,
    val to: String,
    val subject: String,
    val isPendingSend: Boolean = false
)

data class ScheduledEmailEvent(
    val to: String,
    val subject: String,
    val scheduledAt: Long
)
