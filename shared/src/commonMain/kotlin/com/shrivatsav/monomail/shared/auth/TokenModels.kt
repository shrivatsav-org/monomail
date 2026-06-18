package com.shrivatsav.monomail.shared.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("id_token") val idToken: String? = null,
    @SerialName("scope") val scope: String? = null
)

@Serializable
data class GoogleUserInfo(
    val email: String? = null,
    val name: String? = null,
    val picture: String? = null
)

@Serializable
data class GraphUserInfo(
    val mail: String? = null,
    val userPrincipalName: String? = null,
    val displayName: String? = null
)
