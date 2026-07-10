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
        pushNotificationManager: com.shrivatsav.monomail.push.PushNotificationManager
    ): AuthManager = AuthManager(context, accountManager, pushNotificationManager)

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
                val profileRetrofit = buildRetrofitClient(
                    context, accountManager, authManager, profile, providerCache
                )
                profileRetrofit.cachedToken.set(profile.accessToken.takeIf { it.isNotEmpty() })
                createProvider(profile, profileRetrofit, context, authManager)
            }
        }
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

private fun buildRetrofitClient(
    context: Context,
    accountManager: AccountManager,
    authManager: AuthManager,
    profile: UserProfile,
    providerCache: java.util.concurrent.ConcurrentHashMap<String, EmailProvider>
): RetrofitClient = RetrofitClient(
    tokenRefresher = { refreshAccountToken(context, accountManager, authManager, profile, providerCache) },
    onRefreshFailed = { authManager.notifyReauthRequired(profile.email, profile.provider) },
    onHttpError = { code -> android.util.Log.w("AppModule", "HTTP $code for ${profile.id}") }
)

private fun refreshAccountToken(
    context: Context,
    accountManager: AccountManager,
    authManager: AuthManager,
    profile: UserProfile,
    providerCache: java.util.concurrent.ConcurrentHashMap<String, EmailProvider>
): String? {
    val currentProfile = runBlocking { accountManager.getAccounts().find { it.id == profile.id } }
        ?: return null
    return try {
        val newToken = fetchNewToken(context, authManager, currentProfile)
        if (newToken != null) {
            runBlocking { authManager.updateAccessToken(currentProfile.copy(accessToken = newToken)) }
            providerCache.remove(profile.id)
        } else if (currentProfile.provider == "outlook") {
            android.util.Log.w("AppModule", "Outlook silent token refresh returned null for ${currentProfile.id}")
        }
        newToken
    } catch (e: Exception) {
        android.util.Log.e("AppModule", "Token refresh failed for ${currentProfile.id}", e)
        null
    }
}

private fun fetchNewToken(
    context: Context,
    authManager: AuthManager,
    profile: UserProfile
): String? = when (profile.provider) {
    "gmail" -> {
        val oldToken = profile.accessToken
        if (oldToken.isNotEmpty()) { GoogleAuthUtil.clearToken(context, oldToken) }
        GoogleAuthUtil.getToken(context, Account(profile.email, "com.google"), AuthManager.GMAIL_SCOPE)
    }
    "outlook" -> runBlocking { authManager.microsoftAuthManager.getAccessTokenSilently(profile.id) }
    else -> null
}

private fun createProvider(
    profile: UserProfile,
    retrofit: RetrofitClient,
    context: Context,
    authManager: AuthManager
): EmailProvider = when (profile.provider) {
    "gmail" -> GmailProvider(retrofit.gmailApi, context)
    "outlook" -> OutlookProvider(retrofit.outlookApi, context)
    "imap" -> createImapProvider(profile, context, authManager)
    else -> throw IllegalArgumentException("Unknown provider: ${profile.provider}")
}

private fun createImapProvider(
    profile: UserProfile,
    context: Context,
    authManager: AuthManager
): ImapProvider = try {
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
