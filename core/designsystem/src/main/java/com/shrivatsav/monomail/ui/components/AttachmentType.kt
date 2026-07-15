package com.shrivatsav.monomail.ui.components

import java.util.Locale

enum class AttachmentCategory {
    IMAGE,
    VIDEO,
    PDF,
    ARCHIVE,
    CODE,
    UNKNOWN
}

fun classifyAttachment(mimeType: String, name: String): AttachmentCategory {
    val mt = mimeType.lowercase()
    val lower = name.lowercase()

    if (mt.startsWith("image/") ||
        lower.endsWith(".png") || lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") || lower.endsWith(".gif") ||
        lower.endsWith(".webp") || lower.endsWith(".bmp") ||
        lower.endsWith(".svg")
    ) return AttachmentCategory.IMAGE

    if (mt.startsWith("video/") ||
        lower.endsWith(".mp4") || lower.endsWith(".mov") ||
        lower.endsWith(".avi") || lower.endsWith(".mkv") ||
        lower.endsWith(".webm") || lower.endsWith(".3gp")
    ) return AttachmentCategory.VIDEO

    if (mt == "application/pdf" || lower.endsWith(".pdf")) return AttachmentCategory.PDF

    if (mt.startsWith("application/") && (
                mt.contains("zip") || mt.contains("rar") || mt.contains("tar") ||
                mt.contains("gzip") || mt.contains("7z") || mt.contains("compress")
                ) ||
        lower.endsWith(".zip") || lower.endsWith(".rar") ||
        lower.endsWith(".7z") || lower.endsWith(".tar") ||
        lower.endsWith(".gz")
    ) return AttachmentCategory.ARCHIVE

    if (mt.startsWith("text/") ||
        lower.endsWith(".kt") || lower.endsWith(".java") ||
        lower.endsWith(".py") || lower.endsWith(".js") ||
        lower.endsWith(".ts") || lower.endsWith(".xml") ||
        lower.endsWith(".json") || lower.endsWith(".yml") ||
        lower.endsWith(".yaml") || lower.endsWith(".html") ||
        lower.endsWith(".css") || lower.endsWith(".sh") ||
        lower.endsWith(".md") || lower.endsWith(".sql")
    ) return AttachmentCategory.CODE

    return AttachmentCategory.UNKNOWN
}

fun isPreviewableInApp(category: AttachmentCategory): Boolean = when (category) {
    AttachmentCategory.IMAGE,
    AttachmentCategory.VIDEO,
    AttachmentCategory.PDF -> true
    else -> false
}

fun isImageAttachment(mimeType: String, name: String): Boolean =
    classifyAttachment(mimeType, name) == AttachmentCategory.IMAGE

fun isVideoAttachment(mimeType: String, name: String): Boolean =
    classifyAttachment(mimeType, name) == AttachmentCategory.VIDEO

fun isPdfAttachment(mimeType: String, name: String): Boolean =
    classifyAttachment(mimeType, name) == AttachmentCategory.PDF

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "${bytes / 1024} KB"
    bytes > 0 -> "$bytes B"
    else -> "Unknown size"
}
