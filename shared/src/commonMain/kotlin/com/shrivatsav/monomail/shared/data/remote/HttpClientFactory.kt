package com.shrivatsav.monomail.shared.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

private val sharedJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}

/**
 * Builds a Ktor client using the platform engine on the classpath
 * (Darwin on iOS, Android engine on Android). If [bearerToken] is provided,
 * it is attached to every request.
 */
fun createJsonHttpClient(bearerToken: String? = null): HttpClient = HttpClient {
    install(ContentNegotiation) { json(sharedJson) }
    if (bearerToken != null) {
        defaultRequest { header(HttpHeaders.Authorization, "Bearer $bearerToken") }
    }
}
