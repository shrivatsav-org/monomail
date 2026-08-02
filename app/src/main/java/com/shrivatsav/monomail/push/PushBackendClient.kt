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

    /** Register this device's FCM token for push delivery. No OAuth token sent — the app
     *  calls Gmail watch / Graph subscriptions directly before calling this. */
    suspend fun registerDevice(
        accountId: String,
        email: String,
        fcmToken: String,
        provider: String
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
                put("provider", provider)
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/register")
                .addHeader("X-Api-Key", BuildConfig.PUSH_API_KEY)
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
        email: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val baseUrl = BuildConfig.PUSH_BACKEND_URL
            if (baseUrl.isBlank()) return@withContext Result.success(Unit)

            val json = JSONObject().apply {
                put("accountId", accountId)
                put("email", email)
            }

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/unregister")
                .addHeader("X-Api-Key", BuildConfig.PUSH_API_KEY)
                .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Unregister failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Call Gmail watch API directly from the device (app holds the token — no need to send it
     *  to the backend). */
    suspend fun callGmailWatch(
        accessToken: String,
        pubSubTopic: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("topicName", pubSubTopic)
                put("labelIds", org.json.JSONArray().put("INBOX"))
            }
            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/watch")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Gmail watch failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Call Gmail stop API to cancel push notifications for this account. */
    suspend fun callGmailStop(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://gmail.googleapis.com/gmail/v1/users/me/stop")
                .addHeader("Authorization", "Bearer $accessToken")
                .post("".toRequestBody())
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.success(Unit)
                else Result.failure(Exception("Gmail stop failed: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
