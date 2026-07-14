package com.shrivatsav.monomail.core.data.auth

import android.content.Context

object GoogleAuthHelperImpl : GoogleAuthHelper {
    override suspend fun clearToken(context: Context, token: String) {
        // No-op for Github flavor
    }

    override suspend fun getToken(context: Context, email: String, scope: String): String {
        throw GoogleAuthException("Google Sign-In is disabled in this build.")
    }

    override suspend fun signIn(context: Context, clientId: String): GoogleSignInData {
        throw GoogleAuthException("Google Sign-In is disabled in this build.")
    }

    override suspend fun clearCredentialState(context: Context) {
        // No-op for Github flavor
    }
}

fun provideGoogleAuthHelper(): GoogleAuthHelper = GoogleAuthHelperImpl
