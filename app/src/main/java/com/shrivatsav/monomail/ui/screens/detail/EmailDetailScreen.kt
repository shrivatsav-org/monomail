package com.shrivatsav.monomail.ui.screens.detail
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
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
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.settings.EmailTheme
import com.shrivatsav.monomail.ui.screens.inbox.AvatarCircle
import com.shrivatsav.monomail.data.pgp.PgpDecryptionResult
import com.shrivatsav.monomail.util.normalizeEmailBody
import com.shrivatsav.monomail.util.stripUnsafeHtml
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

import androidx.compose.material3.ModalBottomSheet
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
    val emailTheme by viewModel.emailTheme.collectAsState()
    val showInlineAttachments by viewModel.showInlineAttachments.collectAsState()
    val state by viewModel.state.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val decryptedBodies by viewModel.decryptedBodies.collectAsState()
    val isDeveloperMode by viewModel.isDeveloperMode.collectAsState()

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
                        AnimatedContent(
                            targetState = isStarred,
                            transitionSpec = {
                                (fadeIn(tween(200)) + scaleIn(tween(200))) togetherWith
                                (fadeOut(tween(150)) + scaleOut(tween(150)))
                            },
                            label = "starToggle"
                        ) { starred ->
                            Icon(
                                imageVector = if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (starred) "Unstar" else "Star",
                                tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
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
        DetailContent(
            state = state,
            padding = padding,
            isConversationView = isConversationView,
            fontScaleMultiplier = fontScaleMultiplier,
            loadRemoteImages = loadRemoteImages,
            emailTheme = emailTheme,
            showInlineAttachments = showInlineAttachments,
            isDeveloperMode = isDeveloperMode,
            decryptedBodies = decryptedBodies,
            currentUserEmail = viewModel.currentUserEmail,
            onReply = onReply,
            onForward = onForward,
            onFetchAttachment = onFetchAttachment
        )
    }

}

