package com.shrivatsav.monomail.data.provider.imap

import com.shrivatsav.monomail.data.provider.ProviderMessage

internal data class ImapRawMessage(
    val messageId: String,
    val references: String,
    val inReplyTo: String,
    val date: Long,
    val providerMessage: ProviderMessage
)

object ImapThreader {
    /**
     * Groups raw IMAP messages into threads based on Message-ID, In-Reply-To, and References headers.
     * Returns a map of Thread ID -> List of ProviderMessage (sorted chronologically).
     */
    internal fun groupByReferences(messages: List<ImapRawMessage>): Map<String, List<ProviderMessage>> {
        val rootMap = mutableMapOf<String, String>() // Maps a Message-ID to its Thread's Root Message-ID

        // 1. First pass: establish parent-child relationships
        for (msg in messages) {
            val msgId = msg.messageId
            val refs = msg.references.split(Regex("\\s+")).filter { it.isNotBlank() }
            val inReplyTo = msg.inReplyTo.takeIf { it.isNotBlank() }

            // The root of this message's thread is the first valid reference, or itself if none.
            // If References is empty but In-Reply-To exists, use In-Reply-To as the parent.
            var parentId: String? = null
            if (refs.isNotEmpty()) {
                parentId = refs.first()
            } else if (inReplyTo != null) {
                parentId = inReplyTo
            }

            if (parentId != null) {
                // Link this message to the parent's root (or the parent itself if not seen yet)
                rootMap[msgId] = findRoot(parentId, rootMap)
            } else {
                rootMap[msgId] = msgId
            }
        }

        // 2. Second pass: flatten all relationships so every msg points directly to the absolute root
        val finalizedRoots = mutableMapOf<String, String>()
        for (msgId in rootMap.keys) {
            finalizedRoots[msgId] = findRoot(msgId, rootMap)
        }

        // 3. Group messages by their finalized root ID
        val threads = mutableMapOf<String, MutableList<ImapRawMessage>>()
        for (msg in messages) {
            val rootId = finalizedRoots[msg.messageId] ?: msg.messageId
            threads.getOrPut(rootId) { mutableListOf() }.add(msg)
        }

        // 4. Sort each thread chronologically and map back to ProviderMessage
        return threads.mapValues { (_, threadMsgs) ->
            threadMsgs.sortedBy { it.date }.map { it.providerMessage }
        }
    }

    private fun findRoot(msgId: String, rootMap: Map<String, String>): String {
        var current = msgId
        val visited = mutableSetOf<String>()
        while (rootMap.containsKey(current) && rootMap[current] != current) {
            if (!visited.add(current)) {
                // Cycle detected (malformed headers), break out
                break
            }
            current = rootMap[current]!!
        }
        return current
    }
}
