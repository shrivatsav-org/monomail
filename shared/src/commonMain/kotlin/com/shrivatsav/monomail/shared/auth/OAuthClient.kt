package com.shrivatsav.monomail.shared.auth

import com.shrivatsav.monomail.shared.data.remote.createJsonHttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * Stateless OAuth2 Authorization-Code + PKCE helper. Token endpoints are called
 * with a short-lived no-auth client; the profile endpoint with a bearer client.
 */
class OAuthClient {

    fun buildAuthUrl(
        provider: MailProvider,
        config: OAuthClientConfig,
        pkce: PkcePair
    ): String {
        val p = OAuthProviderConfig.forProvider(provider)
        return URLBuilder(p.authEndpoint).apply {
            parameters.append("client_id", config.clientIdFor(provider))
            parameters.append("redirect_uri", config.redirectUri)
            parameters.append("response_type", "code")
            parameters.append("scope", p.scopes.joinToString(" "))
            parameters.append("code_challenge", pkce.challenge)
            parameters.append("code_challenge_method", pkce.challengeMethod)
            parameters.append("state", pkce.state)
            if (provider == MailProvider.GMAIL) {
                parameters.append("access_type", "offline")
                parameters.append("prompt", "consent")
            }
        }.buildString()
    }

    /** Returns the authorization code from a redirect callback URL, validating state. */
    fun parseCallback(callbackUrl: String, expectedState: String): String {
        val url = Url(callbackUrl)
        url.parameters["error"]?.let { throw IllegalStateException("OAuth error: $it") }
        val state = url.parameters["state"]
        if (state != expectedState) throw IllegalStateException("OAuth state mismatch")
        return url.parameters["code"] ?: throw IllegalStateException("No authorization code in callback")
    }

    suspend fun exchangeCode(
        provider: MailProvider,
        config: OAuthClientConfig,
        code: String,
        verifier: String
    ): OAuthTokenResponse {
        val p = OAuthProviderConfig.forProvider(provider)
        val client = createJsonHttpClient()
        try {
            return client.submitForm(
                url = p.tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", config.redirectUri)
                    append("client_id", config.clientIdFor(provider))
                    append("code_verifier", verifier)
                }
            ).body()
        } finally {
            client.close()
        }
    }

    suspend fun refresh(
        provider: MailProvider,
        config: OAuthClientConfig,
        refreshToken: String
    ): OAuthTokenResponse {
        val p = OAuthProviderConfig.forProvider(provider)
        val client = createJsonHttpClient()
        try {
            return client.submitForm(
                url = p.tokenEndpoint,
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", config.clientIdFor(provider))
                }
            ).body()
        } finally {
            client.close()
        }
    }

    suspend fun fetchProfile(
        provider: MailProvider,
        accessToken: String
    ): UserProfileInfo {
        val p = OAuthProviderConfig.forProvider(provider)
        val client = createJsonHttpClient(accessToken)
        try {
            return when (provider) {
                MailProvider.GMAIL -> {
                    val info: GoogleUserInfo = client.get(p.profileEndpoint).body()
                    UserProfileInfo(
                        email = info.email ?: "",
                        displayName = info.name ?: info.email ?: "User",
                        photoUrl = info.picture
                    )
                }
                MailProvider.OUTLOOK -> {
                    val info: GraphUserInfo = client.get(p.profileEndpoint).body()
                    val email = info.mail ?: info.userPrincipalName ?: ""
                    UserProfileInfo(
                        email = email,
                        displayName = info.displayName ?: email.ifEmpty { "User" },
                        photoUrl = null
                    )
                }
            }
        } finally {
            client.close()
        }
    }

    data class UserProfileInfo(
        val email: String,
        val displayName: String,
        val photoUrl: String?
    )
}
