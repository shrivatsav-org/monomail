package com.shrivatsav.monomail.feature.attachment

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.core.data.attachment.AttachmentCacheManager
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.ui.components.AttachmentCategory
import com.shrivatsav.monomail.ui.components.classifyAttachment
import com.shrivatsav.monomail.ui.components.isPreviewableInApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed class AttachmentViewerState {
    data object Loading : AttachmentViewerState()
    data class Ready(
        val category: AttachmentCategory,
        val uri: android.net.Uri,
        val file: File,
        val name: String
    ) : AttachmentViewerState()

    data class Fallback(
        val name: String,
        val mimeType: String,
        val size: Long,
        val bytes: ByteArray?
    ) : AttachmentViewerState()

    data class Error(val message: String) : AttachmentViewerState()
}

@HiltViewModel
class AttachmentViewerViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val cacheManager: AttachmentCacheManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val messageId: String = savedStateHandle["messageId"] ?: ""
    private val attachmentId: String = savedStateHandle["attachmentId"] ?: ""
    private val mimeType: String = savedStateHandle["mimeType"] ?: "application/octet-stream"
    private val name: String = savedStateHandle["name"] ?: "attachment"

    private val _state = MutableStateFlow<AttachmentViewerState>(AttachmentViewerState.Loading)
    val state: StateFlow<AttachmentViewerState> = _state.asStateFlow()

    init {
        loadAttachment()
    }

    private fun loadAttachment() {
        viewModelScope.launch {
            _state.value = AttachmentViewerState.Loading
            try {
                val category = classifyAttachment(mimeType, name)
                val bytes = withContext(Dispatchers.IO) {
                    repository.getAttachmentBytes(messageId, attachmentId)
                }

                if (bytes == null || bytes.isEmpty()) {
                    _state.value = AttachmentViewerState.Error("Failed to load attachment")
                    return@launch
                }

                if (isPreviewableInApp(category)) {
                    val file = withContext(Dispatchers.IO) {
                        cacheManager.cacheFile(messageId, attachmentId, name, bytes)
                    }
                    val uri = withContext(Dispatchers.IO) {
                        cacheManager.cacheBytes(messageId, attachmentId, name, bytes)
                    }
                    _state.value = AttachmentViewerState.Ready(
                        category = category,
                        uri = uri,
                        file = file,
                        name = name
                    )
                } else {
                    _state.value = AttachmentViewerState.Fallback(
                        name = name,
                        mimeType = mimeType,
                        size = bytes.size.toLong(),
                        bytes = bytes
                    )
                }
            } catch (e: Exception) {
                Log.e("AttachmentViewerVM", "Failed to load attachment", e)
                _state.value = AttachmentViewerState.Error(
                    e.message ?: "Unknown error loading attachment"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cacheManager.cleanExcept(emptySet())
    }
}
