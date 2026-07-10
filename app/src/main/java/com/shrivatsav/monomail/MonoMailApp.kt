package com.shrivatsav.monomail
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.shrivatsav.monomail.data.worker.ActionQueueManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
@HiltAndroidApp
class MonoMailApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var actionQueueManager: ActionQueueManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        initializeMailcap()
        actionQueueManager.start()
    }

    private fun initializeMailcap() {
        val mc = jakarta.activation.CommandMap.getDefaultCommandMap() as jakarta.activation.MailcapCommandMap
        mc.addMailcap("text/html;; x-java-content-handler=org.eclipse.angus.mail.handlers.text_html")
        mc.addMailcap("text/xml;; x-java-content-handler=org.eclipse.angus.mail.handlers.text_xml")
        mc.addMailcap("text/plain;; x-java-content-handler=org.eclipse.angus.mail.handlers.text_plain")
        mc.addMailcap("multipart/*;; x-java-content-handler=org.eclipse.angus.mail.handlers.multipart_mixed")
        mc.addMailcap("message/rfc822;; x-java-content-handler=org.eclipse.angus.mail.handlers.message_rfc822")
        jakarta.activation.CommandMap.setDefaultCommandMap(mc)
    }
}