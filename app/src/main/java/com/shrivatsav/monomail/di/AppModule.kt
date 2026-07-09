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
                val profileRetrofit = RetrofitClient(
                    tokenRefresher = {
                        val currentProfile = runBlocking { accountManager.getAccounts().find { it.id == profile.id } }
                            ?: return@RetrofitClient null
                        try {
                            val newToken = when {
                                currentProfile.provider == "gmail" -> {
                                    val oldToken = currentProfile.accessToken
                                    if (oldToken.isNotEmpty()) {
                                        GoogleAuthUtil.clearToken(context, oldToken)
                                    }
                                    GoogleAuthUtil.getToken(
                                        context,
                                        Account(currentProfile.email, "com.google"),
                                        AuthManager.GMAIL_SCOPE
                                    )
                                }
                                currentProfile.provider == "outlook" -> {
                                    runBlocking {
                                        authManager.microsoftAuthManager.getAccessTokenSilently(currentProfile.id)
                                    }
                                }
                                else -> null
                            }
                            if (newToken != null) {
                                runBlocking { authManager.updateAccessToken(currentProfile.copy(accessToken = newToken)) }
                                // Invalidate cache entry so the next provider factory call
                                // creates a fresh RetrofitClient with the latest token.
                                providerCache.remove(profile.id)
                            } else if (currentProfile.provider == "outlook") {
                                android.util.Log.w("AppModule", "Outlook silent token refresh returned null for ${currentProfile.id}")
                            }
                            newToken
                        } catch (e: Exception) {
                            android.util.Log.e("AppModule", "Token refresh failed for ${currentProfile.id}", e)
                            null
                        }
                    },
                    onRefreshFailed = {
                        authManager.notifyReauthRequired(profile.email, profile.provider)
                    },
                    onHttpError = { code ->
                        // Non-auth HTTP errors (e.g. 404 on a stale thread) are
                        // propagated here for logging / downstream error handling.
                        android.util.Log.w("AppModule", "HTTP $code for ${profile.id}")
                    }
                )
                // Seed the cached token so the first request has credentials.
                profileRetrofit.cachedToken.set(profile.accessToken.takeIf { it.isNotEmpty() })
                when (profile.provider) {
                    "gmail" -> GmailProvider(profileRetrofit.gmailApi, context)
                    "outlook" -> OutlookProvider(profileRetrofit.outlookApi, context)
                    "imap" -> {
                        try {
                            val configJson = SecurityUtil.decryptString(profile.accessToken)
                                ?: throw IllegalStateException("Cannot decrypt IMAP config")
                            val config = ImapAccountConfig.fromJson(configJson)
                            val password = SecurityUtil.decryptString(profile.refreshToken)
                                ?: throw IllegalStateException("Cannot decrypt IMAP password")
                            ImapProvider(config, password, context)
                        } catch (e: Exception) {
                            android.util.Log.e("AppModule", "Failed to create IMAP provider for ${profile.id}", e)
                            authManager.notifyReauthRequired(profile.email, "imap")
                            // Re-throw so the provider factory crashes this call;
                            // the re-auth notification lets the user know why.
                            throw e
                        }
                    }
                    else -> throw IllegalArgumentException("Unknown provider: ${profile.provider}")
                }
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
