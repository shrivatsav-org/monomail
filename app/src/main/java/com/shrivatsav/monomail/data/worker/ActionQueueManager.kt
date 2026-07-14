package com.shrivatsav.monomail.data.worker

import android.util.Log
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.core.database.local.PendingActionDao
import com.shrivatsav.monomail.core.database.local.PendingActionEntity
import com.shrivatsav.monomail.core.database.local.PendingActionStatus
import com.shrivatsav.monomail.core.database.local.PendingActionType
import com.shrivatsav.monomail.core.network.provider.EmailProvider
import com.shrivatsav.monomail.core.network.provider.ResourceNotFoundException
import com.shrivatsav.monomail.core.network.remote.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.jvm.JvmSuppressWildcards
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActionQueueManager @Inject constructor(
    private val pendingActionDao: PendingActionDao,
    private val accountManager: AccountManager,
    private val providerFactory: (@JvmSuppressWildcards (com.shrivatsav.monomail.auth.UserProfile) -> EmailProvider)
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null
    private val tag = "ActionQueue"

    fun getPendingFlow(): Flow<List<PendingActionEntity>> = pendingActionDao.getPendingFlow()

    fun getFailedCountFlow(): Flow<Int> = pendingActionDao.getFailedCountFlow()

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            Log.d(tag, "Queue processor started")
            while (isActive) {
                try {
                    val action = pendingActionDao.getNextPending()
                    if (action == null) {
                        delay(2000)
                        continue
                    }
                    processAction(action)
                } catch (e: Exception) {
                    Log.e(tag, "Queue loop error", e)
                    delay(5000)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun processAction(action: PendingActionEntity) {
        pendingActionDao.updateStatus(action.id, PendingActionStatus.IN_FLIGHT)
        Log.d(tag, "Processing ${action.actionType} for thread ${action.threadId}")
        try {
            val accounts = accountManager.getAccounts()
            val profile = accounts.find { it.id == action.accountId }
            if (profile == null) {
                pendingActionDao.delete(action.id)
                return
            }
            val provider = providerFactory(profile)
            when (action.actionType) {
                PendingActionType.TOGGLE_STAR -> {
                    val starred = action.payload.toBoolean()
                    provider.toggleStar(action.threadId, starred)
                }
                PendingActionType.MARK_READ -> {
                    if (action.emailIdsJson.isNotBlank()) {
                        val emailIds = action.emailIdsJson.split(",").filter { it.isNotBlank() }
                        provider.batchMarkRead(emailIds)
                    } else {
                        provider.markRead(action.threadId, true)
                    }
                }
                PendingActionType.MARK_UNREAD -> {
                    provider.markRead(action.threadId, false)
                }
                PendingActionType.ARCHIVE -> provider.archiveThread(action.threadId)
                PendingActionType.UNARCHIVE -> provider.unarchiveThread(action.threadId)
                PendingActionType.DELETE -> provider.trashThread(action.threadId)
                PendingActionType.RESTORE -> provider.restoreThread(action.threadId)
                PendingActionType.SNOOZE -> {
                    // Provider-level snooze is not yet supported; the pending action
                    // keeps the thread excluded during sync cycles so the local
                    // snoozed state is not overwritten by the server response.
                }
                PendingActionType.UNSNOOZE -> {
                    // No-op pending action to gate sync overwrites during the
                    // brief window before the action is processed and deleted.
                }
            }
            pendingActionDao.delete(action.id)
            Log.d(tag, "Completed ${action.actionType} for thread ${action.threadId}")
        } catch (e: ResourceNotFoundException) {
            Log.w(tag, "Resource gone for ${action.id} — already applied locally, deleting")
            pendingActionDao.delete(action.id)
        } catch (e: RetrofitClient.AuthFailedException) {
            Log.w(tag, "Auth failure for ${action.id}, marking FAILED")
            pendingActionDao.updateStatus(action.id, PendingActionStatus.FAILED)
        } catch (e: Exception) {
            Log.e(tag, "Failed ${action.actionType} for thread ${action.threadId}", e)
            if (action.retryCount >= 5) {
                pendingActionDao.updateStatus(action.id, PendingActionStatus.FAILED)
            } else {
                pendingActionDao.incrementRetry(action.id, e.message ?: "Unknown error")
            }
        }
    }
}
