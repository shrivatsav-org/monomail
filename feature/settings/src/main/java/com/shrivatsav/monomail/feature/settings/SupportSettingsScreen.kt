package com.shrivatsav.monomail.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SupportSettingsScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val kofiIcon = remember { android.graphics.BitmapFactory.decodeStream(context.resources.openRawResource(com.shrivatsav.monomail.core.designsystem.R.raw.kofi))?.asImageBitmap()?.let { androidx.compose.ui.graphics.painter.BitmapPainter(it) } }

    val discordIcon: Painter = painterResource(com.shrivatsav.monomail.core.designsystem.R.drawable.ic_discord)

    ScrollableSettingsScaffold(title = "Support & Donate", onBack = onBack) {
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GroupLabel(text = "Donate")
                SupportButton(label = "Buy me a coffee", onClick = { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }, primary = true) { m ->
                    if (kofiIcon != null) Icon(painter = kofiIcon, contentDescription = null, modifier = m, tint = Color.Unspecified)
                    else Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = m)
                }
                SupportButton(label = "Donate Crypto (BASE)", onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", "0xB27Ba9241de81F6DBCB322aDd76a9d9686462e9E"))
                    android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                }, primary = true) { m -> Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, modifier = m) }

                Spacer(modifier = Modifier.height(8.dp))

                GroupLabel(text = "Community")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(
                        onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = "GitHub", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(
                        onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(painter = discordIcon, contentDescription = "Discord", tint = Color.Unspecified)
                    }
                    IconButton(
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, "Check out Monomail — a private, open-source email client: https://github.com/shrivatsav-0/monomail")
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share via"))
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
