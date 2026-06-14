package com.shrivatsav.monomail.auth

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val accessToken: String,
    val provider: String,          // "gmail" or "outlook"
    val refreshToken: String = ""  // Used by Outlook (MSAL), empty for Gmail
)