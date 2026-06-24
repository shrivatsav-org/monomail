package com.shrivatsav.monomail
import android.app.Application
import dagger.hilt.android.HiltAndroidApp
@HiltAndroidApp
class MonoMailApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initializeMailcap()
        System.loadLibrary("sqlcipher")
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