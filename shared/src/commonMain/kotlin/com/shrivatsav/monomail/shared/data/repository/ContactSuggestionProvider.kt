package com.shrivatsav.monomail.shared.data.repository

import com.shrivatsav.monomail.shared.data.model.Email
import com.shrivatsav.monomail.shared.data.model.EmailThread

class ContactSuggestionProvider {
    data class EmailContact(
        val name: String,
        val email: String
    )

    private val contacts = mutableSetOf<EmailContact>()

    fun indexFromThreads(threads: List<EmailThread>) {
        threads.forEach { thread ->
            contacts.add(EmailContact(thread.from, thread.fromEmail))
            thread.participants.forEach { name ->
                contacts.add(EmailContact(name, name))
            }
        }
    }

    fun indexFromEmails(emails: List<Email>) {
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
