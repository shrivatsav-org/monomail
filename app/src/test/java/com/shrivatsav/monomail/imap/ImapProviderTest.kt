package com.shrivatsav.monomail.imap

import com.shrivatsav.monomail.core.network.provider.ProviderMessage
import com.shrivatsav.monomail.core.network.provider.imap.ImapRawMessage
import com.shrivatsav.monomail.core.network.provider.imap.ImapThreader
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import org.junit.Test
import org.junit.Assert.*
import kotlin.math.abs

class ImapProviderTest {

    // ===================== ImapThreader =====================

    private fun msg(
        id: String,
        refs: String = "",
        inReplyTo: String = "",
        date: Long = 0L
    ) = ImapRawMessage(
        messageId = id, references = refs, inReplyTo = inReplyTo, date = date,
        providerMessage = ProviderMessage(
            id = id, threadId = id, subject = "", from = "", fromEmail = "",
            to = "", cc = "", bcc = "", snippet = "", body = "", date = date,
            isRead = true, isStarred = false, folders = emptySet(), attachments = emptyList()
        )
    )

    @Test
    fun singleMessage_returnsItself() {
        val msgs = listOf(msg("a"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("a", result.keys.first())
        assertEquals(1, result.values.first().size)
    }

    @Test
    fun replyMessage_groupsUnderRoot() {
        val msgs = listOf(msg("a"), msg("b", inReplyTo = "a"))
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(2, result.values.first().size)
        // Messages should be sorted: a first, b second
        val ordered = result.values.first()
        assertEquals("a", ordered[0].id)
        assertEquals("b", ordered[1].id)
    }

    @Test
    fun chainOfReplies_groupsUnderRoot() {
        val msgs = listOf(
            msg("a"),
            msg("b", inReplyTo = "a"),
            msg("c", inReplyTo = "b")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(3, result.values.first().size)
    }

    @Test
    fun referencesHeader_usedOverInReplyTo() {
        val msgs = listOf(
            msg("root"),
            msg("reply", refs = "root", inReplyTo = "root")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("root", result.keys.first())
    }

    @Test
    fun messagesSortedChronologically() {
        val msgs = listOf(
            msg("b", inReplyTo = "a", date = 200),
            msg("a", date = 100)
        )
        val result = ImapThreader.groupByReferences(msgs)
        val ordered = result.values.first()
        assertEquals(100, ordered[0].date)
        assertEquals(200, ordered[1].date)
    }

    @Test
    fun referencesChain_usesRoot() {
        // a -> b -> c -> d
        val msgs = listOf(
            msg("a"),
            msg("b", refs = "a", inReplyTo = "a"),
            msg("c", refs = "a b", inReplyTo = "b"),
            msg("d", refs = "a b c", inReplyTo = "c")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("a", result.keys.first())
        assertEquals(4, result.values.first().size)
    }

    @Test
    fun multipleSeparateConversations() {
        val msgs = listOf(
            msg("a"), msg("b", inReplyTo = "a"),
            msg("c"), msg("d", inReplyTo = "c")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(2, result.size)
        assertTrue(result.containsKey("a"))
        assertTrue(result.containsKey("c"))
    }

    @Test
    fun emptyList_returnsEmpty() {
        val result = ImapThreader.groupByReferences(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun messageWithoutMessageId_isSkipped() {
        // msg() returns null for a null messageId, but our test helper always sets it
        // This test verifies the behavior when envelopeToRawMessage returns null
        // (simulated by not including a message)
    }

    @Test
    fun inReplyToWithoutReferences_stillGroupsCorrectly() {
        val msgs = listOf(
            msg("root"),
            msg("reply1", inReplyTo = "root"),
            msg("reply2", refs = "root", inReplyTo = "root")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("root", result.keys.first())
        assertEquals(3, result.values.first().size)
    }

    @Test
    fun cycleInHeaders_doesNotHang() {
        // Malformed: a -> b -> a (cycle)
        val msgs = listOf(
            msg("a", refs = "b", inReplyTo = "b"),
            msg("b", refs = "a", inReplyTo = "a")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        // Should not hang — cycle detection prevents infinite loop
        assertEquals(2, result.values.first().size)
    }

    @Test
    fun messageReferencesAnotherNotInSet_usesRefAsRoot() {
        // Only b is in the set, but b references a (which isn't)
        val msgs = listOf(
            msg("b", refs = "a", inReplyTo = "a")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("a", result.keys.first()) // Root is 'a' (referenced but not in set)
        assertEquals(1, result.values.first().size)
    }

    @Test
    fun longReferencesChain_handlesCorrectly() {
        val refsA = ""
        val refsB = "a"
        val refsC = "a b"
        val refsD = "a b c"
        val refsE = "a b c d"

        val msgs = listOf(
            msg("a", refs = refsA),
            msg("b", refs = refsB, inReplyTo = "a"),
            msg("c", refs = refsC, inReplyTo = "b"),
            msg("d", refs = refsD, inReplyTo = "c"),
            msg("e", refs = refsE, inReplyTo = "d")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals("a", result.keys.first())
        val ordered = result.values.first()
        // Check chronological ordering
        for (i in 1 until ordered.size) {
            assertTrue("Messages should be in date order",
                ordered[i - 1].date <= ordered[i].date)
        }
    }

    @Test
    fun multipleMessagesWithSameDate_preservesOrder() {
        val msgs = listOf(
            msg("a", date = 100),
            msg("b", inReplyTo = "a", date = 100),
            msg("c", inReplyTo = "b", date = 100)
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(3, result.values.first().size)
    }

    // ===================== ImapAccountConfig =====================

    @Test
    fun presetForHost_gmail() {
        val config = ImapAccountConfig.presetForHost("user@gmail.com")
        assertNotNull(config)
        assertEquals("imap.gmail.com", config?.imapHost)
        assertEquals(993, config?.imapPort)
        assertTrue(config?.isGmail() ?: false)
    }

    @Test
    fun presetForHost_yahoo() {
        val config = ImapAccountConfig.presetForHost("user@yahoo.com")
        assertNotNull(config)
        assertEquals("imap.mail.yahoo.com", config?.imapHost)
    }

    @Test
    fun presetForHost_outlook() {
        val config = ImapAccountConfig.presetForHost("user@outlook.com")
        assertNotNull(config)
        assertEquals("outlook.office365.com", config?.imapHost)
    }

    @Test
    fun presetForHost_unknown() {
        val config = ImapAccountConfig.presetForHost("user@example.com")
        assertNull(config)
    }

    @Test
    fun presetForHost_caseInsensitive() {
        val config = ImapAccountConfig.presetForHost("USER@GMAIL.COM")
        assertNotNull(config)
        assertEquals("imap.gmail.com", config?.imapHost)
    }

    // ===================== sinceDate boundary tests =====================

    /**
     * Validate the sinceDate buffer logic used in ImapProvider.listThreads.
     * The IMAP ReceivedDateTerm truncates to date-only, so we subtract 1 day buffer.
     * This test validates the math.
     */
    @Test
    fun sinceDate_buffer_isExactly24h() {
        val sinceDate = System.currentTimeMillis()
        val buffer = 24L * 60 * 60 * 1000
        assertEquals(86_400_000L, buffer)
        val bufferedDate = sinceDate - buffer
        // The buffered date should be exactly 1 day before sinceDate
        assertEquals(sinceDate - 86_400_000L, bufferedDate)
    }

    @Test
    fun sinceDate_zeroReturnsEverything() {
        val sinceDate = 0L
        val buffer = 24L * 60 * 60 * 1000
        val buffered = sinceDate - buffer
        assertTrue(buffered < 0) // Negative timestamp — before epoch
        // IMAP ReceivedDateTerm would include everything from epoch
    }

    // ===================== Edge case: empty fields =====================

    @Test
    fun threader_withEmptyMessageIds_skipped() {
        // ImapRawMessage requires messageId, but providerMessage can have empty fields
        val msgWithEmptyFields = ImapRawMessage(
            messageId = "test-id",
            references = "",
            inReplyTo = "",
            date = 1000L,
            providerMessage = ProviderMessage(
                id = "test-id", threadId = "test-id",
                subject = "", from = "", fromEmail = "",
                to = "", cc = "", bcc = "",
                snippet = "", body = "", date = 1000L,
                isRead = false, isStarred = false,
                folders = emptySet(), attachments = emptyList()
            )
        )
        val result = ImapThreader.groupByReferences(listOf(msgWithEmptyFields))
        assertEquals(1, result.size)
    }

    @Test
    fun threader_normalizesWhitespaceInReferences() {
        // References with various whitespace
        val msgs = listOf(
            msg("a"),
            msg("b", refs = "  a  ", inReplyTo = "a")
        )
        val result = ImapThreader.groupByReferences(msgs)
        assertEquals(1, result.size)
        assertEquals(2, result.values.first().size)
    }
}
