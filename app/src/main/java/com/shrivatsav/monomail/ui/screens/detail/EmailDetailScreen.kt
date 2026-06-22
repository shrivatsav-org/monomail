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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Forward
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.model.Email
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EmailDetailScreen(
    viewModel: EmailDetailViewModel,
    onBack: () -> Unit,
    isConversationView: Boolean = true,
    onReply: (to: String, subject: String, body: String, threadId: String, messageId: String) -> Unit = { _, _, _, _, _ -> },
    onForward: (subject: String, body: String, threadId: String, messageId: String) -> Unit = { _, _, _, _ -> },
    onFetchAttachment: suspend (String, String) -> ByteArray? = { _, _ -> null }
) {
    val state by viewModel.state.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
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
                    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Outlined.MoreVert,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Mark Unread") },
                                onClick = { showMenu = false; viewModel.markUnread(onComplete = { onBack() }) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = { showMenu = false; viewModel.archiveThread(onComplete = { onBack() }) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Move to Trash") },
                                onClick = { showMenu = false; viewModel.trashThread(onComplete = { onBack() }) }
                            )
                        }
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
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    if (s.isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (s.refreshError != null) {
                        Text(
                            text = s.refreshError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    ThreadConversationContent(
                        emails = emails,
                        modifier = Modifier.weight(1f),
                        isConversationView = isConversationView,
                        onReply = { onReply(latestEmail.fromEmail, latestEmail.subject, latestEmail.body, latestEmail.threadId, latestEmail.id) },
                        onForward = { onForward(latestEmail.subject, latestEmail.body, latestEmail.threadId, latestEmail.id) },
                        onFetchAttachment = { msgId, attId -> viewModel.fetchAttachmentBytes(msgId, attId) }
                    )
                }
            }
        }
    }
}
@Composable
private fun ThreadConversationContent(
    emails: List<Email>,
    modifier: Modifier = Modifier,
    isConversationView: Boolean = true,
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onFetchAttachment: suspend (String, String) -> ByteArray? = { _, _ -> null }
) {
    val bgColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.background.toArgb())
    val textColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.onBackground.toArgb())
    val linkColor = String.format("#%06X", 0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb())
    val expandedMap = remember(emails) {
        if (isConversationView) {
            mutableStateMapOf<String, Boolean>().apply {
                emails.forEachIndexed { index, email ->
                    put(email.id, index == emails.lastIndex)
                }
            }
        } else {
            mutableStateMapOf<String, Boolean>().apply {
                emails.forEach { put(it.id, true) }
            }
        }
    }
    val subject = emails.firstOrNull()?.subject ?: "(no subject)"
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item {
        Text(
            text = subject,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        if (emails.size > 1) {
            Text(
                text = "${emails.size} messages",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        } 
        itemsIndexed(emails, key = { _, email -> email.id }) { index, email ->
            val isExpanded = expandedMap[email.id] ?: true
            if (isConversationView) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedMap[email.id] = !isExpanded }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                        if (email.cc.isNotBlank() || email.bcc.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = buildString {
                                    if (email.cc.isNotBlank()) append("cc: ${email.cc}")
                                    if (email.cc.isNotBlank() && email.bcc.isNotBlank()) append("  ")
                                    if (email.bcc.isNotBlank()) append("bcc: ${email.bcc}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    MessageBody(
                        email = email,
                        bgColor = bgColor,
                        textColor = textColor,
                        linkColor = linkColor,
                        onFetchAttachment = onFetchAttachment
                    )
                }
            } else {
                MessageBody(
                    email = email,
                    bgColor = bgColor,
                    textColor = textColor,
                    linkColor = linkColor,
                    onFetchAttachment = onFetchAttachment,
                    showSender = true,
                    messageCount = emails.size
                )
            }
            if (index < emails.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
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
                        contentDescription = "Reply",
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
                        contentDescription = "Forward",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Forward", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
@Composable
private fun MessageBody(
    email: Email,
    bgColor: String,
    textColor: String,
    linkColor: String,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
    showSender: Boolean = false,
    messageCount: Int = 0
) {
    Column {
        if (showSender) {
            val isMsgUnread = !email.isRead
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val initial = email.from.firstOrNull()?.uppercase() ?: "?"
                Box {
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
                    if (isMsgUnread) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName(email.from),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isMsgUnread) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (messageCount > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$messageCount",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMsgUnread) {
                            Text(
                                text = "\u2022  ",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = formatDetailDate(email.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    if (email.cc.isNotBlank() || email.bcc.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildString {
                                if (email.cc.isNotBlank()) append("cc: ${email.cc}")
                                if (email.cc.isNotBlank() && email.bcc.isNotBlank()) append("  ")
                                if (email.bcc.isNotBlank()) append("bcc: ${email.bcc}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        val htmlContent = remember(email.id, email.body, bgColor, textColor, linkColor) {
            val cleanBody = stripQuotedText(email.body)
            """
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
                    blockquote, .gmail_quote, .gmail_extra,
                    .yahoo_quoted, .moz-cite-prefix,
                    [name="quoted-content"] { display: none !important; }
                </style>
            </head>
            <body>$cleanBody</body>
            </html>
            """.trimIndent()
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.loadsImagesAutomatically = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                }
            },
            update = { webView ->
                if (webView.tag != htmlContent) {
                    webView.tag = htmlContent
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                }
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (email.attachments.isNotEmpty()) {
            AttachmentsSection(
                attachments = email.attachments,
                onFetchAttachment = onFetchAttachment
            )
        }
    }
}

private fun openAttachment(context: android.content.Context, attachment: EmailAttachmentInfo, bytes: ByteArray?) {
    if (bytes == null) return
    try {
        val attachmentsDir = java.io.File(context.cacheDir, "attachments")
        attachmentsDir.mkdirs()
        val file = java.io.File(attachmentsDir, attachment.name)
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, attachment.mimeType)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Open with..."))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
private fun isImageAttachment(attachment: EmailAttachmentInfo): Boolean {
    val lowerName = attachment.name.lowercase()
    return attachment.mimeType.startsWith("image/") ||
        lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
        lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif") ||
        lowerName.endsWith(".webp")
}
private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }
}
@Composable
private fun AttachmentsSection(
    attachments: List<EmailAttachmentInfo>,
    onFetchAttachment: suspend (String, String) -> ByteArray?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "${attachments.size} Attachment${if (attachments.size > 1) "s" else ""}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        val (imageAttachments, fileAttachments) = attachments.partition { isImageAttachment(it) }
        imageAttachments.forEach { attachment ->
            ImageAttachmentCard(
                attachment = attachment,
                onFetchAttachment = onFetchAttachment
            )
        }
        if (fileAttachments.isNotEmpty()) {
            fileAttachments.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { attachment ->
                        FileAttachmentCard(
                            attachment = attachment,
                            onFetchAttachment = onFetchAttachment,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
@Composable
private fun ImageAttachmentCard(
    attachment: EmailAttachmentInfo,
    onFetchAttachment: suspend (String, String) -> ByteArray?
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var imageBytes by remember { androidx.compose.runtime.mutableStateOf<ByteArray?>(null) }
    androidx.compose.runtime.LaunchedEffect(attachment.id) {
        imageBytes = onFetchAttachment(attachment.messageId, attachment.id)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                if (imageBytes != null) {
                    openAttachment(context, attachment, imageBytes)
                }
            }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val bytes = imageBytes
            if (bytes != null) {
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.FillWidth
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attachment",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Image,
                contentDescription = "Attachment Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatFileSize(attachment.size.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}
@Composable
private fun FileAttachmentCard(
    attachment: EmailAttachmentInfo,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isFetching by remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val ext = attachment.name.substringAfterLast('.', "").uppercase()
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable {
                if (!isFetching) {
                    isFetching = true
                    scope.launch {
                        val bytes = onFetchAttachment(attachment.messageId, attachment.id)
                        openAttachment(context, attachment, bytes)
                        isFetching = false
                    }
                }
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (ext.length in 1..4) ext else "FILE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(attachment.size.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
private val detailDateFormat = SimpleDateFormat("MMM d, yyyy  h:mm a", Locale.getDefault())

private fun displayName(from: String): String {
    val nameMatch = Regex("""^"?([^"<]+?)"\s*<""").find(from)
    return nameMatch?.groupValues?.get(1)?.trim() ?: from.trim()
}
private fun formatDetailDate(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    return detailDateFormat.format(Date(epochMillis))
}
private fun stripQuotedText(html: String): String {
    var result = html
    result = result.replace(Regex("<blockquote[^>]*>[\\s\\S]*?</blockquote>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("<div\\s+class=\"gmail_quote\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("<div\\s+class=\"gmail_extra\"[^>]*>[\\s\\S]*?</div>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("<br>\\s*On .{10,80} wrote:\\s*<br>", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("\\n\\s*On .{10,80} wrote:\\s*\\n", RegexOption.IGNORE_CASE), "")
    result = result.replace(Regex("(^|<br>)(&gt;|>)\\s?.*", RegexOption.IGNORE_CASE), "")
    return result.trim()
}
