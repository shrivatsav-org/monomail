package com.shrivatsav.monomail.util

/**
 * Lightweight HTML sanitizer for email body content.
 *
 * Strips dangerous/undesirable elements and attributes from HTML that arrives
 * via email providers, while preserving the visual content.  Designed for
 * WebView rendering where JavaScript is already disabled — this is an extra
 * defence-in-depth layer, not the primary security boundary.
 *
 * No Jsoup dependency is needed; all operations are regex / string-based and
 * cover the common attack surface in HTML email.
 */
object HtmlSanitizer {

    /**
     * Sanitize raw HTML from an email body.
     *
     * @param html  The raw HTML to clean.
     * @return      Cleaned HTML safe for display.
     */
    fun sanitize(html: String): String {
        if (html.isBlank()) return html

        var result = html

        // 1. Strip <base> — prevents an attacker from hijacking relative URLs
        result = BASE_TAG_REGEX.replace(result, "")

        // 2. Strip <script> and </script> and everything between
        result = SCRIPT_TAG_REGEX.replace(result, "")

        // 3. Strip <iframe>, <object>, <embed> and everything between
        result = IFRAME_TAG_REGEX.replace(result, "")
        result = OBJECT_TAG_REGEX.replace(result, "")
        result = EMBED_TAG_REGEX.replace(result, "")

        // 4. Strip <form>, <input>, <button>, <select>, <textarea> and everything between
        result = FORM_TAG_REGEX.replace(result, "")
        result = INPUT_TAG_REGEX.replace(result, "")
        result = BUTTON_TAG_REGEX.replace(result, "")
        result = SELECT_TAG_REGEX.replace(result, "")
        result = TEXTAREA_TAG_REGEX.replace(result, "")

        // 5. Strip <style> tags that import external resources
        //    Keep inline <style> but remove @import and url() that point to external resources
        result = STYLE_IMPORT_REGEX.replace(result, "")

        // 6. Strip event-handler attributes (onclick, onerror, onload, onmouseover, etc.)
        result = EVENT_HANDLER_REGEX.replace(result, "")

        // 7. Strip javascript: and vbscript: in href attributes (keep data:image URIs)
        result = JS_HREF_REGEX.replace(result, "\"#$1\"")

        // 8. Strip <meta> tags that could cause redirects
        result = META_REFRESH_REGEX.replace(result, "")

        // 9. Strip data: URIs in href that aren't images (potential XSS via data:text/html, etc.)
        result = BAD_DATA_HREF_REGEX.replace(result, "")

        return result.trim()
    }

    /**
     * Strip all `<style>` blocks from email HTML.
     *
     * Email <style> tags contain color/background/font rules that override our
     * dark-mode CSS (since they appear later in the DOM). Removing them lets
     * our injected <head> styles take full control in "adapt" mode.
     *
     * In "original" mode this should NOT be called so the email renders as-sent.
     */
    fun stripStyleTags(html: String): String {
        if (html.isBlank()) return html
        return STYLE_TAG_REGEX.replace(html, "")
    }

    /**
     * Strip `bgcolor` HTML attributes from all tags.
     *
     * `bgcolor` is an old-school HTML attribute (not CSS) so CSS selectors
     * like `[style*="background"]` don't catch it. Removing the attribute
     * ensures dark-mode background overrides work correctly.
     */
    fun stripBgcolorAttrs(html: String): String {
        if (html.isBlank()) return html
        return BGCOLOR_ATTR_REGEX.replace(html, "")
    }

    /**
     * Strip fixed-width HTML attributes (`width="600"` etc.) from tables and
     * images so CSS `max-width: 100%` can make them responsive.
     */
    fun stripFixedWidthAttrs(html: String): String {
        if (html.isBlank()) return html
        return FIXED_WIDTH_ATTR_REGEX.replace(html, "")
    }

