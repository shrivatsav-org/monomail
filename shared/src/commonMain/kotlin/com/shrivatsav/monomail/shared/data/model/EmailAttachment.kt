package com.shrivatsav.monomail.shared.data.model

/**
 * An attachment selected on the UI side to be sent with an outgoing email.
 *
 * Platform-neutral: the UI layer (Android content resolver / iOS document picker)
 * is responsible for reading the file contents into [bytes] before handing it to
 * the shared module. No `android.net.Uri` here.
 */
data class EmailAttachment(
    val name: String,
    val size: Long,
    val mimeType: String,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmailAttachment) return false
        return name == other.name &&
            size == other.size &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
