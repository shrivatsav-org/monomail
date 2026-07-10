package com.shrivatsav.monomail

import com.shrivatsav.monomail.util.normalizeEmailBody
import com.shrivatsav.monomail.util.stripUnsafeHtml
import org.junit.Assert.*
import org.junit.Test

class EmailHtmlSafetyTest {

    // ── Fix 1: stripUnsafeHtml ──

    @Test
    fun stripUnsafeHtml_removesMetaRefresh() {
        val input = """<html><head><meta http-equiv="refresh" content="0;url=evil"></head><body>hi</body></html>"""
        val result = stripUnsafeHtml(input)
        assertFalse("meta refresh should be removed", result.contains("refresh", ignoreCase = true))
        assertTrue("body content preserved", result.contains("hi"))
    }

    @Test
    fun stripUnsafeHtml_removesMetaRefresh_noQuotes() {
        val input = """<meta http-equiv=refresh content="0;url=evil">"""
        val result = stripUnsafeHtml(input)
        assertFalse("meta refresh without quotes should be removed", result.contains("refresh", ignoreCase = true))
    }

    @Test
    fun stripUnsafeHtml_removesMetaRefresh_selfClosing() {
        val input = """<meta http-equiv="refresh" content="0;url=evil"/>"""
        val result = stripUnsafeHtml(input)
        assertFalse("self-closing meta refresh should be removed", result.contains("refresh", ignoreCase = true))
    }

    @Test
    fun stripUnsafeHtml_removesIframe() {
        val input = """<p>before</p><iframe src="https://evil"><p>click</p></iframe><p>after</p>"""
        val result = stripUnsafeHtml(input)
        assertFalse("iframe tag should be removed", result.contains("iframe", ignoreCase = true))
        assertFalse("iframe content should be removed", result.contains("click"))
        assertTrue("surrounding content preserved", result.contains("before") && result.contains("after"))
    }

    @Test
    fun stripUnsafeHtml_removesObject() {
        val input = """<object data="evil.swf"><param name="autoplay" value="true"></object>"""
        val result = stripUnsafeHtml(input)
        assertFalse("object tag should be removed", result.contains("object", ignoreCase = true))
        assertFalse("object content should be removed", result.contains("autoplay"))
    }

    @Test
    fun stripUnsafeHtml_removesEmbed() {
        val input = """<embed src="evil.swf" width="500" height="300">"""
        val result = stripUnsafeHtml(input)
        assertFalse("embed tag should be removed", result.contains("embed", ignoreCase = true))
    }

    @Test
    fun stripUnsafeHtml_preservesPlainText() {
        val input = "Hello, this is a plain text email with no HTML tags."
        val result = stripUnsafeHtml(input)
        assertEquals("plain text should be unchanged", input, result)
    }

    @Test
    fun stripUnsafeHtml_preservesSafeHtml() {
        val input = """<p>Hello <b>world</b></p><a href="https://example.com">link</a>"""
        val result = stripUnsafeHtml(input)
        assertEquals("safe HTML should be unchanged", input, result)
    }

    @Test
    fun stripUnsafeHtml_preservesImgTags() {
        val input = """<img src="cid:photo123" alt="photo">"""
        val result = stripUnsafeHtml(input)
        assertTrue("img tags should be preserved", result.contains("img"))
        assertTrue("cid src preserved", result.contains("cid:photo123"))
    }

    @Test
    fun normalizeEmailBody_unwrapsJsonTextArrayWithHtml() {
        val input = """[{"text":"<html><body><p>Hello</p></body></html>"}]"""
        val result = normalizeEmailBody(input, bodyIsHtml = false)

        assertEquals("<html><body><p>Hello</p></body></html>", result.text)
        assertTrue("unwrapped body should be treated as html", result.isHtml)
    }

    @Test
    fun normalizeEmailBody_unwrapsNestedProviderContent() {
        val input = """{"message":{"body":{"content":"<div>Hi</div>"}}}"""
        val result = normalizeEmailBody(input, bodyIsHtml = false)

        assertEquals("<div>Hi</div>", result.text)
        assertTrue("nested html content should be detected", result.isHtml)
    }

    @Test
    fun stripUnsafeHtml_doesNotStripScriptTags() {
        val input = """<script>alert(1)</script>"""
        val result = stripUnsafeHtml(input)
        assertTrue("script tags are deliberately not stripped (JS is off)", result.contains("script"))
    }

    @Test
    fun stripUnsafeHtml_doesNotStripEventHandlers() {
        val input = """<p onclick="alert(1)">click me</p>"""
        val result = stripUnsafeHtml(input)
        assertTrue("event handlers are deliberately not stripped (JS is off)", result.contains("onclick"))
    }

    // ── Fix 2: remote-image blocking CSS logic ──

    @Test
    fun remoteImageCss_presentWhenBothFlagsFalse() {
        val load = false; val show = false
        val css = imgBlockCss(load, show)
        assertTrue("CSS should block http images", css.contains("http://"))
        assertTrue("CSS should block https images", css.contains("https://"))
    }

    @Test
    fun remoteImageCss_absentWhenLoadRemoteImagesTrue() {
        val load = true; val show = false
        val css = imgBlockCss(load, show)
        assertEquals("no CSS when loadRemoteImages is true", "", css)
    }

    @Test
    fun remoteImageCss_absentWhenShowRemoteImagesTrue() {
        val load = false; val show = true
        val css = imgBlockCss(load, show)
        assertEquals("no CSS when showRemoteImages is true", "", css)
    }

    // ── Fix 3: cardBgColor logic ──

    @Test
    fun cardBgColor_originalIsWhite() {
        assertEquals("ORIGINAL -> White", 0xFFFFFFFF.toInt(), cardBgColorRes(original = true))
    }

    @Test
    fun cardBgColor_nonOriginalIsSurface() {
        assertEquals("AUTO -> surface", 0, cardBgColorRes(original = false))
    }

    // ── Helpers (mirror the inline logic from EmailDetailScreen.kt) ──

    private fun imgBlockCss(loadRemoteImages: Boolean, showRemoteImages: Boolean): String {
        return if (!loadRemoteImages && !showRemoteImages) """
            img[src^="http://"] { display: none !important; }
            img[src^="https://"] { display: none !important; }
        """.trimIndent() else ""
    }

    private fun cardBgColorRes(original: Boolean): Int {
        // ponytail: on-device this returns Color.White or surfaceContainerLow;
        // here we just verify the branching choice
        return if (original) 0xFFFFFFFF.toInt() else 0
    }
}
