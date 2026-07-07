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
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.ImageNotSupported
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Share
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
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.settings.EmailTheme
import com.shrivatsav.monomail.ui.screens.inbox.AvatarCircle
import com.shrivatsav.monomail.data.pgp.PgpDecryptionResult
import com.shrivatsav.monomail.util.HtmlSanitizer
import com.shrivatsav.monomail.util.EmailImageGenerator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val emailTheme by viewModel.emailTheme.collectAsState()
    val state by viewModel.state.collectAsState()
    val isStarred by viewModel.isStarred.collectAsState()
    val decryptedBodies by viewModel.decryptedBodies.collectAsState()

    var isGeneratingImage by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showShareModal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
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
                    if (isGeneratingImage) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        IconButton(onClick = { 
                            if (state is EmailDetailState.Success) {
                                val emails = (state as EmailDetailState.Success).emails
                                if (emails.size > 1) {
                                    showShareModal = true
                                } else if (emails.isNotEmpty()) {
                                    isGeneratingImage = true
                                    coroutineScope.launch {
                                        EmailImageGenerator.shareEmailsAsImage(context, emails, emails.first().subject, fontScaleMultiplier)
                                        isGeneratingImage = false
                                    }
                                }
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share as Image",
                                tint = MaterialTheme.colorScheme.onSurface
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
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().padding(padding)) {
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
                                androidx.compose.material3.CircularProgressIndicator(
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
                        // Reply should target the other party, not the current user. If the last
                        // message in the thread was sent by us, reply to the most recent message
                        // that wasn't (falling back to its recipient if the whole thread is ours).
                        val myEmail = viewModel.currentUserEmail
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
                            renderMarkdown = renderMarkdown,
                            emailTheme = emailTheme,
                            onReply = { onReply(replyTarget, latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                            onForward = { onForward(latestEmail.subject, latestBody, latestEmail.threadId, latestEmail.id) },
                            onFetchAttachment = onFetchAttachment
                        )
                    }
                }
            }
        }
    }

    if (showShareModal && state is EmailDetailState.Success) {
        val emails = (state as EmailDetailState.Success).emails
        ShareEmailSelectionModal(
            emails = emails,
            subject = emails.firstOrNull()?.subject ?: "",
            onDismiss = { showShareModal = false },
            onConfirm = { selectedEmails ->
                showShareModal = false
                isGeneratingImage = true
                coroutineScope.launch {
                    EmailImageGenerator.shareEmailsAsImage(context, selectedEmails, selectedEmails.firstOrNull()?.subject ?: "", fontScaleMultiplier)
                    isGeneratingImage = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareEmailSelectionModal(
    emails: List<Email>,
    subject: String,
    onDismiss: () -> Unit,
    onConfirm: (List<Email>) -> Unit
) {
    var selectedIds by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(emails.map { it.id }.toSet()) }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Share Emails", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(emails) { email ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectedIds.contains(email.id)) selectedIds = selectedIds - email.id
                                else selectedIds = selectedIds + email.id
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = selectedIds.contains(email.id),
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(email.from, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text(
                                text = email.snippet,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(
                onClick = { onConfirm(emails.filter { it.id in selectedIds }) },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Generate Image")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ThreadConversationContent(
    emails: List<Email>,
    decryptedBodies: Map<String, PgpDecryptionResult> = emptyMap(),
    modifier: Modifier = Modifier,
    isConversationView: Boolean = true,
    fontScaleMultiplier: Float = 1f,
    loadRemoteImages: Boolean = true,
    renderMarkdown: Boolean = false,
    emailTheme: EmailTheme = EmailTheme.AUTO,
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
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = fadeIn(tween(200)) + expandVertically(tween(200)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
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
                                    .padding(start = 28.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
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
                                bgColor = bgColor,
                                textColor = textColor,
                                linkColor = linkColor,
                                fontScaleMultiplier = fontScaleMultiplier,
                                loadRemoteImages = loadRemoteImages,
                                renderMarkdown = renderMarkdown,
                                emailTheme = emailTheme,
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
                    emailTheme = emailTheme,
                    onFetchAttachment = onFetchAttachment,
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

/**
 * Heuristic: does this HTML body look like a rich/styled/designed email
 * that should be rendered in Original mode under AUTO?
 *
 * Returns true when the HTML uses layout tables, explicit inline
 * background colors, column-like structures, or has many images —
 * all signs of a designed template rather than a plain message.
 */
private fun looksStyled(body: String): Boolean {
    // Trivial — no HTML worth calling "styled"
    if (body.length < 200) return false

    // Presence of layout tables with width attributes (common in template emails)
    val bodyLower = body.lowercase()
    val hasLayoutTable = bodyLower.contains("<table") && (
        bodyLower.contains("role=\"presentation\"") ||
        bodyLower.contains("width=\"") ||
        bodyLower.contains("cellpadding") ||
        body.contains("bgcolor", ignoreCase = true) ||
        body.contains("background-color:", ignoreCase = true)
    )
    // Multiple images — likely a newsletter/marketing mail
    val imgCount = bodyLower.split("<img", "<img ").count() - 1
    val hasMultipleImages = imgCount >= 3
    // Non-trivial inline style — deliberate styling
    val hasInlineStyles = bodyLower.contains("style=\"") &&
            (bodyLower.contains("font-family:") || bodyLower.contains("color:") || bodyLower.contains("padding:"))

    return (hasLayoutTable && hasInlineStyles) || hasMultipleImages
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
    emailTheme: EmailTheme = EmailTheme.AUTO,
    onFetchAttachment: suspend (String, String) -> ByteArray?,
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
    val bodyText = if (isEncryptedBlob) "" else (decryptedResult?.decryptedBody ?: email.body)
    val bodyIsHtml = !isEncryptedBlob && email.bodyIsHtml
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
        bodyText.contains("appendonsend", ignoreCase = true) ||
        bodyText.contains("divRplyFwdMsg", ignoreCase = true) ||
        bodyText.contains("On ", ignoreCase = true) && bodyText.contains(" wrote:", ignoreCase = true)
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

        // Images blocked banner (shown when loadRemoteImages is off and showRemoteImages is still false)

        val htmlContent = remember(email.id, bodyText, bgColor, textColor, linkColor, fontScaleMultiplier, showQuotedText, loadRemoteImages, showRemoteImages, renderMarkdown, emailTheme) {
            val displayBody = try {
                // Determine body: preserve or strip quoted text
                val body = if (showQuotedText) bodyText else HtmlSanitizer.stripQuotedText(bodyText)

                // Convert markdown to HTML for plain text bodies if enabled
                if (bodyIsHtml) {
                    body
                } else if (renderMarkdown) {
                    try {
                        markdownToHtml(body)
                    } catch (_: Exception) {
                        TextUtils.htmlEncode(body).replace("\n", "<br>")
                    }
                } else {
                    TextUtils.htmlEncode(body).replace("\n", "<br>")
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageBody", "Failed to process email body", e)
                // Fallback: show raw text
                TextUtils.htmlEncode(bodyText).replace("\n", "<br>")
            }

            // Autolink URLs then sanitize
            val cleanBody = HtmlSanitizer.sanitize(autolinkHtml(displayBody))
            // Wrap Outlook forward/reply block so CSS can hide/show it as a unit
            val wrappedBody = wrapOutlookQuoted(cleanBody)

            // Resolve effective render mode from the user's preference
            val isStyled = (displayBody.length > 200 && bodyIsHtml) && looksStyled(displayBody)
            val effectiveMode = when (emailTheme) {
                EmailTheme.FORCE_DARK -> "adapt"
                EmailTheme.FORCE_LIGHT -> "original"
                EmailTheme.ORIGINAL -> "original"
                EmailTheme.AUTO -> if (isStyled) "original" else "adapt"
            }

            // In adapt mode: strip <style> tags (they override our dark CSS),
            // bgcolor attrs (CSS selectors can't target these), and fixed widths.
            // In original mode: only strip fixed-width attrs for responsiveness.
            val enhancedBody = when (effectiveMode) {
                "adapt" -> {
                    var b = wrappedBody
                    b = HtmlSanitizer.stripStyleTags(b)
                    b = HtmlSanitizer.stripBgcolorAttrs(b)
                    b = HtmlSanitizer.stripFixedWidthAttrs(b)
                    b
                }
                else -> {
                    HtmlSanitizer.stripFixedWidthAttrs(wrappedBody)
                }
            }

            // Shared quoted-text CSS (same for both modes)
            val quotedCss = """
                /* Collapsible quoted text - hidden by default */
                blockquote, .gmail_quote, .gmail_extra,
                .yahoo_quoted, .moz-cite-prefix,
                #appendonsend, #divRplyFwdMsg,
                [name="quoted-content"],
                .outlook-quoted {
                    display: none;
                }
                .show-quotes blockquote,
                .show-quotes .gmail_quote,
                .show-quotes .gmail_extra,
                .show-quotes .yahoo_quoted,
                .show-quotes .moz-cite-prefix,
                .show-quotes #appendonsend,
                .show-quotes #divRplyFwdMsg,
                .show-quotes [name="quoted-content"],
                .show-quotes .outlook-quoted {
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

            // Build CSS for each mode
            val modeCss = if (effectiveMode == "adapt") {
                """
                    /* === Adapted (dark-friendly) mode === */
                    @font-face {
                        font-family: 'GoogleSans';
                        src: url('file:///android_res/font/google_sans_flex.ttf') format('truetype');
                    }
                    body {
                        font-family: 'GoogleSans', -apple-system, 'Helvetica Neue', Arial, sans-serif;
                        font-size: ${fontSize}px;
                        line-height: 1.65;
                        margin: 0;
                        padding: 4px 4px 4px 4px !important;
                        background-color: $bgColor;
                        color: $textColor;
                        word-break: break-word;
                        overflow-wrap: break-word;
                        -webkit-font-smoothing: antialiased;
                    }
                    /* Force text color on ALL elements so inline color:#333 is overridden in dark mode */
                    body.monomail-dark * {
                        color: $textColor !important;
                    }
                    body.monomail-dark a, body.monomail-dark a * {
                        color: $linkColor !important;
                    }
                    p { margin: 0 0 1em 0; }
                    p:last-child { margin-bottom: 0; }
                    img, video, iframe, embed {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 8px;
                    }
                    img { display: block; margin: 8px 0; }
                    a, a * { color: $linkColor !important; text-decoration: underline !important; word-break: break-word; }
                    h1, h2, h3, h4 { margin: 1.2em 0 0.6em 0; line-height: 1.3; color: $textColor; }
                    h1:first-child, h2:first-child, h3:first-child { margin-top: 0; }
                    hr { border: none; border-top: 1px solid ${textColor}22; margin: 16px 0; }
                    table {
                        max-width: 100% !important;
                        border-collapse: collapse;
                        overflow-x: auto;
                        word-break: break-word;
                        margin: 8px 0;
                        font-size: ${smallFontSize}px;
                    }
                    /* Wrapper to make wide tables scrollable */
                    .monomail-table-wrap {
                        overflow-x: auto;
                        -webkit-overflow-scrolling: touch;
                        max-width: 100%;
                    }
                    td, th {
                        word-break: break-word;
                        padding: 6px 10px;
                    }
                    th { font-weight: 600; }
                    pre, code { white-space: pre-wrap; font-size: ${smallFontSize}px; word-break: break-word; }
                    pre { background-color: ${textColor}0a; border-radius: 8px; padding: 12px; margin: 8px 0; overflow-x: auto; }
                    code { background-color: ${textColor}0a; border-radius: 4px; padding: 1px 4px; }
                    pre code { background: none; padding: 0; border-radius: 0; }
                    ul, ol { margin: 8px 0; padding-left: 24px; }
                    li { margin: 4px 0; }
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
                """.trimIndent()
            } else {
                """
                    /* === Original (as-sent) mode === */
                    @font-face {
                        font-family: 'GoogleSans';
                        src: url('file:///android_res/font/google_sans_flex.ttf') format('truetype');
                    }
                    body {
                        font-family: 'GoogleSans', -apple-system, 'Helvetica Neue', Arial, sans-serif;
                        font-size: ${fontSize}px;
                        line-height: 1.65;
                        margin: 0;
                        padding: 4px 4px 4px 4px !important;
                        background-color: #ffffff !important;
                        color: #1a1a1a;
                        word-break: break-word;
                        overflow-wrap: break-word;
                        -webkit-font-smoothing: antialiased;
                        /* Responsive: constrain body width */
                        max-width: 100vw;
                        overflow-x: hidden;
                    }
                    /* Make all containers responsive */
                    table {
                        border-collapse: collapse;
                        margin: 8px 0;
                        font-size: ${smallFontSize}px;
                        max-width: 100% !important;
                        width: auto !important;
                    }
                    /* Override fixed-width tables from email templates */
                    table[width], td[width], th[width] {
                        max-width: 100% !important;
                    }
                    td, th {
                        word-break: break-word;
                        padding: 6px 10px;
                        max-width: 100vw;
                    }
                    th { font-weight: 600; }
                    img, video, iframe, embed {
                        max-width: 100% !important;
                        height: auto !important;
                    }
                    /* Override fixed-width images */
                    img[width] {
                        width: auto !important;
                        max-width: 100% !important;
                    }
                    p { margin: 0 0 1em 0; }
                    p:last-child { margin-bottom: 0; }
                    h1, h2, h3, h4 { margin: 1.2em 0 0.6em 0; line-height: 1.3; }
                    h1:first-child, h2:first-child, h3:first-child { margin-top: 0; }
                    pre, code { white-space: pre-wrap; font-size: ${smallFontSize}px; word-break: break-word; }
                    pre { background-color: #0000000a; border-radius: 8px; padding: 12px; margin: 8px 0; overflow-x: auto; }
                    code { background-color: #0000000a; border-radius: 4px; padding: 1px 4px; }
                    pre code { background: none; padding: 0; border-radius: 0; }
                    ul, ol { margin: 8px 0; padding-left: 24px; }
                    li { margin: 4px 0; }
                    /* Constrain any fixed-width div/section */
                    div[style*="width"], section[style*="width"] {
                        max-width: 100% !important;
                        width: auto !important;
                    }
                """.trimIndent()
            }

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
                <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src http: https: data: cid:; font-src 'none'; frame-src 'none';">
                <style>
                    * { box-sizing: border-box; }
                    $modeCss
                    $quotedCss
                    $imgBlockCss
                </style>
            </head>
            <body class="${if (showQuotedText) "show-quotes " else ""}${if (useDarkTheme && effectiveMode == "adapt") "monomail-dark" else ""}">$enhancedBody</body>
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                            fontWeight = FontWeight.SemiBold,
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

        // Card bg must match the email body bg so the padding area blends seamlessly
        val isStyledEmail = bodyIsHtml && bodyText.length > 200 && looksStyled(bodyText)
        val cardBgColor = when (emailTheme) {
            EmailTheme.FORCE_DARK -> MaterialTheme.colorScheme.background
            EmailTheme.FORCE_LIGHT, EmailTheme.ORIGINAL -> androidx.compose.ui.graphics.Color.White
            EmailTheme.AUTO -> if (isStyledEmail) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.background
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
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.domStorageEnabled = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.loadsImagesAutomatically = true
                        try {
                            WebView::class.java.getMethod("setAllowFileAccess", Boolean::class.java)
                                .invoke(this, true)
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

            Spacer(modifier = Modifier.height(16.dp))
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


/**
 * Wraps the Outlook forward/reply block in a &lt;div class="outlook-quoted"&gt;
 * so that CSS can hide/show the entire block (separator, headers, and the
 * previous message body that follows) as a unit.
 *
 * Operates on sanitized HTML right before it goes into the WebView template.
 */
private fun wrapOutlookQuoted(html: String): String {
    // Find the forward block — look for appendonsend or divRplyFwdMsg
    val appendonIdx = html.indexOf("id=\"appendonsend\"", ignoreCase = true)
    val fwdIdx = html.indexOf("id=\"divRplyFwdMsg\"", ignoreCase = true)
    val markerIdx = when {
        appendonIdx >= 0 -> {
            val tagStart = html.lastIndexOf('<', appendonIdx)
            if (tagStart >= 0) tagStart else appendonIdx - 4
        }
        fwdIdx >= 0 -> {
            val tagStart = html.lastIndexOf('<', fwdIdx)
            if (tagStart >= 0) tagStart else fwdIdx - 4
        }
        else -> return html
    }

    if (markerIdx < 0) return html

    // Insert </div> just before </body> and <div class="outlook-quoted"> before the marker
    val bodyCloseIdx = html.indexOf("</body>", ignoreCase = true)
    if (bodyCloseIdx < 0 || bodyCloseIdx <= markerIdx) return html

    val before = html.substring(0, markerIdx)
    val wrapped = html.substring(markerIdx, bodyCloseIdx)
    val after = html.substring(bodyCloseIdx)

    return "$before<div class=\"outlook-quoted\">$wrapped</div>$after"
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
 * Uses a single-pass line-by-line tokenizer instead of 25 sequential
 * regex replacements, avoiding intermediate String allocations.
 *
 * Handles: headers, bold, italic, inline code, code blocks, links,
 * unordered lists, ordered lists, blockquotes, and horizontal rules.
 */
private fun markdownToHtml(markdown: String): String {
    // Single char-by-char pass to escape HTML entities
    val escaped = StringBuilder(markdown.length)
    for (c in markdown) {
        when (c) {
            '&' -> escaped.append("&amp;")
            '<' -> escaped.append("&lt;")
            '>' -> escaped.append("&gt;")
            else -> escaped.append(c)
        }
    }

    // Single-pass line-by-line tokenization
    val result = StringBuilder(escaped.length + (escaped.length shr 1))
    val lines = escaped.split('\n')
    var i = 0

    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trimStart()

        when {
            // Fenced code blocks
            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                result.append("<pre><code>")
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    result.append(lines[i]).append('\n')
                    i++
                }
                result.append("</code></pre>\n")
                i++
            }

            // Indented code blocks (4 spaces or tab)
            (line.startsWith("    ") || line.startsWith('\t')) &&
                i + 1 < lines.size && !lines[i + 1].isBlank() -> {
                result.append("<code>").append(trimmed).append("</code><br>\n")
                i++
            }

            // ATX Headers
            trimmed.startsWith("###### ") -> { result.append("<h6>").append(processInline(trimmed.removePrefix("###### "))).append("</h6>\n"); i++ }
            trimmed.startsWith("##### ") -> { result.append("<h5>").append(processInline(trimmed.removePrefix("##### "))).append("</h5>\n"); i++ }
            trimmed.startsWith("#### ") -> { result.append("<h4>").append(processInline(trimmed.removePrefix("#### "))).append("</h4>\n"); i++ }
            trimmed.startsWith("### ") -> { result.append("<h3>").append(processInline(trimmed.removePrefix("### "))).append("</h3>\n"); i++ }
            trimmed.startsWith("## ") -> { result.append("<h2>").append(processInline(trimmed.removePrefix("## "))).append("</h2>\n"); i++ }
            trimmed.startsWith("# ") -> { result.append("<h1>").append(processInline(trimmed.removePrefix("# "))).append("</h1>\n"); i++ }

            // Blockquotes
            trimmed.startsWith("&gt; ") -> {
                result.append("<blockquote>").append(processInline(trimmed.removePrefix("&gt; "))).append("</blockquote>\n")
                i++
            }

            // Horizontal rules
            line.matches(HORIZONTAL_RULE) -> {
                result.append("<hr>\n")
                i++
            }

            // Unordered list items
            trimmed.matches(UNORDERED_LIST_ITEM) -> {
                result.append("<ul>")
                while (i < lines.size) {
                    val l = lines[i].trimStart()
                    if (!l.matches(UNORDERED_LIST_ITEM)) break
                    result.append("<li>").append(processInline(l.drop(2))).append("</li>")
                    i++
                }
                result.append("</ul>\n")
            }

            // Ordered list items
            trimmed.matches(ORDERED_LIST_ITEM) -> {
                result.append("<ol>")
                while (i < lines.size) {
                    val l = lines[i].trimStart()
                    if (!l.matches(ORDERED_LIST_ITEM)) break
                    result.append("<li>").append(processInline(l.dropWhile { it != '.' }.drop(1).trimStart())).append("</li>")
                    i++
                }
                result.append("</ol>\n")
            }

            // Blank line
            line.isBlank() -> { result.append('\n'); i++ }

            // Regular text
            else -> { result.append(processInline(line)).append('\n'); i++ }
        }
    }

    var html = result.toString().trimEnd('\n')

    // Line break handling
    html = html.replace("\n\n", "</p><p>")
    html = html.replace(LINE_BREAK, "<br>")

    // Wrap in paragraph if not already wrapped in block-level tags
    if (!html.startsWith("<h") && !html.startsWith("<p") && !html.startsWith("<pre") &&
        !html.startsWith("<ul") && !html.startsWith("<ol") && !html.startsWith("<blockquote")) {
        html = "<p>$html</p>"
    }

    return html
}

private val HORIZONTAL_RULE = Regex("^[-*_]{3,}\\s*$")
private val UNORDERED_LIST_ITEM = Regex("^[*\\-] .+$")
private val ORDERED_LIST_ITEM = Regex("^\\d+\\. .+$")
private val LINE_BREAK = Regex("""\n(?!</)""")

/**
 * Single-pass inline formatting scanner. Processes one line for
 * inline code, images, links, bold, italic, and strikethrough
 * in a single forward scan to avoid intermediate allocations.
 */
private fun processInline(text: String): String {
    val sb = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        val remaining = text.length - i

        // Inline code `code`
        if (c == '`' && remaining > 1) {
            val end = text.indexOf('`', i + 1)
            if (end != -1) {
                sb.append("<code>").append(escapeXml(text.substring(i + 1, end))).append("</code>")
                i = end + 1
                continue
            }
        }

        // Image ![alt](url)
        if (c == '!' && remaining > 2 && text[i + 1] == '[') {
            val closeBracket = text.indexOf(']', i + 2)
            if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val alt = text.substring(i + 2, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    sb.append("<img src=\"").append(url).append("\" alt=\"").append(alt).append("\">")
                    i = closeParen + 1
                    continue
                }
            }
        }

        // Link [text](url)
        if (c == '[') {
            val closeBracket = text.indexOf(']', i + 1)
            if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                val closeParen = text.indexOf(')', closeBracket + 2)
                if (closeParen != -1) {
                    val linkText = text.substring(i + 1, closeBracket)
                    val url = text.substring(closeBracket + 2, closeParen)
                    sb.append("<a href=\"").append(url).append("\">").append(linkText).append("</a>")
                    i = closeParen + 1
                    continue
                }
            }
        }

        // Bold **text**
        if (c == '*' && remaining > 1 && text[i + 1] == '*') {
            val end = text.indexOf("**", i + 2)
            if (end != -1) {
                sb.append("<strong>").append(text.substring(i + 2, end)).append("</strong>")
                i = end + 2
                continue
            }
        }

        // Bold __text__
        if (c == '_' && remaining > 1 && text[i + 1] == '_') {
            val end = text.indexOf("__", i + 2)
            if (end != -1) {
                sb.append("<strong>").append(text.substring(i + 2, end)).append("</strong>")
                i = end + 2
                continue
            }
        }

        // Strikethrough ~~text~~
        if (c == '~' && remaining > 1 && text[i + 1] == '~') {
            val end = text.indexOf("~~", i + 2)
            if (end != -1) {
                sb.append("<del>").append(text.substring(i + 2, end)).append("</del>")
                i = end + 2
                continue
            }
        }

        // Italic *text*
        if (c == '*' && (remaining < 2 || text[i + 1] != '*')) {
            val end = text.indexOf('*', i + 1)
            if (end != -1 && (end + 1 >= text.length || text[end + 1] != '*')) {
                sb.append("<em>").append(text.substring(i + 1, end)).append("</em>")
                i = end + 1
                continue
            }
        }

        // Italic _text_
        if (c == '_' && (remaining < 2 || text[i + 1] != '_')) {
            val end = text.indexOf('_', i + 1)
            if (end != -1 && (end + 1 >= text.length || text[end + 1] != '_')) {
                sb.append("<em>").append(text.substring(i + 1, end)).append("</em>")
                i = end + 1
                continue
            }
        }

        // Plain character
        sb.append(c)
        i++
    }
    return sb.toString()
}

/**
 * Minimal XML entity escaping for code content.
 */
private fun escapeXml(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
