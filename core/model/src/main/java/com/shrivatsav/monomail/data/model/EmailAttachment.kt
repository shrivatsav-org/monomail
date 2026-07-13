package com.shrivatsav.monomail.data.model
import android.net.Uri
data class EmailAttachment(
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String
)
