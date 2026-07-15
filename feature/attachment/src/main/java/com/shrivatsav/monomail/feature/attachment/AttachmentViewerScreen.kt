package com.shrivatsav.monomail.feature.attachment

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.shrivatsav.monomail.ui.components.AttachmentCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentViewerScreen(
    viewModel: AttachmentViewerViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    when (val s = state) {
        is AttachmentViewerState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { },
                        navigationIcon = {
                            androidx.compose.material3.IconButton(onClick = onBack) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        is AttachmentViewerState.Ready -> {
            when (s.category) {
                AttachmentCategory.IMAGE -> PhotoViewerScreen(
                    uri = s.uri,
                    name = s.name,
                    onBack = onBack
                )

                AttachmentCategory.VIDEO -> VideoPlayerScreen(
                    uri = s.uri,
                    name = s.name,
                    onBack = onBack
                )

                AttachmentCategory.PDF -> PdfViewerScreen(
                    file = s.file,
                    name = s.name,
                    onBack = onBack
                )

                else -> FallbackScreen(
                    name = s.name,
                    mimeType = "",
                    size = 0,
                    bytes = null,
                    onBack = onBack
                )
            }
        }

        is AttachmentViewerState.Fallback -> FallbackScreen(
            name = s.name,
            mimeType = s.mimeType,
            size = s.size,
            bytes = s.bytes,
            onBack = onBack
        )

        is AttachmentViewerState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Attachment") },
                        navigationIcon = {
                            androidx.compose.material3.IconButton(onClick = onBack) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = s.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
