import re

with open('feature/detail/src/main/java/com/shrivatsav/monomail/feature/detail/EmailDetailScreen.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1 & 2. Destructure items and update ThreadConversationContent caller
content = content.replace(
    'val emails = s.emails',
    'val emails = s.emails\n            val items = s.items'
)

content = content.replace(
    'emails = emails,\n                            decryptedBodies = decryptedBodies,',
    'items = items,\n                            emails = emails,\n                            decryptedBodies = decryptedBodies,'
)

content = content.replace(
    'onNavigateToAttachmentViewer = onNavigateToAttachmentViewer\n                        )',
    'onNavigateToAttachmentViewer = onNavigateToAttachmentViewer,\n                            onToggleGroup = { viewModel.expandEmails(it) },\n                            onStarMessage = { id, starred -> viewModel.toggleEmailStar(id, starred) },\n                            onArchiveMessage = { id -> viewModel.archiveEmail(id) },\n                            onDeleteMessage = { id -> viewModel.trashEmail(id) }\n                        )'
)

# 3. Update ThreadConversationContent signature
content = content.replace(
    'fun ThreadConversationContent(\n    emails: List<Email>,',
    'fun ThreadConversationContent(\n    items: List<com.shrivatsav.monomail.feature.detail.ThreadListItem>,\n    emails: List<Email>,'
)

content = content.replace(
    'onNavigateToAttachmentViewer: (messageId: String, attachmentId: String, mimeType: String, name: String) -> Unit = { _, _, _, _ -> }\n)',
    'onNavigateToAttachmentViewer: (messageId: String, attachmentId: String, mimeType: String, name: String) -> Unit = { _, _, _, _ -> },\n    onToggleGroup: (List<String>) -> Unit = {},\n    onStarMessage: (String, Boolean) -> Unit = { _, _ -> },\n    onArchiveMessage: (String) -> Unit = {},\n    onDeleteMessage: (String) -> Unit = {}\n)'
)

# 4. Rewrite itemsIndexed loop
old_loop = """        itemsIndexed(emails, key = { _, email -> email.id }) { index, email ->
            val isExpanded = expandedMap[email.id] ?: true
            if (config.isConversationView) {
                ConversationEmailItem(
                    email = email,
                    index = index,
                    isExpanded = isExpanded,
                    onToggleExpand = { expandedMap[email.id] = !isExpanded },
                    config = config,
                    decryptedBodies = decryptedBodies,
                    onFetchAttachment = onFetchAttachment,
                    onNavigateToAttachmentViewer = onNavigateToAttachmentViewer
                )
            } else {
                MessageBody(
                    email = email,
                    decryptedResult = decryptedBodies[email.id],
                    config = config,
                    onFetchAttachment = onFetchAttachment,
                    onNavigateToAttachmentViewer = onNavigateToAttachmentViewer,
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
        }"""

new_loop = """        itemsIndexed(items, key = { index, item -> 
            when (item) {
                is com.shrivatsav.monomail.feature.detail.ThreadListItem.Message -> item.email.id
                is com.shrivatsav.monomail.feature.detail.ThreadListItem.CollapsedGroup -> "group_${item.hiddenEmailIds.firstOrNull()}_$index"
            }
        }) { index, item ->
            when (item) {
                is com.shrivatsav.monomail.feature.detail.ThreadListItem.Message -> {
                    val email = item.email
                    val isExpanded = expandedMap[email.id] ?: item.isExpanded
                    if (config.isConversationView) {
                        ConversationEmailItem(
                            email = email,
                            index = emails.indexOf(email),
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedMap[email.id] = !isExpanded },
                            config = config,
                            decryptedBodies = decryptedBodies,
                            onFetchAttachment = onFetchAttachment,
                            onNavigateToAttachmentViewer = onNavigateToAttachmentViewer,
                            onStar = { onStarMessage(email.id, email.isStarred) },
                            onArchive = { onArchiveMessage(email.id) },
                            onDelete = { onDeleteMessage(email.id) }
                        )
                    } else {
                        MessageBody(
                            email = email,
                            decryptedResult = decryptedBodies[email.id],
                            config = config,
                            onFetchAttachment = onFetchAttachment,
                            onNavigateToAttachmentViewer = onNavigateToAttachmentViewer,
                            showSender = true,
                            messageCount = emails.size
                        )
                    }
                }
                is com.shrivatsav.monomail.feature.detail.ThreadListItem.CollapsedGroup -> {
                    CollapsedGroupItem(
                        count = item.count,
                        onClick = { onToggleGroup(item.hiddenEmailIds) }
                    )
                }
            }
            if (index < items.lastIndex) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }"""

content = content.replace(old_loop, new_loop)

# 5. Update ConversationEmailItem signature
content = content.replace(
    'onNavigateToAttachmentViewer: (messageId: String, attachmentId: String, mimeType: String, name: String) -> Unit\n) {',
    'onNavigateToAttachmentViewer: (messageId: String, attachmentId: String, mimeType: String, name: String) -> Unit,\n    onStar: () -> Unit,\n    onArchive: () -> Unit,\n    onDelete: () -> Unit\n) {\n    var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }'
)

# 6. Add Kebab Menu UI
old_kebab_target = """                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }"""

new_kebab = """                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Message options",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text(if (email.isStarred) "Unstar" else "Star") },
                                onClick = { showMenu = false; onStar() },
                                leadingIcon = { Icon(if (email.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, null) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Archive") },
                                onClick = { showMenu = false; onArchive() },
                                leadingIcon = { Icon(Icons.Rounded.Archive, null) }
                            )
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Delete") },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) }
                            )
                        }
                    }
                }"""
content = content.replace(old_kebab_target, new_kebab)

# 7. Add CollapsedGroupItem at end
collapsed_group_code = """
@Composable
fun CollapsedGroupItem(count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).size(28.dp)
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}
"""
content += collapsed_group_code

with open('feature/detail/src/main/java/com/shrivatsav/monomail/feature/detail/EmailDetailScreen.kt', 'w', encoding='utf-8') as f:
    f.write(content)

