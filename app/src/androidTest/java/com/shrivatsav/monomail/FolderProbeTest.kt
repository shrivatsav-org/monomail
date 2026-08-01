package com.shrivatsav.monomail

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.network.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.security.SecurityUtil
import jakarta.mail.Folder
import jakarta.mail.Session
import jakarta.mail.Store
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Properties

/**
 * Ground truth: connects to the stored IMAP account and lists every folder
 * with its message count, plus per-folder counts for the last 7 days.
 * No DB involvement — pure server state.
 */
@RunWith(AndroidJUnit4::class)
class FolderProbeTest {

    @Test
    fun probeFolders(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val accountManager = AccountManager(context)
        val accounts = accountManager.getAccounts()
        val account = accounts.firstOrNull { it.provider == "imap" }
            ?: throw IllegalStateException("No IMAP account stored: ${accounts.map { it.email }}")
        val config = ImapAccountConfig.fromJson(SecurityUtil.decryptString(account.accessToken)!!)
        val password = SecurityUtil.decryptString(account.refreshToken)!!

        val props = Properties().apply {
            setProperty("mail.store.protocol", "imaps")
            setProperty("mail.imaps.host", config.imapHost)
            setProperty("mail.imaps.port", config.imapPort.toString())
            setProperty("mail.imaps.ssl.enable", "true")
            setProperty("mail.imaps.connectiontimeout", "15000")
            setProperty("mail.imaps.timeout", "15000")
        }
        val store: Store = Session.getInstance(props).getStore("imaps")
        store.connect(config.username, password)

        val sb = StringBuilder()
        val cutoff = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val defaultFolder = store.defaultFolder

        fun walk(parent: Folder, depth: Int) {
            val children = parent.list("*")
            for (f in children) {
                val total = try { if (f.exists()) f.messageCount else 0 } catch (_: Exception) { 0 }
                val recent = try {
                    if (f.exists()) {
                        f.open(Folder.READ_ONLY)
                        val n = try {
                            f.search(jakarta.mail.search.ReceivedDateTerm(
                                jakarta.mail.search.ComparisonTerm.GE,
                                java.util.Date(cutoff)
                            )).size
                        } finally { if (f.isOpen) f.close(false) }
                        n
                    } else 0
                } catch (_: Exception) { 0 }
                sb.append("  ".repeat(depth)).append("${f.fullName} | total=$total | last7d=$recent\n")
                walk(f, depth + 1)
            }
        }
        walk(defaultFolder, 0)

        store.close()

        val outDir = context.getExternalFilesDir(null) ?: error("no external files dir")
        val outFile = File(outDir, "folder_probe.txt")
        outFile.writeText(sb.toString())
        android.util.Log.i("FolderProbe", "Wrote folder probe:\n$sb")
    }
}
