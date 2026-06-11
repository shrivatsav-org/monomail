package com.shrivatsav.monomail

import android.accounts.Account
import android.app.Application
import com.google.android.gms.auth.GoogleAuthUtil
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.TokenManager
import com.shrivatsav.monomail.data.remote.RetrofitClient
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.runBlocking

class MonoMailApp : Application() {

    lateinit var tokenManager: TokenManager
        private set

    lateinit var authManager: AuthManager
        private set

    lateinit var emailRepository: EmailRepository
        private set

    lateinit var contactSuggestionProvider: ContactSuggestionProvider
        private set

    override fun onCreate() {
        super.onCreate()
        tokenManager = TokenManager(this)
        authManager = AuthManager(this, tokenManager)
        contactSuggestionProvider = ContactSuggestionProvider()

        val retrofitClient = RetrofitClient(
            tokenProvider = { authManager.currentUser?.accessToken },
            tokenRefresher = {
                // Invalidate old token & fetch fresh one
                val profile = authManager.currentUser ?: return@RetrofitClient null
                try {
                    val oldToken = profile.accessToken
                    if (oldToken.isNotEmpty()) {
                        GoogleAuthUtil.clearToken(this@MonoMailApp, oldToken)
                    }
                    val newToken = GoogleAuthUtil.getToken(
                        this@MonoMailApp,
                        Account(profile.email, "com.google"),
                        AuthManager.GMAIL_SCOPE
                    )
                    // Update stored profile with new token
                    val updated = profile.copy(accessToken = newToken)
                    runBlocking { tokenManager.saveSession(updated) }
                    authManager.updateAccessToken(updated)
                    newToken
                } catch (e: Exception) {
                    null
                }
            }
        )
        emailRepository = EmailRepository(retrofitClient.gmailApi, this)
    }
}