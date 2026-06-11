package com.shrivatsav.monomail.data.repository

/**
 * Provides email address suggestions from cached inbox senders.
 * No extra permissions needed — uses addresses already fetched.
 */
class ContactSuggestionProvider {

    data class EmailContact(
        val name: String,
        val email: String
    )

    private val contacts = mutableSetOf<EmailContact>()

    /** Index sender addresses from a list of threads or emails. */
    fun indexFromThreads(threads: List<com.shrivatsav.monomail.data.model.EmailThread>) {
        threads.forEach { thread ->
            contacts.add(EmailContact(thread.from, thread.fromEmail))
            thread.participants.forEach { name ->
                // We only have display names from participants; store as-is
                contacts.add(EmailContact(name, name))
            }
        }
    }

    fun indexFromEmails(emails: List<com.shrivatsav.monomail.data.model.Email>) {
        emails.forEach { email ->
            contacts.add(EmailContact(email.from, email.fromEmail))
        }
    }

    /** Return contacts matching the query by name or email prefix. */
    fun suggest(query: String): List<EmailContact> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()
        return contacts
            .filter { it.name.lowercase().contains(q) || it.email.lowercase().contains(q) }
            .distinctBy { it.email.lowercase() }
            .sortedBy { it.name }
            .take(5)
    }
}
