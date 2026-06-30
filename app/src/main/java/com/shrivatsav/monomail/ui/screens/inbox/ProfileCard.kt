package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.auth.UserProfile

@Composable
internal fun ProfileCard(
    userProfile: UserProfile,
    accounts: List<UserProfile>,
    onSignOut: () -> Unit,
    onShowSwitchAccount: () -> Unit,
    onCycleAccount: (String) -> Unit,
    onSettings: () -> Unit,
    onAddAccount: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 32.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedContent(
                    targetState = userProfile.id,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.9f)) togetherWith
                        (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.9f))
                    },
                    label = "profileContent"
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.pointerInput(accounts) {
                                if (accounts.size > 1) {
                                    var totalDrag = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDrag = 0f },
                                        onHorizontalDrag = { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                            if (kotlin.math.abs(totalDrag) > 60f) {
                                                val currentIdx = accounts.indexOfFirst { it.id == userProfile.id }
                                                if (currentIdx != -1) {
                                                    val nextIdx = if (totalDrag > 0)
                                                        (currentIdx + 1) % accounts.size
                                                    else
                                                        if (currentIdx - 1 < 0) accounts.size - 1 else currentIdx - 1
                                                    onCycleAccount(accounts[nextIdx].id)
                                                }
                                                totalDrag = 0f
                                            }
                                        }
                                    )
                                }
                            }
                        ) {
                            if (accounts.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((-16).dp),
                                    modifier = Modifier.offset(x = 28.dp)
                                ) {
                                    accounts.filter { it.id != userProfile.id }.take(2).forEach { acc ->
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                                .clip(CircleShape)
                                                .alpha(0.45f)
                                        ) {
                                            AvatarCircle(
                                                acc.photoUrl,
                                                acc.displayName,
                                                44.dp,
                                                MaterialTheme.typography.titleSmall
                                            )
                                        }
                                    }
                                }
                            }
                            Box(modifier = Modifier.border(3.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                                AvatarCircle(
                                    photoUrl = userProfile.photoUrl,
                                    displayName = userProfile.displayName,
                                    size = 72.dp,
                                    textStyle = MaterialTheme.typography.headlineSmall
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = userProfile.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = userProfile.email,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }

                if (accounts.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                        modifier = Modifier.clickable { onShowSwitchAccount() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${accounts.size} accounts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                thickness = 0.5.dp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                ProfileMenuItem(Icons.Rounded.Settings, "Settings", onSettings)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onAddAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add account",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
