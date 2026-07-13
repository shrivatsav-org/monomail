package com.shrivatsav.monomail.auth

import android.accounts.Account
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

object GoogleAuthHelperImpl : GoogleAuthHelper {
    override suspend fun clearToken(context: Context, token: String) {
        GoogleAuthUtil.clearToken(context, token)
    }

    override suspend fun getToken(context: Context, email: String, scope: String): String {
        try {
            return GoogleAuthUtil.getToken(context, Account(email, "com.google"), scope)
        } catch (e: UserRecoverableAuthException) {
            throw GoogleAuthException(e.message ?: "UserRecoverableAuthException", e.intent)
        } catch (e: Exception) {
            throw GoogleAuthException(e.message ?: "Unknown error")
        }
    }

    override suspend fun signIn(context: Context, clientId: String): GoogleSignInData {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val credentialManager = CredentialManager.create(context)
            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            val googleIdTokenCredential = GoogleIdTokenCredential
                .createFrom(result.credential.data)
            return GoogleSignInData(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName ?: "User",
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString()
            )
        } catch (e: GetCredentialException) {
            throw GoogleAuthException(
                "Google sign-in failed: ${e.message ?: e.type}. " +
                "Make sure a Google account is added in Settings > Accounts."
            )
        }
    }

    override suspend fun clearCredentialState(context: Context) {
        val credentialManager = CredentialManager.create(context)
        credentialManager.clearCredentialState(ClearCredentialStateRequest())
    }
}

fun provideGoogleAuthHelper(): GoogleAuthHelper = GoogleAuthHelperImpl