@Composable
private fun DetailContent(
    state: EmailDetailState,
    padding: androidx.compose.foundation.layout.PaddingValues,
    isConversationView: Boolean,
    fontScaleMultiplier: Float,
    loadRemoteImages: Boolean,
    emailTheme: EmailTheme,
    showInlineAttachments: Boolean,
    isDeveloperMode: Boolean,
    decryptedBodies: Map<String, PgpDecryptionResult>,
    currentUserEmail: String,
    onReply: (to: String, subject: String, body: String, threadId: String, messageId: String) -> Unit,
    onForward: (subject: String, body: String, threadId: String, messageId: String) -> Unit,
    onFetchAttachment: suspend (String, String) -> ByteArray?
) {
    when (val s = state) {
        is EmailDetailState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = s.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        is EmailDetailState.Success -> {
            val emails = s.emails
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (s.isRefreshing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (s.refreshError != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = s.refreshError,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (emails.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Loading…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                } else {
                    val latestEmail = emails.last()
                    val latestBody = decryptedBodies[latestEmail.id]?.decryptedBody ?: latestEmail.body
                    val myEmail = currentUserEmail
                    val replyTarget = emails.lastOrNull {
                        it.fromEmail.isNotBlank() && !it.fromEmail.equals(myEmail, ignoreCase = true)
                    }?.fromEmail ?: latestEmail.to
                    ThreadConversationContent(
                        emails = emails,
                        decryptedBodies = decryptedBodies,
                        modifier = Modifier.weight(1f),
                        isConversationView = isConversationView,
                        fontScaleMultiplier = fontScaleMultiplier,
                        loadRemoteImages = loadRemoteImages,
                        emailTheme = emailTheme,
                        showInlineAttachments = showInlineAttachments,
                        isDeveloperMode = isDeveloperMode,
                        onReply = { onReply(replyTarget, latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                        onForward = { onForward(latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                        onFetchAttachment = onFetchAttachment
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadConversationContent(
    emails: List<Email>,
    decryptedBodies: Map<String, PgpDecryptionResult> = emptyMap(),
    modifier: Modifier = Modifier,
    isConversationView: Boolean = true,
    fontScaleMultiplier: Float = 1f,
    loadRemoteImages: Boolean = true,
    emailTheme: EmailTheme = EmailTheme.AUTO,
    showInlineAttachments: Boolean = true,
    isDeveloperMode: Boolean = false,
    onReply: () -> Unit = {},
    onForward: () -> Unit = {},
    onFetchAttachment: suspend (String, String) -> ByteArray? = { _, _ -> null }
) {
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
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Text(
            text = subject,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        )
        if (emails.size > 1) {
            Text(
                text = "${emails.size} messages in thread",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 0.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        emails.forEachIndexed { index, email ->
            val isExpanded = expandedMap[email.id] ?: true
            if (isConversationView) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (index % 2 == 1 && isExpanded)
                                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.background
                        )
                        .clickable { expandedMap[email.id] = !isExpanded }
                        .padding(start = 16.dp, end = 24.dp, top = 12.dp, bottom = 0.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Avatar column
                    Box(
                        modifier = Modifier.width(40.dp)
                    ) {
                        AvatarCircle(
                            photoUrl = null,
                            displayName = displayName(email.from),
                            size = 36.dp,
                            textStyle = MaterialTheme.typography.titleSmall
                        )
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
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = email.fromEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "to:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = email.to,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (!isExpanded) {
                            val snippetText = remember(email.snippet) {
                                email.snippet
                                    .replace(Regex("^\\s*(>|&gt;|\\|).*", RegexOption.MULTILINE), "")
                                    .replace(Regex("On\\s.+\\swrote:"), "")
                                    .replace(Regex("<blockquote[^>]*>[\\s\\S]*?</blockquote>"), "")
                                    .trim()
                            }
                            Spacer(modifier = Modifier.height(4.dp))
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
                // Per-email attachment summary — between sender info and body
                if (!showInlineAttachments && email.attachments.isNotEmpty()) {
                    val perEmailAttachments = email.attachments.map { it to displayName(email.from) }
                    ThreadAttachmentsSummary(
                        attachmentsWithSender = perEmailAttachments,
                        onFetchAttachment = onFetchAttachment
                    )
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (index % 2 == 1)
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.25f)
                                else MaterialTheme.colorScheme.background
                            )
                    ) {
                        // Thread connecting line — height matches content dynamically
                        if (index < emails.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .heightIn(min = 120.dp)
                                    .padding(start = 28.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            )
                        }
                        Column(modifier = Modifier.weight(1f).heightIn(min = 120.dp)) {
                            // Show CC/BCC when expanded
                            if (email.cc.isNotBlank() || email.bcc.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                                textColor = textColor,
                                linkColor = linkColor,
                                fontScaleMultiplier = fontScaleMultiplier,
                                loadRemoteImages = loadRemoteImages,
                                emailTheme = emailTheme,
                                showInlineAttachments = showInlineAttachments,
                                onFetchAttachment = onFetchAttachment,
                                isDeveloperMode = isDeveloperMode,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            } else {
                MessageBody(
                    email = email,
                    decryptedResult = decryptedBodies[email.id],
                    textColor = textColor,
                    linkColor = linkColor,
                    fontScaleMultiplier = fontScaleMultiplier,
                    loadRemoteImages = loadRemoteImages,
                    emailTheme = emailTheme,
                    showInlineAttachments = showInlineAttachments,
                    onFetchAttachment = onFetchAttachment,
                    isDeveloperMode = isDeveloperMode,
                    showSender = true,
                    messageCount = emails.size
                )
            }
            if (index < emails.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onReply,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Reply",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reply", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onForward,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Forward,
                        contentDescription = "Forward",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Forward", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        
    }
}

@Composable
private fun MessageBody(
    email: Email,
    decryptedResult: PgpDecryptionResult? = null,
    textColor: String,
    linkColor: String,
    fontScaleMultiplier: Float = 1f,
    loadRemoteImages: Boolean = true,
    emailTheme: EmailTheme = EmailTheme.AUTO,
    showInlineAttachments: Boolean = true,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
    isDeveloperMode: Boolean = false,
    showSender: Boolean = false,
    messageCount: Int = 0,
    modifier: Modifier = Modifier
) {
    // Check for actual PGP content (not just plaintext mentioning MIME types)
    // ponytail: uses starts-with heuristics — full MIME parser would be more precise
    val isEncryptedBlob = decryptedResult == null && (
        email.body.startsWith("-----BEGIN PGP MESSAGE-----") ||
        email.body.contains("multipart/encrypted;") ||     // MIME header, not prose
        email.body.contains("multipart/encrypted\r\n")       // MIME header with CRLF
    )
    val normalizedBody = if (isEncryptedBlob) {
        normalizeEmailBody("", bodyIsHtml = false)
    } else {
        normalizeEmailBody(decryptedResult?.decryptedBody ?: email.body, email.bodyIsHtml)
    }
    val bodyText = normalizedBody.text
    val bodyIsHtml = !isEncryptedBlob && normalizedBody.isHtml
    // ponytail: strip runs on composition thread; move to background when HTML prep is refactored
    val safeBodyText = if (bodyIsHtml) stripUnsafeHtml(bodyText) else bodyText
    Column(modifier = modifier) {
        // Encryption badge
        if (decryptedResult != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                    .padding(horizontal = 16.dp, vertical = 32.dp)
            ) {
                Text(
                    text = "Decrypting…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            return
        }
    var showQuotedText by remember { mutableStateOf(false) }
    val hasQuotedText = remember(safeBodyText) {
        safeBodyText.contains("<blockquote", ignoreCase = true) ||
        safeBodyText.contains("gmail_quote", ignoreCase = true) ||
        safeBodyText.contains("gmail_extra", ignoreCase = true) ||
        safeBodyText.contains("yahoo_quoted", ignoreCase = true) ||
        safeBodyText.contains("moz-cite-prefix", ignoreCase = true) ||
        safeBodyText.contains("appendonsend", ignoreCase = true) ||
        safeBodyText.contains("divRplyFwdMsg", ignoreCase = true) ||
        safeBodyText.contains("On ", ignoreCase = true) && safeBodyText.contains(" wrote:", ignoreCase = true)
    }
    if (showSender) {
        val isMsgUnread = !email.isRead
        var showCcBcc by remember { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Row 1: Avatar + Name + Date
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(
                    photoUrl = null,
                    displayName = displayName(email.from),
                    size = 40.dp,
                    textStyle = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName(email.from),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (isMsgUnread) FontWeight.Bold else FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatDetailDate(email.date),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = email.fromEmail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isMsgUnread) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
            // Row 2: To / CC / BCC
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "to:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = email.to,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (email.cc.isNotBlank() || email.bcc.isNotBlank())
                                showCcBcc = !showCcBcc
                        }
                )
            }
            if (showCcBcc && (email.cc.isNotBlank() || email.bcc.isNotBlank())) {
                Spacer(modifier = Modifier.height(4.dp))
                if (email.cc.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "cc:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = email.cc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = email.bcc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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

        // Per-email attachment summary — shown between sender info and body when dropdown mode
        // Only when MessageBody handles sender info (non-conversation view); conversation view
        // inserts it before the MessageBody call in the email loop.
        if (showSender && !showInlineAttachments && email.attachments.isNotEmpty()) {
            val perEmailAttachments = email.attachments.map { it to displayName(email.from) }
            Spacer(modifier = Modifier.height(8.dp))
            ThreadAttachmentsSummary(
                attachmentsWithSender = perEmailAttachments,
                onFetchAttachment = onFetchAttachment
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        var showRemoteImages by remember { mutableStateOf(false) }

        if (!loadRemoteImages && !showRemoteImages && bodyIsHtml) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Remote images blocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showRemoteImages = true }) {
                    Text("Show images", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        val useOverviewScaling = remember(safeBodyText, bodyIsHtml) {
            bodyIsHtml && looksFixedWidthTemplate(safeBodyText) && !looksMobileFriendly(safeBodyText) && !looksDataTableEmail(safeBodyText)
        }

        // ponytail: CSS zoom scales 600px template emails to fit the card
        val emailZoomFactor = if (useOverviewScaling) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val availableWidthDp = screenWidthDp - 56
            (availableWidthDp.toFloat() / 600f).coerceIn(0.3f, 1.0f)
        } else 1.0f

        val htmlContent = remember(email.id, safeBodyText, fontScaleMultiplier, showQuotedText, showInlineAttachments, loadRemoteImages, showRemoteImages, emailTheme, useOverviewScaling, emailZoomFactor) {
            val displayBody = if (bodyIsHtml) {
                safeBodyText
            } else {
                TextUtils.htmlEncode(safeBodyText).replace("\n", "<br>")
            }

            val bodyWithCidPlaceholders = if (!showInlineAttachments && email.attachments.isNotEmpty()) {
                var b = displayBody
                for (att in email.attachments) {
                    if (att.name.isBlank()) continue
                    val escapedName = Regex.escape(att.name)
                    val cidPattern = Regex(
                        """<img[^>]*src\s*=\s*["']cid:${escapedName}["'][^>]*>""",
                        RegexOption.IGNORE_CASE
                    )
                    b = b.replace(cidPattern) {
                        """<div style="padding:8px;margin:8px 0;background:#f0f0f0;border-radius:6px;text-align:center;font-family:sans-serif;font-size:13px;color:#666;">📎 <em>${att.name}</em> (inline image — see attachments)</div>"""
                    }
                }
                b
            } else {
                displayBody
            }

            val imgBlockCss = if (!loadRemoteImages && !showRemoteImages) """
                img[src^="http://"] { display: none !important; }
                img[src^="https://"] { display: none !important; }
            """.trimIndent() else ""

            val quotedCss = """
                blockquote, .gmail_quote, .gmail_extra,
                .yahoo_quoted, .moz-cite-prefix,
                #appendonsend, #divRplyFwdMsg,
                [name="quoted-content"] {
                    display: none;
                }
                .show-quotes blockquote,
                .show-quotes .gmail_quote,
                .show-quotes .gmail_extra,
                .show-quotes .yahoo_quoted,
                .show-quotes .moz-cite-prefix,
                .show-quotes #appendonsend,
                .show-quotes #divRplyFwdMsg,
                .show-quotes [name="quoted-content"] {
                    display: block;
                    padding: 0 !important;
                    margin: 0 !important;
                    background: transparent !important;
                }
                blockquote {
                    border-left: 3px solid ${linkColor}44;
                    padding: 4px 0 4px 12px !important;
                    margin: 8px 0 !important;
                    background: transparent !important;
                    color: ${textColor}aa;
                }
            """.trimIndent()

            val fontSize = (15f * fontScaleMultiplier).coerceIn(10f, 28f)
            val baseCss = """
                    body, body * {
                        box-sizing: border-box !important;
                    }
                    body {
                        font-size: ${fontSize}px;
                        line-height: 1.65;
                        margin: 0;
                        padding: 4px;
                        word-break: break-word;
                        overflow-wrap: break-word;
                        -webkit-font-smoothing: antialiased;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    $imgBlockCss
                    $quotedCss
            """.trimIndent()
            val responsiveCss = if (useOverviewScaling) {
                """
                    html, body {
                        min-width: 0 !important;
                        overflow-x: hidden !important;
                    }
                    body {
                        zoom: $emailZoomFactor;
                    }
                    $baseCss
                """.trimIndent()
            } else {
                """
                    html, body {
                        width: 100% !important;
                        min-width: 0 !important;
                        max-width: 100% !important;
                        overflow-x: auto !important;
                    }
                    body {
                        font-size: ${fontSize}px;
                        line-height: 1.65;
                        margin: 0;
                        padding: 4px;
                        word-break: break-word;
                        overflow-wrap: break-word;
                        -webkit-font-smoothing: antialiased;
                    }
                    body, body * {
                        box-sizing: border-box !important;
                    }
                    center, .gwfw, .m-shell, .td {
                        width: 100% !important;
                        min-width: 0 !important;
                        max-width: 100% !important;
                    }
                    div, p, span, a, h1, h2, h3, h4, h5, h6 {
                        max-width: 100% !important;
                        min-width: 0 !important;
                        overflow-wrap: break-word;
                    }
                    th[scope="col"] {
                        width: auto !important;
                        min-width: max-content !important;
                        white-space: nowrap !important;
                    }
                    [width="600"], [width="640"], [width="700"] {
                        width: 100% !important;
                    }
                    [style*="width:600px"], [style*="width: 600px"],
                    [style*="min-width:600px"], [style*="min-width: 600px"],
                    [style*="width:640px"], [style*="width: 640px"],
                    [style*="min-width:640px"], [style*="min-width: 640px"] {
                        width: 100% !important;
                        min-width: 0 !important;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    $imgBlockCss
                    $quotedCss
                """.trimIndent()
            }
            val viewport = "width=device-width, initial-scale=1.0"

            """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="$viewport">
                <style>
                    $responsiveCss
                </style>
            </head>
            <body class="${if (showQuotedText) "show-quotes" else ""}">
                ${stripBodyInlineStyles(bodyWithCidPlaceholders)}
                <style>
                    $responsiveCss
                </style>
            </body>
            </html>
            """.trimIndent()
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

        val cardBgColor = if (emailTheme == EmailTheme.ORIGINAL) Color.White
            else MaterialTheme.colorScheme.surfaceContainerLow

        val useDarkening = when (emailTheme) {
            EmailTheme.FORCE_DARK -> true
            EmailTheme.AUTO -> isSystemInDarkTheme()
            else -> false
        }

        Column {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardBgColor, RoundedCornerShape(16.dp))
                    .padding(12.dp),
                factory = { context ->
                    WebView(context).apply {
                        emailContentWebView = this
                        settings.javaScriptEnabled = false
                        settings.domStorageEnabled = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.loadsImagesAutomatically = true
                        try {
                            WebView::class.java.getMethod("setAllowFileAccess", Boolean::class.java)
                                .invoke(this, true)
                        } catch (e: Exception) { android.util.Log.w("EmailDetail", "setAllowFileAccess failed", e) }
                        try {
                            WebView::class.java.getMethod("setAllowContentAccess", Boolean::class.java)
                                .invoke(this, false)
                        } catch (e: Exception) { android.util.Log.w("EmailDetail", "setAllowContentAccess failed", e) }
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = true
                        var downX = 0f
                        var downY = 0f
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    downX = event.x
                                    downY = event.y
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    val dx = kotlin.math.abs(event.x - downX)
                                    val dy = kotlin.math.abs(event.y - downY)
                                    view.parent?.requestDisallowInterceptTouchEvent(dx > dy)
                                }
                                android.view.MotionEvent.ACTION_UP,
                                android.view.MotionEvent.ACTION_CANCEL -> {
                                    view.parent?.requestDisallowInterceptTouchEvent(false)
                                }
                            }
                            false
                        }
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
                                        android.util.Log.e("EmailDetail", "Failed to open URL in browser", e)
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
                                        android.util.Log.e("EmailDetail", "Failed to open URL in browser", e)
                                    }
                                }
                                return super.shouldOverrideUrlLoading(view, url)
                            }
                            // Log sub-resource HTTP errors (e.g. images that return 404)
                            // ponytail: logs only — bulk/notification UI would require a JS bridge
                            @Suppress("DEPRECATION")
                            @Deprecated("Deprecated in Java")
                            override fun onReceivedHttpError(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                                errorResponse: android.webkit.WebResourceResponse?
                            ) {
                                super.onReceivedHttpError(view, request, errorResponse)
                                if (request?.isForMainFrame != true) {
                                    android.util.Log.w("EmailWebView", "HTTP ${errorResponse?.statusCode} for ${request?.url}")
                                }
                            }
                            override fun onReceivedError(
                                view: android.webkit.WebView?,
                                request: android.webkit.WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                android.util.Log.e("EmailWebView", "WebView error: ${error?.description} on ${request?.url}")
                            }
                        }
                    }
                },
                update = { webView ->
                    webView.settings.loadWithOverviewMode = false
                    webView.settings.useWideViewPort = false
                    webView.isHorizontalScrollBarEnabled = !useOverviewScaling
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                        WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, useDarkening)
                    }
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        try {
                            webView.loadDataWithBaseURL("file:///android_asset/", htmlContent, "text/html", "UTF-8", null)
                        } catch (e: Exception) {
                            android.util.Log.e("EmailWebView", "Failed to load email HTML content", e)
                        }
                    }
                }
            )

            // "Show quoted text" toggle
            if (hasQuotedText) {
                TextButton(
                    onClick = { showQuotedText = !showQuotedText },
                    modifier = Modifier.padding(start = 16.dp)
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
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            // Developer: copy raw body
            if (isDeveloperMode) {
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("email_body", email.body))
                        android.widget.Toast.makeText(context, "Raw body copied", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Copy raw ${if (email.bodyIsHtml) "HTML" else "text"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }
            }

            if (showInlineAttachments && email.attachments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
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
        android.util.Log.e("EmailDetail", "Failed to open attachment", e)
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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${attachments.size} Attachment${if (attachments.size > 1) "s" else ""}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
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
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = "Attachment Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatFileSize(attachment.size.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
            .clip(RoundedCornerShape(12.dp))
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
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (ext.length in 1..4) ext else "FILE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(attachment.size.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun ThreadAttachmentsSummary(
    attachmentsWithSender: List<Pair<EmailAttachmentInfo, String>>,
    onFetchAttachment: suspend (String, String) -> ByteArray?
) {
    var expanded by remember { mutableStateOf(false) }
    val imageAttachments = attachmentsWithSender.filter { isImageAttachment(it.first) }
    val fileAttachments = attachmentsWithSender.filter { !isImageAttachment(it.first) }
    val totalCount = attachmentsWithSender.size
    val imageCount = imageAttachments.size

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AttachmentToggleButton(
            totalCount = totalCount,
            imageCount = imageCount,
            expanded = expanded,
            onClick = { expanded = !expanded }
        )

        if (expanded) {
            ExpandedAttachmentList(
                imageAttachments = imageAttachments,
                fileAttachments = fileAttachments,
                onFetchAttachment = onFetchAttachment
            )
        }
    }
}

@Composable
private fun AttachmentToggleButton(
    totalCount: Int,
    imageCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.AttachFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$totalCount Attachment${if (totalCount != 1) "s" else ""}" +
                if (imageCount > 0) " ($imageCount image${if (imageCount != 1) "s" else ""})" else "",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ExpandedAttachmentList(
    imageAttachments: List<Pair<EmailAttachmentInfo, String>>,
    fileAttachments: List<Pair<EmailAttachmentInfo, String>>,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        imageAttachments.forEach { (attachment, sender) ->
            Column {
                ImageAttachmentCard(
                    attachment = attachment,
                    onFetchAttachment = onFetchAttachment
                )
                Text(
                    text = "from $sender",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }

        if (fileAttachments.isNotEmpty()) {
            FileAttachmentsGrid(
                fileAttachments = fileAttachments,
                onFetchAttachment = onFetchAttachment
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FileAttachmentsGrid(
    fileAttachments: List<Pair<EmailAttachmentInfo, String>>,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
) {
    BoxWithConstraints {
        val columnWidth = 200.dp
        val columns = maxOf(1, (maxWidth / columnWidth).toInt())
        fileAttachments.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (attachment, sender) ->
                    Column(modifier = Modifier.weight(1f)) {
                        FileAttachmentCard(
                            attachment = attachment,
                            onFetchAttachment = onFetchAttachment,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "from $sender",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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

private fun looksFixedWidthTemplate(html: String): Boolean {
    if (html.length < 500) return false
    return FIXED_WIDTH_ATTR.containsMatchIn(html) ||
        FIXED_WIDTH_STYLE.containsMatchIn(html) ||
        html.contains("m-shell", ignoreCase = true) ||
        html.contains("tbl_main", ignoreCase = true)
}

private fun looksDataTableEmail(html: String): Boolean {
    val lower = html.lowercase(Locale.US)
    return lower.contains("<thead") ||
        lower.contains("<th") ||
        lower.contains("scope=\"col\"") ||
        lower.contains("role=\"columnheader\"")
}

// ponytail: emails with "stack" class + column stacking are genuinely responsive — skip zoom
private fun looksMobileFriendly(html: String): Boolean {
    val lower = html.lowercase(Locale.US)
    return lower.contains("stack") && lower.contains("column")
}



// ponytail: strips inline style="" from <body> tags so wrapper CSS isn't overridden
private val BODY_STYLE_RE = Regex("""<body\b([^>]*?)style\s*=\s*"[^"]*"([^>]*?)>""", RegexOption.IGNORE_CASE)
private val BODY_STYLE_RE_SINGLE = Regex("""<body\b([^>]*?)style\s*=\s*'[^']*'([^>]*?)>""", RegexOption.IGNORE_CASE)

private fun stripBodyInlineStyles(html: String): String {
    var result = BODY_STYLE_RE.replace(html) { m ->
        val pre = m.groupValues[1]
        val post = m.groupValues[2]
        "<body$pre$post>"
    }
    result = BODY_STYLE_RE_SINGLE.replace(result) { m ->
        val pre = m.groupValues[1]
        val post = m.groupValues[2]
        "<body$pre$post>"
    }
    return result
}

private val FIXED_WIDTH_ATTR = Regex("""\bwidth\s*=\s*["']?(5[4-9]\d|[6-8]\d\d)["']?""", RegexOption.IGNORE_CASE)
private val FIXED_WIDTH_STYLE = Regex("""(?:width|min-width)\s*:\s*(5[4-9]\d|[6-8]\d\d)px""", RegexOption.IGNORE_CASE)

private fun formatDetailDate(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    return detailDateFormat.format(Date(epochMillis))
}




