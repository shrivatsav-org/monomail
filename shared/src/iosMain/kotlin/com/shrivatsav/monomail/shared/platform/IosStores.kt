package com.shrivatsav.monomail.shared.platform

import platform.Foundation.NSUserDefaults

/** iOS preferences store backed by NSUserDefaults. */
class IosKeyValueStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : KeyValueStore {
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
    override fun getBoolean(key: String): Boolean? =
        if (defaults.objectForKey(key) == null) null else defaults.boolForKey(key)
    override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = key)
    override fun getFloat(key: String): Float? =
        if (defaults.objectForKey(key) == null) null else defaults.floatForKey(key)
    override fun putFloat(key: String, value: Float) = defaults.setFloat(value, forKey = key)
    override fun remove(key: String) = defaults.removeObjectForKey(key)
    override fun clear() {
        defaults.dictionaryRepresentation().keys.forEach { k ->
            (k as? String)?.let { defaults.removeObjectForKey(it) }
        }
    }
}

/**
 * INTERIM iOS secure store backed by NSUserDefaults.
 *
 * TODO(security): replace with a Keychain-backed implementation using
 * kSecClassGenericPassword + kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
 * (no iCloud sync). Refresh tokens must NOT live in NSUserDefaults long-term.
 * Tracked as the next task in the security-hardening pass.
 */
class IosSecureStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val namespace: String = "secure."
) : SecureStore {
    override fun getString(key: String): String? = defaults.stringForKey(namespace + key)
    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = namespace + key)
    override fun remove(key: String) = defaults.removeObjectForKey(namespace + key)
    override fun clear() {
        defaults.dictionaryRepresentation().keys.forEach { k ->
            (k as? String)?.let { if (it.startsWith(namespace)) defaults.removeObjectForKey(it) }
        }
    }
}
