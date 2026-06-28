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

    suspend fun registerDevice(
        accountId: String,
        email: String,
        fcmToken: String,
        accessToken: String,
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
                put("accessToken", accessToken)
                put("provider", provider)
            }

            val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/register")
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
}
