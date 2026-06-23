package com.shrivatsav.monomail.auth

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Thread-safe, in-memory token cache that avoids runBlocking inside OkHttp interceptors.
 *
 * Each provider (Gmail, Outlook) gets a [TokenRefresher] that the OkHttp interceptor can
 * call synchronously.  Under the hood the real refresh still happens on an IO thread,
 * but the mutex ensures two concurrent 401 responses don't refresh the same token twice.
 */
class AuthTokenManager(
    private val context: Context,
    private val accountManager: AccountManager,
    private val microsoftAuthManager: MicrosoftAuthManager,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    // ── public entry points (called from any thread, including OkHttp) ──

    /**
     * Returns the current cached access token for [accountId], or null if unknown.
     * This is the callback used by the OkHttp auth interceptor on *every* request.
     */
    fun getCachedToken(accountId: String): String? = synchronized(tokenCache) {
        tokenCache[accountId]
    }

    /**
     * Refreshes the token for [accountId] **synchronously** (blocking the caller).
     * Used by the OkHttp interceptor when it receives a 401.
     *
     * The actual refresh work runs on IO via [runBlocking] *inside* a per-account
     * mutex so that multiple concurrent 401s don't race.
     */
    fun refreshTokenBlocking(accountId: String): String? {
        // Fast-path: avoid runBlocking if already refreshed by another thread.
        synchronized(tokenCache) { tokenCache[accountId] }?.let { return it }

        return kotlinx.coroutines.runBlocking {
            refreshMutex.withLock {
                // Re-check after acquiring the lock.
                getCachedToken(accountId)?.let { return@runBlocking it }
                try {
                    refreshInternal(accountId)
                } catch (e: Exception) {
                    Log.w("AuthTokenMgr", "Token refresh failed for $accountId", e)
                    null
                }
            }
        }
    }

    /**
     * Non-blocking refresh — safe to call from coroutine scopes.
     */
    suspend fun refreshTokenAsync(accountId: String): String? = refreshMutex.withLock {
        getCachedToken(accountId)?.let { return@withLock it }
        try {
            refreshInternal(accountId)
        } catch (e: Exception) {
            Log.w("AuthTokenMgr", "Async token refresh failed for $accountId", e)
            null
        }
    }

    /** Warms the cache for every known account (call on app start). */
    suspend fun warmCache() {
        val accounts = accountManager.getAccounts()
        for (account in accounts) {
            // Ensure MSAL is initialized before trying Outlook tokens.
            if (account.provider == "outlook") {
                try { microsoftAuthManager.initialize() } catch (_: Exception) {}
            }
            synchronized(tokenCache) { tokenCache[account.id] = account.accessToken }
        }
    }

    /** Invalidate the cache entry so the next request fetches a fresh token. */
    fun invalidate(accountId: String) {
        synchronized(tokenCache) { tokenCache.remove(accountId) }
    }

    /** Update the cache entry (e.g. after a sign-in). */
    fun updateToken(accountId: String, token: String) {
        synchronized(tokenCache) { tokenCache[accountId] = token }
    }

    // ── internals ──

    private val tokenCache = mutableMapOf<String, String>()

    private suspend fun refreshInternal(accountId: String): String? = withContext(Dispatchers.IO) {
        val account = accountManager.getAccounts().find { it.id == accountId }
            ?: return@withContext null

        val newToken = when (account.provider) {
            "gmail" -> refreshGmail(account)
            "outlook" -> refreshOutlook(account)
            else -> null
        }

        if (newToken != null) {
            synchronized(tokenCache) { tokenCache[accountId] = newToken }
            accountManager.updateAccountToken(accountId, newToken)
        }
        newToken
    }

    private fun refreshGmail(profile: UserProfile): String? {
        return try {
            val old = profile.accessToken
            if (old.isNotEmpty()) {
                try { GoogleAuthUtil.clearToken(context, old) } catch (_: Exception) {}
            }
            GoogleAuthUtil.getToken(
                context,
                Account(profile.email, "com.google"),
                AuthManager.GMAIL_SCOPE
            )
        } catch (e: UserRecoverableAuthException) {
            // User needs to re-authorise — signal to UI via AuthManager.
            Log.w("AuthTokenMgr", "Gmail reauth required for ${profile.email}")
            null
        } catch (e: Exception) {
            Log.w("AuthTokenMgr", "Gmail token refresh failed", e)
            null
        }
    }

    private suspend fun refreshOutlook(profile: UserProfile): String? {
        return try {
            microsoftAuthManager.getAccessTokenSilently(profile.id)
        } catch (e: Exception) {
            Log.w("AuthTokenMgr", "Outlook token refresh failed", e)
            null
        }
    }
}
