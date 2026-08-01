package com.shrivatsav.monomail.core.data.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Asks the user to exempt the app from battery optimization (Doze), so
 * background IMAP sync and body downloads keep running after the app closes.
 * Fires the system dialog; no-op if the app is already exempt or the intent
 * can't be launched (e.g. OEMs that disable it).
 */
object BatteryOptimization {
    fun requestExemptionIfNeeded(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        if (pm.isIgnoringBatteryOptimizations(context.packageName)) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d("BatteryOptimization", "Requesting battery optimization exemption")
        } catch (e: Exception) {
            Log.w("BatteryOptimization", "Could not launch battery optimization request: ${e.message}")
        }
    }
}
