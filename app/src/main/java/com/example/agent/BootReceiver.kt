package com.example.agent

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.agent.ErrorReporter

class BootReceiver : BroadcastReceiver() {

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
            val serviceIntent = Intent(context, AgentService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            ErrorReporter.report(e, "BootReceiver")
            // Service start failed silently
        }
    }
}