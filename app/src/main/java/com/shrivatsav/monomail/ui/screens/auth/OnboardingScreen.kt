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
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import com.shrivatsav.monomail.ui.components.IllustrationType
import com.shrivatsav.monomail.ui.components.MonoIllustration
import com.shrivatsav.monomail.ui.theme.MonoOpacity
import com.shrivatsav.monomail.ui.theme.MonoSpring
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

private const val TOTAL_PAGES = 5

private data class PageContent(val illustration: IllustrationType, val title: String, val description: String)

private val pageContents = listOf(
    PageContent(IllustrationType.ENVELOPE, "Welcome to Monomail", "An open-source, privacy-first email client built for modern Android. We're currently in active beta, rapidly evolving with community feedback."),
    PageContent(IllustrationType.CONNECTION, "Why Monomail?", "Seamlessly manage all your accounts in one place with our Unified Inbox. Enjoy full support for Gmail, Outlook, and IMAP/SMTP, with more providers on the way."),
    PageContent(IllustrationType.PAPER_PLANE, "Modern Layout & Features", "Experience an elegant Material 3 Expressive design with a customizable bottom dock. Packed with powerful tools like undo send, swipe gestures, long-press menus, custom templates, and scheduled send."),
    PageContent(IllustrationType.ENVELOPE, "Support Monomail", "Monomail is free, open-source, and built with privacy in mind. If you find it useful, here is how you can support its active development."),
    PageContent(IllustrationType.SHIELD, "Permissions & Setup", "To provide real-time alerts with contact names and ensure push notifications arrive instantly even when the app is closed, Monomail requires background permissions.")
)

