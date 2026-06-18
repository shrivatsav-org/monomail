package com.shrivatsav.monomail.shared.auth

import com.shrivatsav.monomail.shared.platform.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Multiplatform account store. Accounts are persisted as JSON in [SecureStore]
 * (Keychain on iOS, EncryptedSharedPreferences on Android). Reactivity is
 * provided by an in-memory [MutableStateFlow] kept in sync on every mutation,
 * replacing the Android DataStore the original used.
 */
class AccountManager(
    private val secureStore: SecureStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    private companion object {
        const val KEY_ACCOUNTS = "accounts_json"
        const val KEY_ACTIVE = "active_account_id"
    }

    private val _accounts = MutableStateFlow(loadAccounts())
    val accountsFlow: StateFlow<List<UserProfile>> = _accounts.asStateFlow()

    private var activeId: String? = secureStore.getString(KEY_ACTIVE)

    private val _activeAccount = MutableStateFlow(computeActive())
    val activeAccountFlow: StateFlow<UserProfile?> = _activeAccount.asStateFlow()

    private fun computeActive(): UserProfile? {
        val accounts = _accounts.value
        if (accounts.isEmpty()) return null
        return accounts.find { it.id == activeId } ?: accounts.first()
    }

    private fun refreshActive() {
        _activeAccount.value = computeActive()
    }

    private fun loadAccounts(): List<UserProfile> {
        val raw = secureStore.getString(KEY_ACCOUNTS) ?: return emptyList()
        return try {
            json.decodeFromString(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(accounts: List<UserProfile>) {
        secureStore.putString(KEY_ACCOUNTS, json.encodeToString(accounts))
        _accounts.value = accounts
        refreshActive()
    }

    fun getAccounts(): List<UserProfile> = _accounts.value

    fun getActiveAccount(): UserProfile? = computeActive()

    fun addAccount(profile: UserProfile) {
        val accounts = _accounts.value.toMutableList()
        val index = accounts.indexOfFirst { it.email == profile.email && it.provider == profile.provider }
        if (index != -1) accounts[index] = profile else accounts.add(profile)
        persist(accounts)
    }

    fun removeAccount(accountId: String) {
        val accounts = _accounts.value.filterNot { it.id == accountId }
        if (activeId == accountId) {
            activeId = null
            secureStore.remove(KEY_ACTIVE)
        }
        persist(accounts)
    }

    fun setActiveAccountId(accountId: String) {
        activeId = accountId
        secureStore.putString(KEY_ACTIVE, accountId)
        refreshActive()
    }

    fun updateAccountToken(accountId: String, newToken: String) {
        val accounts = _accounts.value.map {
            if (it.id == accountId) it.copy(accessToken = newToken) else it
        }
        persist(accounts)
    }

    fun clearAll() {
        secureStore.remove(KEY_ACCOUNTS)
        secureStore.remove(KEY_ACTIVE)
        activeId = null
        _accounts.value = emptyList()
        refreshActive()
    }
}
