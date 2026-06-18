package com.shrivatsav.monomail.shared.platform

/**
 * Platform browser redirect for the OAuth authorization step. The ONLY part of
 * auth that must be platform-specific:
 *  - iOS  -> ASWebAuthenticationSession
 *  - Android -> Chrome Custom Tabs / browser intent
 *
 * [authorize] opens [authUrl], waits for the system to redirect back to a URL
 * beginning with the app's [callbackScheme], and returns that full callback URL
 * (which carries the authorization `code` and `state` query params).
 */
expect class OAuthBrowser {
    suspend fun authorize(authUrl: String, callbackScheme: String): String
}
