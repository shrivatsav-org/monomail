package com.shrivatsav.monomail.push

import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.shrivatsav.monomail.worker.EmailSyncWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FcmMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushNotificationManager: PushNotificationManager

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i("FcmService", "Received new FCM token")
        CoroutineScope(Dispatchers.IO).launch {
            pushNotificationManager.onTokenRefresh(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        Log.i("FcmService", "onMessageReceived called! Raw data map: $data")
        val accountId = data["accountId"]
        if (accountId == null) {
            Log.e("FcmService", "accountId missing from message data! Cannot enqueue sync worker.")
            return
        }
        Log.i("FcmService", "Received push notification for account: $accountId")

        val workRequest = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInputData(Data.Builder().putString(EmailSyncWorker.KEY_ACCOUNT_ID, accountId).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "push_sync_$accountId",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }
}
