package com.shrivatsav.monomail.shared.auth

enum class MailProvider { GMAIL, OUTLOOK }

/**
 * Static OAuth endpoint/scope config per provider. Client IDs and the redirect URI
 * are supplied at runtime via [OAuthClientConfig] so no secrets live in source.
 */
data class OAuthProviderConfig(
    val authEndpoint: String,
    val tokenEndpoint: String,
    val scopes: List<String>,
    val profileEndpoint: String
) {
    companion object {
        val GMAIL = OAuthProviderConfig(
            authEndpoint = "https://accounts.google.com/o/oauth2/v2/auth",
            tokenEndpoint = "https://oauth2.googleapis.com/token",
            scopes = listOf(
                "https://www.googleapis.com/auth/gmail.modify",
                "openid", "email", "profile"
            ),
            profileEndpoint = "https://openidconnect.googleapis.com/v1/userinfo"
        )

        val OUTLOOK = OAuthProviderConfig(
            authEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
            tokenEndpoint = "https://login.microsoftonline.com/common/oauth2/v2.0/token",
            scopes = listOf(
                "https://graph.microsoft.com/Mail.ReadWrite",
                "https://graph.microsoft.com/Mail.Send",
                "offline_access", "openid", "email", "profile"
            ),
            profileEndpoint = "https://graph.microsoft.com/v1.0/me"
        )

        fun forProvider(provider: MailProvider): OAuthProviderConfig = when (provider) {
            MailProvider.GMAIL -> GMAIL
            MailProvider.OUTLOOK -> OUTLOOK
        }
    }
}

/**
 * Runtime credentials supplied by the host app (Android / iOS).
 * - [gmailClientId] / [outlookClientId]: native/public OAuth client IDs (PKCE, no secret).
 * - [redirectUri]: custom-scheme redirect, e.g. "com.shrivatsav.monomail://oauth2redirect".
 * - [callbackScheme]: the scheme portion used by the platform browser session, e.g. "com.shrivatsav.monomail".
 */
data class OAuthClientConfig(
    val gmailClientId: String,
    val outlookClientId: String,
    val redirectUri: String,
    val callbackScheme: String
) {
    fun clientIdFor(provider: MailProvider): String = when (provider) {
        MailProvider.GMAIL -> gmailClientId
        MailProvider.OUTLOOK -> outlookClientId
    }
}
