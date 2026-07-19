package com.shrivatsav.monomail.feature.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shrivatsav.monomail.core.data.settings.*
import com.shrivatsav.monomail.ui.components.SlideSheet
import com.shrivatsav.monomail.ui.theme.MonoOpacity
import com.shrivatsav.monomail.ui.theme.MonoSpring

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsDetailTopBar(title: String, onNavigateBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = cornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = MonoSpring.bouncy()
            )
    ) {
        Column(content = content)
    }
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        thickness = 0.5.dp
    )
}

@Composable
internal fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else MonoOpacity.disabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(
                start = if (indented) 32.dp else 16.dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary
            ),
            enabled = enabled
        )
    }
}

@Composable
internal fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .alpha(if (enabled) 1f else MonoOpacity.disabled)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(22.dp)
        )
    }
}
@Composable
internal fun ThemeSelectorRow(
    currentTheme: ThemeMode,
    onThemeSelected: (ThemeMode) -> Unit
) {
    SelectorRow(
        icon = Icons.Rounded.DarkMode,
        iconDescription = "Theme",
        title = "Theme",
        labelPrefix = "theme",
        entries = ThemeMode.entries.map { mode ->
            SelectorEntry(mode.displayName(), currentTheme == mode) { onThemeSelected(mode) }
        }
    )
}

@Composable
internal fun EmailColorsRow(
    currentTheme: EmailTheme,
    onThemeSelected: (EmailTheme) -> Unit
) {
    SelectorRow(
        icon = Icons.Rounded.Contrast,
        iconDescription = "Email colors",
        title = "Email Colors",
        labelPrefix = "emailColor",
        entries = EmailTheme.entries.map { mode ->
            SelectorEntry(mode.displayName(), currentTheme == mode) { onThemeSelected(mode) }
        },
        description = currentTheme.description()
    )
}

@Composable
internal fun MonochromeToggleRow(
    monochrome: Boolean,
    onToggle: (Boolean) -> Unit
) {
    SettingsToggleRow(
        icon = Icons.Rounded.Palette,
        title = "Monochrome Colors",
        subtitle = if (monochrome) "Using black & white palette" else "Using dynamic color scheme",
        checked = monochrome,
        onCheckedChange = onToggle
    )
}

