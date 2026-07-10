package com.shrivatsav.monomail.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.shrivatsav.monomail.BuildConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class RetrofitClient(
    private val tokenRefresher: () -> String?,
    private val onRefreshFailed: () -> Unit = {},
    private val onHttpError: ((code: Int) -> Unit)? = null,
) {
    // External callers can update the cached token (e.g. after a fresh sign-in).
    internal val cachedToken = AtomicReference<String?>(null)

    class AuthFailedException(message: String) : IOException(message)

    private fun obtainRefreshedToken(originalToken: String?): String? = synchronized(this) {
        val currentToken = cachedToken.get()
        if (currentToken != null && currentToken != originalToken) {
            currentToken
        } else {
            tokenRefresher()?.also { cachedToken.set(it) }
        }
    }

    private fun isAuthError(code: Int) = code == 401 || code == 403

    private fun createAuthInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val token = cachedToken.get()
        val newRequest = if (token != null) {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            request
        }
        val response = chain.proceed(newRequest)

        if (!isAuthError(response.code)) {
            if (response.code in 400..599) onHttpError?.invoke(response.code)
            return@Interceptor response
        }

        response.close()
        val retryResponse = retryWithTokenRefresh(request, chain, token)
        if (retryResponse != null) return@Interceptor retryResponse

        onRefreshFailed()
        throw AuthFailedException("Authentication failed. Please sign in again.")
    }

    private fun retryWithTokenRefresh(
        request: okhttp3.Request,
        chain: Interceptor.Chain,
        originalToken: String?
    ): okhttp3.Response? {
        for (attempt in 1..2) {
            val newToken = obtainRefreshedToken(originalToken)
            if (newToken != null) {
                val retryRequest = request.newBuilder().header("Authorization", "Bearer $newToken").build()
                val retryResponse = chain.proceed(retryRequest)
                if (!isAuthError(retryResponse.code)) return retryResponse
                retryResponse.close()
            }
            if (attempt < 2) Thread.sleep(500L * attempt)
        }
        return null
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                    else HttpLoggingInterceptor.Level.NONE
        }
    }
    private val baseHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
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
