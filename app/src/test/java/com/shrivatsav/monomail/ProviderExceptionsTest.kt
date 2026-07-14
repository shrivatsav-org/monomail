package com.shrivatsav.monomail

import com.shrivatsav.monomail.core.network.provider.ResourceNotFoundException
import org.junit.Test
import org.junit.Assert.*

class ProviderExceptionsTest {

    @Test
    fun resourceNotFoundException_holdsMessage() {
        val ex = ResourceNotFoundException("Thread abc not found")
        assertEquals("Thread abc not found", ex.message)
    }

    @Test
    fun resourceNotFoundException_holdsCause() {
        val cause = RuntimeException("HTTP 404")
        val ex = ResourceNotFoundException("Thread abc not found", cause)
        assertEquals("Thread abc not found", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun resourceNotFoundException_withoutCause_hasNullCause() {
        val ex = ResourceNotFoundException("gone")
        assertNull(ex.cause)
    }

    @Test
    @Suppress("USELESS_IS_CHECK")
    fun resourceNotFoundException_isException() {
        val ex = ResourceNotFoundException("x")
        assertTrue(ex is Exception)
    }

    @Test
    fun resourceNotFoundException_caughtAsException() {
        // Verifies catch blocks in EmailRepository and ActionQueueManager
        // can catch ResourceNotFoundException via its Exception supertype
        val result = runCatching { throw ResourceNotFoundException("test") }
        assertTrue(result.exceptionOrNull() is Exception)
        assertEquals("test", result.exceptionOrNull()?.message)
    }

    @Test
    fun resourceNotFoundException_caughtAsThrowable() {
        val result = runCatching { throw ResourceNotFoundException("caught") }
        assertTrue(result.exceptionOrNull() is Throwable)
        assertEquals("caught", result.exceptionOrNull()?.message)
    }

    @Test
    fun resourceNotFoundException_messageNeverNull() {
        val ex = ResourceNotFoundException("")
        assertNotNull(ex.message)
    }

    @Test
    fun resourceNotFoundException_chainPreservesMessage() {
        val inner = ResourceNotFoundException("inner")
        val outer = ResourceNotFoundException("outer", inner)
        assertEquals("outer", outer.message)
        assertSame(inner, outer.cause)
        assertEquals("inner", outer.cause?.message)
    }
}
