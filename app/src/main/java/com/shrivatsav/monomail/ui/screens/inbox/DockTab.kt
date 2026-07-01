package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.ui.theme.MonoMailTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DockTab(
    isActive: Boolean,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    scale: Float
) {
    AnimatedDockTab(isActive, icon, label, contentDescription, onClick, scale)
}

@Composable
fun AnimatedDockTab(
    isActive: Boolean,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    scale: Float,
) {
    val transition = updateTransition(targetState = isActive, label = "dockTab")

    val bgColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) },
        label = "dockTabBg"
    ) { active -> if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent }

    val iconColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) },
        label = "dockIconColor"
    ) { active ->
        if (active) MaterialTheme.colorScheme.onSurface
        else MonoMailTheme.extendedColors.onSurfaceMuted
    }

    val labelColor by transition.animateColor(
        transitionSpec = { spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium) },
        label = "dockLabelColor"
    ) { active ->
        if (active) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0f)
    }

    val labelWidth by transition.animateDp(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium) },
        label = "dockLabelWidth"
    ) { active -> if (active) 72.dp else 0.dp }

    val iconScale by transition.animateFloat(
        transitionSpec = { spring(dampingRatio = 0.7f, stiffness = 400f) },
        label = "dockIconScale"
    ) { active -> if (active) 1.1f else 1f }

    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = iconColor,
        onClick = onClick,
        modifier = Modifier
            .height((42 * scale).dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size((22 * scale).dp)
                    .scale(iconScale)
            )

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = labelWidth)
            )
        }
    }
}
