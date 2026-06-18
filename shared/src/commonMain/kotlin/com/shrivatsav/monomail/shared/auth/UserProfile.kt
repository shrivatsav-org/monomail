package com.shrivatsav.monomail.shared.auth

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val accessToken: String,
    val provider: String,
    val refreshToken: String = ""
)
