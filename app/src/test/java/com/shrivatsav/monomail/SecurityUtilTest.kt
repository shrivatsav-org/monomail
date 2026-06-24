package com.shrivatsav.monomail

import org.junit.Ignore
import org.junit.Test
import org.junit.Assert.*

@Ignore("requires Android Keystore / EncryptedSharedPreferences — run on device or with Robolectric")
class SecurityUtilTest {
    @Test
    fun encryption_roundtrip_returnsOriginal() {
        val original = "sensitive-data-123"
        val encrypted = com.shrivatsav.monomail.security.SecurityUtil.encryptString(original)
        assertNotNull(encrypted)
        assertNotEquals(original, encrypted)
        val decrypted = com.shrivatsav.monomail.security.SecurityUtil.decryptString(encrypted)
        assertEquals(original, decrypted)
    }

    @Test
    fun decrypt_invalidData_returnsNull() {
        val result = com.shrivatsav.monomail.security.SecurityUtil.decryptString("invalid-base64")
        assertNull(result)
    }
}
