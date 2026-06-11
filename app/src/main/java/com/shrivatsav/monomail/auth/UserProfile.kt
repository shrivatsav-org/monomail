package com.shrivatsav.monomail.auth

data class UserProfile(
    val id: String,
    val displayName: String,
    val email: String,
    val photoUrl: String?,
    val idToken: String,
    val accessToken: String
)