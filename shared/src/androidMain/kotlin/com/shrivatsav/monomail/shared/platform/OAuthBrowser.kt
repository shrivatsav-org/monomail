package com.shrivatsav.monomail.shared.platform

/**
 * Android OAuth redirect. TODO: integrate Chrome Custom Tabs launched from the
 * host Activity, capturing the redirect via a custom-scheme intent filter.
 * Stubbed for now (Android refactor onto shared is a later milestone).
 */
actual class OAuthBrowser {
    actual suspend fun authorize(authUrl: String, callbackScheme: String): String {
        throw NotImplementedError("Android OAuthBrowser pending Custom Tabs integration")
    }
}
