package com.example.agent

import android.content.Context

class CommandHandler(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val registry = CommandRegistry(ctx, telegram)

    fun handle(cmd: String) {
        val trimmed = cmd.trim()
        val withoutSlash = if (trimmed.startsWith("/")) trimmed.substring(1) else trimmed

        val handled = registry.handle(withoutSlash)

        if (!handled) {
            telegram.sendText("Unknown command: $cmd\nType /help")
        }
    }
}