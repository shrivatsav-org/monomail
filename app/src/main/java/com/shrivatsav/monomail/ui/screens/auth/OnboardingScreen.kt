package com.shrivatsav.monomail.ui.screens.auth

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinishOnboarding: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    var permissionsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionsGranted = permissions.values.all { it }
    }

    val powerManager = context.getSystemService(PowerManager::class.java)
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    val kofiIcon = remember {
        val bmp = android.graphics.BitmapFactory.decodeStream(
            context.resources.openRawResource(com.shrivatsav.monomail.R.raw.kofi)
        )
        if (bmp != null) androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
        else null
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val icon = when (page) {
                        0 -> Icons.Outlined.Email
                        1 -> Icons.Outlined.AccountTree
                        2 -> Icons.Outlined.DashboardCustomize
                        3 -> Icons.Outlined.VolunteerActivism
                        else -> Icons.Outlined.Security
                    }
                    val title = when (page) {
                        0 -> "Welcome to Monomail"
                        1 -> "Why Monomail?"
                        2 -> "Modern Layout & Features"
                        3 -> "Support Monomail"
                        else -> "Permissions & Setup"
                    }
                    val description = when (page) {
                        0 -> "An open-source, privacy-first email client built for modern Android. We're currently in active beta, rapidly evolving with community feedback."
                        1 -> "Seamlessly manage all your accounts in one place with our Unified Inbox. Enjoy full support for Gmail, Outlook, and IMAP/SMTP, with more providers on the way."
                        2 -> "Experience an elegant Material 3 Expressive design with a customizable bottom dock. Packed with powerful tools like undo send, swipe gestures, long-press menus, custom templates, and scheduled send."
                        3 -> "Monomail is free, open-source, and built with privacy in mind. If you find it useful, here is how you can support its active development."
                        else -> "To provide real-time alerts with contact names and ensure push notifications arrive instantly even when the app is closed, Monomail requires background permissions."
                    }

                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    if (page == 3) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OnboardingSupportCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Buy me a coffee",
                                    onClick = { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }
                                ) {
                                    if (kofiIcon != null) {
                                        Icon(
                                            painter = kofiIcon,
                                            contentDescription = null,
                                            modifier = Modifier.size(22.dp),
                                            tint = Color.Unspecified
                                        )
                                    } else {
                                        Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(22.dp))
                                    }
                                }
                                OnboardingSupportCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Pay with UPI",
                                    onClick = { uriHandler.openUri("upi://pay?pa=shrivatsav@slc&pn=Sharan%20Shrivatsav&mode=02") }
                                ) {
                                    Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(22.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OnboardingSupportCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Star on GitHub",
                                    onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") }
                                ) {
                                    Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(22.dp))
                                }
                                OnboardingSupportCard(
                                    modifier = Modifier.weight(1f),
                                    label = "Join Discord",
                                    onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") }
                                ) {
                                    Icon(Icons.Outlined.HeadsetMic, contentDescription = null, modifier = Modifier.size(22.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                OnboardingSupportIconAction(
                                    icon = Icons.Outlined.Share,
                                    contentDescription = "Share Monomail",
                                    onClick = {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                android.content.Intent.EXTRA_TEXT,
                                                "Check out Monomail - a private, open-source email client: https://github.com/shrivatsav-0/monomail"
                                            )
                                        }
                                        context.startActivity(android.content.Intent.createChooser(intent, "Share Monomail"))
                                    }
                                )
                                OnboardingSupportIconAction(
                                    icon = Icons.Outlined.BugReport,
                                    contentDescription = "Report Issue",
                                    onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail/issues") }
                                )
                                OnboardingSupportIconAction(
                                    icon = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "Donate Crypto (BASE)",
                                    onClick = {
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", "0xB27Ba9241de81F6DBCB322aDd76a9d9686462e9E"))
                                        android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }
                        }
                    }

                    if (page == 4) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                    } else {
                                        permissionsGranted = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.9f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (permissionsGranted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (permissionsGranted) Icons.Outlined.CheckCircle else Icons.Outlined.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (permissionsGranted) "Notifications Granted" else "Grant Notifications",
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isIgnoringBatteryOptimizations) {
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                            isIgnoringBatteryOptimizations = true
                                        } catch (e: Exception) {
                                            android.util.Log.e("Onboarding", "Failed to launch battery settings", e)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(0.9f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isIgnoringBatteryOptimizations) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isIgnoringBatteryOptimizations) Icons.Outlined.CheckCircle else Icons.Outlined.BatteryChargingFull,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isIgnoringBatteryOptimizations) "Battery Optimization Disabled" else "Disable Battery Optimization",
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicator dots
                Row(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(5) { index ->
                        val selected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(if (selected) 12.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (pagerState.currentPage > 0) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(64.dp))
                    }

                    if (pagerState.currentPage < 4) {
                        Button(
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Next", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    } else {
                        Button(
                            onClick = onFinishOnboarding,
                            shape = RoundedCornerShape(16.dp),
                            enabled = permissionsGranted
                        ) {
                            Text("Get Started", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingSupportCard(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OnboardingSupportIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
