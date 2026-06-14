package com.shrivatsav.monomail.data.remote

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitClient(
    private val tokenProvider: (provider: String) -> String?,
    private val tokenRefresher: (provider: String) -> String?,
) {

    private fun createAuthInterceptor(provider: String) = Interceptor { chain ->
        val token = tokenProvider(provider)
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        val response = chain.proceed(request)

        if (response.code == 401) {
            response.close()
            val newToken = tokenRefresher(provider)
            if (newToken != null) {
                val retryRequest = chain.request().newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
        }
        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val gmailHttpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor("gmail"))
        .addInterceptor(loggingInterceptor)
        .build()

    private val outlookHttpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor("outlook"))
        .addInterceptor(loggingInterceptor)
        .build()

    private val gmailRetrofit = Retrofit.Builder()
        .baseUrl("https://gmail.googleapis.com/gmail/v1/")
        .client(gmailHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val outlookRetrofit = Retrofit.Builder()
        .baseUrl("https://graph.microsoft.com/v1.0/")
        .client(outlookHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val gmailApi: GmailApi = gmailRetrofit.create(GmailApi::class.java)
    val outlookApi: OutlookApi = outlookRetrofit.create(OutlookApi::class.java)
}
