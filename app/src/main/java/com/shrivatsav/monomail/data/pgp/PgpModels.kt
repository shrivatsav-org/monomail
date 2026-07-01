package com.shrivatsav.monomail.data.pgp

data class PgpKeyInfo(
    val fingerprint: String,
    val userId: String,
    val algorithm: String,
    val creationDate: Long,
    val isPrivate: Boolean,
    val isExpired: Boolean,
    val isPassphraseProtected: Boolean = false
)

data class PgpSignature(
    val isValid: Boolean,
    val signer: String
)

data class PgpDecryptionResult(
    val decryptedBody: String,
    val signatures: List<PgpSignature>? = null,
    val needsPassphrase: Boolean = false,
    val fingerprint: String? = null
)

data class PgpEncryptionResult(
    val encryptedBody: String,
    val contentType: String = "multipart/encrypted; protocol=\"application/pgp-encrypted\""
)
