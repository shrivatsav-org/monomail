package com.shrivatsav.monomail

import com.shrivatsav.monomail.core.network.provider.ProviderMessage
import com.shrivatsav.monomail.core.network.provider.imap.ImapRawMessage
import com.shrivatsav.monomail.core.network.provider.imap.ImapThreader
import org.junit.Test
import org.junit.Assert.*

class ImapThreaderTest {
    private fun msg(id: String, refs: String = "", inReplyTo: String = "", date: Long = 0L) = ImapRawMessage(
        messageId = id, references = refs, inReplyTo = inReplyTo, date = date,
        providerMessage = ProviderMessage(id = id, threadId = id, subject = "", from = "", fromEmail = "",
            to = "", cc = "", bcc = "", snippet = "", body = "", date = date, isRead = true, isStarred = false,
            folders = emptySet(), attachments = emptyList())
    )

    @Test
    fun singleMessage_returnsItself() {
        val msgs = listOf(msg("a"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("a", result.keys.first())
    }

    @Test
    fun replyMessage_groupsUnderRoot() {
        val msgs = listOf(msg("a"), msg("b", inReplyTo = "a"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(2, result.values.first().size)
    }

    @Test
    fun chainOfReplies_groupsUnderRoot() {
        val msgs = listOf(msg("a"), msg("b", inReplyTo = "a"), msg("c", inReplyTo = "b"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(3, result.values.first().size)
    }

    @Test
    fun referencesHeader_usedOverInReplyTo() {
        val msgs = listOf(msg("a"), msg("b", refs = "a", inReplyTo = "a"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
    }

    @Test
    fun messagesSortedChronologically() {
        val msgs = listOf(msg("b", inReplyTo = "a", date = 200), msg("a", date = 100))
        val result = ImapThreader.groupByReferences(msgs)
        val ordered = result.values.first()
        assertEquals(100, ordered[0].date)
        assertEquals(200, ordered[1].date)
    }
}
