package com.shrivatsav.monomail.core.data.licensing

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.shrivatsav.monomail.core.data.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object LicenseCheckerHolder {

    fun create(context: Context): LicenseChecker {
        // Debug builds are sideloaded and would always fail the verdict
        // (package not installed via Play with this signature) — treat as
        // licensed so local development keeps working.
        return if (BuildConfig.DEBUG) DebugLicenseChecker
        else PlayIntegrityLicenseChecker(context.applicationContext)
    }
}

/** Dev convenience: debug builds are always licensed. */
private object DebugLicenseChecker : LicenseChecker {
    override fun isCachedLicensed(): Boolean = true
    override suspend fun check(): Boolean = true
    override fun clearCache() {}
}

/**
 * Play Integrity licensing verdict (`appLicensingVerdict`). Requires Google
 * Play services; fails closed (unlicensed) when unavailable. A failed network
 * check falls back to the last cached verdict (offline grace).
 */
private class PlayIntegrityLicenseChecker(context: Context) : LicenseChecker {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var memoryCache: Boolean? = null

    override fun isCachedLicensed(): Boolean =
        memoryCache ?: prefs.getBoolean(KEY_LICENSED, false)

    override suspend fun check(): Boolean = mutex.withLock {
        val verdict = withContext(Dispatchers.IO) {
            try {
                val manager = IntegrityManagerFactory.create(appContext)
                val request = IntegrityTokenRequest.builder().build()
                val response = Tasks.await(
                    manager.requestIntegrityToken(request),
                    TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
                parseLicensingVerdict(response.token())
            } catch (e: Exception) {
                Log.w(TAG, "Integrity check failed", e)
                null
            }
        }
        val result = when (verdict) {
            "LICENSED" -> true
            "UNLICENSED" -> false
            // null = error/offline: keep the last cached verdict (grace), else fail closed
            else -> memoryCache ?: prefs.getBoolean(KEY_LICENSED, false)
        }
        memoryCache = result
        prefs.edit().putBoolean(KEY_LICENSED, result).apply()
        result
    }

    override fun clearCache() {
        memoryCache = null
        prefs.edit().remove(KEY_LICENSED).apply()
    }

    private fun parseLicensingVerdict(token: String): String? {
        // JWS compact serialization: header.payload.signature (all base64url)
        val parts = token.split('.')
        if (parts.size < 2) {
            Log.w(TAG, "Integrity token is not a JWS")
            return null
        }
        val payload = decodeBase64Url(parts[1]) ?: return null
        return JSONObject(payload)
            .optJSONObject("appLicensing")
            ?.optString("appLicensingVerdict")
            ?.takeIf { it.isNotBlank() }
    }

    private fun decodeBase64Url(input: String): String? {
        return try {
            val padded = input.replace('-', '+').replace('_', '/')
                .padEnd(((input.length + 3) / 4) * 4, '=')
            String(android.util.Base64.decode(padded, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode integrity token", e)
            null
        }
    }

    private companion object {
        const val TAG = "PlayLicensing"
        const val PREFS_NAME = "monomail_license"
        const val KEY_LICENSED = "play_licensed"
        const val TIMEOUT_SECONDS = 15L
    }
}
