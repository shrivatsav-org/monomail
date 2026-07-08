package com.shrivatsav.monomail.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View.MeasureSpec
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.shrivatsav.monomail.data.model.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object EmailImageGenerator {

    /**
     * Generates an image of the provided emails (stitched together) and opens a share sheet.
     */
    suspend fun shareEmailsAsImage(context: Context, emails: List<Email>, subject: String, fontScaleMultiplier: Float = 1f) {
        val html = buildHtmlForEmails(emails, subject, fontScaleMultiplier)
        val bitmap = captureHtmlAsBitmap(context, html)
        if (bitmap != null) {
            shareBitmap(context, bitmap)
        }
    }

    private fun buildHtmlForEmails(emails: List<Email>, subject: String, fontScaleMultiplier: Float): String {
        val dateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())
        
        val baseFontSize = (16 * fontScaleMultiplier).toInt()
        val titleFontSize = (24 * fontScaleMultiplier).toInt()
        val senderFontSize = (16 * fontScaleMultiplier).toInt()
        val detailFontSize = (14 * fontScaleMultiplier).toInt()
        val avatarFontSize = (20 * fontScaleMultiplier).toInt()
        
        val emailsHtml = emails.joinToString("<hr style='border: none; border-top: 1px solid #e0e0e0; margin: 32px 0;'/>") { email ->
            val initial = email.from.firstOrNull()?.uppercase() ?: "?"
            val dateStr = dateFormat.format(Date(email.date))
            
            var bodyHtml = email.body
            if (email.bodyIsHtml) {
                bodyHtml = HtmlSanitizer.stripQuotedText(bodyHtml)
                bodyHtml = HtmlSanitizer.sanitize(bodyHtml)
                bodyHtml = HtmlSanitizer.stripStyleTags(bodyHtml)
                bodyHtml = HtmlSanitizer.stripBgcolorAttrs(bodyHtml)
            } else {
                bodyHtml = HtmlSanitizer.stripQuotedText(bodyHtml).replace("\n", "<br>")
            }

            """
            <div class="email-message">
                <div class="email-header">
                    <div class="sender-row">
                        <div class="avatar">$initial</div>
                        <div class="sender-info">
                            <div class="sender-name">${email.from}</div>
                            <div class="sender-details">${email.fromEmail} • $dateStr</div>
                        </div>
                    </div>
                </div>
                <div class="email-body">
                    $bodyHtml
                </div>
            </div>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
            <style>
                @font-face {
                    font-family: 'GoogleSans';
                    src: url('file:///android_res/font/google_sans_flex.ttf') format('truetype');
                }
                body {
                    margin: 0;
                    padding: 48px 32px;
                    background-color: #ffffff;
                    color: #000000;
                    font-family: 'GoogleSans', -apple-system, sans-serif;
                    -webkit-font-smoothing: antialiased;
                    word-break: break-word;
                    overflow-wrap: break-word;
                }
                .email-container {
                    max-width: 800px;
                    margin: 0 auto;
                }
                .subject {
                    margin: 0 0 24px 0;
                    font-size: ${titleFontSize}px;
                    line-height: 1.3;
                    color: #000000;
                }
                .sender-row {
                    display: flex;
                    align-items: center;
                    margin-bottom: 24px;
                }
                .avatar {
                    width: 48px;
                    height: 48px;
                    border-radius: 24px;
                    background-color: #000000;
                    color: #ffffff;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    font-size: ${avatarFontSize}px;
                    font-weight: bold;
                    margin-right: 16px;
                    flex-shrink: 0;
                }
                .sender-name {
                    font-weight: bold;
                    font-size: ${senderFontSize}px;
                    color: #000000;
                }
                .sender-details {
                    color: #666666;
                    font-size: ${detailFontSize}px;
                    margin-top: 2px;
                }
                .email-body {
                    font-size: ${baseFontSize}px;
                    line-height: 1.65;
                }
                .email-body img, .email-body video, .email-body iframe {
                    max-width: 100% !important;
                    height: auto !important;
                }
                .email-body img[width] {
                    width: auto !important;
                }
                .email-body table {
                    max-width: 100% !important;
                    width: auto !important;
                    border-collapse: collapse;
                }
                .email-body table[width], .email-body td[width], .email-body th[width] {
                    max-width: 100% !important;
                }
                .email-body div[style*="width"], .email-body section[style*="width"] {
                    max-width: 100% !important;
                    width: auto !important;
                }
                .email-body p { margin: 0 0 1em 0; }
                .email-body a { color: #0b57d0; text-decoration: none; }
                .email-body pre, .email-body code { white-space: pre-wrap; word-break: break-word; }
            </style>
        </head>
        <body>
            <div class="email-container">
                <div class="subject">$subject</div>
                $emailsHtml
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    private suspend fun captureHtmlAsBitmap(context: Context, html: String): Bitmap? = withContext(Dispatchers.Main) {
        kotlinx.coroutines.withTimeoutOrNull(10000L) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                var isResumed = false
                val webView = WebView(context)
                
                // Keep a reference to prevent GC during load
                val webViewRef = java.util.concurrent.atomic.AtomicReference(webView)
                
                webView.settings.apply {
                    javaScriptEnabled = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                    allowFileAccess = true
                }

                // Initial layout with a realistic size to encourage the WebView to render
                val screenWidth = context.resources.displayMetrics.widthPixels
                webView.layout(0, 0, screenWidth, 1920)

                webView.webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        android.util.Log.d("EmailImageGenerator", "Progress: $newProgress")
                    }
                }

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        android.util.Log.d("EmailImageGenerator", "onPageFinished fired")
                        if (isResumed) return
                        
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            if (isResumed) return@postDelayed
                            try {
                                android.util.Log.d("EmailImageGenerator", "Measuring WebView...")
                                val widthMeasureSpec = MeasureSpec.makeMeasureSpec(screenWidth, MeasureSpec.EXACTLY)
                                val heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                                
                                view.measure(widthMeasureSpec, heightMeasureSpec)
                                view.layout(0, 0, view.measuredWidth, view.measuredHeight)

                                val bitmap = Bitmap.createBitmap(
                                    view.measuredWidth.coerceAtLeast(1),
                                    view.measuredHeight.coerceAtLeast(1),
                                    Bitmap.Config.ARGB_8888
                                )
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(android.graphics.Color.WHITE)
                                view.draw(canvas)
                                
                                isResumed = true
                                android.util.Log.d("EmailImageGenerator", "Bitmap captured successfully!")
                                continuation.resume(bitmap)
                            } catch (e: Exception) {
                                android.util.Log.e("EmailImageGenerator", "Exception in capture", e)
                                if (!isResumed) {
                                    isResumed = true
                                    continuation.resume(null)
                                }
                            }
                        }, 500)
                    }
                }

                webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null)
                
                continuation.invokeOnCancellation {
                    webViewRef.get()?.destroy()
                }
            }
        }
    }

    private suspend fun shareBitmap(context: Context, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("EmailImageGenerator", "Saving bitmap to cache...")
            val cachePath = File(context.cacheDir, "shared_email")
            cachePath.mkdirs()
            val file = File(cachePath, "email_share_${System.currentTimeMillis()}.png")
            val fos = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
            fos.close()
            android.util.Log.d("EmailImageGenerator", "Bitmap saved to ${file.absolutePath}")

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            android.util.Log.d("EmailImageGenerator", "Generated URI: $uri")

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            withContext(Dispatchers.Main) {
                android.util.Log.d("EmailImageGenerator", "Starting chooser intent...")
                val chooser = Intent.createChooser(intent, "Share Email")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                android.util.Log.d("EmailImageGenerator", "Chooser intent started!")
            }
        } catch (e: Exception) {
            android.util.Log.e("EmailImageGenerator", "Error sharing bitmap", e)
        }
    }
}
