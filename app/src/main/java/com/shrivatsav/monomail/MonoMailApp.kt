package com.shrivatsav.monomail
import android.accounts.Account
import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
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
import com.shrivatsav.monomail.worker.SnoozeWorker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
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
    lateinit var database: AppDatabase
        private set

    data class SentEmailEvent(
        val threadId: String?,
        val to: String,
        val subject: String
    )

    private val _sentEmailEvents = MutableSharedFlow<SentEmailEvent>(replay = 1)
    val sentEmailEvents = _sentEmailEvents.asSharedFlow()

    fun emitSentEmailEvent(event: SentEmailEvent) {
        _sentEmailEvents.tryEmit(event)
    }

    data class ScheduledEmailEvent(
        val to: String,
        val subject: String,
        val scheduledAt: Long
    )

    private val _scheduledEmailEvents = MutableSharedFlow<ScheduledEmailEvent>(replay = 0)
    val scheduledEmailEvents = _scheduledEmailEvents.asSharedFlow()

    fun emitScheduledEmailEvent(event: ScheduledEmailEvent) {
        _scheduledEmailEvents.tryEmit(event)
    }

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        accountManager = AccountManager(this)
        authManager = AuthManager(this, accountManager)
        contactSuggestionProvider = ContactSuggestionProvider()
        settingsDataStore = SettingsDataStore(this)
        database = AppDatabase.getDatabase(this)
        val providerFactory: (UserProfile) -> EmailProvider = { profile ->
            val profileRetrofit = RetrofitClient(
                tokenProvider = { 
                    runBlocking { accountManager.getAccounts().find { it.id == profile.id } }?.accessToken
                },
                tokenRefresher = {
                    val currentProfile = runBlocking { accountManager.getAccounts().find { it.id == profile.id } } ?: return@RetrofitClient null
                    try {
                        if (currentProfile.provider == "gmail") {
                            val oldToken = currentProfile.accessToken
                            if (oldToken.isNotEmpty()) {
                                GoogleAuthUtil.clearToken(this@MonoMailApp, oldToken)
                            }
                            val newToken = GoogleAuthUtil.getToken(
                                this@MonoMailApp,
                                Account(currentProfile.email, "com.google"),
                                AuthManager.GMAIL_SCOPE
                            )
                            val updated = currentProfile.copy(accessToken = newToken)
                            runBlocking { authManager.updateAccessToken(updated) }
                            newToken
                        } else if (currentProfile.provider == "outlook") {
                            val newToken = runBlocking {
                                authManager.microsoftAuthManager.getAccessTokenSilently(currentProfile.id)
                            }
                            if (newToken != null) {
                                val updated = currentProfile.copy(accessToken = newToken)
                                runBlocking { authManager.updateAccessToken(updated) }
                            }
                            newToken
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                },
                onRefreshFailed = {
                    val p = runBlocking { accountManager.getActiveAccount() }
                    if (p != null) {
                        authManager.notifyReauthRequired(p.email, p.provider)
                    }
                }
            )
            when (profile.provider) {
                "gmail" -> GmailProvider(profileRetrofit.gmailApi, this)
                "outlook" -> OutlookProvider(profileRetrofit.outlookApi, this)
                else -> GmailProvider(profileRetrofit.gmailApi, this) 
            }
        }
        emailRepository = EmailRepository(
            providerFactory = providerFactory,
            database = database,
            context = this,
            accountManager = accountManager
        )
        val snoozeRequest = PeriodicWorkRequestBuilder<SnoozeWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "snooze_check",
            ExistingPeriodicWorkPolicy.KEEP,
            snoozeRequest
        )
    }
}