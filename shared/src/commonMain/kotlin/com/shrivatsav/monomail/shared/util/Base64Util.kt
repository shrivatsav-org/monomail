package com.shrivatsav.monomail.shared.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Multiplatform Base64 helpers backed by kotlin.io.encoding.Base64.
 * Replaces android.util.Base64 usage from the original Android code.
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64Util {

    // URL-safe alphabet, tolerant of missing padding (Gmail uses base64url, no padding).
    private val urlSafe = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Decode a base64url string (Gmail message bodies / attachments). */
    fun decodeUrl(data: String): ByteArray = urlSafe.decode(data.trim())

    /** Encode bytes as base64url without padding/line breaks (Gmail raw message). */
    fun encodeUrl(bytes: ByteArray): String = urlSafe.encode(bytes)

    /** Decode standard base64, tolerant of embedded line breaks (Outlook attachments). */
    fun decodeStandard(data: String): ByteArray = Base64.Mime.decode(data)

    /** Encode standard base64 with no line breaks (Graph attachment contentBytes). */
    fun encodeStandardNoWrap(bytes: ByteArray): String = Base64.encode(bytes)

    /** Encode standard base64 with MIME line wrapping + CRLF (MIME message bodies). */
    fun encodeMime(bytes: ByteArray): String = Base64.Mime.encode(bytes)
}
