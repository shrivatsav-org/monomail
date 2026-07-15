package com.shrivatsav.monomail

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            exception.printStackTrace(pw)
            val stackTrace = sw.toString()

            val crashFile = File(context.cacheDir, "last_crash.txt")
            crashFile.writeText(stackTrace)

            val intent = Intent(context, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)

            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    companion object {
        fun install(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, defaultHandler))
            }
        }
        
        fun getLastError(context: Context): String? {
            val crashFile = File(context.cacheDir, "last_crash.txt")
            return if (crashFile.exists()) {
                crashFile.readText()
            } else null
        }
        
        fun clearError(context: Context) {
            val crashFile = File(context.cacheDir, "last_crash.txt")
            if (crashFile.exists()) {
                crashFile.delete()
            }
        }
    }
}
