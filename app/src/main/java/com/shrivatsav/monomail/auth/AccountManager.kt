package com.shrivatsav.monomail.auth
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.shrivatsav.monomail.security.SecurityUtil
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")
class AccountManager(private val context: Context) {
    companion object {
        private const val TAG = "AccountManager"
        private val KEY_ACCOUNTS_JSON = stringPreferencesKey("accounts_json")
        private val KEY_ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_account_id")
        private val KEY_USER_ID      = stringPreferencesKey("user_id")
        private val KEY_EMAIL        = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
    private val gson = Gson()

    // Emits true when SecurityUtil.decryptString fails (e.g. KeyStore corruption),
    // allowing the UI to show a specific error instead of a silent empty state.
    private val _decryptionFailed = MutableStateFlow(false)
    val decryptionFailed: StateFlow<Boolean> = _decryptionFailed.asStateFlow()
    fun clearDecryptionFailed() { _decryptionFailed.value = false }

    suspend fun getAccounts(): List<UserProfile> {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_ACCOUNTS_JSON]
        if (json != null) {
            val decryptedJson = SecurityUtil.decryptString(json)
            if (decryptedJson == null) {
                Log.w(TAG, "Failed to decrypt accounts data — treating as corrupt")
                _decryptionFailed.value = true
                return emptyList()
            }
            val type = object : TypeToken<List<UserProfile>>() {}.type
            try {
                return gson.fromJson(decryptedJson, type)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deserialize accounts data", e)
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
        // Cap at 10 accounts to prevent runaway storage
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ACCOUNTS_JSON]
            val accounts = if (json != null) {
                val decryptedJson = SecurityUtil.decryptString(json)
                if (decryptedJson == null) {
                    Log.w(TAG, "Failed to decrypt accounts in addAccount — treating as corrupt")
                    _decryptionFailed.value = true
                    return@edit
                }
                gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
            } else {
                mutableListOf()
            }
            if (accounts.size >= 10) {
                Log.w(TAG, "Account limit reached (10) — refusing to add more")
                return@edit
            }
            val index = accounts.indexOfFirst { it.email == profile.email && it.provider == profile.provider }
            if (index != -1) {
                accounts[index] = profile
            } else {
                accounts.add(profile)
            }
            // Serialize defensively: catch and log serialization errors instead of crashing
            try {
                val serialized = gson.toJson(accounts)
                prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(serialized)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to serialize/encrypt accounts on add", e)
            }
        }
    }
    suspend fun removeAccount(accountId: String) {
        context.dataStore.edit { prefs ->
            val json = prefs[KEY_ACCOUNTS_JSON]
            if (json != null) {
                val decryptedJson = SecurityUtil.decryptString(json)
                if (decryptedJson == null) {
                    Log.w(TAG, "Failed to decrypt accounts in removeAccount — treating as corrupt")
                    _decryptionFailed.value = true
                    return@edit
                }
                val accounts = gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
                accounts.removeAll { it.id == accountId }
                try { prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts)) } catch (e: Exception) { Log.e(TAG, "Failed to serialize accounts", e) }
            }
            if (prefs[KEY_ACTIVE_ACCOUNT_ID] == accountId) {
                prefs.remove(KEY_ACTIVE_ACCOUNT_ID)
            }
        }
    }
    suspend fun getActiveAccount(): UserProfile? {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_ACCOUNTS_JSON] ?: return null
        val decryptedJson = SecurityUtil.decryptString(json)
        if (decryptedJson == null) {
            Log.w(TAG, "Failed to decrypt accounts data in getActiveAccount")
            _decryptionFailed.value = true
            return null
        }
        val type = object : TypeToken<List<UserProfile>>() {}.type
        val accounts: List<UserProfile> = try {
            gson.fromJson(decryptedJson, type)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deserialize accounts in getActiveAccount", e)
            return null
        }
        if (accounts.isEmpty()) return null
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
                val decryptedJson = SecurityUtil.decryptString(json)
                if (decryptedJson == null) {
                    Log.w(TAG, "Failed to decrypt accounts in updateAccountToken — treating as corrupt")
                    _decryptionFailed.value = true
                    return@edit
                }
                val accounts = gson.fromJson(decryptedJson, Array<UserProfile>::class.java).toMutableList()
                val index = accounts.indexOfFirst { it.id == accountId }
                if (index != -1) {
                    accounts[index] = accounts[index].copy(accessToken = newToken)
                    try { prefs[KEY_ACCOUNTS_JSON] = SecurityUtil.encryptString(gson.toJson(accounts)) } catch (e: Exception) { Log.e(TAG, "Failed to serialize accounts", e) }
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
        val decryptedJson = SecurityUtil.decryptString(json)
        if (decryptedJson == null) {
            Log.w(TAG, "Failed to decrypt accounts data in accountsFlow")
            _decryptionFailed.value = true
            return@map emptyList()
        }
        val type = object : TypeToken<List<UserProfile>>() {}.type
        try { gson.fromJson(decryptedJson, type) } catch (e: Exception) { emptyList() }
    }
    val activeAccountFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val json = prefs[KEY_ACCOUNTS_JSON] ?: return@map null
        val decryptedJson = SecurityUtil.decryptString(json)
        if (decryptedJson == null) {
            Log.w(TAG, "Failed to decrypt accounts data in activeAccountFlow")
            _decryptionFailed.value = true
            return@map null
        }
        val type = object : TypeToken<List<UserProfile>>() {}.type
        val accounts: List<UserProfile> = try { gson.fromJson(decryptedJson, type) } catch (e: Exception) { emptyList() }
        if (accounts.isEmpty()) return@map null
        val activeId = prefs[KEY_ACTIVE_ACCOUNT_ID]
        accounts.find { it.id == activeId } ?: accounts.first()
    }
}
