package com.shrivatsav.monomail.util

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.util.regex.Pattern

data class NormalizedEmailBody(
    val text: String,
    val isHtml: Boolean
)

fun normalizeEmailBody(rawBody: String, bodyIsHtml: Boolean): NormalizedEmailBody {
    val extracted = extractProviderBodyText(rawBody.trim())
    val text = extracted ?: rawBody
    return NormalizedEmailBody(
        text = text,
        isHtml = bodyIsHtml || looksLikeHtml(text)
    )
}

// ponytail: runs on composition thread via remember{}; move to background
// dispatcher when HTML prep is refactored out of compose
fun stripUnsafeHtml(html: String): String {
    var result = html
    result = META_REFRESH.matcher(result).replaceAll("")
    result = IFRAME.matcher(result).replaceAll("")
    result = OBJECT.matcher(result).replaceAll("")
    result = EMBED.matcher(result).replaceAll("")
    return result
}

private fun extractProviderBodyText(trimmedBody: String): String? {
    if (!(trimmedBody.startsWith("[") || trimmedBody.startsWith("{"))) return null

    return runCatching {
        val root = JsonParser.parseString(trimmedBody)
        findBodyText(root)?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun findBodyText(element: JsonElement?): String? {
    if (element == null || element.isJsonNull) return null

    if (element.isJsonPrimitive && element.asJsonPrimitive.isString) {
        val value = element.asString
        return value.takeIf { looksLikeHtml(it) }
    }

    if (element.isJsonArray) {
        element.asJsonArray.forEach { child ->
            findBodyText(child)?.let { return it }
        }
        return null
    }

    if (element.isJsonObject) {
        val obj = element.asJsonObject
        listOf("html", "text", "content", "body").forEach { key ->
            val value = obj.get(key)
            if (value?.isJsonPrimitive == true && value.asJsonPrimitive.isString) {
                return value.asString
            }
        }
        obj.entrySet().forEach { (_, value) ->
            findBodyText(value)?.let { return it }
        }
    }

    return null
}

private fun looksLikeHtml(text: String): Boolean =
    HTML_TAG.matcher(text).find()

private val META_REFRESH = Pattern.compile(
    """<meta\s+[^>]*http-equiv\s*=\s*["']?refresh["']?[^>]*\s*/?>""",
    Pattern.CASE_INSENSITIVE
)
private val IFRAME = Pattern.compile(
    """<iframe[^>]*>.*?</iframe>""",
    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
)
private val OBJECT = Pattern.compile(
    """<object[^>]*>.*?</object>""",
    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
)
private val EMBED = Pattern.compile(
    """<embed\s+[^>]*/?>""",
    Pattern.CASE_INSENSITIVE
)
private val HTML_TAG = Pattern.compile(
    """</?(html|head|body|table|tbody|tr|td|div|span|p|br|a|img|style|strong|em|ul|ol|li|blockquote)\b""",
    Pattern.CASE_INSENSITIVE
)