    /**
     * Strips quoted text from the email body.
     */
    fun stripQuotedText(html: String): String {
        var result = html
        result = result.replace(Regex("<blockquote[^>]*>[\\s\\S]*?</blockquote>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<div\\s+class=\"gmail_quote\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<div\\s+class=\"gmail_extra\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("<br>\\s*On .{10,80} wrote:\\s*<br>", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("\\n\\s*On .{10,80} wrote:\\s*\\n", RegexOption.IGNORE_CASE), "")
        result = result.replace(Regex("(^|<br>)(&gt;|>)\\s?.*", RegexOption.IGNORE_CASE), "")
        // Outlook web / 365 quoted text markers
        result = result.replace(Regex("<div\\s+id=\"appendonsend\"[^>]*>\\s*</div>", RegexOption.IGNORE_CASE), "")
        // divRplyFwdMsg contains nested <div> elements — use depth-counting to strip fully
        result = stripOutlookPreviousMessage(result)

        // Clean up trailing empty space, <br> tags, and empty paragraphs left behind
        var previous = ""
        while (previous != result) {
            previous = result
            // Remove empty <p> or <div> at the end
            result = result.replace(Regex("(?:<(p|div)[^>]*>(?:<br\\s*/?>|\\s|&nbsp;)*</\\1>)+(?=</div>|</body>|$)", RegexOption.IGNORE_CASE), "")
            // Remove trailing <br> tags or spaces
            result = result.replace(Regex("(?:<br\\s*/?>|\\s|&nbsp;)+(?=</div>|</body>|$)", RegexOption.IGNORE_CASE), "")
        }

        return result.trim()
    }

    /**
     * Strips the Outlook forward/reply section completely, including:
     * - the &lt;div id="appendonsend"&gt; marker
     * - the &lt;hr&gt; separator
     * - the &lt;div id="divRplyFwdMsg"&gt; headers (From/Sent/To/Subject)
     * - any content after it up to &lt;/body&gt; (the previous message body)
     */
    private fun stripOutlookPreviousMessage(html: String): String {
        var result = html

        // Find appendonsend marker first (it precedes the forward block)
        val appendonPattern = Regex("<div\\s+[^>]*id=\"appendonsend\"[^>]*>\\s*</div>", RegexOption.IGNORE_CASE)
        val appendonMatch = appendonPattern.find(result)

        // Find divRplyFwdMsg
        val fwdPattern = Regex("<div\\s+[^>]*id=\"divRplyFwdMsg\"[^>]*>", RegexOption.IGNORE_CASE)
        val fwdMatch = fwdPattern.find(result)

        if (appendonMatch == null && fwdMatch == null) return result

        // Determine strip start: use appendonsend if found, otherwise divRplyFwdMsg
        val stripStart = when {
            appendonMatch != null -> appendonMatch.range.first
            fwdMatch != null -> fwdMatch.range.first
            else -> return result
        }

        // Strip everything from stripStart to </body> (or end of string)
        val bodyEnd = result.indexOf("</body>", ignoreCase = true, startIndex = stripStart)
        val effectiveEnd = if (bodyEnd >= stripStart) bodyEnd else result.length

        result = result.substring(0, stripStart) + result.substring(effectiveEnd)

        // Clean up any remaining <hr> separators
        result = result.replace(Regex("\\s*<hr[^>]*>\\s*", RegexOption.IGNORE_CASE), " ")

        return result.trim()
    }

    // region Regex patterns

    private val BASE_TAG_REGEX = Regex(
        """<base\b[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    private val SCRIPT_TAG_REGEX = Regex(
        """<script\b[^>]*>[\s\S]*?<\/script\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val IFRAME_TAG_REGEX = Regex(
        """<iframe\b[^>]*>[\s\S]*?<\/iframe\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val OBJECT_TAG_REGEX = Regex(
        """<object\b[^>]*>[\s\S]*?<\/object\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val EMBED_TAG_REGEX = Regex(
        """<embed\b[^>]*>[\s\S]*?<\/embed\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val FORM_TAG_REGEX = Regex(
        """<form\b[^>]*>[\s\S]*?<\/form\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val INPUT_TAG_REGEX = Regex(
        """<input\b[^>]*>""",
        RegexOption.IGNORE_CASE
    )

    private val BUTTON_TAG_REGEX = Regex(
        """<button\b[^>]*>[\s\S]*?<\/button\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val SELECT_TAG_REGEX = Regex(
        """<select\b[^>]*>[\s\S]*?<\/select\s*>""",
        RegexOption.IGNORE_CASE
    )

    private val TEXTAREA_TAG_REGEX = Regex(
        """<textarea\b[^>]*>[\s\S]*?<\/textarea\s*>""",
        RegexOption.IGNORE_CASE
    )

    /** Matches @import inside <style> blocks.  Not perfect but catches the common case. */
    private val STYLE_IMPORT_REGEX = Regex(
        """@import\s+url\s*\([^)]+\)""",
        RegexOption.IGNORE_CASE
    )

    /** Matches any attribute starting with "on" followed by a letter (case-insensitive). */
    private val EVENT_HANDLER_REGEX = Regex(
        """\s+on\w+\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
        RegexOption.IGNORE_CASE
    )

    /** Replaces javascript: / vbscript: in href attributes with "#" prefixed anchor. */
    private val JS_HREF_REGEX = Regex(
        """\bhref\s*=\s*"(?:javascript|vbscript|j\x61va script):""",
        RegexOption.IGNORE_CASE
    )

    /** Strip <meta http-equiv="refresh" ...>  */
    private val META_REFRESH_REGEX = Regex(
        """<meta\b[^>]*http-equiv\s*=\s*["']refresh["'][^>]*>""",
        RegexOption.IGNORE_CASE
    )

    /** Strip non-image data: URIs in href */
    private val BAD_DATA_HREF_REGEX = Regex(
        """\bhref\s*=\s*"data:(?!image\/)[^"]*"""",
        RegexOption.IGNORE_CASE
    )

    /** Matches entire <style>...</style> blocks including content. */
    private val STYLE_TAG_REGEX = Regex(
        """<style\b[^>]*>[\s\S]*?</style\s*>""",
        RegexOption.IGNORE_CASE
    )

    /** Matches bgcolor="..." or bgcolor='...' HTML attributes. */
    private val BGCOLOR_ATTR_REGEX = Regex(
        """\s+bgcolor\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+)""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Matches width="NNN" HTML attributes on tags.
     * Does not match width in CSS style attributes.
     */
    private val FIXED_WIDTH_ATTR_REGEX = Regex(
        """\s+width\s*=\s*(?:"[^"]*"|'[^']*')""",
        RegexOption.IGNORE_CASE
    )

    // endregion
}
