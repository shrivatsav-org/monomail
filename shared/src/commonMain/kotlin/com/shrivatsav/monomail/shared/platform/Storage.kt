package com.shrivatsav.monomail.shared.platform

/**
 * Plain key/value storage for non-secret app preferences.
 * Android actual: SharedPreferences. iOS actual: NSUserDefaults.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String): Boolean?
    fun putBoolean(key: String, value: Boolean)
    fun getFloat(key: String): Float?
    fun putFloat(key: String, value: Float)
    fun remove(key: String)
    fun clear()
}

/**
 * Secure storage for secrets (OAuth refresh tokens, encrypted-account JSON,
 * the Android SQLCipher key). Android actual: EncryptedSharedPreferences.
 * iOS actual: Keychain (kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly, no iCloud sync).
 */
interface SecureStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
    fun clear()
}
