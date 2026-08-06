package com.shrivatsav.monomail.core.network.provider

import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide cache of created [EmailProvider] instances keyed by account id.
 *
 * Providers hold their account's config/token at construction time, so any
 * change (token refresh, IMAP/SMTP config edit) must invalidate the entry —
 * the next access then rebuilds with fresh settings.
 */
object ProviderCache {
    private val providers = ConcurrentHashMap<String, EmailProvider>()

    fun getOrCreate(accountId: String, create: () -> EmailProvider): EmailProvider =
        providers.getOrPut(accountId) { create() }

    fun invalidate(accountId: String) {
        providers.remove(accountId)
    }
}
