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
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
class RetrofitClient(
    tokenProvider: () -> String?,
    private val tokenRefresher: () -> String?,
    private val onRefreshFailed: () -> Unit = {},
) {
    private val cachedToken = AtomicReference(tokenProvider())
    class AuthFailedException(message: String) : IOException(message)

    private fun createAuthInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val token = cachedToken.get()
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
                cachedToken.set(newToken)
                response.close()
                val retryRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            }
            response.close()
            onRefreshFailed()
            throw AuthFailedException("Authentication failed. Please sign in again.")
        }
        response
    }
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    private val baseHttpClient = OkHttpClient.Builder()
        .addInterceptor(createAuthInterceptor())
        .addInterceptor(loggingInterceptor)
        .build()
    private val gsonConverter = GsonConverterFactory.create()
    private val gmailRetrofit = Retrofit.Builder()
        .baseUrl("https://gmail.googleapis.com/gmail/v1/")
        .client(baseHttpClient)
        .addConverterFactory(gsonConverter)
        .build()
    private val outlookRetrofit = Retrofit.Builder()
        .baseUrl("https://graph.microsoft.com/v1.0/")
        .client(baseHttpClient)
        .addConverterFactory(gsonConverter)
        .build()
    val gmailApi: GmailApi = gmailRetrofit.create(GmailApi::class.java)
    val outlookApi: OutlookApi = outlookRetrofit.create(OutlookApi::class.java)
}
