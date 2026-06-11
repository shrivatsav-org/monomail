package com.shrivatsav.monomail.auth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

class TokenManager(private val context: Context) {

    companion object {
        private val KEY_USER_ID      = stringPreferencesKey("user_id")
        private val KEY_EMAIL        = stringPreferencesKey("email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("display_name")
        private val KEY_PHOTO_URL    = stringPreferencesKey("photo_url")
        private val KEY_ID_TOKEN     = stringPreferencesKey("id_token")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_LAST_KNOWN_EMAIL_ID = stringPreferencesKey("last_known_email_id")
    }

    /** Persist the full user session after sign-in. */
    suspend fun saveSession(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID]      = profile.id
            prefs[KEY_EMAIL]        = profile.email
            prefs[KEY_DISPLAY_NAME] = profile.displayName
            prefs[KEY_ID_TOKEN]     = profile.idToken
            prefs[KEY_ACCESS_TOKEN] = profile.accessToken
            profile.photoUrl?.let { prefs[KEY_PHOTO_URL] = it }
        }
    }

    /** Restore a saved session, or null if none exists. */
    suspend fun getStoredProfile(): UserProfile? {
        val prefs = context.dataStore.data.first()
        val email       = prefs[KEY_EMAIL]        ?: return null
        val accessToken = prefs[KEY_ACCESS_TOKEN] ?: return null

        return UserProfile(
            id          = prefs[KEY_USER_ID] ?: email,
            displayName = prefs[KEY_DISPLAY_NAME] ?: "User",
            email       = email,
            photoUrl    = prefs[KEY_PHOTO_URL],
            idToken     = prefs[KEY_ID_TOKEN] ?: "",
            accessToken = accessToken
        )
    }

    /** Observable access token for network interceptors. */
    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCESS_TOKEN]
    }

    /** Wipe everything on sign-out. */
    suspend fun clearSession() {
        context.dataStore.edit { it.clear() }
    }

    /** Set the ID of the last email we've processed for notifications. */
    suspend fun setLastKnownEmailId(emailId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_KNOWN_EMAIL_ID] = emailId
        }
    }

    /** Get the ID of the last email we've processed for notifications. */
    suspend fun getLastKnownEmailId(): String? {
        val prefs = context.dataStore.data.first()
        return prefs[KEY_LAST_KNOWN_EMAIL_ID]
    }
}
