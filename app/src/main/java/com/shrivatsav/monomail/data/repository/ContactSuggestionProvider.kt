package com.shrivatsav.monomail.data.repository
class ContactSuggestionProvider {
    data class EmailContact(
        val name: String,
        val email: String
    )
    private val contacts = mutableSetOf<EmailContact>()
    fun indexFromThreads(threads: List<com.shrivatsav.monomail.data.model.EmailThread>) {
        threads.forEach { thread ->
            contacts.add(EmailContact(thread.from, thread.fromEmail))
            // Participants list only contains names, not email addresses.
            // Skip them to avoid using display names as email addresses.
        }
    }
    fun indexFromEmails(emails: List<com.shrivatsav.monomail.data.model.Email>) {
        emails.forEach { email ->
            contacts.add(EmailContact(email.from, email.fromEmail))
        }
    }
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