@Composable
private fun SupportPage(uriHandler: androidx.compose.ui.platform.UriHandler, context: android.content.Context, kofiIcon: androidx.compose.ui.graphics.painter.Painter?) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingSupportCard(modifier = Modifier.weight(1f), label = "Buy me a coffee", onClick = { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }) {
                if (kofiIcon != null) Icon(painter = kofiIcon, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.Unspecified)
                else Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            OnboardingSupportCard(modifier = Modifier.weight(1f), label = "Pay with UPI", onClick = { uriHandler.openUri("upi://pay?pa=shrivatsav@slc&pn=Sharan%20Shrivatsav&mode=02") }) {
                Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OnboardingSupportCard(modifier = Modifier.weight(1f), label = "Star on GitHub", onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") }) {
                Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            OnboardingSupportCard(modifier = Modifier.weight(1f), label = "Join Discord", onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") }) {
                Icon(Icons.Rounded.HeadsetMic, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            OnboardingSupportIconAction(
                icon = Icons.Rounded.Share,
                contentDescription = "Share Monomail",
                onClick = {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Check out Monomail - a private, open-source email client: https://github.com/shrivatsav-0/monomail")
                    }
                    context.startActivity(Intent.createChooser(intent, "Share Monomail"))
                }
            )
            OnboardingSupportIconAction(
                icon = Icons.Rounded.BugReport,
                contentDescription = "Report Issue",
                onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail/issues") }
            )
            OnboardingSupportIconAction(
                icon = Icons.Rounded.AccountBalanceWallet,
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

@Composable
private fun PermissionsPage(
    permissionsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestBatteryOptimization: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PermissionButton(
            granted = permissionsGranted,
            onClick = onRequestNotifications,
            grantedLabel = "Notifications Granted",
            defaultLabel = "Grant Notifications",
            grantedIcon = Icons.Rounded.CheckCircle,
            defaultIcon = Icons.Rounded.Notifications
        )
        PermissionButton(
            granted = isIgnoringBatteryOptimizations,
            onClick = onRequestBatteryOptimization,
            grantedLabel = "Battery Optimization Disabled",
            defaultLabel = "Disable Battery Optimization",
            grantedIcon = Icons.Rounded.CheckCircle,
            defaultIcon = Icons.Rounded.BatteryChargingFull
        )
    }
}

@Composable
private fun PermissionButton(granted: Boolean, onClick: () -> Unit, grantedLabel: String, defaultLabel: String, grantedIcon: androidx.compose.ui.graphics.vector.ImageVector, defaultIcon: androidx.compose.ui.graphics.vector.ImageVector) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.9f),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
    ) {
        Icon(imageVector = if (granted) grantedIcon else defaultIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = if (granted) grantedLabel else defaultLabel, modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun PagerControls(currentPage: Int, onPrev: () -> Unit, onNext: () -> Unit, onFinish: () -> Unit, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        if (currentPage > 0) TextButton(onClick = onPrev) { Text("Back") }
        else Spacer(modifier = Modifier.width(64.dp))

        if (currentPage < TOTAL_PAGES - 1) Button(onClick = onNext, shape = RoundedCornerShape(16.dp)) {
            Text("Next", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else {
            val transition = rememberInfiniteTransition(label = "pulse_transition")
            val scale by transition.animateFloat(
                initialValue = 1f,
                targetValue = if (enabled) 1.03f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_scale"
            )
            
            Button(
                onClick = onFinish, 
                shape = RoundedCornerShape(16.dp), 
                enabled = enabled, 
                modifier = Modifier.graphicsLayer { 
                    scaleX = scale 
                    scaleY = scale 
                }
            ) {
                Text("Get Started", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun PageIndicatorDots(currentPage: Int) {
    Row(modifier = Modifier.padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
        repeat(TOTAL_PAGES) { index ->
            val selected = currentPage == index
            val width by animateDpAsState(
                targetValue = if (selected) 24.dp else 8.dp,
                animationSpec = MonoSpring.bouncy(),
                label = "indicatorWidth"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinishOnboarding: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TOTAL_PAGES })

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

    val isIgnoringBatteryOptimizations by remember {
        mutableStateOf(context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true)
    }

    val kofiIcon = remember {
        android.graphics.BitmapFactory.decodeStream(context.resources.openRawResource(com.shrivatsav.monomail.R.raw.kofi))
            ?.asImageBitmap()?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) }
    }

    Scaffold(modifier = Modifier.fillMaxSize(), containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingPage(
                    page = page,
                    uriHandler = uriHandler,
                    context = context,
                    kofiIcon = kofiIcon,
                    permissionsState = PermissionsState(granted = permissionsGranted, batteryOptimizationIgnored = isIgnoringBatteryOptimizations),
                    onRequestNotifications = { requestNotifications(context, permissionLauncher) { permissionsGranted = true } },
                    onRequestBatteryOptimization = { requestBatteryOptimization(context, isIgnoringBatteryOptimizations) },
                    pageOffset = pageOffset
                )
            }

            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                PageIndicatorDots(pagerState.currentPage)
                PagerControls(
                    currentPage = pagerState.currentPage,
                    onPrev = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    onFinish = onFinishOnboarding,
                    enabled = permissionsGranted
                )
            }
        }
    }
}

private data class PermissionsState(val granted: Boolean, val batteryOptimizationIgnored: Boolean)

@Composable
private fun OnboardingPage(
    page: Int,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    context: android.content.Context,
    kofiIcon: androidx.compose.ui.graphics.painter.Painter?,
    permissionsState: PermissionsState,
    onRequestNotifications: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    pageOffset: Float = 0f
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val content = pageContents[page]

        Box(
            modifier = Modifier
                .height(200.dp)
                .fillMaxWidth()
                .offset(x = (pageOffset * -40).dp), // subtle parallax
            contentAlignment = Alignment.Center
        ) {
            MonoIllustration(type = content.illustration, size = 160.dp, animated = true)
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = content.title, 
            style = MaterialTheme.typography.headlineMedium, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.onBackground, 
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(x = (pageOffset * -20).dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = content.description, 
            style = MaterialTheme.typography.bodyLarge, 
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary), 
            textAlign = TextAlign.Center,
            modifier = Modifier.offset(x = (pageOffset * -10).dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Box(modifier = Modifier.offset(x = (pageOffset * -5).dp)) {
            when (page) {
                3 -> SupportPage(uriHandler, context, kofiIcon)
                4 -> PermissionsPage(
                    permissionsGranted = permissionsState.granted,
                    onRequestNotifications = onRequestNotifications,
                    isIgnoringBatteryOptimizations = permissionsState.batteryOptimizationIgnored,
                    onRequestBatteryOptimization = onRequestBatteryOptimization
                )
            }
        }
    }
}

private fun requestNotifications(
    context: android.content.Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    onGranted: () -> Unit
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
    } else {
        onGranted()
    }
}

private fun requestBatteryOptimization(context: android.content.Context, isIgnoring: Boolean) {
    if (!isIgnoring) {
        try {
            context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") })
        } catch (e: Exception) {
            android.util.Log.e("Onboarding", "Failed to launch battery settings", e)
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
