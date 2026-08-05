package com.shrivatsav.monomail.core.network.provider.imap


import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Authentication method for IMAP/SMTP connections.
 * PASSWORD: Standard password auth (most providers)
 * APP_PASSWORD: App-specific password (Gmail, Yahoo)
 * XOAUTH2: OAuth2 token auth (Gmail API invite-only)
 */
enum class AuthMethod { PASSWORD, APP_PASSWORD, XOAUTH2 }


/**
 * Data model for IMAP/SMTP server configuration.
 * Serialized to JSON and stored encrypted in UserProfile.accessToken.
 */
data class ImapAccountConfig(
    @SerializedName("imapHost") val imapHost: String,
    @SerializedName("imapPort") val imapPort: Int,
    @SerializedName("imapSsl") val imapSsl: Boolean,
    @SerializedName("imapStartTls") val imapStartTls: Boolean,
    
    @SerializedName("smtpHost") val smtpHost: String,
    @SerializedName("smtpPort") val smtpPort: Int,
    @SerializedName("smtpSsl") val smtpSsl: Boolean,
    @SerializedName("smtpStartTls") val smtpStartTls: Boolean,
    
    @SerializedName("username") val username: String,
    
    @SerializedName("authMethod") val authMethod: AuthMethod = AuthMethod.PASSWORD,
    @SerializedName("displayName") val displayName: String = ""
) {
    /**
     * The SMTP settings actually used at send time, after fallbacks and
     * normalization:
     * - blank host falls back to the IMAP host (most providers serve SMTP
     *   on the same hostname)
     * - port is derived (465 SSL / 587 STARTTLS) when SMTP was left at
     *   setup defaults (blank host or port <= 0)
     * - 587 is the IANA STARTTLS submission port: a stored 587+SSL config
     *   (the setup UI defaults SSL on) normalizes to STARTTLS, since
     *   implicit TLS on 587 fails the handshake on most servers
     */
    fun effectiveSmtp(): EffectiveSmtp {
        val host = smtpHost.ifBlank { imapHost }
        val port = if (smtpHost.isBlank() || smtpPort <= 0) {
            if (smtpSsl) 465 else 587
        } else {
            smtpPort
        }
        val startTls = smtpStartTls || (smtpPort == 587 && smtpSsl)
        val useSsl = smtpSsl && !(smtpPort == 587 && !smtpStartTls)
        return EffectiveSmtp(host, port, startTls, useSsl)
    }

    /** A sender's FROM domain to use as the SMTP HELO hostname. */
    fun heloDomain(from: String): String =
        from.substringAfterLast("@").ifBlank { effectiveSmtp().host }

    fun toJson(): String = Gson().toJson(this)

    /** Returns true if this is a Gmail/Googlemail account */
    fun isGmail(): Boolean =
        imapHost.contains("gmail", ignoreCase = true) ||
        smtpHost.contains("gmail", ignoreCase = true) ||
        imapHost.contains("googlemail", ignoreCase = true)

    companion object {
        fun fromJson(json: String): ImapAccountConfig = Gson().fromJson(json, ImapAccountConfig::class.java)

        /**
         * Returns a preset configuration based on common email domains.
         */
        fun presetForHost(emailOrHost: String): ImapAccountConfig? {
            val lower = emailOrHost.lowercase()
            return when {
                lower.contains("zoho") -> ImapAccountConfig(
                    imapHost = "imap.zoho.com", imapPort = 993, imapSsl = true, imapStartTls = false,
                    smtpHost = "smtp.zoho.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false,
                    username = ""
                )
                lower.contains("gmail") || lower.contains("googlemail") -> ImapAccountConfig(
                    imapHost = "imap.gmail.com", imapPort = 993, imapSsl = true, imapStartTls = false,
                    smtpHost = "smtp.gmail.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false,
                    username = ""
                )
                lower.contains("yahoo") -> ImapAccountConfig(
                    imapHost = "imap.mail.yahoo.com", imapPort = 993, imapSsl = true, imapStartTls = false,
                    smtpHost = "smtp.mail.yahoo.com", smtpPort = 465, smtpSsl = true, smtpStartTls = false,
                    username = ""
                )
                lower.contains("outlook") || lower.contains("hotmail") -> ImapAccountConfig(
                    imapHost = "outlook.office365.com", imapPort = 993, imapSsl = true, imapStartTls = false,
                    smtpHost = "smtp-mail.outlook.com", smtpPort = 587, smtpSsl = false, smtpStartTls = true,
                    username = ""
                )
                else -> null
            }
        }
    }
}

/**
 * Resolved SMTP settings: what [ImapAccountConfig.effectiveSmtp] produces.
 */
data class EffectiveSmtp(
    val host: String,
    val port: Int,
    val startTls: Boolean,
    val useSsl: Boolean
)
