package com.shrivatsav.monomail.ui.screens.compose
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.TextButton
import android.webkit.WebView
import android.webkit.WebSettings
import android.view.ViewGroup
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShortText
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import coil.compose.AsyncImage
import com.shrivatsav.monomail.data.model.EmailAttachment
import android.net.Uri
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ComposeScreen(
    viewModel: ComposeViewModel,
    onBack: () -> Unit,
    onSent: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            var name = "attachment"
            var size = 0L
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                    if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                }
            }
            var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
            if (mimeType == "application/octet-stream") {
                val lowerName = name.lowercase()
                if (lowerName.endsWith(".png")) mimeType = "image/png"
                else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) mimeType = "image/jpeg"
                else if (lowerName.endsWith(".gif")) mimeType = "image/gif"
                else if (lowerName.endsWith(".webp")) mimeType = "image/webp"
            }
            viewModel.addAttachment(EmailAttachment(uri, name, size, mimeType))
        }
    }
    LaunchedEffect(state.isSent) {
        if (state.isSent) onSent()
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    if (state.showConfirmSendDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirmSend() },
            title = { Text("Confirm Send") },
            text = { Text("Are you sure you want to send this email?") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmSend() }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirmSend() }) {
                    Text("Cancel")
                }
            }
        )
    }
    if (state.showSchedulePicker) {
        ScheduleSendDialog(
            onDismiss = { viewModel.dismissSchedulePicker() },
            onSchedule = { millis -> viewModel.scheduleSend(millis) }
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        ),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (state.mode) {
                            ComposeMode.NEW -> "Compose"
                            ComposeMode.REPLY -> "Reply"
                            ComposeMode.FORWARD -> "Forward"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                },
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
                    IconButton(onClick = { launcher.launch("*/*") }) {
                        Icon(
                            imageVector = Icons.Rounded.AttachFile,
                            contentDescription = "Attach",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    var showTemplates by remember { androidx.compose.runtime.mutableStateOf(false) }
                    val templates by viewModel.templatesFlow.collectAsState(initial = emptyList())
                    IconButton(onClick = { showTemplates = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = "Templates",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (showTemplates) {
                        ModalBottomSheet(
                            onDismissRequest = { showTemplates = false },
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 32.dp)
                            ) {
                                Text(
                                    text = "Templates",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                if (templates.isEmpty()) {
                                    Text(
                                        text = "No templates. Add them in Settings.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                } else {
                                    templates.forEach { template ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.applyTemplate(template.subject, template.body)
                                                    showTemplates = false
                                                }
                                                .padding(horizontal = 24.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = template.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (template.subject.isNotEmpty()) {
                                                    Text(
                                                        text = template.subject,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.isSending) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(54.dp)
                                .padding(end = 8.dp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else if (!state.isSent) {
                        val canSend = state.to.isNotBlank()
                        IconButton(
                            onClick = { viewModel.showSchedulePicker() },
                            enabled = canSend
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "Schedule",
                                tint = if (canSend)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.send() },
                            enabled = canSend
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = "Send",
                                tint = if (canSend)
                                    MaterialTheme.colorScheme.onSurface
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) {
                Snackbar(snackbarData = it)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            ComposeTextField(
                value = state.to,
                onValueChange = viewModel::updateTo,
                label = "To",
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                enabled = state.mode != ComposeMode.REPLY
            )
            AnimatedVisibility(
                visible = suggestions.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 20.dp)
                ) {
                    suggestions.forEach { contact ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectSuggestion(contact) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (contact.email != contact.name) {
                                Text(
                                    text = contact.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
            var showCcBcc by remember { androidx.compose.runtime.mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { showCcBcc = !showCcBcc }) {
                    Text(
                        text = if (showCcBcc) "Cc/Bcc" else "Cc/Bcc",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedVisibility(visible = showCcBcc) {
                Column {
                    ComposeTextField(
                        value = state.cc,
                        onValueChange = viewModel::updateCc,
                        label = "Cc",
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    ComposeTextField(
                        value = state.bcc,
                        onValueChange = viewModel::updateBcc,
                        label = "Bcc",
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            ComposeTextField(
                value = state.subject,
                onValueChange = viewModel::updateSubject,
                label = "Subject",
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            AnimatedVisibility(visible = state.attachments.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.attachments) { attachment ->
                        AttachmentPreview(
                            attachment = attachment,
                            onRemove = { viewModel.removeAttachment(attachment) }
                        )
                    }
                }
            }
            if (state.attachments.isNotEmpty()) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            var bodyWebView by remember { mutableStateOf<WebView?>(null) }
            val bodyTextColor = "#%06X".format(MaterialTheme.colorScheme.onBackground.toArgb() and 0xFFFFFF)
            var isBold by remember { mutableStateOf(false) }
            var isItalic by remember { mutableStateOf(false) }
            var isUnderline by remember { mutableStateOf(false) }
            var isBullet by remember { mutableStateOf(false) }
            var isNumber by remember { mutableStateOf(false) }
            var isQuote by remember { mutableStateOf(false) }
            FormattingToolbar(
                webView = bodyWebView,
                isBold = isBold, isItalic = isItalic, isUnderline = isUnderline,
                isBullet = isBullet, isNumber = isNumber, isQuote = isQuote,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 4.dp)
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        val fmtCallback = { json: String ->
                            mainHandler.post {
                                try {
                                    val o = org.json.JSONObject(json)
                                    isBold = o.optBoolean("bold")
                                    isItalic = o.optBoolean("italic")
                                    isUnderline = o.optBoolean("underline")
                                    isBullet = o.optBoolean("bullet")
                                    isNumber = o.optBoolean("number")
                                    isQuote = o.optBoolean("quote")
                                } catch (_: Exception) {}
                            }
                        }
                        WebView(ctx).apply {
                            setBackgroundColor(Color.TRANSPARENT)
                            settings.javaScriptEnabled = true
                            settings.cacheMode = WebSettings.LOAD_NO_CACHE
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onBodyChanged(html: String) {
                                    viewModel.updateBody(html)
                                }
                                @JavascriptInterface
                                fun onFormatStateChanged(json: String) {
                                    fmtCallback(json)
                                }
                            }, "Android")
                            val initialBody = state.body
                            val initialHtml = buildString {
                                append("""<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><style>
                                    body{font-family:-apple-system,'Helvetica Neue',Arial,sans-serif;font-size:15px;line-height:1.6;margin:12px 16px;padding:0;color:$bodyTextColor;background:transparent;min-height:280px;word-break:break-word;overflow-wrap:break-word;}
                                </style></head><body contenteditable="true">""")
                                if (initialBody.isNotEmpty()) append(initialBody)
                                append("""</body></html>""")
                            }
                            loadDataWithBaseURL(null, initialHtml.trimIndent(), "text/html", "UTF-8", null)
                            setWebViewClient(object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String) {
                                    view.evaluateJavascript("""
                                        function reportFmt(){Android.onFormatStateChanged(JSON.stringify({bold:document.queryCommandState('bold'),italic:document.queryCommandState('italic'),underline:document.queryCommandState('underline'),bullet:document.queryCommandState('insertUnorderedList'),number:document.queryCommandState('insertOrderedList'),quote:document.queryCommandState('formatBlock')=='blockquote'}));}
                                        document.body.addEventListener('input',function(){Android.onBodyChanged(document.body.innerHTML);reportFmt();});
                                        document.body.addEventListener('mouseup',reportFmt);
                                        document.body.addEventListener('keyup',reportFmt);
                                        setTimeout(reportFmt,200);
                                    """.trimIndent(), null)
                                }
                            })
                            bodyWebView = this
                        }
                    }
                )
            }
            if (state.originalBody != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(horizontal = 4.dp)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(Color.TRANSPARENT)
                                settings.javaScriptEnabled = false
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                val quoted = buildString {
                                    append("""<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1.0"><style>
                                        body{font-family:-apple-system,'Helvetica Neue',Arial,sans-serif;font-size:13px;line-height:1.4;margin:8px 16px;padding:0;color:$bodyTextColor;word-break:break-word;overflow-wrap:break-word;opacity:0.6;}
                                        img{max-width:100%;height:auto;}
                                        blockquote,.gmail_quote,.gmail_extra,.yahoo_quoted,.moz-cite-prefix,[name="quoted-content"]{display:none!important;}
                                    </style></head><body>""")
                                    append(state.originalBody)
                                    append("""</body></html>""")
                                }
                                loadDataWithBaseURL(null, quoted.trimIndent(), "text/html", "UTF-8", null)
                            }
                        }
                    )
                }
            }

        }
    }
}
@Composable
private fun ComposeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    textStyle: TextStyle,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minHeight: Int = 0
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = textStyle,
        singleLine = singleLine,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (minHeight > 0) Modifier.height(minHeight.dp)
                else Modifier
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = label,
                    style = textStyle.copy(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                        fontWeight = FontWeight.Normal
                    )
                )
            }
            innerTextField()
        }
    )
}
@Composable
private fun FormattingToolbar(
    webView: WebView?,
    isBold: Boolean, isItalic: Boolean, isUnderline: Boolean,
    isBullet: Boolean, isNumber: Boolean, isQuote: Boolean,
    modifier: Modifier = Modifier
) {
    val activeTint = MaterialTheme.colorScheme.primary
    val inactiveTint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val btnModifier = Modifier.size(36.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('bold',false,null);reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.Rounded.FormatBold, "Bold", tint = if (isBold) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('italic',false,null);reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.Rounded.FormatItalic, "Italic", tint = if (isItalic) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('underline',false,null);reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.Rounded.FormatUnderlined, "Underline", tint = if (isUnderline) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('insertUnorderedList',false,null);reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bullet list", tint = if (isBullet) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('insertOrderedList',false,null);reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.Rounded.FormatListNumbered, "Numbered list", tint = if (isNumber) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = { webView?.evaluateJavascript("document.execCommand('formatBlock',false,'<blockquote>');reportFmt();", null) }, modifier = btnModifier) {
            Icon(Icons.AutoMirrored.Rounded.ShortText, "Quote", tint = if (isQuote) activeTint else inactiveTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSendDialog(
    onDismiss: () -> Unit,
    onSchedule: (Long) -> Unit
) {
    var step by remember { androidx.compose.runtime.mutableStateOf(0) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis() + 3600000)
    val timePickerState = rememberTimePickerState(
        initialHour = (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) + 1) % 24,
        initialMinute = 0,
        is24Hour = true
    )
    if (step == 0) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { step = 1 }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Select time") },
            text = {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                    val cal = java.util.Calendar.getInstance().apply {
                        timeInMillis = dateMillis
                        set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(java.util.Calendar.MINUTE, timePickerState.minute)
                        set(java.util.Calendar.SECOND, 0)
                        set(java.util.Calendar.MILLISECOND, 0)
                    }
                    onSchedule(cal.timeInMillis)
                }) { Text("Schedule") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AttachmentPreview(
    attachment: EmailAttachment,
    onRemove: () -> Unit
) {
    val isImage = attachment.mimeType.startsWith("image/")
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (isImage) {
            AsyncImage(
                model = attachment.uri,
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = attachment.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
