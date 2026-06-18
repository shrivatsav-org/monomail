package com.shrivatsav.monomail.shared.platform

import android.content.Context

/** Android preferences store backed by SharedPreferences. */
class AndroidKeyValueStore(
    context: Context,
    name: String = "app_settings"
) : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = if (prefs.contains(key)) prefs.getString(key, null) else null
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun getBoolean(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
    override fun putBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()
    override fun getFloat(key: String): Float? = if (prefs.contains(key)) prefs.getFloat(key, 0f) else null
    override fun putFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun clear() = prefs.edit().clear().apply()
}

/**
 * INTERIM Android secure store backed by plain SharedPreferences.
 * TODO(security): replace with EncryptedSharedPreferences (androidx.security.crypto)
 * to restore the original encrypted-at-rest token storage.
 */
class AndroidSecureStore(
    context: Context,
    name: String = "secure_prefs"
) : SecureStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = if (prefs.contains(key)) prefs.getString(key, null) else null
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun remove(key: String) = prefs.edit().remove(key).apply()
    override fun clear() = prefs.edit().clear().apply()
}
