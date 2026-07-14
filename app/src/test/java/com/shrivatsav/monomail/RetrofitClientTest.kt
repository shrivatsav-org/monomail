package com.shrivatsav.monomail

import com.shrivatsav.monomail.core.network.remote.RetrofitClient
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

class RetrofitClientTest {

    @Test
    fun authFailedException_isIOException() {
        val ex = RetrofitClient.AuthFailedException("auth failed")
        assertTrue(
            "AuthFailedException must extend IOException for OkHttp interceptor compatibility",
            IOException::class.java.isAssignableFrom(ex::class.java)
        )
        assertEquals("auth failed", ex.message)
    }

    @Test
    fun authFailedException_holdsMessage() {
        val ex = RetrofitClient.AuthFailedException("Session expired. Please sign in again.")
        assertEquals("Session expired. Please sign in again.", ex.message)
    }

    @Test
    fun authFailedException_caughtAsIOException() {
        val result = runCatching { throw RetrofitClient.AuthFailedException("test") }
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun authFailedException_caughtAsException() {
        val result = runCatching { throw RetrofitClient.AuthFailedException("test") }
        assertTrue(result.exceptionOrNull() is Exception)
    }

    @Test
    fun cachedToken_defaultsToNull() {
        // Verifies the internal cachedToken AtomicReference starts null,
        // which forces the first request to use the seeded token value.
        val client = RetrofitClient(
            tokenRefresher = { null },
            onRefreshFailed = { },
            onHttpError = { }
        )
        assertNull("cachedToken should default to null", client.cachedToken.get())
    }

    @Test
    fun cachedToken_canBeSeeded() {
        val client = RetrofitClient(
            tokenRefresher = { "refreshed" },
            onRefreshFailed = { },
            onHttpError = { }
        )
        client.cachedToken.set("seed-token")
        assertEquals("seed-token", client.cachedToken.get())
    }

    @Test
    fun cachedToken_updateAfterRefresh() {
        val client = RetrofitClient(
            tokenRefresher = { "refreshed" },
            onRefreshFailed = { },
            onHttpError = { }
        )
        client.cachedToken.set("initial")
        client.cachedToken.set("updated")
        assertEquals("updated", client.cachedToken.get())
    }

    @Test
    fun cachedToken_holdsNull() {
        // Tokens can be explicitly cleared (e.g. after sign-out)
        val client = RetrofitClient(
            tokenRefresher = { null },
            onRefreshFailed = { },
            onHttpError = { }
        )
        client.cachedToken.set("seed")
        client.cachedToken.set(null)
        assertNull(client.cachedToken.get())
    }

    @Test
    fun cachedToken_roundTrip() {
        val client = RetrofitClient(
            tokenRefresher = { null },
            onRefreshFailed = { },
            onHttpError = { }
        )
        // Simulates lifecycle: seed -> refresh -> read
        client.cachedToken.set("a")
        assertEquals("a", client.cachedToken.get())
        client.cachedToken.set("b")
        assertEquals("b", client.cachedToken.get())
        client.cachedToken.set("c")
        assertEquals("c", client.cachedToken.get())
    }
}
