package com.shrivatsav.monomail.shared.auth

import com.shrivatsav.monomail.shared.platform.OAuthBrowser

/**
 * Cross-platform auth orchestration: drives the OAuth2 + PKCE flow, fetches the
 * user profile, and persists the account. The only platform-specific dependency
 * is [OAuthBrowser] (the redirect step). Replaces the Android MSAL + Google
 * Credentials Manager flows.
 */
class AuthManager(
    private val accountManager: AccountManager,
    private val browser: OAuthBrowser,
    private val clientConfig: OAuthClientConfig,
    private val oauth: OAuthClient = OAuthClient()
) {
    suspend fun signIn(provider: MailProvider): Result<UserProfile> {
        return try {
            val pkce = Pkce.generate()
            val authUrl = oauth.buildAuthUrl(provider, clientConfig, pkce)
            val callback = browser.authorize(authUrl, clientConfig.callbackScheme)
            val code = oauth.parseCallback(callback, pkce.state)
            val tokens = oauth.exchangeCode(provider, clientConfig, code, pkce.verifier)
            val info = oauth.fetchProfile(provider, tokens.accessToken)
            val providerName = if (provider == MailProvider.GMAIL) "gmail" else "outlook"
            val profile = UserProfile(
                id = "${providerName}_${info.email}",
                displayName = info.displayName,
                email = info.email,
                photoUrl = info.photoUrl,
                accessToken = tokens.accessToken,
                provider = providerName,
                refreshToken = tokens.refreshToken ?: ""
            )
            accountManager.addAccount(profile)
            accountManager.setActiveAccountId(profile.id)
            Result.success(profile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Refresh the access token for an account, persisting and returning the new token. */
    suspend fun refreshAccessToken(account: UserProfile): String? {
        if (account.refreshToken.isEmpty()) return account.accessToken.ifEmpty { null }
        val provider = if (account.provider == "gmail") MailProvider.GMAIL else MailProvider.OUTLOOK
        return try {
            val tokens = oauth.refresh(provider, clientConfig, account.refreshToken)
            accountManager.updateAccountToken(account.id, tokens.accessToken)
            tokens.accessToken
        } catch (e: Exception) {
            null
        }
    }

    fun signOut(accountId: String) = accountManager.removeAccount(accountId)

    fun signOutAll() = accountManager.clearAll()
}