private data class SelectorEntry(
    val label: String,
    val isSelected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Icon + title header above a segmented row of [SelectorItem]s, with an optional
 * description line below. Shared by the theme and email-color pickers.
 */
@Composable
private fun SelectorRow(
    icon: ImageVector,
    iconDescription: String,
    title: String,
    labelPrefix: String,
    entries: List<SelectorEntry>,
    description: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = iconDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            entries.forEach { entry ->
                SelectorItem(
                    modifier = Modifier.weight(1f),
                    label = entry.label,
                    isSelected = entry.isSelected,
                    labelPrefix = labelPrefix,
                    onClick = entry.onClick
                )
            }
        }
        if (description != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SelectorItem(
    modifier: Modifier = Modifier,
    label: String,
    isSelected: Boolean,
    labelPrefix: String,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(250), label = "${labelPrefix}Bg"
    )
    val textColor by animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(250), label = "${labelPrefix}Text"
    )
    val scaleAnim by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "${labelPrefix}Scale"
    )
    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scaleAnim, scaleY = scaleAnim)
            .clip(cornerShape(10.dp))
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
internal fun FontSizeRow(
    currentScale: FontScale,
    onScaleChanged: (FontScale) -> Unit
) {
    val previewSize by animateFloatAsState(
        targetValue = when (currentScale) {
            FontScale.EXTRA_SMALL -> 11f
            FontScale.SMALL       -> 13f
            FontScale.DEFAULT     -> 15f
            FontScale.LARGE       -> 17f
            FontScale.EXTRA_LARGE -> 20f
        },
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "fontSize"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.FormatSize,
                contentDescription = "Font Size",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Font Size",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currentScale.displayName(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Surface(
            shape = cornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "The quick brown fox jumps over the lazy dog",
                fontSize = previewSize.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "A",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = currentScale.ordinal.toFloat(),
                onValueChange = { onScaleChanged(FontScale.entries[it.toInt()]) },
                valueRange = 0f..4f,
                steps = 3,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            Text(
                text = "A",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun NavSizeRow(
    scale: Float,
    onScaleChanged: (Float) -> Unit
) {
    val previewScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "navScale"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SpaceDashboard,
                contentDescription = "Navigation Size",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Navigation Size",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(scale * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp * previewScale),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp * previewScale),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp * previewScale, vertical = 4.dp * previewScale),
                        horizontalArrangement = Arrangement.spacedBy(4.dp * previewScale),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { i ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp * previewScale)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                    )
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(20.dp * previewScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
            Slider(
                value = scale,
                onValueChange = onScaleChanged,
                valueRange = 0.6f..1.4f,
                steps = 7,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            )
        }
    }
}

@Composable
internal fun SwipeThresholdRow(
    threshold: Float,
    onThresholdChanged: (Float) -> Unit
) {
    // Steps: 0.20, 0.25, 0.30, 0.35, 0.40, 0.45, 0.50, 0.55, 0.60  (9 values = 8 steps)
    val steps = listOf(0.20f, 0.25f, 0.30f, 0.35f, 0.40f, 0.45f, 0.50f, 0.55f, 0.60f)
    val label = when {
        threshold <= 0.25f -> "Short"
        threshold <= 0.35f -> "Medium-Short"
        threshold <= 0.45f -> "Medium"
        threshold <= 0.52f -> "Medium-Long"
        else               -> "Long"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.SwipeRight,
                contentDescription = "Swipe Sensitivity",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Swipe Distance",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "How far you must swipe before the action triggers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Slider(
                value = steps.indexOfFirst { it >= threshold - 0.01f }.coerceAtLeast(0).toFloat(),
                onValueChange = { onThresholdChanged(steps[it.toInt().coerceIn(0, steps.lastIndex)]) },
                valueRange = 0f..(steps.lastIndex.toFloat()),
                steps = steps.size - 2,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.onSurface,
                    activeTrackColor = MaterialTheme.colorScheme.onSurface,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BottomSheetPickerRow(
    icon: ImageVector,
    title: String,
    currentValue: String,
    options: List<String>,
    onSelected: (Int) -> Unit,
    indented: Boolean = false
) {
    var showSheet by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (showSheet) 90f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "chevronRot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(start = if (indented) 32.dp else 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = currentValue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp).rotate(chevronRotation)
        )
    }
    if (showSheet) {
        PickerBottomSheet(
            title = title,
            options = options,
            currentValue = currentValue,
            onSelected = { index -> onSelected(index); showSheet = false },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerBottomSheet(
    title: String,
    options: List<String>,
    currentValue: String,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SlideSheet(onDismiss = onDismiss, title = title) {
        options.forEachIndexed { index, option ->
            val isSelected = option == currentValue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(index) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun InfoRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MonoOpacity.secondary),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ── Display-name extensions ──────────────────────────────────────────

internal fun ThemeMode.displayName() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT  -> "Light"
    ThemeMode.DARK   -> "Dark"
}

internal fun FontScale.displayName() = when (this) {
    FontScale.EXTRA_SMALL -> "XS"
    FontScale.SMALL       -> "Small"
    FontScale.DEFAULT     -> "Default"
    FontScale.LARGE       -> "Large"
    FontScale.EXTRA_LARGE -> "XL"
}

internal fun SwipeAction.displayName() = when (this) {
    SwipeAction.ARCHIVE     -> "Archive"
    SwipeAction.STAR        -> "Star"
    SwipeAction.DELETE      -> "Delete"
    SwipeAction.READ_UNREAD -> "Mark Read/Unread"
}

internal fun DefaultReply.displayName() = when (this) {
    DefaultReply.REPLY     -> "Reply"
    DefaultReply.REPLY_ALL -> "Reply All"
}

internal fun SyncFrequency.displayName() = when (this) {
    SyncFrequency.MIN_15  -> "15 minutes"
    SyncFrequency.MIN_30  -> "30 minutes"
    SyncFrequency.HOUR_1  -> "1 hour"
    SyncFrequency.MANUAL  -> "Manual"
}

internal fun UndoSendWindow.displayName() = "${seconds}s"

internal fun EmailTheme.displayName() = when (this) {
    EmailTheme.AUTO     -> "Auto"
    EmailTheme.ORIGINAL -> "Original"
}

internal fun EmailTheme.description() = when (this) {
    EmailTheme.AUTO     -> "Adapt simple emails to the app theme; show styled emails as sent"
    EmailTheme.ORIGINAL -> "Always show the sender's original colors on a light background"
}

// ── Templates Card ───────────────────────────────────────────────────

@Composable
internal fun TemplatesCard(viewModel: SettingsViewModel) {
    val templates by viewModel.templates.collectAsState()
    var editingIndex by remember { mutableStateOf(-1) }
    var showEditor by remember { mutableStateOf(false) }
    var nameInput by remember { mutableStateOf("") }
    var subjectInput by remember { mutableStateOf("") }
    var bodyInput by remember { mutableStateOf("") }
    SettingsCard {
        SectionHeader(icon = Icons.Rounded.Description, title = "Templates")
        if (templates.isEmpty()) {
            EmptyTemplatesState()
        } else {
            TemplateList(
                templates = templates,
                onEdit = { index, template ->
                    editingIndex = index
                    nameInput = template.name
                    subjectInput = template.subject
                    bodyInput = template.body
                    showEditor = true
                },
                onDelete = { index ->
                    viewModel.saveTemplates(templates.toMutableList().apply { removeAt(index) })
                }
            )
        }
        CardDivider()
        TextButton(
            onClick = {
                editingIndex = -1
                nameInput = ""
                subjectInput = ""
                bodyInput = ""
                showEditor = true
            },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add Template")
        }
    }
    if (showEditor) {
        TemplateEditorDialog(
            state = TemplateEditorState(editingIndex, nameInput, subjectInput, bodyInput),
            onNameChange = { nameInput = it },
            onSubjectChange = { subjectInput = it },
            onBodyChange = { bodyInput = it },
            onSave = {
                if (nameInput.isNotBlank()) {
                    val template = EmailTemplate(nameInput, subjectInput, bodyInput)
                    val updated = templates.toMutableList()
                    if (editingIndex >= 0) updated[editingIndex] = template
                    else updated.add(template)
                    viewModel.saveTemplates(updated)
                    showEditor = false
                }
            },
            onDismiss = { showEditor = false }
        )
    }
}

@Composable
private fun EmptyTemplatesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Description,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.size(36.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "No templates yet",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Save your go-to replies for quick access",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun TemplateList(
    templates: List<EmailTemplate>,
    onEdit: (Int, EmailTemplate) -> Unit,
    onDelete: (Int) -> Unit,
) {
    templates.forEachIndexed { index, template ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = template.subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onEdit(index, template) }) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onDelete(index) }) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp))
            }
        }
        if (index < templates.lastIndex) CardDivider()
    }
}

