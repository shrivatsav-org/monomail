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
import com.shrivatsav.monomail.data.repository.ContactPhotoProvider
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
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
        accountManager: AccountManager
    ): AuthManager = AuthManager(context, accountManager)

    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore =
        SettingsDataStore(context)

    @Provides @Singleton
    fun provideContactSuggestionProvider(): ContactSuggestionProvider =
        ContactSuggestionProvider()

    @Provides @Singleton
    fun provideContactPhotoProvider(@ApplicationContext context: Context): ContactPhotoProvider =
        ContactPhotoProvider(context)

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
    ): (UserProfile) -> EmailProvider = { profile ->
        val profileRetrofit = RetrofitClient(
            tokenProvider = {
                runBlocking { accountManager.getAccounts().find { it.id == profile.id } }?.accessToken
            },
            tokenRefresher = {
                val currentProfile = runBlocking { accountManager.getAccounts().find { it.id == profile.id } }
                    ?: return@RetrofitClient null
                try {
                    when {
                        currentProfile.provider == "gmail" -> {
                            val oldToken = currentProfile.accessToken
                            if (oldToken.isNotEmpty()) {
                                GoogleAuthUtil.clearToken(context, oldToken)
                            }
                            val newToken = GoogleAuthUtil.getToken(
                                context,
                                Account(currentProfile.email, "com.google"),
                                AuthManager.GMAIL_SCOPE
                            )
                            runBlocking { authManager.updateAccessToken(currentProfile.copy(accessToken = newToken)) }
                            newToken
                        }
                        currentProfile.provider == "outlook" -> {
                            val newToken = runBlocking {
                                authManager.microsoftAuthManager.getAccessTokenSilently(currentProfile.id)
                            }
                            if (newToken != null) {
                                runBlocking { authManager.updateAccessToken(currentProfile.copy(accessToken = newToken)) }
                            }
                            newToken
                        }
                        else -> null
                    }
                } catch (_: Exception) { null }
            },
            onRefreshFailed = {
                authManager.notifyReauthRequired(profile.email, profile.provider)
            }
        )
        when (profile.provider) {
            "gmail" -> GmailProvider(profileRetrofit.gmailApi, context)
            "outlook" -> OutlookProvider(profileRetrofit.outlookApi, context)
            "imap" -> {
                val configJson = SecurityUtil.decryptString(profile.accessToken)
                    ?: throw IllegalStateException("Cannot decrypt IMAP config")
                val config = ImapAccountConfig.fromJson(configJson)
                val password = SecurityUtil.decryptString(profile.refreshToken)
                    ?: throw IllegalStateException("Cannot decrypt IMAP password")
                ImapProvider(config, password, context)
            }
            else -> throw IllegalArgumentException("Unknown provider: ${profile.provider}")
        }
    }

    @Provides @Singleton
    fun provideEmailRepository(
        providerFactory: (@JvmSuppressWildcards (UserProfile) -> EmailProvider),
        database: com.shrivatsav.monomail.data.local.AppDatabase,
        @ApplicationContext context: Context,
        accountManager: AccountManager
    ): EmailRepository = EmailRepository(providerFactory, database, context, accountManager)
}
