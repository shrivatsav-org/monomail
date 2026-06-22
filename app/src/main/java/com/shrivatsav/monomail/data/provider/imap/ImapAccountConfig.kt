package com.shrivatsav.monomail.data.provider.imap

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

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
    
    @SerializedName("username") val username: String
) {
    fun toJson(): String = Gson().toJson(this)
    
    companion object {
        fun fromJson(json: String): ImapAccountConfig = Gson().fromJson(json, ImapAccountConfig::class.java)

        /**
         * Returns a preset configuration based on common email domains.
         */
        fun presetForHost(emailOrHost: String): ImapAccountConfig? {
            val lower = emailOrHost.lowercase()
            return when {
                lower.contains("zoho") -> ImapAccountConfig(
                    imapHost = "imap.zoho.in", imapPort = 993, imapSsl = true, imapStartTls = false,
                    smtpHost = "smtp.zoho.in", smtpPort = 465, smtpSsl = true, smtpStartTls = false,
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
