package com.android.systemui.updater.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.android.systemui.updater.util.Obfuscator

/**
 * Monitors device security state (root, emulator, debuggable, etc.)
 */
class SecurityMonitor(private val context: Context) {

    /**
     * Checks if the device is rooted.
     * @return true if rooted, false otherwise
     */
    fun isDeviceRooted(): Boolean {
        // Check common root indicators
        val rootCheck = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system/su",
            "/su/bin/su",
            "/system/sudo",
            "/system/bin/failsafe/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/sd/bin/su",
            "/system/bin/failsafe/su",
            "/system/usr/bin/we-need-root",
            "/system/xbin/which",
            "/system/bin/which"
        )
        rootCheck.forEach { path ->
            if (java.io.File(path).exists()) return true
        }
        // Check for su using which
        val su = "su"
        val output = executeCommand("which $su")
        return output.contains("su")
    }

    /**
     * Checks if the device is an emulator.
     * @return true if emulator, false otherwise
     */
    fun isEmulator(): Boolean {
        return Build.BRAND.startsWith("generic") ||
                Build.DEVICE.startsWith("generic") ||
                Build.HARDWARE.contains("goldfish") ||
                Build.HARDWARE.contains("vbox86") ||
                Build.HARDWARE.contains("virtual") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                Build.PRODUCT.contains("sdk") ||
                Build.PRODUCT.contains("google_sdk") ||
                Build.PRODUCT.contains("sdk_google") ||
                Build.PRODUCT.contains("aosp") ||
                Build.PRODUCT.contains("sdk_x86") ||
                Build.PRODUCT.contains("vbox86p") ||
                Build.PRODUCT.contains("emulator") ||
                Build.PRODUCT.contains("simulator")
    }

    /**
     * Checks if the device is debuggable.
     * @return true if debuggable, false otherwise
     */
    fun isDebuggable(): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Checks if developer options are enabled.
     * @return true if developer options enabled, false otherwise
     */
    fun areDeveloperOptionsEnabled(): Boolean {
        val secure = Settings.Secure
        return Settings.Secure.getInt(
                context.contentResolver,
                secure.DEVELOPMENT_SETTINGS_ENABLED,
                0) != 0
    }

    /**
     * Checks if the device is secured (not rooted, not emulator, not debuggable, etc.)
     * @return true if secured, false otherwise
     */
    fun isDeviceSecured(): Boolean {
        return !isDeviceRooted() && !isEmulator() && !isDebuggable() && !areDeveloperOptionsEnabled()
    }

    /**
     * Executes a shell command and returns the output.
     * @param command The command to execute
     * @return The command output as a string
     */
    private fun executeCommand(command: String): String {
        val process = Runtime.getRuntime().exec(command)
        val inputStream = process.inputStream
        val bytes = inputStream.readBytes()
        return String(bytes)
    }
}