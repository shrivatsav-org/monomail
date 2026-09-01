package com.shrivatsav.monomail.core.network.mapper

import com.shrivatsav.monomail.core.network.provider.ProviderContentException
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class GmailBodyDecoderTest {
    @Test
    fun decodesLargeBodyWithoutDroppingContent() {
        val body = "message body ".repeat(100_000)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(body.toByteArray())

        assertEquals(body, decodeGmailBody(encoded))
    }

    @Test(expected = ProviderContentException::class)
    fun invalidBodyIsAContentFailure() {
        decodeGmailBody("%%%not-base64%%")
    }
}
