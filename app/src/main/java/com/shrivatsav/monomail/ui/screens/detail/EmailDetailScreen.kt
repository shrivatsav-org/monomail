package com.shrivatsav.monomail.ui.screens.detail
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Forward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import com.shrivatsav.monomail.data.pgp.PgpDecryptionResult
import com.shrivatsav.monomail.util.HtmlSanitizer
import kotlinx.coroutines.launch
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
    // Read settings from ViewModel (consolidated — no direct DataStore collection)
    val isConversationView by viewModel.isConversationView.collectAsState()
    val fontScaleMultiplier by viewModel.fontScaleMultiplier.collectAsState()
    val loadRemoteImages by viewModel.loadRemoteImages.collectAsState()
    val renderMarkdown by viewModel.renderMarkdown.collectAsState()
    val state by viewModel.state.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val decryptedBodies by viewModel.decryptedBodies.collectAsState()
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
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleStar() }) {
                        Icon(
                            imageVector = if (isStarred) Icons.Rounded.Star else Icons.Rounded.Star,
                            contentDescription = if (isStarred) "Unstar" else "Star",
                            tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
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
                    if (emails.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                "Loading...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        val latestEmail = emails.last()
                        val latestBody = decryptedBodies[latestEmail.id]?.decryptedBody ?: latestEmail.body
                        ThreadConversationContent(
                            emails = emails,
                            decryptedBodies = decryptedBodies,
                            modifier = Modifier.weight(1f),
                            isConversationView = isConversationView,
                            fontScaleMultiplier = fontScaleMultiplier,
                            loadRemoteImages = loadRemoteImages,
                            renderMarkdown = renderMarkdown,
                            onReply = { onReply(latestEmail.fromEmail, latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                            onForward = { onForward(latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                            onFetchAttachment = onFetchAttachment
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun ThreadConversationContent(
    emails: List<Email>,
    decryptedBodies: Map<String, PgpDecryptionResult> = emptyMap(),
    modifier: Modifier = Modifier,
    isConversationView: Boolean = true,
    fontScaleMultiplier: Float = 1f,
    loadRemoteImages: Boolean = true,
    renderMarkdown: Boolean = false,
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
                        .background(
                            if (index % 2 == 1 && isExpanded)
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f)
                            else MaterialTheme.colorScheme.background
                        )
                        .clickable { expandedMap[email.id] = !isExpanded }
                        .padding(start = 12.dp, end = 20.dp, top = 10.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Vertical connecting line + avatar column
                    Box(
                        modifier = Modifier.width(36.dp)
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
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = displayName(email.from),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = formatRelativeDate(email.date),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = email.fromEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "to: ${email.to}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!isExpanded) {
                            val snippetText = remember(email.snippet) {
                                email.snippet
                                    .replace(Regex("^\\s*(>|&gt;|\\|).*", RegexOption.MULTILINE), "")
                                    .replace(Regex("On\\s.+\\swrote:"), "")
                                    .replace(Regex("<blockquote[^>]*>[\\s\\S]*?</blockquote>"), "")
                                    .trim()
                            }
                            Text(
                                text = snippetText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 1)
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.background
                            )
                    ) {
                        // Thread connecting line
                        if (index < emails.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(200.dp)
                                    .padding(start = 24.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            // Show CC/BCC when expanded
                            if (email.cc.isNotBlank() || email.bcc.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, end = 12.dp, top = 4.dp, bottom = 2.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Column {
                                        if (email.cc.isNotBlank()) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "cc:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = email.cc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 5,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        if (email.bcc.isNotBlank()) {
                                            if (email.cc.isNotBlank()) Spacer(modifier = Modifier.height(2.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "bcc:",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.width(28.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = email.bcc,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 5,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            MessageBody(
                                email = email,
                                decryptedResult = decryptedBodies[email.id],
                                bgColor = bgColor,
                                textColor = textColor,
                                linkColor = linkColor,
                                fontScaleMultiplier = fontScaleMultiplier,
                                loadRemoteImages = loadRemoteImages,
                                renderMarkdown = renderMarkdown,
                                onFetchAttachment = onFetchAttachment,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                MessageBody(
                    email = email,
                    decryptedResult = decryptedBodies[email.id],
                    bgColor = bgColor,
                    textColor = textColor,
                    linkColor = linkColor,
                    fontScaleMultiplier = fontScaleMultiplier,
                    loadRemoteImages = loadRemoteImages,
                    renderMarkdown = renderMarkdown,
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
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
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
                        imageVector = Icons.AutoMirrored.Rounded.Forward,
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
    decryptedResult: PgpDecryptionResult? = null,
    bgColor: String,
    textColor: String,
    linkColor: String,
    fontScaleMultiplier: Float = 1f,
    loadRemoteImages: Boolean = true,
    renderMarkdown: Boolean = false,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
    showSender: Boolean = false,
    messageCount: Int = 0,
    modifier: Modifier = Modifier
) {
    val isEncryptedBlob = decryptedResult == null &&
            (email.body.contains("-----BEGIN PGP MESSAGE-----") ||
             email.body.contains("multipart/encrypted"))
    val bodyText = if (isEncryptedBlob) "" else (decryptedResult?.decryptedBody ?: email.body)
    val bodyIsHtml = !isEncryptedBlob && email.bodyIsHtml
    Column(modifier = modifier) {
        // Encryption badge
        if (decryptedResult != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Encrypted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Encrypted",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                val sigs = decryptedResult.signatures
                if (!sigs.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    sigs.forEach { sig ->
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (sig.isValid) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                            contentDescription = if (sig.isValid) "Valid signature" else "Invalid signature",
                            tint = if (sig.isValid) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = sig.signer,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // Show decrypting placeholder while PGP decryption is in progress
        if (isEncryptedBlob) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "Decrypting…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            return
        }
    val fontSize = (15f * fontScaleMultiplier).coerceIn(10f, 28f)
    val smallFontSize = (13f * fontScaleMultiplier).coerceIn(9f, 24f)
    var showQuotedText by remember { mutableStateOf(false) }
    var showRemoteImages by remember { mutableStateOf(false) }
    val hasQuotedText = remember(bodyText) {
        bodyText.contains("<blockquote", ignoreCase = true) ||
        bodyText.contains("gmail_quote", ignoreCase = true) ||
        bodyText.contains("gmail_extra", ignoreCase = true) ||
        bodyText.contains("yahoo_quoted", ignoreCase = true) ||
        bodyText.contains("moz-cite-prefix", ignoreCase = true) ||
        bodyText.contains("On ", ignoreCase = true) && bodyText.contains(" wrote:", ignoreCase = true)
    }
    if (showSender) {
        val isMsgUnread = !email.isRead
        var showCcBcc by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Row 1: Avatar + Name + Date
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(10.dp))
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
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDetailDate(email.date),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = email.fromEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMsgUnread) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
            // Row 2: To / CC / BCC
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "to: ${email.to}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    if (email.cc.isNotBlank() || email.bcc.isNotBlank())
                        showCcBcc = !showCcBcc
                }
            )
            if (showCcBcc && (email.cc.isNotBlank() || email.bcc.isNotBlank())) {
                if (email.cc.isNotBlank()) {
                    Text(
                        text = "cc: ${email.cc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (email.bcc.isNotBlank()) {
                    Text(
                        text = "bcc: ${email.bcc}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (messageCount > 1) {
                Text(
                    text = "$messageCount messages in thread",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            }
        }
    }

        // Images blocked banner (shown when loadRemoteImages is off and showRemoteImages is still false)

        val htmlContent = remember(email.id, bodyText, bgColor, textColor, linkColor, fontScaleMultiplier, showQuotedText, loadRemoteImages, showRemoteImages, renderMarkdown) {
            // Determine body: preserve or strip quoted text
            val displayBody = if (showQuotedText) bodyText else stripQuotedText(bodyText)

            // Convert markdown to HTML for plain text bodies if enabled
            val preparedBody = if (bodyIsHtml) {
                displayBody
            } else if (renderMarkdown) {
                try {
                    markdownToHtml(displayBody)
                } catch (_: Exception) {
                    TextUtils.htmlEncode(displayBody)
                        .replace("\n", "<br>")
                }
            } else {
                TextUtils.htmlEncode(displayBody)
                    .replace("\n", "<br>")
            }

            // Autolink URLs then sanitize
            val cleanBody = HtmlSanitizer.sanitize(autolinkHtml(preparedBody))

            // Image blocking CSS — block http/https images unless user explicitly allows them
            val imgBlockCss = if (!loadRemoteImages && !showRemoteImages) """
                img[src^="http://"] { display: none !important; }
                img[src^="https://"] { display: none !important; }
            """.trimIndent() else ""

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

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src http: https: data:; font-src 'none'; frame-src 'none';">
                <style>
                    * { box-sizing: border-box; }
                    body {
                        font-family: -apple-system, 'Helvetica Neue', Arial, sans-serif;
                        font-size: ${fontSize}px;
                        line-height: 1.6;
                        margin: 12px 16px 0 16px;
                        padding: 0;
                        background-color: $bgColor;
                        color: $textColor;
                        word-break: break-word;
                        overflow-wrap: break-word;
                    }
                    img, video, iframe, embed {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    img { display: block; }
                    a, a * { color: $linkColor !important; text-decoration: underline !important; word-break: break-word; }
                    table {
                        width: 100% !important;
                        max-width: 100% !important;
                        border-collapse: collapse;
                        overflow-x: auto;
                        display: inline-table;
                        word-break: break-word;
                    }
                    td, th { word-break: break-word; padding: 4px 8px; border-color: inherit; }
                    pre, code { white-space: pre-wrap; font-size: ${smallFontSize}px; word-break: break-word; }
                    /* Collapsible quoted text \u2014 hidden by default, shown when parent has .show-quotes */
                    blockquote, .gmail_quote, .gmail_extra,
                    .yahoo_quoted, .moz-cite-prefix,
                    [name="quoted-content"] {
                        display: none;
                    }
                    .show-quotes blockquote,
                    .show-quotes .gmail_quote,
                    .show-quotes .gmail_extra,
                    .show-quotes .yahoo_quoted,
                    .show-quotes .moz-cite-prefix,
                    .show-quotes [name="quoted-content"] {
                        display: block;
                        padding: 0 !important;
                        margin: 0 !important;
                        background: transparent !important;
                    }
                    blockquote {
                        padding: 0 !important;
                        margin: 0 !important;
                        background: transparent !important;
                    }
                    /* App-theme-aware overrides: transparent explicit backgrounds in dark theme */
                    body.monomail-dark [style*="background-color:#"] {
                        background-color: transparent !important;
                    }
                    body.monomail-dark [style*="background:#"] {
                        background: transparent !important;
                    }
                    $imgBlockCss
                </style>
            </head>
            <body class="${if (showQuotedText) "show-quotes " else ""}${if (useDarkTheme) "monomail-dark" else ""}">$cleanBody</body>
            </html>
            """.trimIndent()
        }

        // "Images blocked" banner — only shown when remote images are disabled by setting
        if (!loadRemoteImages && !showRemoteImages) {
            val hasExternalImages = remember(bodyText) {
                bodyText.contains("""src="http""", ignoreCase = true)
            }
            if (hasExternalImages) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ImageNotSupported,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Remote images blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { showRemoteImages = true }) {
                        Text(
                            text = "Show",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        var emailContentWebView by remember { mutableStateOf<WebView?>(null) }
        DisposableEffect(email.id) {
            onDispose {
                emailContentWebView?.apply {
                    removeAllViews()
                    destroy()
                }
                emailContentWebView = null
            }
        }

        Column {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                factory = { context ->
                    WebView(context).apply {
                        emailContentWebView = this
                        settings.javaScriptEnabled = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.domStorageEnabled = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.loadsImagesAutomatically = loadRemoteImages || showRemoteImages
                        try {
                            WebView::class.java.getMethod("setAllowFileAccess", Boolean::class.java)
                                .invoke(this, false)
                        } catch (_: Exception) {}
                        try {
                            WebView::class.java.getMethod("setAllowContentAccess", Boolean::class.java)
                                .invoke(this, false)
                        } catch (_: Exception) {}
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?
                            ): Boolean {
                                request?.url?.let { uri ->
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return super.shouldOverrideUrlLoading(view, request)
                            }
                            @Suppress("DEPRECATION")
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                                url?.let {
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(it)).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                        return true
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                return super.shouldOverrideUrlLoading(view, url)
                            }
                        }
                    }
                },
                update = { webView ->
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    }
                }
            )

            // "Show quoted text" toggle
            if (hasQuotedText) {
                TextButton(
                    onClick = { showQuotedText = !showQuotedText },
                    modifier = Modifier.padding(start = 12.dp)
                ) {
                    Icon(
                        imageVector = if (showQuotedText) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showQuotedText) "Hide quoted text" else "Show quoted text",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            if (email.attachments.isNotEmpty()) {
                AttachmentsSection(
                    attachments = email.attachments,
                    onFetchAttachment = onFetchAttachment
                )
            }
        }
    }
}

private fun openAttachment(context: android.content.Context, attachment: EmailAttachmentInfo, bytes: ByteArray?) {
    if (bytes == null) return
    try {
        val attachmentsDir = java.io.File(context.cacheDir, "attachments")
        attachmentsDir.mkdirs()
        val safeName = attachment.name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = java.io.File(attachmentsDir, safeName)
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
            // Responsive column count — each card minimum ~200dp
            BoxWithConstraints {
                val columnWidth = 200.dp
                val availableWidth = maxWidth
                val columns = maxOf(1, (availableWidth / columnWidth).toInt())
                fileAttachments.chunked(columns).forEach { rowItems ->
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
                        // Fill remaining slots with spacers for consistent sizing
                        repeat(columns - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
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
                            imageVector = Icons.Rounded.AttachFile,
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
                imageVector = Icons.Rounded.Image,
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

/**
 * Returns a relative timestamp for recent messages ("2h ago", "Yesterday")
 * and falls back to the full date for older messages.
 */
private fun formatRelativeDate(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1  -> "just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24   -> "${hours}h ago"
        days == 1L   -> "Yesterday"
        days < 7     -> "${days}d ago"
        days < 365   -> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
            val month = cal.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.SHORT, Locale.getDefault())
            "${cal.get(java.util.Calendar.DAY_OF_MONTH)} $month"
        }
        else -> formatDetailDate(epochMillis)
    }
}

private fun displayName(from: String): String {
    val nameMatch = Regex("""^"?([^"<]+?)"?\s*<""").find(from)
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

private fun autolinkHtml(html: String): String {
    if (!html.contains("http://", ignoreCase = true) && !html.contains("https://", ignoreCase = true)) {
        return html
    }
    val urlRegex = Regex("""\b(https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]*[-A-Za-z0-9+&@#/%=~_|])""")
    val sb = StringBuilder()
    var i = 0
    val len = html.length
    var inAnchor = false

    while (i < len) {
        val nextTag = html.indexOf('<', i)
        if (nextTag == -1) {
            val text = html.substring(i)
            if (inAnchor) {
                sb.append(text)
            } else {
                sb.append(urlRegex.replace(text) { match ->
                    val url = match.groupValues[1]
                    """<a href="$url">$url</a>"""
                })
            }
            break
        }

        if (nextTag > i) {
            val text = html.substring(i, nextTag)
            if (inAnchor) {
                sb.append(text)
            } else {
                sb.append(urlRegex.replace(text) { match ->
                    val url = match.groupValues[1]
                    """<a href="$url">$url</a>"""
                })
            }
        }

        val tagEnd = html.indexOf('>', nextTag)
        if (tagEnd == -1) {
            sb.append(html.substring(nextTag))
            break
        }

        val tag = html.substring(nextTag, tagEnd + 1)
        sb.append(tag)
        if (tag.startsWith("<a ", ignoreCase = true) || tag.startsWith("<a>", ignoreCase = true)) {
            inAnchor = true
        } else if (tag.startsWith("</a>", ignoreCase = true)) {
            inAnchor = false
        }
        i = tagEnd + 1
    }
    return sb.toString()
}

/**
 * Converts a subset of Markdown syntax to HTML for WebView rendering.
 * Handles: headers, bold, italic, inline code, code blocks, links,
 * unordered lists, ordered lists, blockquotes, and horizontal rules.
 */
private fun markdownToHtml(markdown: String): String {
    var html = markdown

    // Escape HTML entities first to prevent XSS/injection
    html = html.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    // Code block (fenced) — must be before inline code
    html = html.replace(Regex("""```(\w*)\n([\s\S]*?)```""")) { match ->
        val code = match.groupValues[2].trimEnd()
        "<pre><code>${code}</code></pre>"
    }

    // Indented code blocks (4 spaces at start of line)
    html = html.replace(Regex("""(?m)^(?:    |\t)(.+)$""")) { match ->
        "<code>${match.groupValues[1]}</code><br>"
    }

    // Headers (atx-style)
    html = html.replace(Regex("""(?m)^##### (.+)$""")) { "<h5>${it.groupValues[1]}</h5>" }
    html = html.replace(Regex("""(?m)^#### (.+)$""")) { "<h4>${it.groupValues[1]}</h4>" }
    html = html.replace(Regex("""(?m)^### (.+)$""")) { "<h3>${it.groupValues[1]}</h3>" }
    html = html.replace(Regex("""(?m)^## (.+)$""")) { "<h2>${it.groupValues[1]}</h2>" }
    html = html.replace(Regex("""(?m)^# (.+)$""")) { "<h1>${it.groupValues[1]}</h1>" }

    // Blockquotes
    html = html.replace(Regex("""(?m)^&gt; (.*)$""")) { "<blockquote>${it.groupValues[1]}</blockquote>" }

    // Horizontal rules
    html = html.replace(Regex("""(?m)^([-*_]){3,}\s*$"""), "<hr>")

    // Unordered lists
    html = html.replace(Regex("""(?m)^[*-] (.+)$""")) { "<li>${it.groupValues[1]}</li>" }

    // Ordered lists
    html = html.replace(Regex("""(?m)^\d+\. (.+)$""")) { "<li>${it.groupValues[1]}</li>" }

    // Wrap consecutive <li> in <ul> tags
    html = html.replace(Regex("""(?:<li>.*?</li>)+""")) {
        val listItems = it.value.split("</li>").filter { it.isNotBlank() }
        if (listItems.size <= 1) return@replace it.value
        "<ul>${it.value}</ul>"
    }
    // Fix: wrap isolated <li> items too
    html = html.replace(Regex("""(<li>.*?</li>)(?!</li>)""")) { "<ul>${it.value}</ul>" }

    // Inline code (must be after code blocks)
    html = html.replace(Regex("""`([^`\n]+?)`""")) { "<code>${it.groupValues[1]}</code>" }

    // Images — must be before links
    html = html.replace(Regex("""!\[([^\]]*)\]\(([^)]+)\)""")) { "<img src=\"${it.groupValues[2]}\" alt=\"${it.groupValues[1]}\">" }

    // Links [text](url)
    html = html.replace(Regex("""\[([^\]]+)\]\(([^)]+)\)""")) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }

    // Bold
    html = html.replace(Regex("""\*\*(.+?)\*\*""")) { "<strong>${it.groupValues[1]}</strong>" }
    html = html.replace(Regex("""__(.+?)__""")) { "<strong>${it.groupValues[1]}</strong>" }

    // Italic
    html = html.replace(Regex("""\*(.+?)\*""")) { "<em>${it.groupValues[1]}</em>" }
    html = html.replace(Regex("""_(.+?)_""")) { "<em>${it.groupValues[1]}</em>" }

    // Strikethrough
    html = html.replace(Regex("""~~(.+?)~~""")) { "<del>${it.groupValues[1]}</del>" }

    // Line breaks (preserve double newlines as paragraph breaks, single as <br>)
    html = html.replace(Regex("""\n\n+"""), "</p><p>")
    html = html.replace(Regex("""\n(?!</)"""), "<br>")

    // Wrap in paragraph if not already wrapped in block-level tags
    if (!html.startsWith("<h") && !html.startsWith("<p") && !html.startsWith("<pre") && !html.startsWith("<ul") && !html.startsWith("<blockquote")) {
        html = "<p>$html</p>"
    }

    return html
}
