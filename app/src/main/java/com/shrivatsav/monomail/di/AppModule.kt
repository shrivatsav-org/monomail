package com.shrivatsav.monomail.di

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.shrivatsav.monomail.ScheduledEmailEvent
import com.shrivatsav.monomail.SentEmailEvent
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.provider.EmailProvider
import com.shrivatsav.monomail.data.provider.GmailProvider
import com.shrivatsav.monomail.data.provider.OutlookProvider
import com.shrivatsav.monomail.data.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.data.provider.imap.ImapProvider
import com.shrivatsav.monomail.data.remote.RetrofitClient
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import com.shrivatsav.monomail.security.SecurityUtil
import dagger.Module
import kotlin.jvm.JvmSuppressWildcards
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideAccountManager(@ApplicationContext context: Context): AccountManager =
        AccountManager(context)

    @Provides @Singleton
    fun provideAuthManager(
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        pushNotificationManager: com.shrivatsav.monomail.push.PushNotificationManager,
        database: com.shrivatsav.monomail.data.local.AppDatabase
    ): AuthManager = AuthManager(context, accountManager, pushNotificationManager, database)

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides @Singleton
    fun provideSentEmailEvents(): MutableSharedFlow<SentEmailEvent> =
        MutableSharedFlow(replay = 1)

    @Provides @Singleton
    fun provideScheduledEmailEvents(): MutableSharedFlow<ScheduledEmailEvent> =
        MutableSharedFlow(replay = 0)

    @Provides @Singleton
    fun provideProviderFactory(
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        authManager: AuthManager
    ): (UserProfile) -> EmailProvider {
        val providerCache = java.util.concurrent.ConcurrentHashMap<String, EmailProvider>()
        return { profile ->
            providerCache.getOrPut(profile.id) {
                val retrofit = createRetrofitClient(profile, context, accountManager, authManager, providerCache)
                retrofit.cachedToken.set(profile.accessToken.takeIf { it.isNotEmpty() })
                createProvider(profile, retrofit, context, authManager)
            }
        }
    }

    private fun createRetrofitClient(
        profile: UserProfile,
        context: Context,
        accountManager: AccountManager,
        authManager: AuthManager,
        providerCache: java.util.concurrent.ConcurrentHashMap<String, EmailProvider>
    ) = RetrofitClient(
        tokenRefresher = { refreshProfileToken(profile, context, accountManager, authManager, providerCache) },
        onRefreshFailed = { authManager.notifyReauthRequired(profile.email, profile.provider) },
        onHttpError = { code -> android.util.Log.w("AppModule", "HTTP $code for ${profile.id}") }
    )

    private fun refreshProfileToken(
        profile: UserProfile,
        context: Context,
        accountManager: AccountManager,
        authManager: AuthManager,
        providerCache: java.util.concurrent.ConcurrentHashMap<String, EmailProvider>
    ): String? {
        // ponytail: runBlocking required — tokenRefresher is sync (OkHttp interceptor). Dispatchers.IO keeps DataStore ops off the dispatcher thread.
        val currentProfile = runBlocking(kotlinx.coroutines.Dispatchers.IO) { accountManager.getAccounts().find { it.id == profile.id } }
            ?: return null
        return try {
            val newToken = fetchNewToken(currentProfile, context, authManager)
            if (newToken != null) {
                runBlocking(kotlinx.coroutines.Dispatchers.IO) { authManager.updateAccessToken(currentProfile.copy(accessToken = newToken)) }
                providerCache.remove(profile.id)
            }
            newToken
        } catch (e: Exception) {
            android.util.Log.e("AppModule", "Token refresh failed for ${profile.id}", e)
            null
        }
    }

    private fun fetchNewToken(profile: UserProfile, context: Context, authManager: AuthManager): String? = when (profile.provider) {
        "gmail" -> {
            // ponytail: get new token first, then clear old — if getToken fails, old token is still valid
            val newToken = GoogleAuthUtil.getToken(context, Account(profile.email, "com.google"), AuthManager.GMAIL_SCOPE)
            val oldToken = profile.accessToken
            if (oldToken.isNotEmpty() && oldToken != newToken) GoogleAuthUtil.clearToken(context, oldToken)
            newToken
        }
        "outlook" -> runBlocking { authManager.microsoftAuthManager.getAccessTokenSilently(profile.id) }
        else -> null
    }

    private fun createProvider(profile: UserProfile, retrofit: RetrofitClient, context: Context, authManager: AuthManager): EmailProvider = when (profile.provider) {
        "gmail" -> GmailProvider(retrofit.gmailApi, context)
        "outlook" -> OutlookProvider(retrofit.outlookApi, context)
        "imap" -> createImapProvider(profile, context, authManager)
        else -> throw IllegalArgumentException("Unknown provider: ${profile.provider}")
    }

    private fun createImapProvider(profile: UserProfile, context: Context, authManager: AuthManager): ImapProvider = try {
        val configJson = SecurityUtil.decryptString(profile.accessToken)
            ?: throw IllegalStateException("Cannot decrypt IMAP config")
        val config = ImapAccountConfig.fromJson(configJson)
        val password = SecurityUtil.decryptString(profile.refreshToken)
            ?: throw IllegalStateException("Cannot decrypt IMAP password")
        ImapProvider(config, password, context)
    } catch (e: Exception) {
        android.util.Log.e("AppModule", "Failed to create IMAP provider for ${profile.id}", e)
        authManager.notifyReauthRequired(profile.email, "imap")
        throw e
    }

    @Provides @Singleton
    fun provideEmailRepository(
        providerFactory: (@JvmSuppressWildcards (UserProfile) -> EmailProvider),
        database: com.shrivatsav.monomail.data.local.AppDatabase,
        @ApplicationContext context: Context,
        accountManager: AccountManager,
        pendingActionDao: com.shrivatsav.monomail.data.local.PendingActionDao
    ): EmailRepository = EmailRepository(providerFactory, database, context, accountManager, pendingActionDao)
}
