# Plan: Fix EmailDetailScreen — WebView scroll behavior and dark-mode theming

## Files Modified

| File | Reason |
|------|--------|
| `gradle/libs.versions.toml` | Add `androidx.webkit` version + library entries |
| `app/build.gradle.kts` | Add `implementation(libs.androidx.webkit)` dependency |
| `app/.../ui/screens/detail/EmailDetailScreen.kt` | All functional changes |

---

## Phase 0: Add `androidx.webkit` dependency

The project has no WebKit dependency yet. We need `androidx.webkit:webkit` ≥1.5.0 (using 1.12.1) for `WebSettingsCompat.setAlgorithmicDarkeningAllowed`.

### File: `gradle/libs.versions.toml`

**Edit 0a** — Add version entry in `[versions]` (after `pgpainless` line):

```
old:
pgpainless = "2.0.3"

new:
pgpainless = "2.0.3"
webkit = "1.12.1"
```

**Edit 0b** — Add library entry in `[libraries]` (after pgpainless-core line):

```
old:
pgpainless-core = { group = "org.pgpainless", name = "pgpainless-core", version.ref = "pgpainless" }

new:
pgpainless-core = { group = "org.pgpainless", name = "pgpainless-core", version.ref = "pgpainless" }
androidx-webkit = { group = "androidx.webkit", name = "webkit", version.ref = "webkit" }
```

### File: `app/build.gradle.kts`

**Edit 0c** — Add dependency (after the `pgpainless.core` line around 178):

```
old:
    implementation(libs.pgpainless.core)

new:
    implementation(libs.pgpainless.core)
    implementation(libs.androidx.webkit)
```

---

## Phase 1: WebView scroll fix (Part 1)

All edits in `app/.../ui/screens/detail/EmailDetailScreen.kt`.

### 1A: Add measured-height state variables

Insert after `var showRemoteImages by remember { mutableStateOf(false) }` (current line 864).

```
old:
    var showRemoteImages by remember { mutableStateOf(false) }

new:
    var showRemoteImages by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var contentHeightPx by remember(email.id) { mutableStateOf(0) }
```

Note: `LocalDensity` is already imported implicitly via `import androidx.compose.ui.platform.LocalDensity` — verify this import exists. If not, add it.

### 1B: Apply height to AndroidView modifier with placeholder

Current modifier at the AndroidView call (line 1372-1378):

```
old:
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBgColor, RoundedCornerShape(16.dp))
                        .padding(12.dp),

new:
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(cardBgColor, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                        .then(
                            if (contentHeightPx > 0) Modifier.height(with(density) { contentHeightPx.toDp() })
                            else Modifier.height(120.dp)
                        ),
```

### 1C: Disable internal WebView scrolling

After `isHorizontalScrollBarEnabled = false` (current line 1400):

```
old:
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false

new:
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = android.view.View.OVER_SCROLL_NEVER
                        isNestedScrollingEnabled = false
```

### 1D: Add `onPageFinished` to `webViewClient`

Insert after the `onReceivedError` override, before the closing `}` of the `webViewClient` object (current lines 1449-1456). Use `onReceivedError` as anchor:

```
old:
                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            android.util.Log.e("EmailWebView", "WebView error: ${error?.description} on ${request?.url}")
                        }
                    }

new:
                        override fun onReceivedError(
                            view: android.webkit.WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            android.util.Log.e("EmailWebView", "WebView error: ${error?.description} on ${request?.url}")
                        }
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.postDelayed({
                                val h = (view.contentHeight * view.resources.displayMetrics.density).toInt()
                                if (h > 0) contentHeightPx = h
                            }, 50)
                            view?.postDelayed({
                                val h = (view.contentHeight * view.resources.displayMetrics.density).toInt()
                                if (h > 0 && h != contentHeightPx) contentHeightPx = h
                            }, 400)
                        }
                    }
```

### 1E: Add `overflow-x: hidden` to adapt mode `body` CSS

Inside the `body { ... }` block in the "adapt" branch of `modeCss`. Insert after the last property (`-webkit-font-smoothing: antialiased;` at line 1142) before the closing `}`.

```
old:
                        -webkit-font-smoothing: antialiased;
                    }
                    /* Force text color on ALL elements so inline color:#333 is overridden in dark mode */

new:
                        -webkit-font-smoothing: antialiased;
                        overflow-x: hidden;
                    }
                    /* Force text color on ALL elements so inline color:#333 is overridden in dark mode */
```

