package com.shrivatsav.monomail.core.data.repository

import com.shrivatsav.monomail.core.database.local.EmailEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EmailContentMergeTest {
    @Test
    fun emptyIncomingContentPreservesStoredDetailContent() {
        val stored = email(body = "<p>Full body</p>", bodyIsHtml = true, snippet = "Stored snippet", attachments = "[{\"id\":\"a\"}]")
        val incoming = email(body = "", bodyIsHtml = false, snippet = " ", attachments = "[]")

        val merged = mergeStoredEmailContent(incoming, stored)

        assertEquals(stored.body, merged.body)
        assertEquals(stored.bodyIsHtml, merged.bodyIsHtml)
        assertEquals(stored.snippet, merged.snippet)
        assertEquals(stored.attachmentsJson, merged.attachmentsJson)
    }

    @Test
    fun nonEmptyIncomingContentIsRetained() {
        val stored = email(body = "old", bodyIsHtml = false, snippet = "old", attachments = "[{\"id\":\"old\"}]")
        val incoming = email(body = "<p>new</p>", bodyIsHtml = true, snippet = "new", attachments = "[{\"id\":\"new\"}]")

        assertEquals(incoming, mergeStoredEmailContent(incoming, stored))
    }

    private fun email(
        body: String,
        bodyIsHtml: Boolean,
        snippet: String,
        attachments: String
    ) = EmailEntity(
        id = "message",
        accountId = "account",
        threadId = "thread",
        subject = "Subject",
        fromName = "Sender",
        fromEmail = "sender@example.com",
        toEmail = "recipient@example.com",
        snippet = snippet,
        body = body,
        bodyIsHtml = bodyIsHtml,
        date = 1L,
        isRead = false,
        isStarred = false,
        labels = listOf("INBOX"),
        attachmentsJson = attachments
    )
}
