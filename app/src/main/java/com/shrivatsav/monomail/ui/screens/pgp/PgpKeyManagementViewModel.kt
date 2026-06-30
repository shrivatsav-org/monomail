package com.shrivatsav.monomail.ui.screens.pgp

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.pgp.PgpKeyInfo
import com.shrivatsav.monomail.data.pgp.PgpKeyManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PgpKeyManagementViewModel @Inject constructor(
    private val keyManager: PgpKeyManager
) : ViewModel() {

    private val _keys = MutableStateFlow<List<PgpKeyInfo>>(emptyList())
    val keys: StateFlow<List<PgpKeyInfo>> = _keys.asStateFlow()

    private val _exportedKey = MutableStateFlow<String?>(null)
    val exportedKey: StateFlow<String?> = _exportedKey.asStateFlow()

    init {
        refreshKeys()
    }

    private fun refreshKeys() {
        _keys.value = keyManager.listKeys().sortedByDescending { it.creationDate }
    }

    fun generateKey(userId: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keyManager.generateKeyPair(userId.trim())
            }
            refreshKeys()
        }
    }

    fun importKey(armoredKey: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keyManager.importKey(armoredKey.trim())
            }
            refreshKeys()
        }
    }

    fun exportKey(fingerprint: String) {
        viewModelScope.launch {
            val armored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keyManager.exportPublicKey(fingerprint)
            }
            _exportedKey.value = armored
        }
    }

    fun clearExportedKey() {
        _exportedKey.value = null
    }

    fun deleteKey(fingerprint: String) {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                keyManager.deleteKey(fingerprint)
            }
            refreshKeys()
        }
    }

    fun getPgpManager(): PgpKeyManager = keyManager
}