### 1F: Wrap wide tables in `.monomail-table-wrap` in adapt enhancedBody

In the adapt branch of enhancedBody, after the `stripFixedWidthAttrs` call (current lines 1057-1064):

```
old:
                "adapt" -> {
                    var b = wrappedBody
                    b = HtmlSanitizer.stripStyleTags(b)
                    b = HtmlSanitizer.stripBgcolorAttrs(b)
                    b = HtmlSanitizer.stripFixedWidthAttrs(b)
                    b
                }

new:
                "adapt" -> {
                    var b = wrappedBody
                    b = HtmlSanitizer.stripStyleTags(b)
                    b = HtmlSanitizer.stripBgcolorAttrs(b)
                    b = HtmlSanitizer.stripFixedWidthAttrs(b)
                    // Wrap all tables in scrollable wrapper to prevent horizontal overflow
                    b = b.replace(Regex("<table[^>]*>", RegexOption.IGNORE_CASE)) {
                        "<div class=\"monomail-table-wrap\">" + it.value
                    }
                    b
                }
```

---

## Phase 2: Algorithmic darkening (Part 2)

### 2A: Add imports

Insert after the existing webkit imports (current lines 4-6):

```
old:
import android.webkit.WebSettings
import android.webkit.WebView

new:
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
```

### 2B: Extract `useDarkTheme` and `effectiveMode` to separate remembers

Before the `htmlContent` remember block (current line 1017), insert these computations:

Find:
```
        val htmlContent = remember(email.id, bodyText, bgColor, textColor, linkColor, fontScaleMultiplier, showQuotedText, loadRemoteImages, showRemoteImages, renderMarkdown, emailTheme, showInlineAttachments) {
```

Replace with:
```
        // Determine if we're in dark theme by checking background color brightness
        val useDarkTheme = remember(bgColor) {
            try {
                val bgHex = bgColor.removePrefix("#")
                val bgInt = bgHex.toLong(16)
                val r = (bgInt shr 16) and 0xFF
                val g = (bgInt shr 8) and 0xFF
                val b = bgInt and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
                luminance < 128
            } catch (_: Exception) { false }
        }
        // Pre-compute effectiveMode so both htmlContent and WebView update block can use it
        val looksS = remember(bodyText, bodyIsHtml, emailTheme) {
            (bodyText.length > 200 && bodyIsHtml) && looksStyled(bodyText)
        }
        val effectiveMode = remember(looksS, emailTheme) {
            when (emailTheme) {
                EmailTheme.FORCE_DARK -> "adapt"
                EmailTheme.FORCE_LIGHT -> "original"
                EmailTheme.ORIGINAL -> "original"
                EmailTheme.AUTO -> if (looksS) "original" else "adapt"
            }
        }

        val htmlContent = remember(email.id, bodyText, bgColor, textColor, linkColor, fontScaleMultiplier, showQuotedText, loadRemoteImages, showRemoteImages, renderMarkdown, emailTheme, showInlineAttachments) {
```

**Then remove the inline computations from inside the `htmlContent` remember:**

Find and remove these two blocks inside the `htmlContent remember { }` block:

Block 1 (current line ~1046-1052):
```
            val isStyled = (displayBody.length > 200 && bodyIsHtml) && looksStyled(displayBody)
            val effectiveMode = when (emailTheme) {
                EmailTheme.FORCE_DARK -> "adapt"
                EmailTheme.FORCE_LIGHT -> "original"
                EmailTheme.ORIGINAL -> "original"
                EmailTheme.AUTO -> if (isStyled) "original" else "adapt"
            }
```

Block 2 (current lines ~1282-1290):
```
            // Determine if we're in dark theme by checking background color brightness
            val useDarkTheme = try {
                val bgHex = bgColor.removePrefix("#")
                val bgInt = bgHex.toLong(16)
                val r = (bgInt shr 16) and 0xFF
                val g = (bgInt shr 8) and 0xFF
                val b = bgInt and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b)
                luminance < 128
            } catch (_: Exception) { false }
```

### 2C: Remove `monomail-dark` CSS rules from `modeCss` adapt branch

Remove these blocks from the adapt `modeCss` string (current lines 1143-1206):

Block 1 — color forcing (lines 1143-1149):
```
                    /* Force text color on ALL elements so inline color:#333 is overridden in dark mode */
                    body.monomail-dark * {
                        color: $textColor !important;
                    }
                    body.monomail-dark a, body.monomail-dark a * {
                        color: $linkColor !important;
                    }
```

