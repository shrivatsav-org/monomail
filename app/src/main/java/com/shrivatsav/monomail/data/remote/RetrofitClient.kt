package com.shrivatsav.monomail.data.remote

import com.shrivatsav.monomail.auth.AuthTokenManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient(
    private val accountId: String,
    private val tokenManager: AuthTokenManager,
    private val onRefreshFailed: () -> Unit = {},
) {
    private fun createAuthInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val token = tokenManager.getCachedToken(accountId)
        val newRequest = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)
        if (response.code == 401) {
            val newToken = tokenManager.refreshTokenBlocking(accountId)
            if (newToken != null) {
                response.close()
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
            onRefreshFailed()
        }
        response
    }
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }
    // ponytail: single client, only baseUrl differs
    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .build()
    private val gsonFactory = GsonConverterFactory.create()
    private val gmailRetrofit = Retrofit.Builder()
        .baseUrl("https://gmail.googleapis.com/gmail/v1/")
        .client(httpClient)
        .addConverterFactory(gsonFactory)
        .build()
    private val outlookRetrofit = Retrofit.Builder()
        .baseUrl("https://graph.microsoft.com/v1.0/")
        .client(httpClient)
        .addConverterFactory(gsonFactory)
        .build()
    val gmailApi: GmailApi = gmailRetrofit.create(GmailApi::class.java)
    val outlookApi: OutlookApi = outlookRetrofit.create(OutlookApi::class.java)
}
