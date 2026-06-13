package com.shrivatsav.monomail.ui.screens.detail

import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shrivatsav.monomail.data.model.Email
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailDetailScreen(
    viewModel: EmailDetailViewModel,
    onBack: () -> Unit,
    onReply: (to: String, subject: String, body: String, threadId: String, messageId: String) -> Unit = { _, _, _, _, _ -> },
    onForward: (subject: String, body: String, threadId: String, messageId: String) -> Unit = { _, _, _, _ -> },
    onFetchAttachment: suspend (String, String) -> ByteArray? = { _, _ -> null }
) {
    val state by viewModel.state.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleStar() }) {
                        Icon(
                            imageVector = if (isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = if (isStarred) "Unstar" else "Star",
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* more */ }) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        when (val s = state) {
            is EmailDetailState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            is EmailDetailState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(32.dp)
                    )
                }
            }

            is EmailDetailState.Success -> {
                val emails = s.emails
                val latestEmail = emails.lastOrNull() ?: return@Scaffold

                ThreadConversationContent(
                    emails = emails,
                    modifier = Modifier.padding(padding),
                    onReply = { onReply(latestEmail.fromEmail, latestEmail.subject, latestEmail.body, latestEmail.threadId, latestEmail.id) },
                    onForward = { onForward(latestEmail.subject, latestEmail.body, latestEmail.threadId, latestEmail.id) },
                    onFetchAttachment = { msgId, attId -> viewModel.fetchAttachmentBytes(msgId, attId) }
                )
            }
        }
    }
}

@Composable
private fun ThreadConversationContent(
    emails: List<Email>,
    modifier: Modifier = Modifier,
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onFetchAttachment: suspend (String, String) -> ByteArray? = { _, _ -> null }
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) "#121212" else "#FFFFFF"
    val textColor = if (isDark) "#E6E6E6" else "#1A1A1A"
    val linkColor = if (isDark) "#AAAAAA" else "#444444"

    // Track which messages are expanded. Latest is expanded by default.
    val expandedMap = remember(emails.size) {
        mutableStateMapOf<String, Boolean>().apply {
            emails.forEachIndexed { index, email ->
                put(email.id, index == emails.lastIndex)
            }
        }
    }

    val subject = emails.firstOrNull()?.subject ?: "(no subject)"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Subject header (shared across thread)
        Text(
            text = subject,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        // Message count
        if (emails.size > 1) {
            Text(
                text = "${emails.size} messages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Each message in the conversation
        emails.forEachIndexed { index, email ->
            val isExpanded = expandedMap[email.id] ?: (index == emails.lastIndex)

            // Message header — always visible, tappable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedMap[email.id] = !isExpanded }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar
                val initial = email.from.firstOrNull()?.uppercase() ?: "?"
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName(email.from),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!isExpanded) {
                        // Show snippet when collapsed
                        Text(
                            text = email.snippet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = formatDetailDate(email.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded body
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    // Email body rendered in WebView
                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = false
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                settings.loadsImagesAutomatically = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                isVerticalScrollBarEnabled = false
                                isHorizontalScrollBarEnabled = false
                            }
                        },
                        update = { webView ->
                            val cleanBody = stripQuotedText(email.body)
                            val html = """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                                    <style>
                                        * { box-sizing: border-box; }
                                        body {
                                            font-family: -apple-system, 'Helvetica Neue', Arial, sans-serif;
                                            font-size: 15px;
                                            line-height: 1.6;
                                            margin: 12px 16px 0 16px;
                                            padding: 0;
                                            background-color: $bgColor;
                                            color: $textColor;
                                            word-break: break-word;
                                            overflow-wrap: break-word;
                                        }
                                        img { max-width: 100%; height: auto; display: block; }
                                        a { color: $linkColor; }
                                        table { max-width: 100%; word-break: break-word; }
                                        pre, code { white-space: pre-wrap; font-size: 13px; }
                                        /* Hide quoted / forwarded content */
                                        blockquote, .gmail_quote, .gmail_extra,
                                        .yahoo_quoted, .moz-cite-prefix,
                                        [name="quoted-content"] { display: none !important; }
                                    </style>
                                </head>
                                <body>$cleanBody</body>
                                </html>
                            """.trimIndent()
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Attachments Section
                    if (email.attachments.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${email.attachments.size} Attachment${if (email.attachments.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            email.attachments.forEach { attachment ->
                                val lowerName = attachment.name.lowercase()
                                val isImage = attachment.mimeType.startsWith("image/") ||
                                    lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                                    lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") ||
                                    lowerName.endsWith(".webp")
                                    
                                if (isImage) {
                                    var imageBytes by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<ByteArray?>(null) }
                                    androidx.compose.runtime.LaunchedEffect(attachment.id) {
                                        imageBytes = onFetchAttachment(attachment.messageId, attachment.id)
                                    }
                                    if (imageBytes != null) {
                                        coil.compose.AsyncImage(
                                            model = imageBytes,
                                            contentDescription = attachment.name,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(max = 300.dp)
                                                .clip(MaterialTheme.shapes.medium),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxWidth().height(100.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AttachFile,
                                            contentDescription = "Attachment",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = attachment.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${attachment.size / 1024} KB",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Divider between messages (not after last)
            if (index < emails.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Reply / Forward — applies to the latest message
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onReply,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reply", style = MaterialTheme.typography.labelLarge)
            }

            OutlinedButton(
                onClick = onForward,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Forward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Forward", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun displayName(from: String): String {
    val nameMatch = Regex("""^"?([^"<]+?)"\s*<""").find(from)
    return nameMatch?.groupValues?.get(1)?.trim() ?: from.trim()
}

private fun formatDetailDate(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    return SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault()).format(Date(epochMillis))
}

/**
 * Strip quoted / forwarded text from an HTML email body.
 * Removes "On ... wrote:" blocks, "> " prefixed lines, and forwarded headers.
 */
private fun stripQuotedText(html: String): String {
    var result = html

    // Remove <blockquote> tags and their content
    result = result.replace(Regex("<blockquote[^>]*>[\\s\\S]*?</blockquote>", RegexOption.IGNORE_CASE), "")

    // Remove Gmail-style quoted divs
    result = result.replace(Regex("<div\\s+class=\"gmail_quote\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("<div\\s+class=\"gmail_extra\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")

    // Remove "On ... wrote:" line (plain text in HTML)
    result = result.replace(Regex("<br>\\s*On .{10,80} wrote:\\s*<br>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("\\n\\s*On .{10,80} wrote:\\s*\\n", RegexOption.IGNORE_CASE), "")

    // Remove lines starting with "> " (plain-text quoting)
    result = result.replace(Regex("(^|<br>)(&gt;|>)\\s?.*", RegexOption.IGNORE_CASE), "")

    return result.trim()
}