package com.shrivatsav.monomail.core.data.repository

data class EmailContact(
    val name: String,
    val email: String
)

private val contactCache = mutableSetOf<EmailContact>()

fun suggestContacts(query: String): List<EmailContact> {
    if (query.isBlank()) return emptyList()
    val q = query.trim().lowercase()
    return contactCache
        .filter { it.name.lowercase().contains(q) || it.email.lowercase().contains(q) }
        .distinctBy { it.email.lowercase() }
        .sortedBy { it.name }
        .take(5)
}
