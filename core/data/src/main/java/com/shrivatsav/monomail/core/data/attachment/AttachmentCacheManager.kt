package com.shrivatsav.monomail.core.data.attachment

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages temporary cached attachment files on disk.
 *
 * Bytes are written to [cacheDir]/attachments/ and exposed via FileProvider
 * so Media3, PdfRenderer, and other platform APIs can consume them from a Uri.
 * Reuses the same [cacheDir]/attachments/ directory and FileProvider authority
 * that the existing [openAttachment] flow in EmailDetailScreen already uses.
 */
@Singleton
class AttachmentCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File
        get() = File(context.cacheDir, "attachments").also { it.mkdirs() }

    /**
     * Write [bytes] to a temp file and return a content:// URI.
     * Existing file for the same (messageId, attachmentId) is overwritten.
     */
    fun cacheBytes(
        messageId: String,
        attachmentId: String,
        name: String,
        bytes: ByteArray
    ): Uri {
        val file = resolveFile(messageId, attachmentId, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Return a cached URI if the file exists, or null.
     */
    fun getCachedUri(
        messageId: String,
        attachmentId: String,
        name: String
    ): Uri? {
        val file = resolveFile(messageId, attachmentId, name)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Write bytes and return the underlying [File].
     * Useful for APIs that need a file path (PdfRenderer).
     */
    fun cacheFile(
        messageId: String,
        attachmentId: String,
        name: String,
        bytes: ByteArray
    ): File {
        val file = resolveFile(messageId, attachmentId, name)
        file.writeBytes(bytes)
        return file
    }

    /**
     * Remove stale cached files whose ids are not in [keepIds].
     * Called by the ViewModel when the viewer closes.
     */
    fun cleanExcept(keepIds: Set<String>) {
        cacheDir.listFiles()?.forEach { file ->
            val prefix = file.name.substringBeforeLast('_', "")
            if (prefix.isNotEmpty() && prefix !in keepIds) {
                file.delete()
            }
        }
    }

    /**
     * Build a file with a short name to avoid ENAMETOOLONG on long email IDs.
     * Pattern: <mid_short>_<aid_short>_<safe_name>
     */
    private fun resolveFile(messageId: String, attachmentId: String, name: String): File {
        val safeName = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val mid = messageId.take(12)
        val aid = attachmentId.take(12)
        val file = File(cacheDir, "${mid}_${aid}_$safeName")
        return file
    }

}
