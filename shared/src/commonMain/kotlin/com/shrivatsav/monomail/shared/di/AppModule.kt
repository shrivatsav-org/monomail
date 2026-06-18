package com.shrivatsav.monomail.shared.di

import com.shrivatsav.monomail.shared.auth.AccountManager
import com.shrivatsav.monomail.shared.auth.AuthManager
import com.shrivatsav.monomail.shared.auth.OAuthClientConfig
import com.shrivatsav.monomail.shared.data.local.SqlDriverFactory
import com.shrivatsav.monomail.shared.data.local.createDatabase
import com.shrivatsav.monomail.shared.data.provider.ProviderFactory
import com.shrivatsav.monomail.shared.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.shared.data.repository.EmailRepository
import com.shrivatsav.monomail.shared.data.settings.SettingsRepository
import com.shrivatsav.monomail.shared.platform.KeyValueStore
import com.shrivatsav.monomail.shared.platform.OAuthBrowser
import com.shrivatsav.monomail.shared.platform.SecureStore

/**
 * Composition root. The host app (iOS / Android) constructs this once with the
 * platform implementations and reads the wired singletons. Single entry point
 * for the SwiftUI / Compose layers.
 */
class AppModule(
    secureStore: SecureStore,
    keyValueStore: KeyValueStore,
    driverFactory: SqlDriverFactory,
    browser: OAuthBrowser,
    oauthConfig: OAuthClientConfig
) {
    val accountManager: AccountManager = AccountManager(secureStore)
    val settingsRepository: SettingsRepository = SettingsRepository(keyValueStore)
    val database = createDatabase(driverFactory)
    val authManager: AuthManager = AuthManager(accountManager, browser, oauthConfig)
    val contactProvider: ContactSuggestionProvider = ContactSuggestionProvider()
    val repository: EmailRepository = EmailRepository(
        providerFactory = ProviderFactory::create,
        database = database,
        accountManager = accountManager
    )
}
