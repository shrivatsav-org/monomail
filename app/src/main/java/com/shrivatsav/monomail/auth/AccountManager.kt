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
import com.shrivatsav.monomail.security.SecurityUtil
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
class AccountManager(private val context: Context) {
    companion object {
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        private val KEY_USER_ID      = stringPreferencesKey("user_id")
        private val KEY_EMAIL        = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
    private val gson = Gson()
    suspend fun getAccounts(): List<UserProfile> {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_ACCOUNTS_JSON]
        if (json != null) {
            val decryptedJson = SecurityUtil.decryptString(json) ?: json
            val type = object : TypeToken<List<UserProfile>>() {}.type
            try {
                return gson.fromJson(decryptedJson, type)
            } catch (e: Exception) {
                return emptyList()
            }
        }
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
            context.dataStore.edit { it[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(listOf(profile))) }
            return listOf(profile)
        }
        return emptyList()
    }
    suspend fun addAccount(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ACCOUNTS_JSON]
            val accounts = if (json != null) {
                val decryptedJson = SecurityUtil.decryptString(json) ?: json
                gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
            val index = accounts.indexOfFirst { it.email == profile.email && it.provider == profile.provider }
            if (index != -1) {
                accounts[index] = profile
            } else {
                accounts.add(profile)
            }
            prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts))
        }
    }
    suspend fun removeAccount(accountId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ACCOUNTS_JSON]
            if (json != null) {
                val decryptedJson = SecurityUtil.decryptString(json) ?: json
                val accounts = gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
                accounts.removeAll { it.id == accountId }
                prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts))
            }
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
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ACCOUNTS_JSON]
            if (json != null) {
                val decryptedJson = SecurityUtil.decryptString(json) ?: json
                val accounts = gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
                val index = accounts.indexOfFirst { it.id == accountId }
                if (index != -1) {
                    accounts[index] = accounts[index].copy(accessToken = newToken)
                    prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts))
                }
            }
        }
    }
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
    suspend fun getLastKnownEmailId(accountId: String): String? {
        val prefs = context.dataStore.data.first()
        return prefs[stringPreferencesKey("last_timestamp_$accountId")]
    }
    suspend fun setLastKnownEmailId(accountId: String, emailId: String) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("last_timestamp_$accountId")] = emailId
        }
    }
    suspend fun getLastActiveTime(): Long {
        val prefs = context.dataStore.data.first()
        val stored = prefs[stringPreferencesKey("last_active_time")]
        return stored?.toLongOrNull() ?: 0L
    }
    suspend fun setLastActiveTime(time: Long) {
        context.dataStore.edit { prefs ->
            prefs[stringPreferencesKey("last_active_time")] = time.toString()
        }
    }
    val accountsFlow: Flow<List<UserProfile>> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ACCOUNTS_JSON] ?: return@map emptyList()
        val decryptedJson = SecurityUtil.decryptString(json) ?: json
        val type = object : TypeToken<List<UserProfile>>() {}.type
        try { gson.fromJson(decryptedJson, type) } catch (e: Exception) { emptyList() }
    }
    val activeAccountFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ACCOUNTS_JSON] ?: return@map null
        val decryptedJson = SecurityUtil.decryptString(json) ?: json
        val type = object : TypeToken<List<UserProfile>>() {}.type
        val accounts: List<UserProfile> = try { gson.fromJson(decryptedJson, type) } catch (e: Exception) { emptyList() }
        if (accounts.isEmpty()) return@map null
        val activeId = prefs[KEY_ACTIVE_ACCOUNT_ID]
        accounts.find { it.id == activeId } ?: accounts.first()
    }
}
