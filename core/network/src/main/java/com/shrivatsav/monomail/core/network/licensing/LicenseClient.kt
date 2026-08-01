package com.shrivatsav.monomail.core.network.licensing

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LicenseClient {

    sealed class ValidationResult {
        data class Valid(
            val email: String,
            val plan: String,
            val expiresAt: Long?
        ) : ValidationResult()

        object Invalid : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }

    companion object {
        private const val TAG = "LicenseClient"
        private const val BASE_URL = "https://monomail-push-backend.monomail.workers.dev"
        private const val VALIDATE_URL = "$BASE_URL/license/validate"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun validate(key: String): ValidationResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("key", key).toString()
            val request = Request.Builder()
                .url(VALIDATE_URL)
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: "{}"
            val json = JSONObject(responseBody)

            if (response.isSuccessful && json.optBoolean("valid", false)) {
                ValidationResult.Valid(
                    email = json.optString("email", ""),
                    plan = json.optString("plan", "premium"),
                    expiresAt = if (json.has("expiresAt")) json.optLong("expiresAt") else null
                )
            } else {
                ValidationResult.Invalid
            }
        } catch (e: Exception) {
            Log.e(TAG, "License validation failed", e)
            ValidationResult.Error(e.message ?: "Network error")
        }
    }
}
