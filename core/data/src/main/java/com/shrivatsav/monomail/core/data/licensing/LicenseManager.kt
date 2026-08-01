package com.shrivatsav.monomail.core.data.licensing

import android.content.Context
import android.util.Log
import com.shrivatsav.monomail.core.network.licensing.LicenseClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LicenseManager(context: Context) {

    sealed class ValidationResult {
        data class Valid(val email: String, val plan: String, val expiresAt: Long?) : ValidationResult()
        object Invalid : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    companion object {
        private const val TAG = "LicenseManager"
        private const val PREFS_NAME = "monomail_license"
        private const val KEY_LICENSE_KEY = "license_key"
        private const val KEY_IS_VALID = "is_valid"
        private const val KEY_EMAIL = "email"
        private const val KEY_PLAN = "plan"
        private const val KEY_VALIDATED_AT = "validated_at"
        private const val CACHE_DURATION_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    }

    private val licenseClient = LicenseClient()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isLicensed = MutableStateFlow(false)
    val isLicensed: StateFlow<Boolean> = _isLicensed.asStateFlow()

    init {
        _isLicensed.value = prefs.getBoolean(KEY_IS_VALID, false)
    }

    suspend fun checkLicense(): Boolean {
        val cachedKey = prefs.getString(KEY_LICENSE_KEY, null)

        if (cachedKey != null && isCacheFresh()) {
            val cached = prefs.getBoolean(KEY_IS_VALID, false)
            _isLicensed.value = cached
            return cached
        }

        if (cachedKey != null) {
            return validateAndCache(cachedKey)
        }

        _isLicensed.value = false
        return false
    }

    suspend fun activateLicense(key: String): Boolean {
        return validateAndCache(key)
    }

    fun clearLicense() {
        prefs.edit().clear().apply()
        _isLicensed.value = false
    }

    fun getCachedEmail(): String? = prefs.getString(KEY_EMAIL, null)

    fun getCachedLicenseKey(): String? = prefs.getString(KEY_LICENSE_KEY, null)

    fun getCachedPlan(): String? = prefs.getString(KEY_PLAN, null)

    private suspend fun validateAndCache(key: String): Boolean {
        return when (val result = licenseClient.validate(key)) {
            is LicenseClient.ValidationResult.Valid -> {
                prefs.edit()
                    .putString(KEY_LICENSE_KEY, key)
                    .putBoolean(KEY_IS_VALID, true)
                    .putString(KEY_EMAIL, result.email)
                    .putString(KEY_PLAN, result.plan)
                    .putLong(KEY_VALIDATED_AT, System.currentTimeMillis())
                    .apply()
                _isLicensed.value = true
                Log.d(TAG, "License activated for ${result.email}")
                true
            }
            is LicenseClient.ValidationResult.Invalid -> {
                prefs.edit()
                    .putString(KEY_LICENSE_KEY, key)
                    .putBoolean(KEY_IS_VALID, false)
                    .apply()
                _isLicensed.value = false
                false
            }
            is LicenseClient.ValidationResult.Error -> {
                val cachedKey = prefs.getString(KEY_LICENSE_KEY, null)
                if (cachedKey == key) {
                    _isLicensed.value = prefs.getBoolean(KEY_IS_VALID, false)
                } else {
                    _isLicensed.value = false
                }
                false
            }
        }
    }

    private fun isCacheFresh(): Boolean {
        val lastValidation = prefs.getLong(KEY_VALIDATED_AT, 0)
        return System.currentTimeMillis() - lastValidation < CACHE_DURATION_MS
    }
}
