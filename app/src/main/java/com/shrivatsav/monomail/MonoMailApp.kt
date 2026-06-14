package com.shrivatsav.monomail

import android.accounts.Account
import android.app.Application
import com.google.android.gms.auth.GoogleAuthUtil
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.local.AppDatabase
import com.shrivatsav.monomail.data.provider.EmailProvider
import com.shrivatsav.monomail.data.provider.GmailProvider
import com.shrivatsav.monomail.data.provider.OutlookProvider
import com.shrivatsav.monomail.data.remote.RetrofitClient
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import kotlinx.coroutines.runBlocking

class MonoMailApp : Application() {

    lateinit var accountManager: AccountManager
        private set

    lateinit var authManager: AuthManager
        private set

    lateinit var emailRepository: EmailRepository
        private set

    lateinit var contactSuggestionProvider: ContactSuggestionProvider
        private set

    lateinit var settingsDataStore: SettingsDataStore
        private set

    override fun onCreate() {
        super.onCreate()
        accountManager = AccountManager(this)
        authManager = AuthManager(this, accountManager)
        contactSuggestionProvider = ContactSuggestionProvider()
        settingsDataStore = SettingsDataStore(this)

        val retrofitClient = RetrofitClient(
            tokenProvider = { providerName -> 
                val profile = authManager.currentUser
                if (profile?.provider == providerName) profile.accessToken else null
            },
            tokenRefresher = { providerName ->
                // Invalidate old token & fetch fresh one
                val profile = authManager.currentUser ?: return@RetrofitClient null
                if (profile.provider != providerName) return@RetrofitClient null
                try {
                    if (profile.provider == "gmail") {
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
                        runBlocking { authManager.updateAccessToken(updated) }
                        newToken
                    } else if (profile.provider == "outlook") {
                        // Microsoft token refresh
                        val newToken = runBlocking {
                            authManager.microsoftAuthManager.getAccessTokenSilently(profile.id)
                        }
                        if (newToken != null) {
                            val updated = profile.copy(accessToken = newToken)
                            runBlocking { authManager.updateAccessToken(updated) }
                        }
                        newToken
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        )

        val database = AppDatabase.getDatabase(this)

        val providerFactory: (UserProfile) -> EmailProvider = { profile ->
            when (profile.provider) {
                "gmail" -> GmailProvider(retrofitClient.gmailApi, this)
                "outlook" -> OutlookProvider(retrofitClient.outlookApi, this)
                else -> GmailProvider(retrofitClient.gmailApi, this) // Default fallback
            }
        }

        emailRepository = EmailRepository(
            providerFactory = providerFactory,
            database = database,
            context = this,
            accountManager = accountManager
        )
    }
}