@Composable
private fun TemplateEditorDialog(
    state: TemplateEditorState,
    onNameChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    SlideSheet(
        onDismiss = onDismiss,
        title = if (state.editingIndex >= 0) "Edit Template" else "New Template",
        actions = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(onClick = onSave) { Text("Save") }
        }
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.nameInput,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.subjectInput,
                onValueChange = onSubjectChange,
                label = { Text("Subject") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.bodyInput,
                onValueChange = onBodyChange,
                label = { Text("Body") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class TemplateEditorState(
    val editingIndex: Int,
    val nameInput: String,
    val subjectInput: String,
    val bodyInput: String
)

// ── Dock Bar Editor ─────────────────────────────────────────────────

@Composable
internal fun DockBarEditor(
    dockConfig: DockConfig,
    maxSlots: Int,
    onConfigChanged: (DockConfig) -> Unit
) {
    val allTabs = DockTabId.values().filter { it != DockTabId.UNIFIED }
    val availableTabs = allTabs.filter { it !in dockConfig.primaryTabs }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Dock tabs (${dockConfig.primaryTabs.size}/$maxSlots)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        PrimaryTabList(
            primaryTabs = dockConfig.primaryTabs,
            onReorder = { index, direction ->
                val list = dockConfig.primaryTabs.toMutableList()
                val swapIndex = index + direction
                list[index] = list[swapIndex].also { list[swapIndex] = list[index] }
                onConfigChanged(DockConfig(primaryTabs = list))
            },
            onRemove = { index ->
                val list = dockConfig.primaryTabs.toMutableList().apply { removeAt(index) }
                onConfigChanged(DockConfig(primaryTabs = list))
            }
        )
        if (availableTabs.isNotEmpty()) {
            AvailableTabList(
                availableTabs = availableTabs,
                canAdd = dockConfig.primaryTabs.size < maxSlots,
                onAdd = { tabId ->
                    val list = dockConfig.primaryTabs.toMutableList().apply { add(tabId) }
                    onConfigChanged(DockConfig(primaryTabs = list))
                }
            )
        }
    }
}

@Composable
private fun PrimaryTabList(
    primaryTabs: List<DockTabId>,
    onReorder: (index: Int, direction: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    primaryTabs.forEachIndexed { index, tabId ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(dockTabIcon(tabId), contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(dockTabLabel(tabId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            IconButton(onClick = { onReorder(index, -1) }, modifier = Modifier.size(32.dp), enabled = index > 0) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up", modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { onReorder(index, 1) }, modifier = Modifier.size(32.dp), enabled = index < primaryTabs.lastIndex) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down", modifier = Modifier.size(18.dp))
            }
            if (primaryTabs.size > 1) {
                IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = "Remove", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                }
            } else Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun AvailableTabList(
    availableTabs: List<DockTabId>,
    canAdd: Boolean,
    onAdd: (DockTabId) -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
    Text("Available", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
    availableTabs.forEach { tabId ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(dockTabIcon(tabId), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(dockTabLabel(tabId), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (canAdd) {
                IconButton(onClick = { onAdd(tabId) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

internal fun dockTabIcon(tab: DockTabId): ImageVector = when (tab) {
    DockTabId.UNIFIED, DockTabId.INBOX  -> Icons.Rounded.Inbox
    DockTabId.SENT   -> Icons.AutoMirrored.Rounded.Send
    DockTabId.ARCHIVED -> Icons.Rounded.Archive
    DockTabId.SNOOZED  -> Icons.Rounded.Schedule
    DockTabId.STARRED  -> Icons.Rounded.Star
    DockTabId.TRASH    -> Icons.Rounded.Delete
    DockTabId.SPAM     -> Icons.Rounded.Report
}

internal fun dockTabLabel(tab: DockTabId): String = when (tab) {
    DockTabId.UNIFIED  -> "Unified"
    DockTabId.INBOX    -> "Inbox"
    DockTabId.SENT     -> "Sent"
    DockTabId.ARCHIVED -> "Archived"
    DockTabId.SNOOZED  -> "Snoozed"
    DockTabId.STARRED  -> "Starred"
    DockTabId.TRASH    -> "Trash"
    DockTabId.SPAM     -> "Spam"
}

@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        modifier = modifier.padding(start = 4.dp)
    )
}

@Composable
internal fun SupportButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    icon: @Composable (Modifier) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val btnScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btnScale"
    )
    if (primary) {
        FilledTonalButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer(scaleX = btnScale, scaleY = btnScale),
            shape = cornerShape(14.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            icon(Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .graphicsLayer(scaleX = btnScale, scaleY = btnScale),
            shape = cornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
        ) {
            icon(Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
