package com.shrivatsav.monomail.push

import com.shrivatsav.monomail.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushBackendClient @Inject constructor() {
    private val okHttpClient = OkHttpClient()

    /** The backend uses this short-lived provider token to verify ownership and create
     * provider subscriptions, without persisting the token. */
    suspend fun registerDevice(
        accountId: String,
        email: String,
        fcmToken: String,
        installationId: String,
        provider: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.PUSH_BACKEND_URL
            if (baseUrl.isBlank()) {
                return@withContext Result.failure(Exception("PUSH_BACKEND_URL is not configured"))
            }

            val json = JSONObject().apply {
                put("accountId", accountId)
                put("email", email)
                put("fcmToken", fcmToken)
                put("installationId", installationId)
                put("provider", provider)
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/register")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val errorBody = response.body?.string() ?: ""
                    Result.failure(Exception("Failed to register device: ${response.code} ${response.message}. Body: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Remove this device's FCM mapping from the backend. */
    suspend fun unregisterDevice(
        accountId: String,
        email: String,
        installationId: String,
        provider: String,
        accessToken: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.PUSH_BACKEND_URL
            if (baseUrl.isBlank()) return@withContext Result.success(Unit)

            val json = JSONObject().apply {
                put("accountId", accountId)
                put("email", email)
                put("installationId", installationId)
                put("provider", provider)
            }

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/unregister")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else {
                    val errorBody = response.body?.string() ?: ""
                    Result.failure(Exception("Unregister failed: ${response.code}. Body: $errorBody"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

}