Block 2 — background stripping (lines 1187-1206):
```
                    /* Strip ALL background colors in dark mode */
                    body.monomail-dark [style*="background-color"] {
                        background-color: transparent !important;
                    }
                    body.monomail-dark [style*="background:"] {
                        background: transparent !important;
                    }
                    body.monomail-dark [bgcolor] {
                        background-color: transparent !important;
                    }
                    body.monomail-dark td, body.monomail-dark th {
                        background-color: transparent !important;
                        background: transparent !important;
                    }
                    body.monomail-dark div, body.monomail-dark span,
                    body.monomail-dark p, body.monomail-dark table,
                    body.monomail-dark tr {
                        background-color: transparent !important;
                        background: transparent !important;
                    }
```

### 2D: Remove `stripBgcolorAttrs` call from enhancedBody

In the adapt branch (current line 1061):

```
old:
                    var b = wrappedBody
                    b = HtmlSanitizer.stripStyleTags(b)
                    b = HtmlSanitizer.stripBgcolorAttrs(b)
                    b = HtmlSanitizer.stripFixedWidthAttrs(b)

new:
                    var b = wrappedBody
                    b = HtmlSanitizer.stripStyleTags(b)
                    b = HtmlSanitizer.stripFixedWidthAttrs(b)
```

### 2E: Remove `monomail-dark` class from body tag

Current line 1305:

```
old:
            <body class="${if (showQuotedText) "show-quotes " else ""}${if (useDarkTheme && effectiveMode == "adapt") "monomail-dark" else ""}">$bodyWithCidPlaceholders</body>

new:
            <body class="${if (showQuotedText) "show-quotes" else ""}">$bodyWithCidPlaceholders</body>
```

### 2F: Add algorithmic darkening call in WebView update block

In the `update = { webView -> }` block, just before `loadDataWithBaseURL` (current lines 1459-1468):

```
old:
                update = { webView ->
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        try {
                            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)

new:
                update = { webView ->
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        try {
                            // Enable WebView algorithmic darkening for adapt mode
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(
                                    webView.settings,
                                    useDarkTheme && effectiveMode == "adapt"
                                )
                            }
                            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
```

---

## Summary of all edits required to the remember key list

The `htmlContent` remember key list on line 1017 is:
```
remember(email.id, bodyText, bgColor, textColor, linkColor, fontScaleMultiplier,
        showQuotedText, loadRemoteImages, showRemoteImages, renderMarkdown,
        emailTheme, showInlineAttachments)
```

After moving `useDarkTheme` and `effectiveMode` out, `bgColor` is no longer used inside `htmlContent` (the only use was the `useDarkTheme` luminance computation). Remove `bgColor` from the key list.

But wait — after checking: `bgColor` is also used in the CSS template string inside `htmlContent` (for `background-color: $bgColor` in the body CSS). So `bgColor` IS still used and must stay in the key list.

Similarly, `textColor` and `linkColor` are used in CSS. All existing keys remain valid — none are unused.

---

## Verification checklist

| # | Test | Expected outcome |
|---|------|-----------------|
| 1 | Plain-text email | No scrollbars on WebView, outer Column scrolls entire thread |
| 2 | Gmail-quoted-reply thread | Outer Column scrolls smoothly, no inner WebView scroll |
| 3 | HTML marketing email with layout tables | No horizontal overflow, table wraps in scrollable wrapper |
| 4 | Email with colored badges/buttons | In dark mode, badges stay readable (built-in darkening handles color pairs correctly) |
| 5 | "Original" mode email | Visually untouched by Part 2 changes, no algorithmic darkening applied |
| 6 | Toggle dark/light theme | WebView re-renders with correct darkening applied per `useDarkTheme` |
| 7 | Quoted text toggle | Still works; WebView height adjusts after `onPageFinished` remeasure |
| 8 | Build | `./gradlew assembleGithubDebug` succeeds with new `androidx.webkit` dependency |

---

## Constraints reminder

- Do NOT enable JavaScript (`settings.javaScriptEnabled` stays `false`) — verified in all edits
- Do NOT change `EmailDetailViewModel.kt` — no edits touch it
- Do NOT touch "original" mode CSS branch, `looksStyled()`, `cardBgColor` logic, or `EmailTheme` enum — all preserved
- Do NOT add any new dependencies beyond `androidx.webkit`
