package com.shrivatsav.monomail.data.pgp

data class PgpKeyInfo(
    val fingerprint: String,
    val userId: String,
    val algorithm: String,
    val creationDate: Long,
    val isPrivate: Boolean,
    val isExpired: Boolean
)

data class PgpSignature(
    val isValid: Boolean,
    val signer: String
)

data class PgpDecryptionResult(
    val decryptedBody: String,
    val signatures: List<PgpSignature>? = null
)

data class PgpEncryptionResult(
    val encryptedBody: String,
    val contentType: String = "multipart/encrypted; protocol=\"application/pgp-encrypted\""
)
