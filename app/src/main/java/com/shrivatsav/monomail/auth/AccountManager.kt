package com.shrivatsav.monomail.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class AccountManager(private val context: Context) {

    companion object {
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        
        // Legacy keys to support migration
        private val KEY_USER_ID      = stringPreferencesKey("user_id")
        private val KEY_EMAIL        = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    }

    private val gson = Gson()

    /** Get the list of all configured accounts. */
    suspend fun getAccounts(): List<UserProfile> {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_ACCOUNTS_JSON]
        if (json != null) {
            val type = object : TypeToken<List<UserProfile>>() {}.type
            return gson.fromJson(json, type)
        }
        
        // Migration from legacy v1 format
        val legacyEmail = prefs[KEY_EMAIL]
        val legacyToken = prefs[KEY_ACCESS_TOKEN]
        if (legacyEmail != null && legacyToken != null) {
            val profile = UserProfile(
                id = prefs[KEY_USER_ID] ?: legacyEmail,
                displayName = prefs[KEY_DISPLAY_NAME] ?: "User",
                email = legacyEmail,
                photoUrl = prefs[KEY_PHOTO_URL],
                accessToken = legacyToken,
                provider = "gmail",
                refreshToken = ""
            )
            // Save migrated
            context.dataStore.edit { it[KEY_ACCOUNTS_JSON] = gson.toJson(listOf(profile)) }
            return listOf(profile)
        }
        
        return emptyList()
    }

    suspend fun addAccount(profile: UserProfile) {
        val accounts = getAccounts().toMutableList()
        // Replace if already exists (same email + provider)
        val index = accounts.indexOfFirst { it.email == profile.email && it.provider == profile.provider }
        if (index != -1) {
            accounts[index] = profile
        } else {
            accounts.add(profile)
        }
        
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS_JSON] = gson.toJson(accounts)
        }
    }

    suspend fun removeAccount(accountId: String) {
        val accounts = getAccounts().filterNot { it.id == accountId }
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCOUNTS_JSON] = gson.toJson(accounts)
            if (prefs[KEY_ACTIVE_ACCOUNT_ID] == accountId) {
                prefs.remove(KEY_ACTIVE_ACCOUNT_ID)
            }
        }
    }

    suspend fun getActiveAccount(): UserProfile? {
        val accounts = getAccounts()
        if (accounts.isEmpty()) return null
        
        val prefs = context.dataStore.data.first()
        val activeId = prefs[KEY_ACTIVE_ACCOUNT_ID]
        
        return accounts.find { it.id == activeId } ?: accounts.first()
    }

    suspend fun setActiveAccountId(accountId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACTIVE_ACCOUNT_ID] = accountId
        }
    }

    suspend fun updateAccountToken(accountId: String, newToken: String) {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.id == accountId }
        if (index != -1) {
            accounts[index] = accounts[index].copy(accessToken = newToken)
            context.dataStore.edit { prefs ->
                prefs[KEY_ACCOUNTS_JSON] = gson.toJson(accounts)
            }
        }
    }

    /** Wipe everything on global clear. */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
    
    suspend fun getLastKnownEmailId(accountId: String): String? {
        val prefs = context.dataStore.data.first()
        return prefs[stringPreferencesKey("last_email_$accountId")]
    }

    suspend fun setLastKnownEmailId(accountId: String, emailId: String) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("last_email_$accountId")] = emailId
        }
    }
    
    // Listen to changes in the active account
    val activeAccountFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ACCOUNTS_JSON] ?: return@map null
        val type = object : TypeToken<List<UserProfile>>() {}.type
        val accounts: List<UserProfile> = gson.fromJson(json, type)
        if (accounts.isEmpty()) return@map null
        
        val activeId = prefs[KEY_ACTIVE_ACCOUNT_ID]
        accounts.find { it.id == activeId } ?: accounts.first()
    }
}
