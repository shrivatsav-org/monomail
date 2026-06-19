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
    private val tokenProvider: () -> String?,
    private val tokenRefresher: () -> String?,
    private val onRefreshFailed: () -> Unit = {},
) {
    private fun createAuthInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val token = tokenProvider()
        val newRequest = if (token != null) {
            request.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)
        if (response.code == 401) {
            val newToken = tokenRefresher()
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
    private val gmailHttpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .build()
    private val outlookHttpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor())
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
