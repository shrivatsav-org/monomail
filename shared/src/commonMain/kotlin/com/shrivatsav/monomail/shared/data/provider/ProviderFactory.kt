package com.shrivatsav.monomail.shared.data.provider

import com.shrivatsav.monomail.shared.auth.UserProfile
import com.shrivatsav.monomail.shared.data.remote.GmailApi
import com.shrivatsav.monomail.shared.data.remote.OutlookApi
import com.shrivatsav.monomail.shared.data.remote.createJsonHttpClient

/**
 * Builds the right [EmailProvider] for an account, wiring a bearer-authed Ktor
 * client. This is the `(UserProfile) -> EmailProvider` lambda EmailRepository needs.
 *
 * TODO(auth): refresh-on-401 — currently the access token is static per build;
 * token refresh is driven externally via AuthManager.refreshAccessToken.
 */
object ProviderFactory {
    fun create(profile: UserProfile): EmailProvider {
        val client = createJsonHttpClient(profile.accessToken)
        return when (profile.provider) {
            "gmail" -> GmailProvider(GmailApi(client))
            "outlook" -> OutlookProvider(OutlookApi(client))
            else -> throw IllegalArgumentException("Unknown provider: ${profile.provider}")
        }
    }
}
